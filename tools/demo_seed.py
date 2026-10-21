"""Carga de datos de demostración (`python tools/cli.py sembrar-demo`).

Los usuarios de demostración se insertan en la base (no hay API de usuarios);
todo lo demás se crea a través de la API del backend en ejecución, de modo que
se aplican las mismas reglas de negocio, movimientos y auditoría que en el uso
normal. Es idempotente: los datos maestros se buscan antes de crearlos y las
operaciones (compras, ventas, salidas y ajustes) solo se cargan si la base aún
no tiene órdenes de compra.
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from collections.abc import Callable
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

import bcrypt
import psycopg

if __package__:
    from .seed_loader import _id, _insert
else:
    from seed_loader import _id, _insert

Log = Callable[[str], None]


class DemoError(RuntimeError):
    """La carga de demostración no pudo completarse."""


class ConflictoApi(DemoError):
    """La API respondió 409 (el recurso ya existe o el estado no lo permite)."""


def cargar_datos(root: Path) -> dict[str, Any]:
    ruta = root / "db" / "seeds" / "demo.json"
    try:
        return json.loads(ruta.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise DemoError(f"No se pudo leer {ruta}: {exc}") from exc


def validar_datos(datos: dict[str, Any]) -> list[str]:
    """Comprueba las referencias internas del archivo de demostración."""
    errores: list[str] = []
    tipos = {t["codigo"] for t in datos["tipos_producto"]}
    categorias = {
        c["codigo"]: {s["codigo"] for s in c["subcategorias"]} for c in datos["categorias"]
    }
    productos = {p["clave"] for p in datos["productos"]}
    proveedores = {p["clave"] for p in datos["proveedores"]}
    clientes = {c["clave"] for c in datos["clientes"]}
    bodegas = {"GENERAL", *(b["codigo"] for b in datos["bodegas"])}
    usuarios = {u["correo"] for u in datos["usuarios"]}
    for p in datos["productos"]:
        if p["tipo"] not in tipos:
            errores.append(f"Producto {p['clave']}: tipo {p['tipo']} inexistente.")
        if p["subcategoria"] not in categorias.get(p["categoria"], set()):
            errores.append(f"Producto {p['clave']}: subcategoría fuera de {p['categoria']}.")
    for prov in datos["proveedores"]:
        errores += [
            f"Proveedor {prov['clave']}: producto {x} inexistente."
            for x in prov["productos"]
            if x not in productos
        ]
    for compra in datos["compras"]:
        if compra["proveedor"] not in proveedores or compra["bodega"] not in bodegas:
            errores.append(f"Compra {compra['documento']}: proveedor o bodega inexistente.")
        errores += [
            f"Compra {compra['documento']}: producto {x['producto']} inexistente."
            for x in compra["lineas"]
            if x["producto"] not in productos
        ]
    for venta in datos["ventas"]:
        if venta["vendedor"] not in usuarios or (
            venta["cliente"] and venta["cliente"] not in clientes
        ):
            errores.append(f"Venta en {venta['bodega']}: vendedor o cliente inexistente.")
    for ajuste in datos["ajustes"]:
        if ajuste["usuario"] not in usuarios or ajuste["producto"] not in productos:
            errores.append(f"Ajuste de {ajuste['producto']}: usuario o producto inexistente.")
    return errores


class Api:
    """Cliente mínimo de la API REST con sesión JWT."""

    def __init__(self, base: str, correo: str, contrasena: str) -> None:
        self.base = base.rstrip("/")
        self.token = ""
        respuesta = self.post("/api/v1/auth/login", {"correo": correo, "contrasena": contrasena})
        self.token = respuesta["token"]

    def _enviar(self, metodo: str, ruta: str, cuerpo: Any = None) -> Any:
        cabeceras = {"Content-Type": "application/json", "Accept-Language": "es"}
        if self.token:
            cabeceras["Authorization"] = f"Bearer {self.token}"
        datos = None if cuerpo is None else json.dumps(cuerpo).encode("utf-8")
        peticion = urllib.request.Request(
            self.base + ruta, data=datos, headers=cabeceras, method=metodo
        )
        try:
            with urllib.request.urlopen(peticion, timeout=60) as respuesta:
                renovado = respuesta.headers.get("X-Auth-Token")
                if renovado:
                    self.token = renovado
                contenido = respuesta.read()
        except urllib.error.HTTPError as exc:
            detalle = exc.read().decode("utf-8", "replace")
            try:
                detalle = json.loads(detalle).get("detail", detalle)
            except json.JSONDecodeError:
                pass
            clase = ConflictoApi if exc.code == 409 else DemoError
            raise clase(f"{metodo} {ruta} → {exc.code}: {detalle}") from exc
        except urllib.error.URLError as exc:
            raise DemoError(
                f"No se pudo conectar con la API en {self.base}: {exc.reason}. "
                "¿Está el backend en ejecución?"
            ) from exc
        return json.loads(contenido) if contenido else None

    def get(self, ruta: str) -> Any:
        return self._enviar("GET", ruta)

    def post(self, ruta: str, cuerpo: Any = None) -> Any:
        return self._enviar("POST", ruta, cuerpo)

    def put(self, ruta: str, cuerpo: Any) -> Any:
        return self._enviar("PUT", ruta, cuerpo)

    def todos(self, ruta: str) -> list[dict[str, Any]]:
        """Recorre todas las páginas de un listado paginado."""
        separador = "&" if "?" in ruta else "?"
        elementos: list[dict[str, Any]] = []
        pagina = 0
        while True:
            resultado = self.get(f"{ruta}{separador}page={pagina}&size=100")
            elementos += resultado["content"]
            if len(elementos) >= resultado["totalElements"] or not resultado["content"]:
                return elementos
            pagina += 1


def crear_usuarios(
    conexion_bd: dict[str, Any], datos: dict[str, Any], contrasena: str, log: Log
) -> int:
    hash_demo = bcrypt.hashpw(contrasena.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii")
    creados = 0
    with psycopg.connect(**conexion_bd) as conexion:
        idiomas = dict(conexion.execute("SELECT codigo_bcp47, id FROM public.idioma").fetchall())
        for usuario in datos["usuarios"]:
            usuario_id = _id("usuario", usuario["correo"])
            nuevo = _insert(
                conexion,
                "usuario",
                {
                    "id": usuario_id,
                    "correo": usuario["correo"],
                    "nombre": usuario["nombre"],
                    "hash_contrasena": hash_demo,
                    "idioma_id": idiomas.get(usuario.get("idioma", "es")),
                    "intentos_fallidos": 0,
                    "activo": True,
                    "creado_en_utc": datetime.now(UTC),
                },
            )
            rol = conexion.execute(
                "SELECT id FROM public.rol WHERE codigo = %s", (usuario["rol"],)
            ).fetchone()
            if rol is None:
                raise DemoError(f"El rol {usuario['rol']} no existe; ejecute primero crear-bd.")
            _insert(
                conexion,
                "usuario_rol",
                {
                    "id": _id("usuario_rol", f"{usuario['correo']}:{usuario['rol']}"),
                    "usuario_id": usuario_id,
                    "rol_id": rol[0],
                },
            )
            for pais in usuario["paises"]:
                pais_id = conexion.execute(
                    "SELECT id FROM public.pais WHERE codigo_iso = %s", (pais,)
                ).fetchone()[0]
                _insert(
                    conexion,
                    "usuario_pais",
                    {
                        "id": _id("usuario_pais", f"{usuario['correo']}:{pais}"),
                        "usuario_id": usuario_id,
                        "pais_id": pais_id,
                    },
                )
            creados += int(nuevo)
        conexion.commit()
    log(f"Usuarios de demostración nuevos: {creados} de {len(datos['usuarios'])}.")
    return creados


class Cargador:
    def __init__(
        self, api_url: str, admin: Api, datos: dict[str, Any], demo_password: str, log: Log
    ):
        self.api_url = api_url
        self.admin = admin
        self.datos = datos
        self.demo_password = demo_password
        self.log = log
        self.sesiones: dict[str, Api] = {}
        self.paises = {p["codigo"]: p for p in admin.get("/api/v1/referencias/pais")}
        self.monedas = {m["codigo"]: m for m in admin.get("/api/v1/referencias/moneda")}
        self.idiomas = {i["codigo"]: i for i in admin.get("/api/v1/referencias/idioma")}
        self.productos: dict[str, str] = {}
        self.bodegas: dict[str, dict[str, Any]] = {}
        self.proveedores: dict[str, str] = {}
        self.clientes: dict[str, str] = {}

    def como(self, correo: str) -> Api:
        if correo not in self.sesiones:
            self.sesiones[correo] = Api(self.api_url, correo, self.demo_password)
        return self.sesiones[correo]

    # Configuración de Estados Unidos (valores de ejemplo editables).
    def configurar_estados_unidos(self) -> None:
        eeuu = self.datos["estados_unidos"]
        pais = self.admin.get(f"/api/v1/configuracion/paises/{self.paises['US']['id']}")
        impuestos = self.admin.todos("/api/v1/configuracion/impuestos")
        if not any(i["paisId"] == pais["id"] for i in impuestos):
            self.admin.post(
                "/api/v1/configuracion/impuestos",
                {
                    "paisId": pais["id"],
                    "categoriaId": None,
                    "nombre": eeuu["impuesto"]["nombre"],
                    "tasa": eeuu["impuesto"]["tasa"],
                    "incluidoEnPrecio": eeuu["impuesto"]["incluido_en_precio"],
                    "vigenteDesde": "2026-01-01",
                    "vigenteHasta": None,
                },
            )
            self.log(f"Impuesto de Estados Unidos creado ({eeuu['impuesto']['nombre']}).")
        if not pais["habilitado"]:
            self.admin.put(
                f"/api/v1/configuracion/paises/{pais['id']}",
                {
                    "codigoIso": pais["codigoIso"],
                    "nombre": pais["nombre"],
                    "monedaLocalId": pais["monedaLocalId"],
                    "idiomaPredeterminadoId": pais["idiomaPredeterminadoId"],
                    "zonaHoraria": pais["zonaHoraria"],
                    "habilitado": True,
                },
            )
            self.log("Estados Unidos habilitado para vender.")
        usd, cop = self.monedas["USD"]["id"], self.monedas["COP"]["id"]
        tasas = self.admin.todos("/api/v1/configuracion/tipos-cambio")
        if not any(t["monedaOrigenId"] == usd and t["monedaDestinoId"] == cop for t in tasas):
            desde = (datetime.now(UTC) - timedelta(days=1)).replace(microsecond=0).isoformat()
            self.admin.post(
                "/api/v1/configuracion/tipos-cambio",
                {
                    "monedaOrigenId": usd,
                    "monedaDestinoId": cop,
                    "tasa": eeuu["tipo_cambio_usd_cop"],
                    "vigenteDesdeUtc": desde.replace("+00:00", "Z"),
                },
            )
            self.log(f"Tipo de cambio USD→COP de ejemplo: {eeuu['tipo_cambio_usd_cop']}.")

    def catalogo(self) -> None:
        tipos_existentes = {
            t["codigo"]: t for t in self.admin.todos("/api/v1/catalogo/tipos-producto")
        }
        tipos: dict[str, str] = {}
        for tipo in self.datos["tipos_producto"]:
            actual = tipos_existentes.get(tipo["codigo"]) or self.admin.post(
                "/api/v1/catalogo/tipos-producto",
                {"codigo": tipo["codigo"], "nombre": tipo["nombre"], "activo": True},
            )
            tipos[tipo["codigo"]] = actual["id"]
            definidas = {
                d["nombre"]
                for d in self.admin.todos(
                    f"/api/v1/catalogo/definiciones-atributos?tipoProductoId={actual['id']}"
                )
            }
            for atributo in tipo["atributos"]:
                if atributo["nombre"] not in definidas:
                    self.admin.post(
                        "/api/v1/catalogo/definiciones-atributos",
                        {
                            "tipoProductoId": actual["id"],
                            "nombre": atributo["nombre"],
                            "tipoDato": atributo["tipo"],
                            "obligatorio": atributo["obligatorio"],
                            "opciones": atributo.get("opciones"),
                        },
                    )
        categorias_existentes = {
            c["codigo"]: c for c in self.admin.todos("/api/v1/catalogo/categorias")
        }
        categorias: dict[str, str] = {}
        for raiz in self.datos["categorias"]:
            padre = categorias_existentes.get(raiz["codigo"]) or self.admin.post(
                "/api/v1/catalogo/categorias",
                {
                    "codigo": raiz["codigo"],
                    "nombre": raiz["nombre"],
                    "padreId": None,
                    "activo": True,
                },
            )
            categorias[raiz["codigo"]] = padre["id"]
            for sub in raiz["subcategorias"]:
                hija = categorias_existentes.get(sub["codigo"]) or self.admin.post(
                    "/api/v1/catalogo/categorias",
                    {
                        "codigo": sub["codigo"],
                        "nombre": sub["nombre"],
                        "padreId": padre["id"],
                        "activo": True,
                    },
                )
                categorias[sub["codigo"]] = hija["id"]
        nombres = {p["nombre"]: p["id"] for p in self.admin.get("/api/v1/referencias/producto")}
        nuevos = 0
        for producto in self.datos["productos"]:
            if producto["es"]["nombre"] in nombres:
                self.productos[producto["clave"]] = nombres[producto["es"]["nombre"]]
                continue
            creado = self.admin.post(
                "/api/v1/catalogo/productos",
                {
                    "tipoProductoId": tipos[producto["tipo"]],
                    "categoriaId": categorias[producto["categoria"]],
                    "subcategoriaId": categorias[producto["subcategoria"]],
                    "atributos": producto["atributos"],
                    "traducciones": [
                        {"idiomaId": self.idiomas[codigo]["id"], **producto[codigo]}
                        for codigo in ("es", "en")
                    ],
                    "precios": [
                        {
                            "monedaId": self.monedas[moneda]["id"],
                            "importe": importe,
                            "vigenteDesde": "2026-01-01",
                            "vigenteHasta": None,
                        }
                        for moneda, importe in producto["precios"].items()
                    ],
                },
            )
            self.productos[producto["clave"]] = creado["producto"]["id"]
            nuevos += 1
        self.log(
            f"Catálogo: {len(tipos)} tipos, {len(categorias)} categorías, "
            f"{nuevos} productos nuevos."
        )

    def bodegas_y_proveedores(self) -> None:
        existentes = {b["codigo"]: b for b in self.admin.todos("/api/v1/inventario/bodegas")}
        for bodega in self.datos["bodegas"]:
            if bodega["codigo"] not in existentes:
                existentes[bodega["codigo"]] = self.admin.post(
                    "/api/v1/inventario/bodegas",
                    {
                        "codigo": bodega["codigo"],
                        "nombre": bodega["nombre"],
                        "categoriaId": None,
                        "paisId": self.paises[bodega["pais"]]["id"],
                        "activo": True,
                    },
                )
        self.bodegas = existentes
        proveedores = {p["razonSocial"]: p for p in self.admin.todos("/api/v1/proveedores")}
        for prov in self.datos["proveedores"]:
            actual = proveedores.get(prov["razon_social"]) or self.admin.post(
                "/api/v1/proveedores",
                {
                    "razonSocial": prov["razon_social"],
                    "paisId": self.paises[prov["pais"]]["id"],
                    "identificacionTributaria": prov["identificacion"],
                    "personaContacto": prov["contacto"],
                    "correo": prov["correo"],
                    "telefono": prov["telefono"],
                    "monedaCompraId": self.monedas[prov["moneda"]]["id"],
                    "condicionesPago": prov["condiciones"],
                    "activo": True,
                },
            )
            self.proveedores[prov["clave"]] = actual["id"]
            for clave in prov["productos"]:
                try:
                    self.admin.post(
                        f"/api/v1/proveedores/{actual['id']}/productos/{self.productos[clave]}"
                    )
                except ConflictoApi:
                    pass  # El vínculo de proveedor oficial ya existía.
        self.log(f"Bodegas: {len(self.bodegas)}; proveedores: {len(self.proveedores)}.")

    def clientes_demo(self) -> None:
        vendedor = {
            "CO": self.como("vendedor.bogota@pixelvault.example"),
            "US": self.como("ventas.miami@pixelvault.example"),
        }
        for cliente in self.datos["clientes"]:
            api = vendedor[cliente["pais"]]
            existentes = {
                c["correo"]: c for c in api.todos(f"/api/v1/clientes?buscar={cliente['correo']}")
            }
            actual = existentes.get(cliente["correo"]) or api.post(
                "/api/v1/clientes",
                {
                    "paisId": self.paises[cliente["pais"]]["id"],
                    "clasificacion": cliente["clasificacion"],
                    "nombre": cliente["nombre"],
                    "apellidos": cliente.get("apellidos"),
                    "tipoDocumento": cliente.get("tipo_documento"),
                    "documento": cliente.get("documento"),
                    "codigoPostal": cliente.get("codigo_postal"),
                    "direccion": cliente.get("direccion"),
                    "telefono": cliente.get("telefono"),
                    "correo": cliente["correo"],
                    "baseLegal": cliente["base_legal"],
                    "consentimientoRegistrado": True,
                    "activo": True,
                },
            )
            self.clientes[cliente["clave"]] = actual["id"]
        self.log(f"Clientes: {len(self.clientes)}.")

    def operaciones(self) -> bool:
        if self.admin.get("/api/v1/compras/ordenes?page=0&size=1")["totalElements"] > 0:
            self.log(
                "La base ya tiene órdenes de compra: no se cargan compras, ventas, "
                "salidas ni ajustes de demostración."
            )
            return False
        encargado = self.como("encargado.inventario@pixelvault.example")
        for compra in self.datos["compras"]:
            orden = encargado.post(
                "/api/v1/compras/ordenes",
                {
                    "proveedorId": self.proveedores[compra["proveedor"]],
                    "tipoCambio": compra["tipo_cambio"],
                    "lineas": [
                        {
                            "productoId": self.productos[linea["producto"]],
                            "cantidadPedida": linea["cantidad"],
                            "costoUnitario": linea["costo"],
                        }
                        for linea in compra["lineas"]
                    ],
                },
            )
            encargado.post(f"/api/v1/compras/ordenes/{orden['orden']['id']}/enviar")
            por_producto = {linea["producto"]: linea for linea in compra["lineas"]}
            claves = {v: k for k, v in self.productos.items()}
            lineas = []
            for linea in orden["lineas"]:
                cantidad = por_producto[claves[linea["productoId"]]].get(
                    "recibir", linea["cantidadPedida"]
                )
                if cantidad:
                    lineas.append({"ordenCompraLineaId": linea["id"], "cantidad": cantidad})
            encargado.post(
                "/api/v1/compras/entradas",
                {
                    "ordenCompraId": orden["orden"]["id"],
                    "bodegaId": self.bodegas[compra["bodega"]]["id"],
                    "numeroDocumento": compra["documento"],
                    "tipoCambioAplicado": compra["tipo_cambio"],
                    "lineas": lineas,
                },
            )
        self.log(f"Compras recibidas: {len(self.datos['compras'])} órdenes.")

        ventas = 0
        for venta in self.datos["ventas"]:
            api = self.como(venta["vendedor"])
            pais = self.paises[venta["pais"]]
            moneda = api.get("/api/v1/ventas/paises")
            moneda_id = next(p["monedaId"] for p in moneda if p["paisId"] == pais["id"])
            creada = api.post(
                "/api/v1/ventas",
                {
                    "clienteId": self.clientes.get(venta["cliente"]) if venta["cliente"] else None,
                    "paisId": pais["id"],
                    "monedaId": moneda_id,
                    "lineas": [
                        {
                            "productoId": self.productos[linea["producto"]],
                            "bodegaId": self.bodegas[venta["bodega"]]["id"],
                            "cantidad": linea["cantidad"],
                        }
                        for linea in venta["lineas"]
                    ],
                },
            )
            venta_id = creada["venta"]["id"]
            if venta.get("anular"):
                api.post(f"/api/v1/ventas/{venta_id}/anular")
            elif venta["medio_pago"]:
                api.post(f"/api/v1/ventas/{venta_id}/confirmar", {"medioPago": venta["medio_pago"]})
            ventas += 1
        self.log(f"Ventas: {ventas} (confirmadas, una pendiente y una anulada).")

        for salida in self.datos["salidas"]:
            encargado.post(
                "/api/v1/inventario/salidas",
                {
                    "productoId": self.productos[salida["producto"]],
                    "bodegaId": self.bodegas[salida["bodega"]]["id"],
                    "tipo": salida["tipo"],
                    "cantidad": salida["cantidad"],
                    "proveedorId": self.proveedores[salida["proveedor"]],
                    "numeroReporteProveedor": salida["reporte"],
                    "observacion": salida["observacion"],
                },
            )
        for ajuste in self.datos["ajustes"]:
            api = self.como(ajuste["usuario"])
            producto, bodega = (
                self.productos[ajuste["producto"]],
                self.bodegas[ajuste["bodega"]]["id"],
            )
            saldo = api.get(f"/api/v1/inventario/saldos?productoId={producto}&bodegaId={bodega}")[
                "content"
            ][0]
            ruta = "operativos" if ajuste["tipo"] == "OPERATIVO" else "tecnicos"
            creado = api.post(
                f"/api/v1/inventario/ajustes/{ruta}",
                {
                    "productoId": producto,
                    "bodegaId": bodega,
                    "cantidadContada": saldo["cantidad"] + ajuste["diferencia"],
                    "motivo": ajuste["motivo"],
                },
            )
            if ajuste["aprobar"]:
                self.admin.post(f"/api/v1/inventario/ajustes/{creado['id']}/aprobar")
        self.log(f"Salidas: {len(self.datos['salidas'])}; ajustes: {len(self.datos['ajustes'])}.")
        return True


def sembrar_demo(
    root: Path,
    conexion_bd: dict[str, Any],
    api_url: str,
    admin: tuple[str, str],
    demo_password: str,
    log: Log,
) -> None:
    datos = cargar_datos(root)
    errores = validar_datos(datos)
    if errores:
        raise DemoError("db/seeds/demo.json tiene referencias inválidas: " + " ".join(errores))
    crear_usuarios(conexion_bd, datos, demo_password, log)
    cargador = Cargador(api_url, Api(api_url, *admin), datos, demo_password, log)
    cargador.configurar_estados_unidos()
    cargador.catalogo()
    cargador.bodegas_y_proveedores()
    cargador.clientes_demo()
    cargador.operaciones()
