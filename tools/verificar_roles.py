"""Verificación de la entrega: un usuario de cada rol y sus accesos según el Anexo D.

Inicia sesión con el administrador y con los usuarios de demostración de cada rol
contra un backend en ejecución (local, Docker Compose o Render), prueba consultas
GET de cada módulo y revisa los permisos que la sesión recibe. Solo consulta: no
crea ni modifica datos.
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from typing import Any

Pedir = Callable[[str, str, Any, str | None], tuple[int, Any]]

USUARIOS_DEMO = {
    "ENCARGADO_INVENTARIO": "encargado.inventario@pixelvault.example",
    "EXPERTO_TECNICO": "experto.tecnico@pixelvault.example",
    "VENDEDOR_CAJERO": "vendedor.bogota@pixelvault.example",
    "GERENTE_PROPIETARIO": "gerente@pixelvault.example",
}
ROLES = ("ADMINISTRADOR", *USUARIOS_DEMO)
TODOS = frozenset(ROLES)

# Operación del Anexo D, consulta GET y roles que deben tener acceso.
ACCESOS = (
    ("Parametrización", "/api/v1/parametros?size=1", {"ADMINISTRADOR"}),
    ("Consultar catálogo", "/api/v1/catalogo/productos?size=1", TODOS),
    ("Consultar inventario", "/api/v1/inventario/saldos?size=1", TODOS),
    (
        "Proveedores",
        "/api/v1/proveedores?size=1",
        {"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "GERENTE_PROPIETARIO"},
    ),
    (
        "Compras y entradas",
        "/api/v1/compras/ordenes?size=1",
        {"ADMINISTRADOR", "ENCARGADO_INVENTARIO"},
    ),
    ("Ventas", "/api/v1/ventas?size=1", {"ADMINISTRADOR", "VENDEDOR_CAJERO"}),
    ("Clientes", "/api/v1/clientes?size=1", {"ADMINISTRADOR", "VENDEDOR_CAJERO"}),
    (
        "Reportes de inventario",
        "/api/v1/reportes/inventario/existencias?size=1",
        {"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "GERENTE_PROPIETARIO"},
    ),
)

# Acciones sin una consulta GET equivalente: se revisa el permiso de la sesión.
PERMISOS = (
    ("Aprobar ajustes", "AJUSTES_APROBAR", {"ADMINISTRADOR"}),
    ("Solicitar ajuste operativo", "AJUSTE_OPERATIVO_SOLICITAR", {"ENCARGADO_INVENTARIO"}),
    ("Solicitar ajuste técnico", "AJUSTE_TECNICO_SOLICITAR", {"EXPERTO_TECNICO"}),
    (
        "Salidas por pérdida, daño, robo o defecto",
        "SALIDAS_INVENTARIO_GESTIONAR",
        {"ADMINISTRADOR", "ENCARGADO_INVENTARIO"},
    ),
    (
        "Crear y modificar productos",
        "PRODUCTOS_GESTIONAR",
        {"ADMINISTRADOR", "ENCARGADO_INVENTARIO"},
    ),
    ("Anular ventas pendientes", "VENTAS_ANULAR_PENDIENTES", {"ADMINISTRADOR", "VENDEDOR_CAJERO"}),
    (
        "Auditoría de inventario",
        "AUDITORIA_INVENTARIO_LEER",
        {"ADMINISTRADOR", "ENCARGADO_INVENTARIO"},
    ),
    ("Auditoría técnica", "AUDITORIA_TECNICA_LEER", {"ADMINISTRADOR", "EXPERTO_TECNICO"}),
)


@dataclass
class Sesion:
    token: str
    permisos: set[str]


def pedir_http(
    api: str, ruta: str, cuerpo: Any = None, token: str | None = None
) -> tuple[int, Any]:
    peticion = urllib.request.Request(
        api.rstrip("/") + ruta,
        data=None if cuerpo is None else json.dumps(cuerpo).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            **({"Authorization": f"Bearer {token}"} if token else {}),
        },
    )
    try:
        with urllib.request.urlopen(peticion, timeout=90) as respuesta:
            contenido = respuesta.read()
            return respuesta.status, json.loads(contenido) if contenido else None
    except urllib.error.HTTPError as error:
        return error.code, None


def _tabla(titulo: str) -> list[str]:
    return [
        "",
        f"## {titulo}",
        "",
        "| Operación | " + " | ".join(ROLES) + " |",
        "|---|" + "---|" * len(ROLES),
    ]


def verificar_roles(
    api: str,
    admin: tuple[str, str],
    demo_password: str,
    pedir: Pedir | None = None,
) -> tuple[str, int]:
    """Devuelve el informe en Markdown y el número de diferencias con el Anexo D."""
    llamar = pedir or (lambda ruta, cuerpo=None, token=None: pedir_http(api, ruta, cuerpo, token))
    credenciales = {
        "ADMINISTRADOR": admin,
        **{rol: (correo, demo_password) for rol, correo in USUARIOS_DEMO.items()},
    }
    lineas = [
        "# Inicio de sesión por rol y permisos (Anexo D)",
        "",
        f"- API: `{api}` — {datetime.now():%Y-%m-%d %H:%M}",
        "- Generado con `python tools/cli.py verificar-roles`. En las tablas, 200 = acceso"
        " y 403 = acceso denegado; ✅ indica que coincide con el Anexo D.",
        "",
    ]
    diferencias = 0
    sesiones: dict[str, Sesion | None] = {}
    for rol in ROLES:
        correo, clave = credenciales[rol]
        estado, cuerpo = llamar("/api/v1/auth/login", {"correo": correo, "contrasena": clave}, None)
        # El correo del administrador es propio de cada entorno: no se escribe en el informe.
        visible = "ADMIN_EMAIL" if rol == "ADMINISTRADOR" else correo
        if estado != 200 or not cuerpo:
            diferencias += 1
            sesiones[rol] = None
            lineas.append(f"- ❌ {rol}: no se pudo iniciar sesión con `{visible}` (HTTP {estado}).")
            continue
        usuario = cuerpo.get("usuario", {})
        roles = set(usuario.get("roles", []))
        correcto = rol in roles
        diferencias += not correcto
        sesiones[rol] = Sesion(cuerpo["token"], set(usuario.get("permisos", [])))
        lineas.append(
            f"- {'✅' if correcto else '❌'} {rol}: sesión iniciada con `{visible}`"
            f" (roles: {', '.join(sorted(roles))})."
        )

    lineas += _tabla("Accesos a la API")
    for nombre, ruta, permitidos in ACCESOS:
        celdas = []
        for rol in ROLES:
            sesion = sesiones[rol]
            if sesion is None:
                celdas.append("—")
                continue
            estado, _ = llamar(ruta, None, sesion.token)
            correcto = estado == (200 if rol in permitidos else 403)
            diferencias += not correcto
            celdas.append(f"{'✅' if correcto else '❌'} {estado}")
        lineas.append(f"| {nombre} | " + " | ".join(celdas) + " |")

    lineas += _tabla("Permisos de la sesión")
    for nombre, permiso, permitidos in PERMISOS:
        celdas = []
        for rol in ROLES:
            sesion = sesiones[rol]
            if sesion is None:
                celdas.append("—")
                continue
            tiene = permiso in sesion.permisos
            correcto = tiene == (rol in permitidos)
            diferencias += not correcto
            celdas.append(f"{'✅' if correcto else '❌'} {'sí' if tiene else 'no'}")
        lineas.append(f"| {nombre} (`{permiso}`) | " + " | ".join(celdas) + " |")

    resultado = (
        "todo coincide con el Anexo D" if diferencias == 0 else f"{diferencias} diferencia(s)"
    )
    lineas += ["", f"**Resultado:** {resultado}."]
    return "\n".join(lineas) + "\n", diferencias
