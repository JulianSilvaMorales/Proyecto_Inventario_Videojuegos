# PixelVault — Sistema de inventario y ventas para consolas y videojuegos

Aplicación web para administrar catálogo, inventario y ventas con soporte
internacional. El alcance y las decisiones del proyecto están documentados en
[`spec/REQUIREMENTS.md`](spec/REQUIREMENTS.md), el proceso de desarrollo en
[`spec/GUIDE.md`](spec/GUIDE.md) y las instrucciones de trabajo por etapas en
[`spec/INSTRUCTIONS.md`](spec/INSTRUCTIONS.md).

El proyecto se usa en **Windows**: todos los comandos de este documento se
escriben en el **Símbolo del sistema (`cmd`)**, desde la carpeta raíz del
proyecto, y funcionan igual en PowerShell. No se necesita Linux ni Ubuntu en tu
equipo (ver [¿Por qué aparece Linux en el CI?](#por-qué-aparece-linux-en-el-ci)).

## Estado

El proyecto avanza por etapas y cada una se revisa antes de comenzar la
siguiente:

| Etapa | Alcance y resultado |
|---|---|
| E0 | Estructura inicial, documentación base, configuración local y controles de calidad. |
| E1 | Análisis de requisitos, modelo conceptual, entidades, reglas de negocio y permisos. |
| E2 | Modelo de datos modular en JSON, esquema de validación y pruebas del validador. |
| E3 | Generación de SQL PostgreSQL, migraciones, semillas y CLI para crear, sembrar y reiniciar la base con protecciones. |
| E4 | Backend Spring Boot, API `/api/v1`, errores Problem Details, configuración y verificación del esquema. |
| E5.1 | Autenticación, JWT, permisos por rol, bloqueo de cuentas, sesiones y parametrización internacional. |
| E5.2 | Países, monedas, idiomas, tipos de cambio, conversiones e impuestos con vigencia y auditoría. |
| E5.3 | Catálogo de productos, categorías, tipos, atributos, traducciones y precios; productos desactivables, no borrables. |
| E5.4 | Inventario: bodegas, proveedores oficiales, órdenes, recepciones parciales, salidas, saldos, movimientos y ajustes. Implementada y aprobada. |
| E5.5 | Ventas básicas con confirmación de pago, impuestos y moneda; reportes paginados de existencias y movimientos con exportación CSV. Implementada y aprobada. |
| E6.1 | Base web con React, TypeScript, Material UI, inicio de sesión, navegación por permisos, idiomas es/en y formatos localizados. Implementada y aprobada. |
| E6.2 | CRUD genérico generado desde metadatos para parametrización, catálogo, bodegas, proveedores y clientes; API básica de clientes y referencias. Implementada y aprobada. |
| E6.3 | Pantallas operativas de catálogo (productos con atributos dinámicos, traducciones y precios) e inventario (existencias, entradas, salidas, ajustes con aprobación e historial). Revisada y corregida durante E6.4. Implementada y aprobada. |
| E6.4 | Ventas (desglose de impuestos, confirmación de pago, anulación y recibo simulado), reportes con exportación CSV y pantalla de parámetros por ámbito y vigencia. Implementada y aprobada. |
| E7 | Verificación transversal: sin constantes de negocio en el código, importes como texto decimal en toda la API y prueba automatizada de un país y una moneda agregados solo con datos. Implementada y aprobada. |
| E8 | Pruebas y logs: integraciones con PostgreSQL desechable (Testcontainers), pruebas reales de `crear-bd`, flujo E2E con Playwright y comando `probar` con `resumen.md`. Implementada y aprobada. |
| E9 | Integración continua con GitHub Actions (python, backend, frontend y e2e) con logs como artefactos, control de archivos generados y hook local `pre-push`; `main` protegida con CI obligatorio. Implementada y aprobada. |
| E10 | Contenedores: imágenes de backend, frontend y `db-init`, y `docker-compose.yml` que levanta todo con un solo comando; E2E del CI sobre Docker Compose. Incluye la revisión general del proyecto, la limpieza de archivos sin uso, el soporte de `cmd` (ver [Revisión y correcciones de E10](#revisión-y-correcciones-de-e10)), datos de demostración, la nueva identidad visual y la [pantalla de Compras](#compras-purchasespage). Implementada y pendiente de revisión. |
| E11 | Despliegue de demostración en Render: `render.yaml` (base PostgreSQL 16, backend en Docker y frontend estático), `crear-bd` desde el equipo local contra la base de Render, comandos `respaldar` y `restaurar` con la retención de RNF-011 y E2E contra las URL públicas (`--e2e-entorno remoto`). Preparada; falta crear el Blueprint en la cuenta de Render y verificarlo (ver [Despliegue de demostración en Render (E11)](#despliegue-de-demostración-en-render-e11)). |
| Pendiente | Cierre según E12 de la guía (evidencias, README final y lista de fase 2). |

## Resumen de lo construido hasta ahora

**PixelVault** es una aplicación web (React + Spring Boot + PostgreSQL) para una
tienda de consolas y videojuegos que vende en varios países. Hoy permite:

- **Acceso y roles:** inicio de sesión con JWT, cinco roles (administrador,
  encargado de inventario, experto técnico, vendedor/cajero y gerente), menú y
  acciones según permisos, bloqueo por intentos fallidos y sesión con
  vencimiento por inactividad, ambos configurables.
- **Configuración internacional:** países, monedas, idiomas, tipos de cambio,
  impuestos por país y vigencia, y parámetros del negocio o de cada país con
  vigencia (por ejemplo, los medios de pago). Un país y una moneda nuevos se
  agregan solo con datos.
- **Catálogo:** productos con atributos según su tipo, nombres y descripciones
  en español e inglés, y precios por moneda con periodos de vigencia; los
  productos se desactivan, no se borran.
- **Compras:** productos oficiales de cada proveedor y órdenes de compra en la
  moneda del proveedor (borrador → enviada → recibida parcial o completa, o
  cancelada), con tipo de cambio hacia la moneda base.
- **Inventario:** existencias por bodega, entradas contra órdenes de compra,
  salidas por pérdida, daño, robo o defecto, ajustes con aprobación del
  administrador e historial inmutable de movimientos, todo auditado.
- **Ventas:** venta en la moneda local con el impuesto del país, confirmación
  de pago que descuenta inventario, anulación de ventas pendientes y recibo
  simulado.
- **Reportes:** existencias y movimientos con filtros y exportación CSV.
- **Interfaz en español e inglés:** el idioma y los formatos de fecha, número y
  moneda cambian sin recargar la página.

La base de datos se describe en JSON (`db/model/`) y se crea solo con los
scripts de Python. La calidad se comprueba con 173 pruebas automáticas en
cuatro suites (Python, backend, frontend y extremo a extremo) que se ejecutan
con un solo comando y dejan sus logs en `logs/tests/` (ver
[Pruebas y logs](#pruebas-y-logs-e8)).

E6.2 incluye listados paginados con búsqueda, formularios y validación por
metadatos, opciones de referencia y controles de permisos. La API de clientes
filtra por país, registra el consentimiento, permite su revocación en
actualizaciones y desactiva los registros en vez de borrarlos. La reclasificación
automática y la política de retención/eliminación a 12 meses aún no están
implementadas; deberán abordarse en una etapa posterior.

## Requisitos previos

- Java 17 (LTS) y Maven 3.9+
- Python 3.12 o superior
- PostgreSQL 16+
- Node.js LTS y npm para el frontend
- Docker (Docker Desktop en Windows) en ejecución: lo usan las pruebas de integración
  del backend, las de base de datos de Python, la prueba E2E y la ejecución con
  Docker Compose (E10)
- Git

## Preparación local

En `cmd`, desde la carpeta del proyecto (una sola vez):

```bat
py -3.12 -m venv .venv
.venv\Scripts\activate.bat
python -m pip install --upgrade pip
python -m pip install -r requirements-dev.txt
pre-commit install
copy .env.example .env
```

- `requirements-dev.txt` incluye `requirements.txt` (dependencias de ejecución)
  y las herramientas de desarrollo.
- `pre-commit install` instala los hooks de commit y de push (ver E9).
- Completa en `.env` los valores de tu entorno (`DB_PASSWORD`, `JWT_SECRET`,
  `ADMIN_EMAIL`, `ADMIN_PASSWORD`…), en formato `CLAVE=valor` y sin comillas.
  Nunca lo subas al repositorio (está en `.gitignore`).
- En cada consola nueva, activa el entorno con `.venv\Scripts\activate.bat`
  (en PowerShell: `.venv\Scripts\Activate.ps1`).

### Formas de ejecutar el sistema

| Opción | Para qué | Cómo |
|---|---|---|
| Docker Compose | Usar o demostrar el sistema completo sin instalar nada más que Docker. | `docker compose up --build` (ver [Contenedores (E10)](#contenedores-e10)). |
| Desarrollo local | Modificar el código con recarga rápida. Requiere PostgreSQL local. | `python tools/cli.py crear-bd`, luego el backend y el frontend (ver [Backend](#backend-e51e55) y [Frontend](#frontend-e61e64)). |

## CLI de desarrollo

```bat
python tools/cli.py --help
python tools/cli.py validar --help
python tools/cli.py generar --help
```

`validar` comprueba esquema, tipos, nombres, claves, índices y referencias.
`generar` produce la migración inicial y los metadatos resueltos desde el modelo
JSON. `crear-bd` crea la base si falta, aplica migraciones, carga semillas y
verifica el esquema. `sembrar` carga únicamente los datos iniciales y `sembrar-demo` los datos de demostración (ver [Datos de demostración](#datos-de-demostración)). Todos
aceptan `--dry-run` y escriben sus logs en `logs/tools/`.
`V001` es inmutable después de aplicarse para conservar su checksum; los cambios
de esquema posteriores se agregan en una nueva migración versionada.
`V003__bodega_pais_obligatorio.sql` exige el país de cada bodega, como define el
modelo; si alguna bodega existente no tiene país, la migración se detiene con un
mensaje y debe asignarse antes de volver a ejecutar `crear-bd`. La semilla de la
bodega `GENERAL` indica su país con `pais_codigo`. `generar`
actualiza el modelo resuelto y el CRUD sin reescribir esa migración.

`respaldar` y `restaurar` guardan y recuperan la base con `pg_dump` y
`pg_restore` (ver [Respaldo y restauración](#respaldo-y-restauración)).

Todos los comandos que usan la base toman la conexión de `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USER` y `DB_PASSWORD` del `.env` o, si está definida,
de `DATABASE_URL` (por ejemplo, la URL externa de la base de Render). Las
variables definidas en la consola tienen prioridad sobre el `.env`.

`reiniciar-bd` es destructivo: solo permite hosts de loopback y exige confirmar
el nombre exacto de la base con `--confirm-db`. No lo ejecutes contra datos que
necesites conservar. `probar` ejecuta todas las suites de prueba y `todo` encadena
`validar` → `generar` → `crear-bd` → `probar` (ver la sección E8).

## Datos de demostración

Para probar o presentar el sistema con datos realistas existe una carga
**manual** (no se ejecuta sola):

```bat
rem Con Docker Compose (la pila debe estar arriba: docker compose up -d --wait)
docker compose run --rm db-init python tools/cli.py sembrar-demo --api http://backend:8080
rem En desarrollo local (backend arrancado en http://localhost:8080)
python tools/cli.py sembrar-demo
rem Solo muestra lo que cargaría, sin conectarse
python tools/cli.py sembrar-demo --dry-run
```

Requiere en `.env` las credenciales del administrador (`ADMIN_EMAIL`,
`ADMIN_PASSWORD`) y `DEMO_PASSWORD`, la contraseña común de los usuarios de
demostración (8 caracteres o más; no está en el repositorio).

Los datos están en `db/seeds/demo.json` y la lógica en `tools/demo_seed.py`. Los
usuarios se insertan directamente en la base (no hay API de usuarios); **todo lo
demás se crea a través de la API del backend**, con sus reglas de negocio,
movimientos de inventario y auditoría, exactamente como si se hiciera desde la
interfaz.

| Qué se carga | Detalle |
|---|---|
| Usuarios (contraseña `DEMO_PASSWORD`) | `encargado.inventario@pixelvault.example` (encargado de inventario), `experto.tecnico@pixelvault.example` (experto técnico), `vendedor.bogota@pixelvault.example` (vendedor, solo Colombia), `ventas.miami@pixelvault.example` (vendedora, solo Estados Unidos, en inglés) y `gerente@pixelvault.example` (gerente). |
| Estados Unidos | Se habilita con un impuesto de ejemplo (*Sales tax Florida*, 6 %) y un tipo de cambio de ejemplo USD→COP (4.100); ambos se editan en Configuración. |
| Catálogo | 7 tipos de producto con los atributos de RF-001 (videoconsola, videojuego, tarjeta de regalo, de suscripción, accesorio, retro y edición especial), 6 categorías con 11 subcategorías y 15 productos con nombre y descripción en español e inglés y precios en COP y USD. |
| Inventario | Bodegas Bodega general (Colombia), Medellín (Colombia) y Miami (Estados Unidos); 3 proveedores con sus productos oficiales; 3 órdenes de compra recibidas (una parcialmente); una salida por daño; un ajuste aprobado y otro pendiente. |
| Clientes y ventas | 5 clientes (frecuentes e infrecuentes, Colombia y Estados Unidos); 6 ventas: 3 confirmadas en COP, 1 confirmada en USD, 1 pendiente de pago y 1 anulada. |

Las empresas, personas y correos son ficticios (dominio reservado
`.example`); los productos y precios son realistas. La carga es idempotente:
usuarios y datos maestros se buscan antes de crearlos, y las operaciones
(compras, ventas, salidas y ajustes) solo se cargan si la base aún no tiene
órdenes de compra, así que repetirla no duplica nada.

## Backend (E5.1–E5.5)

El backend Spring Boot está en `backend/`, usa Java 17, valida el esquema al
arrancar y no modifica la base (`ddl-auto=validate`). Lee automáticamente el
`.env` de la raíz del proyecto (`spring.config.import` en `application.yml`),
así que no hace falta cargar variables a mano; si una variable también existe
en el entorno del sistema, prevalece la del sistema. Con PostgreSQL local y la
base creada (`python tools/cli.py crear-bd`), en `cmd`:

```bat
cd backend
.\mvnw.cmd spring-boot:run
```

Para detenerlo, `Ctrl+C`. Las pruebas del backend se ejecutan con
`.\mvnw.cmd verify` desde `backend` (necesitan Docker; ver
[Pruebas y logs](#pruebas-y-logs-e8)). Se escribe `.\mvnw.cmd` con `.\` para
que funcione igual en `cmd` y en PowerShell.

Maven Wrapper descarga Maven 3.9.11; requiere que Java 17 esté instalado. La
aplicación expone
`http://localhost:8080/actuator/health`, `http://localhost:8080/swagger-ui` y
`http://localhost:8080/api/v1`. Usa los perfiles `local` y `produccion`. La
autenticación se inicia en `POST /api/v1/auth/login`, el cierre de sesión en
`POST /api/v1/auth/logout` y el desbloqueo administrativo en
`POST /api/v1/admin/usuarios/{usuarioId}/desbloquear`. Define `JWT_SECRET` en
`.env` con una clave aleatoria base64 de 32 bytes o más. No la compartas ni la
subas al repositorio.

Las respuestas de inicio de sesión y las respuestas autenticadas usan
`Cache-Control: no-store` para evitar almacenar credenciales y tokens en caché.
Las sesiones se conservan en memoria: reiniciar el backend cierra todas las
sesiones activas. Las solicitudes autenticadas renuevan el tiempo de inactividad
y devuelven el token actualizado en `X-Auth-Token`; el cliente debe reemplazar
el token anterior por ese valor. La aplicación sigue en desarrollo y no debe
exponerse como sistema operativo en producción.

#### E5.2 — Países, monedas, idiomas, tipos de cambio e impuestos

Los catálogos se administran bajo `/api/v1/configuracion`: `paises`, `monedas`,
`idiomas`, `tipos-cambio` e `impuestos` ofrecen operaciones paginadas (`page`,
`size`, `buscar`) y CRUD. Países, monedas, idiomas e impuestos requieren el
permiso `PARAMETROS_GESTIONAR`; los tipos de cambio y las conversiones requieren
`TIPOS_CAMBIO_GESTIONAR`. Cada cambio queda registrado en auditoría.

Las sentencias SQL de lectura y escritura para estos CRUD se generan desde las
entidades `generar_crud` del modelo cuando ejecutas `python tools/cli.py generar`.
Las validaciones de negocio, los permisos y la auditoría permanecen en los
servicios tipados del backend.

Los tipos de cambio se ingresan manualmente con una fecha de vigencia. La
conversión en `POST /api/v1/conversiones/moneda` usa la tasa directa vigente o,
si no existe, invierte la tasa disponible; redondea según la moneda de destino.
`POST /api/v1/impuestos/calcular` busca el impuesto vigente específico de la
categoría y, si no existe, usa el general del país; desglosa o agrega el
impuesto según `incluidoEnPrecio`. Un país solo puede habilitarse cuando tenga
moneda, idioma, zona horaria e impuesto vigente configurados.

Para validar el modelo:

```bat
python tools/cli.py validar
python tools/cli.py validar --dry-run
```

Para generar el esquema y preparar PostgreSQL:

1. Copia `.env.example` a `.env` y configura `DB_HOST`, `DB_PORT`, `DB_NAME`,
   `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL` y `ADMIN_PASSWORD`; alternativamente, <!-- pragma: allowlist secret -->
   define `DATABASE_URL` y las dos variables del administrador.
2. Genera la migración y crea/siembra la base:

```bat
python tools/cli.py generar
python tools/cli.py crear-bd
```

El usuario inicial se crea con contraseña BCrypt desde el entorno; nunca se
guarda la contraseña en las semillas. Colombia se inicializa habilitada con IVA
del 19%. EE. UU. queda deshabilitado hasta que el administrador configure una
tarifa; no se carga un tipo de cambio COP/USD de ejemplo. Esto bloquea las
operaciones que no tengan la configuración requerida.

#### E5.3 — Catálogo de productos

El catálogo se administra bajo `/api/v1/catalogo`. Las lecturas requieren
`CATALOGO_CONSULTAR`; las operaciones de gestión requieren
`PRODUCTOS_GESTIONAR` (administrador o encargado de inventario). Las listas
aceptan `page`, `size` y `buscar`.

| Recurso | Operaciones |
|---|---|
| `/categorias` | Consultar, crear, actualizar y desactivar categorías raíz o subcategorías. |
| `/tipos-producto` | Consultar, crear, actualizar y desactivar tipos. |
| `/definiciones-atributos` | Consultar, crear y actualizar definiciones; se pueden filtrar con `tipoProductoId`. |
| `/productos` | Consultar, crear, actualizar y desactivar productos. |
| `/productos/{id}/traducciones` | Consultar y agregar traducciones; se actualizan con `/{traduccionId}`. |
| `/productos/{id}/precios` | Consultar precios y agregar un precio con periodo de vigencia. |

Una categoría raíz usa `padreId: null`; una subcategoría debe tener como padre
una categoría raíz activa. Los productos requieren tipo activo, categoría raíz
activa y subcategoría hija activa. Las definiciones admiten `TEXTO`, `ENTERO`,
`DECIMAL`, `BOOLEANO` y `JSON`; `opciones`, si se indica, es una lista de
valores permitidos del tipo declarado. `atributos` debe ser un objeto JSON con
solo atributos definidos para su tipo, respetando valores obligatorios, tipos
y opciones.

`POST /productos` recibe `tipoProductoId`, `categoriaId`, `subcategoriaId`,
`atributos`, una lista no vacía `traducciones` (`idiomaId`, `nombre`,
`descripcion`) y una lista no vacía `precios` (`monedaId`, `importe`,
`vigenteDesde`, `vigenteHasta`). La creación y sus traducciones/precios se
guardan en una sola transacción. El código interno `PRD-<UUID>` lo asigna el
sistema. Solo se aceptan idiomas y monedas existentes; los idiomas deben estar
habilitados. Los precios no pueden tener periodos superpuestos para el mismo
producto y moneda. Para cambiar el precio, se agrega un nuevo periodo; los
registros anteriores no se editan ni eliminan.

Los productos se desactivan con `DELETE /productos/{id}`; no se eliminan
físicamente. Las categorías y tipos también se desactivan, y no se permite
desactivar una categoría con subcategorías o productos activos, ni un tipo con
productos activos. Cada operación de gestión queda registrada en auditoría.
El comando `python tools/cli.py generar` genera las sentencias JDBC de
categorías, tipos, definiciones y productos a partir de `generar_crud` en el
modelo; el producto no tiene sentencia generada de borrado. Traducciones y
precios conservan las operaciones específicas de vigencia definidas por el
repositorio.

#### E5.4 — Inventario

El inventario se administra bajo `/api/v1/inventario`, las compras bajo
`/api/v1/compras` y los proveedores bajo `/api/v1/proveedores`. Las consultas de
existencias, bodegas, movimientos y ajustes requieren `INVENTARIO_CONSULTAR`.
La gestión de bodegas y compras/recepciones requiere `COMPRAS_GESTIONAR`;
proveedores y su relación oficial con productos requieren
`PROVEEDORES_GESTIONAR` (la consulta admite `PROVEEDORES_CONSULTAR`). Las
salidas documentadas requieren `SALIDAS_INVENTARIO_GESTIONAR`, asignado al
administrador y al encargado de inventario.
Las solicitudes de bodega incluyen `paisId`; usuarios con países asignados solo
consultan y operan bodegas de esos países. Un usuario sin países asignados
conserva el alcance global.

| Recurso | Operaciones |
|---|---|
| `/api/v1/inventario/bodegas` | Consultar, crear y actualizar bodegas con país; la asignación no se cambia una vez establecida. No se eliminan físicamente ni se desactivan si conservan saldo positivo o ajustes abiertos. |
| `/api/v1/proveedores` | Consultar, crear y actualizar proveedores; vincular un producto con un proveedor oficial y listar sus productos oficiales (`GET /{id}/productos`). |
| `/api/v1/compras/ordenes` | Crear borrador, enviar al proveedor, consultar (filtros `estado` y `proveedorId`) y cancelar antes de recibir. |
| `/api/v1/compras/contexto` | Moneda base del negocio, para saber cuándo la tasa de la orden es `1`. |
| `/api/v1/compras/entradas` | Registrar y consultar recepciones parciales o completas con documento, tasa aplicada y costos. |
| `/api/v1/inventario/saldos` | Consultar por producto, bodega o país y filtrar saldos positivos. |
| `/api/v1/inventario/salidas` | Registrar pérdidas, daños, robos o defectos con reporte al proveedor oficial. |
| `/api/v1/inventario/movimientos` | Consultar historial por producto, bodega, usuario, tipo y periodo; los movimientos son inmutables. |
| `/api/v1/inventario/ajustes` | Solicitar conteos operativos/técnicos, aprobar y aplicar ajustes; solo el administrador puede aprobar/aplicar. |

El flujo de entrada requiere un proveedor activo vinculado al producto, una
orden enviada y una bodega activa. La orden se crea en `BORRADOR`; solo se
reciben órdenes `ENVIADA_AL_PROVEEDOR` o `RECIBIDA_PARCIAL`. Una recepción puede
ser parcial, no puede exceder la cantidad pendiente ni repetir el documento de
la orden. El costo en moneda base se calcula con la tasa guardada en cada
recepción (la tasa de la moneda base debe ser `1`). Una línea de orden recibe
`productoId`, `cantidadPedida` y `costoUnitario`; la recepción recibe
`ordenCompraId`, `bodegaId`, `numeroDocumento`, `tipoCambioAplicado` y líneas
con `ordenCompraLineaId` y `cantidad`.

Las salidas usan los tipos `PERDIDA`, `DANO`/`DAÑO`, `ROBO` o `DEFECTO`, una
cantidad entera positiva, `proveedorId`, `numeroReporteProveedor` y una nota
opcional. Cada cambio de saldo se serializa por producto y bodega; no se permite
un saldo negativo. Entradas, salidas y ajustes actualizan el saldo, añaden un
movimiento inmutable y escriben auditoría en la misma transacción.

Los ajustes operativos solo los solicita el encargado de inventario y los
técnicos solo el experto técnico. Ambos requieren aprobación administrativa antes de
aplicarse. Si el saldo cambió después del conteo, la aplicación se bloquea; un
administrador puede usar `POST /api/v1/inventario/ajustes/{id}/revalidar` para
actualizar la cantidad del sistema y devolver el ajuste a aprobación pendiente.
Las sentencias JDBC de bodega se generan en `InventarioCrudSql.java`; no se
genera una operación de borrado.
En una base existente, ejecuta `python tools/cli.py sembrar` para agregar el
permiso nuevo y su asignación de roles; la carga es idempotente y usa las
credenciales configuradas localmente en `.env`.
La migración `V002__pais_bodega.sql` agrega el país a las bodegas y conserva la
restricción de no borrar bodegas. En instalaciones previas, asigna un país a
cada bodega existente antes de restringir usuarios por país; la migración deja
ese dato pendiente para no asumir a qué país pertenece una bodega.
El alcance por país también se aplica a proveedores, órdenes de compra,
recepciones, ajustes, ventas y reportes. Una recepción debe usar una bodega del
mismo país que la orden; el país de un proveedor ya asignado no se puede cambiar.

#### E5.5 — Ventas y reportes

Las ventas se administran bajo `/api/v1/ventas`. `POST /api/v1/ventas` crea una
venta pendiente con país, moneda local, cliente opcional y líneas de producto,
bodega y cantidad. Cada bodega debe pertenecer al mismo país de la venta. El
precio vigente, el impuesto del país, el tipo de cambio
hacia la moneda base y los importes calculados se guardan como datos de esa
operación. El borrador comprueba existencias pero no modifica saldos ni crea
movimientos.

`POST /api/v1/ventas/{id}/confirmar` recibe `medioPago`; en una sola transacción
marca el pago y confirma la venta, descuenta todas las líneas, registra los
movimientos `SALIDA_VENTA` y escribe auditoría. Si una bodega ya no está activa
o no hay saldo suficiente, la transacción completa se revierte. Una venta solo
se anula mediante `POST /api/v1/ventas/{id}/anular` mientras siga pendiente y
sin pago recibido. El vendedor requiere `VENTAS_GESTIONAR`; anular requiere
además `VENTAS_ANULAR_PENDIENTES`.

`GET /api/v1/ventas/{id}` consulta la operación y
`GET /api/v1/ventas/{id}/recibo` devuelve el recibo simulado después de
confirmarla. No se conecta a una pasarela de pagos ni se genera facturación
fiscal electrónica.

Los reportes requieren `REPORTES_INVENTARIO` o `REPORTES_TODOS`. Los países
asignados al usuario limitan las existencias, movimientos y ventas visibles;
los filtros `paisId` refinan el reporte dentro de ese alcance:

| Recurso | Filtros y exportación |
|---|---|
| `GET /api/v1/reportes/inventario/existencias` | Paginado; `productoId`, `bodegaId`, `paisId`, `soloConExistencia`. |
| `GET /api/v1/reportes/inventario/existencias.csv` | Exporta todas las existencias que coincidan con esos filtros. |
| `GET /api/v1/reportes/inventario/movimientos` | Paginado; `productoId`, `bodegaId`, `usuarioId`, `tipo`, `desde`, `hasta`; para movimientos de venta, también `paisId` y `monedaId`. |
| `GET /api/v1/reportes/inventario/movimientos.csv` | Exporta los movimientos que coincidan con los mismos filtros. |

Las exportaciones se transmiten como CSV UTF-8, entrecomillan y escapan los
campos, y neutralizan valores de texto que podrían interpretarse como fórmulas
al abrir el archivo en una hoja de cálculo. La transmisión usa un despacho
asíncrono del servidor; la configuración de seguridad lo permite porque solo
continúa una petición ya autenticada y autorizada.

#### Ampliaciones de API para E6.4

| Recurso | Uso |
|---|---|
| `GET /api/v1/ventas` | Lista paginada de ventas (más recientes primero) filtrable por `estado` (`PENDIENTE_DE_PAGO`, `CONFIRMADA`, `ANULADA`) y limitada al alcance por país. Requiere `VENTAS_GESTIONAR`. |
| `GET /api/v1/ventas/paises` | Países habilitados dentro del alcance del usuario, con su moneda local (código y decimales) y los medios de pago del parámetro `ventas.medios_pago` resuelto para el país. Requiere `VENTAS_GESTIONAR`. |
| `GET/POST /api/v1/parametros`, `GET/PUT /api/v1/parametros/{id}` | Administración de parámetros: clave, tipo, valor, mínimo y máximo, ámbito (`NEGOCIO` o `PAIS`) y vigencia, sin periodos superpuestos para la misma clave, ámbito y país. Requiere `PARAMETROS_GESTIONAR`; cada cambio invalida la caché y queda en auditoría. Los valores `DECIMAL` y los límites se aceptan como texto decimal. |
| `GET /api/v1/referencias/producto` | Productos activos con su nombre en el idioma de la solicitud (`Accept-Language`). Requiere `CATALOGO_CONSULTAR` o `PRODUCTOS_GESTIONAR`. |
| `GET /api/v1/referencias/usuario` | Usuarios (nombre y correo) para filtrar reportes. Requiere `REPORTES_TODOS` o `REPORTES_INVENTARIO`. |

#### Ampliaciones de API para la pantalla de Compras

La pantalla de Compras usa la API de E5.4 tal cual; solo se añadieron dos
consultas de lectura, sin cambios en la base de datos:

| Recurso | Uso |
|---|---|
| `GET /api/v1/proveedores/{id}/productos` | Productos oficiales (activos) del proveedor, con su nombre en el idioma de la solicitud (`Accept-Language`): `[{ "id", "codigo", "nombre" }]`. Requiere `PROVEEDORES_CONSULTAR` o `PROVEEDORES_GESTIONAR` y respeta el alcance por país: un usuario de Estados Unidos no puede ver los de un proveedor de Colombia (403). |
| `GET /api/v1/compras/contexto` | `{ "monedaBaseCodigo": "COP" }`, leído del parámetro `moneda.base_codigo_iso`. Requiere `COMPRAS_GESTIONAR`. Con él la pantalla fija la tasa en `1` cuando el proveedor compra en la moneda base, sin escribir "COP" en el código. |

Ambas quedan cubiertas en `InventarioPostgresIntegrationTest` (listado,
rechazo fuera del país, llamada HTTP con permisos y moneda base sembrada).

Las referencias `idioma` y `moneda` también se permiten a `PRODUCTOS_GESTIONAR`
(formulario de productos del encargado de inventario), y `pais` y `moneda` a
los permisos de reportes (filtros). Los medios de pago se configuran como datos:
la semilla define `ventas.medios_pago` para todo el negocio (efectivo, tarjeta y
transferencia bancaria) y para Colombia (además PSE, Nequi y Daviplata), según
la sección de pagos de los requisitos. Otro país o medio se agrega con un
parámetro, sin cambiar código.

## Frontend (E6.1–E6.4)

El cliente React + TypeScript está en `frontend/`. Con el backend iniciado en
`http://localhost:8080`, en `cmd`:

```bat
cd frontend
npm ci
npm run dev
```

El servidor de desarrollo usa `http://localhost:5173`; el backend permite ese
origen por defecto. Si la API está en otra dirección, configura
`VITE_API_URL` en `frontend/.env.local` (por ejemplo,
`VITE_API_URL=http://localhost:8080`). No incluyas credenciales en variables
`VITE_*`: se incorporan al bundle del navegador.

### Identidad visual

La interfaz usa un tema oscuro propio (`src/theme.ts`, `src/App.css`): fondo
azul noche (`#0B0D17`), superficies `#141829`, acentos violeta neón (`#8B5CF6`)
y cian (`#22D3EE`), con contraste suficiente para el texto. El logo
(`PixelVaultLogo`) es la puerta de una bóveda (*vault*) dibujada en *pixel art*
de 16 × 16, en coherencia con el nombre **PixelVault**; la misma figura es el
favicon (`public/favicon.svg`). El recibo simulado se imprime en blanco y negro.

### E6.1 — Base web

La aplicación usa React, TypeScript, Vite y Material UI. La pantalla de acceso
se conecta al backend y la sesión controla la navegación según los permisos
del usuario. La interfaz admite español e inglés, localiza fechas y números, y
centraliza las solicitudes, los errores de API y la renovación del token de
sesión. Las credenciales se envían únicamente al backend; no deben almacenarse
en variables `VITE_*`.

### E6.2 — CRUD genérico dirigido por metadatos

`python tools/cli.py generar` produce `frontend/src/generated/entidades.json`
y `entidades.ts` desde el modelo modular de `db/model/`. Los metadatos incluyen
las rutas y permisos de cada entidad, etiquetas en español e inglés, campos y
tipos, valores iniciales, campos de solo lectura, referencias y validaciones.
`CrudPage` consume esa salida para presentar tablas paginadas, búsqueda,
formularios de creación y edición, validación y confirmación antes de eliminar
o desactivar. Así, las reglas comunes se mantienen en el modelo y las reglas
operativas particulares siguen en los servicios tipados del backend.

La interfaz cubre estas entidades y API:

| Entidad | API |
|---|---|
| Países, monedas, idiomas, tipos de cambio e impuestos | `/api/v1/configuracion/{paises,monedas,idiomas,tipos-cambio,impuestos}` |
| Categorías | `/api/v1/catalogo/categorias` |
| Bodegas | `/api/v1/inventario/bodegas` |
| Proveedores | `/api/v1/proveedores` |
| Clientes | `/api/v1/clientes` |

Las opciones de referencia para formularios se consultan en
`/api/v1/referencias/{entidad}`. El backend verifica permisos en cada recurso y
limita los países disponibles al alcance asignado al usuario. Los registros
que no deben borrarse físicamente se desactivan mediante el flujo de edición;
el comportamiento de borrado se define por entidad.

#### API y reglas de clientes

`/api/v1/clientes` ofrece consulta paginada (`page`, `size` y `buscar`),
consulta por identificador, creación, actualización y desactivación lógica.
Todas las operaciones exigen `CLIENTES_GESTIONAR` y respetan el alcance por
país del usuario; el país de un cliente existente no se puede cambiar. La
creación exige registrar el consentimiento. En una actualización se permite
registrar su revocación. Los clientes frecuentes requieren apellidos, código
postal, dirección y teléfono; los infrecuentes requieren documento. La
auditoría almacena un resumen del cambio, no los datos personales completos.

La reclasificación automática de clientes y la política de retención o
eliminación de datos infrecuentes a los 12 meses no están implementadas en esta
etapa. Deben resolverse antes de considerar completo ese ciclo de vida de
datos.

### E6.3 — Catálogo e inventario

- **Catálogo (`CatalogPage`):** tabla paginada con nombre del producto en el
  idioma de la interfaz, búsqueda por código interno, estado y desactivación
  lógica con confirmación. `ProductFormDialog` genera los campos según las
  definiciones de atributos del tipo (texto, entero, decimal, sí/no, JSON y
  listas de opciones), edita nombre y descripción por idioma (actualiza las
  traducciones existentes y crea las que faltan) y agrega precios como nuevos
  periodos de vigencia; los precios registrados se muestran sin editarse.
- **Inventario (`InventoryPage`):** las pestañas se muestran según los permisos
  del rol. Existencias con filtros por bodega, país y saldo positivo; entradas
  contra órdenes enviadas o parciales con bodega del país de la orden y tasa
  como texto decimal; salidas por pérdida, daño, robo o defecto; ajustes con
  solicitud por el rol correspondiente (operativo o técnico) y aprobación,
  aplicación y revalidación por el administrador; historial de movimientos con
  filtros por bodega, producto, tipo y periodo.

### E6.4 — Ventas, reportes y parámetros

- **Ventas (`SalesPage`):** lista con filtro de estado y acceso al CRUD de
  clientes. Una venta nueva toma el país (y con él su moneda local), un cliente
  opcional del país y productos con bodega y cantidad; al calcularla se crea
  pendiente de pago y se muestra el desglose de precio, impuesto aplicado,
  subtotal, total en la moneda de la venta, tipo de cambio y total en moneda
  base. El pago se confirma con un medio de pago del país; solo quien tiene
  `VENTAS_ANULAR_PENDIENTES` ve la anulación de ventas pendientes. Las ventas
  confirmadas muestran el recibo simulado, imprimible.
- **Reportes (`ReportsPage`):** existencias y movimientos paginados con filtros
  (producto, bodega, país, moneda, usuario, tipo y periodo) y botón de
  exportación CSV que descarga el archivo con la sesión del usuario. Los
  filtros cuyas referencias no están permitidas para el rol no se muestran.
- **Configuración (`SettingsPage`):** pantalla de parámetros con filtro por
  clave y ámbito, valor según su tipo, límites, ámbito (negocio o país) y
  vigencia; y accesos a los CRUD de países, monedas, idiomas, tipos de cambio e
  impuestos.

Los importes y tasas se envían a la API como texto decimal y se muestran con
`Intl` en la moneda de la transacción y el idioma activo; al cambiar de idioma
los textos y formatos se actualizan sin recargar la página.

### Compras (`PurchasesPage`)

Hasta E10 el menú **Compras** solo mostraba "Esta sección se habilitará en la
siguiente subetapa", y las órdenes de compra únicamente se podían crear llamando
a la API. Como sin orden enviada no se puede registrar ninguna entrada de
inventario, se completó esta pantalla antes de cerrar E10. Está en
`frontend/src/components/purchases/` y aparece para quien tiene
`COMPRAS_GESTIONAR` (administrador y encargado de inventario).

**Pestaña "Órdenes de compra"**

- Lista de órdenes con número, proveedor, estado, moneda, tipo de cambio y
  fecha, filtrable por estado y por proveedor.
- **Nueva orden de compra:**
  1. Se elige el proveedor (solo los activos). La moneda es la de compra del
     proveedor y se muestra sin poder cambiarla.
  2. **Tipo de cambio:** si el proveedor compra en la moneda base (COP en la
     semilla), el campo queda fijo en `1`, como exige el backend. Si compra en
     otra moneda (por ejemplo, USD), hay que escribir cuántos COP vale 1 USD
     (hasta 11 enteros y 8 decimales; se acepta punto o coma).
  3. Se agregan líneas: producto, cantidad (entero mayor que cero) y costo
     unitario en la moneda del proveedor (mayor o igual que cero, hasta 4
     decimales). **Solo se ofrecen los productos oficiales del proveedor** y no
     se puede repetir un producto en dos líneas.
  4. **Crear borrador** guarda la orden en estado `BORRADOR` y abre su detalle.
- **Detalle de la orden:** datos generales y, por línea, cantidad pedida,
  recibida, pendiente y costo unitario (con sus 4 decimales, sin redondear).
  Según el estado ofrece:
  - `BORRADOR` → **Enviar al proveedor** (pasa a `ENVIADA_AL_PROVEEDOR`).
  - `BORRADOR` o `ENVIADA_AL_PROVEEDOR` → **Cancelar orden**, con
    confirmación. El backend la rechaza si ya tiene entradas.
  - `ENVIADA_AL_PROVEEDOR` o `RECIBIDA_PARCIAL` → aviso con el botón **Ir a
    Inventario**, porque la mercancía se recibe en *Inventario → Entradas de
    compra* (allí se elige bodega, documento y cantidades).

**Pestaña "Productos por proveedor"**

Muestra los productos oficiales del proveedor elegido. Quien tiene
`PROVEEDORES_GESTIONAR` puede **vincular** un producto más (la lista solo ofrece
los que aún no están vinculados); un proveedor inactivo no admite vínculos.
Estos vínculos son los que limitan qué se puede pedir en una orden y qué
proveedor se puede indicar en una salida por pérdida, daño, robo o defecto.

**Flujo completo de una compra, de principio a fin**

1. *Proveedores* (CRUD): crear el proveedor con su país y moneda de compra.
2. *Compras → Productos por proveedor*: vincular sus productos oficiales.
3. *Compras → Órdenes de compra*: crear la orden y enviarla al proveedor.
4. *Inventario → Entradas de compra*: registrar lo recibido (puede ser en
   varias entregas parciales); la existencia aumenta y queda el movimiento
   `ENTRADA`.

Todos los textos están en español e inglés, los importes y tasas viajan como
texto decimal y cada acción (vincular, crear, enviar y cancelar) queda en la
auditoría del backend.

### Revisión y correcciones de E6.3 y E6.4

E6.3 y la primera parte de E6.4 llegaron sin versionar y con trabajo parcial de
otra herramienta. Antes de completar E6.4 se revisó el código, se ejecutaron
todas las suites y se corrigió lo siguiente.

#### Errores encontrados y corregidos

| Área | Problema | Corrección |
|---|---|---|
| Backend | No compilaba: `ParametroAdminRepository` llamaba a un método inexistente (`safeMap`). | El repositorio usa su propio `RowMapper` de instancia. |
| Backend | El listado de parámetros fallaba: la consulta `COUNT` no tenía cláusula `FROM`. | Consulta corregida; cubierta por prueba de integración. |
| Backend | Un usuario limitado por país podía crear o mover un parámetro a un país fuera de su alcance. | Se valida el país destino al crear y al actualizar. |
| Backend | Los valores `DECIMAL` de parámetros llegaban como `double` y perdían precisión. | Se aceptan como texto decimal y se guardan sin pasar por `double`. |
| Backend | `GET /api/v1/ventas` aceptaba cualquier `estado`. | Valida `PENDIENTE_DE_PAGO`, `CONFIRMADA` o `ANULADA` (400 en otro caso). |
| Backend (E5.5) | La exportación CSV se cortaba: el despacho asíncrono del streaming se rechazaba en Spring Security con la respuesta ya iniciada. | Se permiten los despachos `ASYNC` y `ERROR`, que continúan una petición ya autorizada. Verificado contra el servidor real. |
| Base de datos | El backend no arrancaba sobre una base creada por los scripts: el modelo exige `bodega.pais_id`, las migraciones lo dejaban nulo y la semilla insertaba la bodega `GENERAL` sin país. | Migración `V003__bodega_pais_obligatorio.sql` y `pais_codigo` en la semilla de bodegas (validado por el cargador). |
| Inventario | No se podían aprobar ajustes: el front esperaba `PENDIENTE_APROBACION` y el backend usa `PENDIENTE_DE_APROBACION` (la prueba simulaba el valor equivocado). | Estados tomados del modelo; prueba corregida. |
| Inventario | La página fallaba para experto técnico y vendedor: cargaba proveedores sin permiso y un 403 tumbaba toda la pantalla. | Referencias opcionales tolerantes y pestañas según permisos. |
| Inventario | El administrador veía "Solicitar ajuste", pero el backend exige el rol de encargado o experto. | El botón depende del permiso y del rol, como el backend y el Anexo D. |
| Inventario | Filtro de movimientos con tipos inexistentes (`ENTRADA_COMPRA`, `AJUSTE_POSITIVO`…) y sin filtro de fechas. | Tipos del modelo y filtros desde/hasta. |
| Inventario | La tasa de una entrada se convertía con `parseFloat` y un valor vacío se enviaba como tasa 1. | Tasa como texto decimal validado (máx. 11 enteros y 8 decimales). |
| Catálogo | Al editar un producto se reenviaban traducciones y precios ignorando los errores: los cambios de nombre no se guardaban y se mostraba "guardado". | Las traducciones existentes se actualizan con `PUT` y las nuevas con `POST`; los precios registrados son de solo lectura y los errores se muestran. |
| Catálogo | El encargado de inventario no podía abrir el formulario (403 al consultar idiomas y monedas). | Las referencias `idioma` y `moneda` admiten `PRODUCTOS_GESTIONAR`. |
| Front | Las referencias leían `etiqueta`, pero la API devuelve `nombre`; los productos solo se veían como `PRD-<UUID>`. | Contrato corregido y referencia `producto` con nombre en el idioma de la solicitud. |
| Front | Textos fijos en español fuera de i18n, claves inexistentes (`inventory.filterByCountry`) y errores de validación ocultos detrás de los diálogos. | Todo el texto en `es.json`/`en.json` (verificado que no faltan claves) y errores dentro de cada diálogo. |
| Front | Listas pedidas con `size=200` cuando el backend limita a 100. | Tamaños dentro del máximo. |
| Front | 13 advertencias de lint (efectos con estado síncrono y dependencias faltantes). | Hook común `useApiQuery`; lint sin advertencias. |

#### Funcionalidad añadida para completar E6.4

- **Backend:** listado de ventas; `GET /api/v1/ventas/paises` (moneda local y
  medios de pago por país); API de parámetros; referencias `producto` y
  `usuario`; parámetro `ventas.medios_pago` sembrado para todo el negocio y
  para Colombia, con soporte de parámetros por país en las semillas.
- **Front:** pantallas de ventas, reportes y configuración; descarga CSV
  autenticada (`apiDownload`); utilidades de decimales sin `float`
  (`utils/decimal.ts`) y formato de importes en texto (`formatMoney`);
  encabezado de página común y accesos a los CRUD genéricos por módulo.
- **Marca:** se mantiene **PixelVault** y quedó registrada en la especificación,
  la guía, las instrucciones y el modelo conceptual.

#### Pruebas añadidas

- Backend: `VentasListadoTest`, nuevos casos en `ParametroAdminServiceTest` y
  `ReferenciasServiceTest`, e integración PostgreSQL en
  `ParametroVentasPostgresIntegrationTest` (parámetros, listado y países de
  venta, referencias y decimales como texto vía HTTP). Total: 72 pruebas.
- Front: catálogo, inventario, ventas, reportes, configuración y utilidades de
  decimales y formatos. Total: 29 pruebas en 11 archivos.
- Python: semillas de bodega con país y parámetros por país. Total: 37 pruebas.

#### Pendientes conocidos al cerrar E6.4

- La serialización de `BigDecimal` como número JSON y la lista fija de monedas
  del `Dashboard` se resolvieron en E7 (ver la sección siguiente).
- El paquete del front supera los 500 kB (aviso de Vite).
- La descarga CSV usa nombres fijos (`existencias.csv`, `movimientos.csv`)
  porque CORS no expone `Content-Disposition`.
- La exportación CSV no tiene prueba automática que reproduzca el despacho
  asíncrono real; se verificó manualmente contra el servidor.

## E7 — Verificación de parametrización e internacionalización

### Revisión de constantes de negocio

Se buscaron en el backend y el frontend porcentajes, plazos, monedas, países,
medios de pago y textos fijos:

- **Backend:** los plazos e intentos (`sesion.inactividad_minutos`,
  `seguridad.intentos_fallidos_max`, caché de parámetros) y la moneda base
  (`moneda.base_codigo_iso`) se leen de parámetros; impuestos, tipos de cambio,
  precios, países y monedas son datos. Las constantes que quedan son técnicas:
  escalas decimales de la base de datos, tamaños de página y límites de
  consulta, y los idiomas con archivos de mensajes (`es` por defecto, `en`).
  Los mensajes al usuario salen de `messages*.properties`.
- **Frontend:** se eliminaron los últimos valores fijos:
  - El selector de moneda del panel de inicio usaba `['COP', 'USD', 'EUR']`;
    ahora carga las monedas configuradas (`/api/v1/referencias/moneda`, abierta
    a cualquier rol con `CATALOGO_CONSULTAR`).
  - El cliente HTTP mostraba mensajes fijos en inglés ("The request failed."…)
    cuando el servidor no enviaba detalle; ahora se muestra el detalle
    localizado del servidor o un texto traducido (`apiErrorMessage`, también en
    `CrudPage` y en el inicio/cierre de sesión). Un cuerpo de error que no sea
    Problem Details ya no se muestra en crudo.
  - Se eliminó `formatCurrency` (recibía `number`); todos los importes se
    formatean con `formatMoney` a partir del texto decimal.

### Importes como texto decimal en toda la API

`config/JacksonConfig.java` serializa todos los `BigDecimal` como texto decimal
sin notación científica (por ejemplo `"232.0000"`), como pide la convención de
la guía. Los valores JSON libres (valor de un parámetro, atributos de producto)
conservan su tipo JSON. `CrudPage` envía los campos decimales como texto, y el
backend acepta texto decimal en todas las solicitudes con `BigDecimal`.

### Prueba "agregar un país y una moneda por configuración"

`PaisMonedaPorConfiguracionPostgresIntegrationTest` configura, solo mediante la
API de administración y sin cambiar código, una moneda nueva, un país nuevo con
esa moneda local, su impuesto, su habilitación y el tipo de cambio hacia la
moneda base; crea un producto con precio en la moneda nueva y una bodega en el
país nuevo, y verifica que:

- el país aparece entre los países de venta con su moneda local;
- la venta calcula subtotal, impuesto (16 %), total y total en moneda base con
  el tipo de cambio configurado, y devuelve los importes como texto;
- la confirmación descuenta el inventario y registra el movimiento
  `SALIDA_VENTA`.

La prueba se ejecuta en una transacción que se revierte; no deja datos.

### Prueba de traducciones

`frontend/src/i18n/locales.test.ts` comprueba que `es.json` y `en.json` tienen
las mismas claves y que toda clave `t('…')` usada en la interfaz existe.

### Resultados

| Suite | Resultado |
|---|---|
| Backend `mvn clean verify` con PostgreSQL | 73 pruebas, todas en verde |
| Front: lint, tipos, pruebas y build | 0 advertencias · 31 pruebas en verde · build correcto |
| Python: `validar` y `pytest` | 37 pruebas en verde |
| `pre-commit run --all-files` | Correcto |


## Pruebas y logs (E8)

### Requisitos

- **Docker en ejecución** (Docker Desktop en Windows). Las pruebas no usan tu
  base local: cada corrida crea un PostgreSQL 16 desechable y lo elimina al
  terminar (requisito documentado en la sección 2.6 de la guía).
- El entorno `.venv` del proyecto con `requirements-dev.txt` instalado.
- Java 17, Node LTS y npm. El navegador de Playwright lo instala `probar`.

### Comando `probar`

```bat
rem Todas las suites
python tools/cli.py probar
rem Solo algunas suites
python tools/cli.py probar --suites python,frontend
rem Muestra qué se ejecutaría, sin ejecutar nada
python tools/cli.py probar --dry-run
rem E2E sobre Docker Compose (como en el CI)
python tools/cli.py probar --suites e2e --e2e-entorno compose
rem E2E contra un despliegue publicado (Render), ver E11
python tools/cli.py probar --suites e2e --e2e-entorno remoto
rem validar, generar, crear-bd y probar, en ese orden
python tools/cli.py todo
```

Cada ejecución crea `logs/tests/<AAAAMMDD-HHMMSS>/` con:

- `<suite>.log`: salida de consola de cada paso de la suite;
- `<suite>/`: reportes JUnit XML (pytest, Surefire, Vitest y Playwright; en la
  E2E también capturas y traza si falla);
- `resumen.md`: estado, pruebas, fallos, errores, omitidas y duración por suite,
  con el commit evaluado y cada paso con su código de salida.

El comando devuelve código distinto de cero si falla cualquier paso o prueba.

| Suite | Qué ejecuta |
|---|---|
| `python` | `ruff check`, `ruff format --check` y `pytest` (validación, generación, semillas, CLI y base de datos real). |
| `backend` | `mvnw clean verify`: unitarias e integración con PostgreSQL desechable. |
| `frontend` | `npm ci`, lint, typecheck, Vitest y build. |
| `e2e` | Por defecto (`--e2e-entorno local`): base desechable creada con `crear-bd`, backend (JAR) y front (`vite preview`) en puertos libres. Con `--e2e-entorno compose`: `docker-compose.yml` en un proyecto aislado con puertos y secretos propios, que se elimina al terminar. Con `--e2e-entorno remoto`: no levanta nada; despierta y usa el despliegue indicado en `E2E_BASE_URL` y `E2E_API_URL` con el administrador de `E2E_ADMIN_EMAIL` y `E2E_ADMIN_PASSWORD` (crea datos de prueba en esa base). En todos los casos, Playwright con Chromium. |

### Dónde quedan los logs y reportes

Nada de `logs/` se versiona; las evidencias de la
corrida final se copiarán a `docs/evidencias/` en E12.

| Origen | Ubicación |
|---|---|
| `python tools/cli.py probar` | `logs/tests/<AAAAMMDD-HHMMSS>/resumen.md` (resumen), `<suite>.log` (consola) y `<suite>/` (JUnit XML) |
| Capturas y trazas de la E2E cuando falla | `logs/tests/<AAAAMMDD-HHMMSS>/e2e/artefactos/` (abrir la traza con `npx playwright show-trace <trace.zip>` desde `e2e/`) |
| Comandos del CLI (`validar`, `generar`, `crear-bd`, `sembrar`, `reiniciar-bd`, `respaldar`, `restaurar`) | `logs/tools/<comando>-<marca de tiempo>.log` |
| Respaldos de la base (`respaldar`) | `backups/<base>-<AAAAMMDD-HHMMSS>.dump` (no versionado: contiene datos) |
| `mvnw verify` ejecutado a mano | `backend/target/surefire-reports/` |
| Playwright ejecutado a mano | `e2e/reports/` |

Ejemplo de `resumen.md`:

```text
| Suite | Estado | Pruebas | Fallos | Errores | Omitidas | Duración | Log |
| python | ✅ correcto | 48 | 0 | 0 | 0 | 10 s | python.log |
| backend | ✅ correcto | 73 | 0 | 0 | 0 | 21 s | backend.log |
...
### e2e
- empaquetar backend: ✅ — 3 s
- crear-bd: ✅ — 1 s
- playwright: ✅ — 10 s
```

Cada suite lista sus pasos con el código de salida; si un paso falla, su
`<suite>.log` muestra la salida completa de ese paso entre marcas `=====`.

### Qué cubre cada suite

| Suite | Archivos | Cobertura |
|---|---|---|
| python | `tools/tests/test_model_validator.py`, `test_sql_generator.py`, `test_database.py` | Validación del modelo, generación de SQL y metadatos, semillas, CLI y migraciones con checksum. |
| python | `tools/tests/test_database_integracion.py` | Base real: idempotencia de `crear-bd`, `reiniciar-bd`, saldo no negativo e inmutabilidad. |
| python | `tools/tests/test_test_runner.py` | Conteo de JUnit, `resumen.md` y opciones de `probar` (incluida la E2E remota sin variables). |
| python | `tools/tests/test_respaldo.py` | Retención de RNF-011, contraseña fuera de la línea de comandos, confirmación de `restaurar` y respaldo y restauración reales en un contenedor. |
| backend | `src/test/java/.../service/*Test.java` | Unitarias: autenticación, JWT, sesiones, parámetros, impuestos, conversión, catálogo, clientes, inventario, ventas, referencias y reportes. |
| backend | `src/test/java/.../service/*PostgresIntegrationTest.java` | Integración con API y base: permisos, alcance por país, catálogo, clientes, compras, ventas, ajustes, parámetros y país/moneda agregados solo con datos. |
| frontend | `src/**/*.test.ts(x)` | Inicio de sesión, cliente HTTP, navegación por permisos, CRUD genérico, catálogo, inventario, compras, ventas, reportes, configuración, formatos, decimales y traducciones. |
| e2e | `e2e/tests/flujo-venta.spec.ts` | Flujo completo en la interfaz (ver abajo). |

### Si algo falla

- **"Docker no está disponible"** o integraciones omitidas: inicia Docker
  Desktop y espera a que indique que está en ejecución.
- **Testcontainers no encuentra Docker** con un error 400: la versión de Docker
  Engine exige otra API; ajusta `api.version` en
  `backend/src/test/resources/docker-java.properties`.
- **Una suite concreta:** repítela sola con `--suites <nombre>` y revisa su
  `<suite>.log`.

### Integración del backend con Testcontainers

Las pruebas de integración heredan de `support/PostgresIntegrationTest`: en cada
ejecución se inicia un PostgreSQL 16 desechable y se crea con
`python tools/cli.py crear-bd`, igual que en cualquier otro entorno (sin Flyway).
Ya no usan la base local ni requieren `.env`; `JWT_SECRET` y las credenciales del
administrador de prueba se generan al vuelo. Sin Docker, esas pruebas se omiten.
Python se toma de la variable `PYTHON` o del entorno `.venv` del proyecto.
`src/test/resources/docker-java.properties` fija la versión de la API de Docker
que exige Docker Engine 29.

### Base de datos real en pytest

`tools/tests/test_database_integracion.py` usa un contenedor desechable
(`tools/postgres_desechable.py`) para comprobar los criterios de E3: `crear-bd`
aplica las tres migraciones y es idempotente, `reiniciar-bd` recrea la base, un
saldo negativo se rechaza y `UPDATE`/`DELETE` sobre `movimiento_inventario`
fallan por el trigger de inmutabilidad.

### Prueba de extremo a extremo

`e2e/tests/flujo-venta.spec.ts` recorre en la interfaz, en español: inicio de
sesión → crear producto con precio en COP → en *Compras*, vincular el producto
al proveedor, crear la orden de 5 unidades (tasa fija en 1 por ser COP) y
enviarla → registrar la entrada de las 5 unidades → vender 2 unidades (total
357.000 COP con IVA del 19 %) → confirmar el pago y ver el recibo → comprobar
existencia 3 y el movimiento "Salida por venta". Solo el tipo de producto, las
categorías y el proveedor se preparan por API (son CRUD genéricos ya probados),
para que la prueba sea corta.

### Resultado de la última corrida completa (`logs/tests/20261021-184417/`, E2E sobre Docker Compose)

| Suite | Pruebas |
|---|---:|
| python | 62 |
| backend | 74 |
| frontend | 36 |
| e2e | 1 |
| **Total** | **173, todas en verde** |

## Contenedores (E10)

### Puesta en marcha con un solo comando

Requisitos: Docker Desktop en ejecución y un `.env` con `DB_PASSWORD`,
`JWT_SECRET`, `ADMIN_EMAIL` y `ADMIN_PASSWORD` (ver `.env.example`).

```bat
rem Primer arranque, o después de cambiar el código (construye las imágenes)
docker compose up --build
rem En segundo plano: devuelve la consola cuando todo está sano
docker compose up --build -d --wait
```

Después se entra en `http://localhost:8081` con `ADMIN_EMAIL` y
`ADMIN_PASSWORD`. La API queda en `http://localhost:8080` (salud en
`/actuator/health`).

### Servicios

| Servicio | Imagen | Función | Arranca cuando |
|---|---|---|---|
| `db` | `postgres:16-alpine` | Base de datos con volumen persistente `db-data`. | — |
| `db-init` | `docker/db-init.Dockerfile` (Python 3.12) | Ejecuta `python tools/cli.py crear-bd`: crea la base si falta, aplica migraciones pendientes, carga semillas y verifica; termina con código 0. | `db` está sana (`pg_isready`). |
| `backend` | `docker/backend.Dockerfile` | API Spring Boot con perfil `produccion`; publica `BACKEND_PORT` (8080). | `db-init` terminó bien. |
| `frontend` | `docker/frontend.Dockerfile` | Sitio estático compilado con Vite y servido por nginx; publica `FRONTEND_PORT` (8081). | `backend` está sano (`/actuator/health`). |

### Imágenes

- **Backend:** etapa de compilación con JDK 17 y el Maven Wrapper (Maven
  3.9.11); ejecución con JRE 21, usuario sin privilegios y JVM para poca
  memoria (`-XX:MaxRAMPercentage=65 -XX:+UseSerialGC
  -XX:TieredStopAtLevel=1`). Incluye `db/model/schema-resuelto.json` para validar
  el esquema al arrancar. En reposo usa unos 250 MiB.
- **Frontend:** `npm ci` y `npm run build` con `VITE_API_URL` como argumento de
  construcción (la URL que usa el navegador; no debe contener secretos), servido
  por nginx con respaldo a `index.html` para las rutas de la aplicación, caché
  larga para `/assets/` y sin caché para `index.html`.
- **db-init:** Python 3.12 con solo las dependencias de ejecución
  (`requirements.txt`), `tools/` y `db/`.

`.dockerignore` deja fuera del contexto `.env`, dependencias instaladas,
compilaciones, logs y documentación.

### Configuración

Docker Compose lee el `.env` de la raíz. Dentro de la red de Docker la base es
el servicio `db` (el `DB_HOST` del `.env` no se usa). `CORS_ORIGINS` del backend
y `VITE_API_URL` del frontend se derivan de `FRONTEND_PORT` y `BACKEND_PORT`, de
modo que no chocan con los valores de desarrollo (`5173`). Si falta una variable
obligatoria, `docker compose` se detiene con un mensaje que la nombra.

### ¿Para qué sirve `db-init`? ¿Es necesario?

Sí. El proyecto exige que **la base se cree solo con los scripts de Python**
(sin Flyway ni que el backend modifique el esquema). `db-init` es ese script
empaquetado: un contenedor de un solo uso que ejecuta `python tools/cli.py
crear-bd` contra la base de Docker (crea la base si falta, aplica las
migraciones pendientes, carga las semillas iniciales y verifica el esquema) y
termina. El backend no arranca hasta que `db-init` termina bien, porque al
iniciar valida que el esquema coincida con el modelo. Por eso en Docker Desktop
aparece como **detenido (exited 0)**: es lo esperado. También se reutiliza para
la carga manual de datos de demostración (`docker compose run --rm db-init …`).

### Cuándo reconstruir o borrar los contenedores

- **Después de cambiar el código** (o de traer cambios con `git pull`):
  `docker compose up --build -d --wait`. Reconstruye las imágenes y recrea solo
  los contenedores que cambiaron; los datos se conservan. No hace falta borrar
  nada.
- **Para empezar con una base limpia** (por ejemplo, antes de una
  demostración): `docker compose down -v` y luego `up --build`; después, si se
  quieren, los datos de demostración.
- **Para liberar espacio** de imágenes antiguas: `docker compose down --rmi local`.

### Reinicios y datos

`db-init` se ejecuta en cada arranque y es idempotente: en un segundo arranque
no aplica migraciones ni inserta semillas, así que no duplica datos.

```bat
rem Ver los logs de un servicio (Ctrl+C para salir)
docker compose logs -f backend
rem Detener y conservar los datos (volumen db-data)
docker compose down
rem Detener y BORRAR la base para empezar de cero
docker compose down -v
```

La pila no arranca sola al abrir Docker Desktop: solo corre cuando la levantas
con `docker compose up`, así que no ocupa los puertos 8080/8081 mientras
desarrollas con el backend y el frontend locales.

### Verificación de la etapa

| Comprobación | Resultado |
|---|---|
| `docker compose up --build --wait` desde cero | Los cuatro servicios en orden; `db-init` aplicó V001–V003 y cargó las semillas. |
| Inicio de sesión del administrador, CORS desde el frontend, rutas de la aplicación y URL de la API en el bundle | Correcto. |
| `docker compose down` + `up` | `db-init`: 0 migraciones y 0 semillas nuevas; conteos sin cambios. |
| E2E de Playwright sobre la pila (`probar --suites e2e --e2e-entorno compose`) | Correcta; la pila aislada se elimina al terminar. |

### Revisión y correcciones de E10

Antes de aprobar E10 se revisó todo el proyecto y se aplicaron estas
correcciones:

| Tema | Hallazgo | Corrección |
|---|---|---|
| Resumen del CI | En la página de una ejecución apareció un resumen con "e2e ❌ fallido" y "python 42 pruebas". **No era una ejecución real**: lo escribía la prueba unitaria `test_resumen_lista_cada_suite_y_marca_los_fallos`, que fabrica a propósito un resultado fallido para comprobar el formato de `resumen.md`, y al estar definida la variable `GITHUB_STEP_SUMMARY` en el CI lo añadía al resumen de GitHub. La E2E real pasó. | Las pruebas del orquestador borran esa variable antes de ejecutarse (fixture `sin_resumen_de_github`); comprobado que ya no escriben nada en el resumen del CI. |
| Windows (`cmd`) | Arrancar el backend en local exigía un bloque de PowerShell para cargar `.env`, y el README usaba comandos de PowerShell (`Push-Location`, `;`, comentarios con `#`). | El backend lee `.env` por sí mismo (`spring.config.import`); todos los comandos del README están en sintaxis de `cmd` y se verificaron en `cmd.exe` (CLI, `probar`, `pre-commit`, pruebas del front, Docker Compose y arranque del backend leyendo solo `.env`). |
| Docker Compose | Con `restart: unless-stopped`, la pila volvía a arrancar sola al abrir Docker Desktop y ocupaba los puertos 8080/8081. | Sin política de reinicio: la pila solo corre cuando se levanta con `docker compose up`. |
| Detección de secretos | detect-secrets marcaba la URL de conexión con credenciales que armaba la base de pruebas de Testcontainers. | La conexión se pasa con variables `DB_*` y `DATABASE_URL` vacía. |
| Código sin uso | `ParametroService.invalidar(clave)` no se usaba (la caché se invalida completa con `invalidarTodo()`); 7 claves de traducción ya no las usaba ninguna pantalla. | Eliminados. Se comprobó además que todas las clases y métodos públicos del backend, todas las funciones de `tools/`, todas las exportaciones y clases CSS del front y todos los mensajes del backend se usan. |
| Archivos sin uso | Copias duplicadas de la especificación en la raíz (`GUIDE.md` desactualizada y `REQUIREMENTS.md`); restos de la plantilla de Vite (`frontend/README.md`, `public/favicon.svg` y `public/icons.svg` con el logo de Vite, sin enlazar; `src/assets/hero.png`); 13 archivos `.gitkeep` en carpetas que ya tienen contenido o que el código crea solo (`logs/`). | Eliminados. `INSTRUCTIONS.md` se movió a `spec/`, junto a los demás documentos de referencia. `logs/` queda ignorado por completo en `.gitignore`. |
| Definiciones de atributos | Al preparar los datos de demostración apareció un error de E5.3: una definición sin opciones enviada con `"opciones": null` se rechazaba como "no es una lista" (Jackson lo recibe como nodo JSON nulo). | `null` explícito equivale a no indicar opciones; prueba unitaria añadida. |
| Documentación | El CI se describía "sobre Ubuntu" sin explicar por qué. | Nueva sección [¿Por qué aparece Linux en el CI?](#por-qué-aparece-linux-en-el-ci). |

## Despliegue de demostración en Render (E11)

El objetivo de E11 es una **URL pública para demostrar** el sistema. Se usa
[Render](https://render.com) con su plan gratuito y un *Blueprint*:
`render.yaml`, en la raíz, describe los tres recursos y Render los crea juntos.

### Condiciones del plan gratuito (verificadas el 21-10-2026)

Según la [documentación de Render](https://render.com/docs/free):

| Recurso | Condición | Qué implica para la demostración |
|---|---|---|
| Servicio web (backend) | Se **suspende tras 15 minutos sin tráfico** y tarda **alrededor de 1 minuto** en despertar. 512 MB de memoria. | Abrir la aplicación 2 minutos antes de presentar. La primera petición tras la pausa es lenta. |
| Base PostgreSQL | **Caduca 30 días después de crearla**; luego hay 14 días para pasarla a un plan pago y después Render la borra con sus datos. 1 GB. Solo una base gratuita activa por cuenta. | Respaldar con frecuencia y saber recrearla (ver más abajo). |
| Sitio estático (frontend) | Gratuito; consume el ancho de banda y los minutos de compilación incluidos. | Sin pausas: siempre responde. |
| Cuenta | 750 horas de instancia gratuitas al mes. | Suficiente para un servicio encendido todo el mes. |

Estas condiciones cambian con el tiempo: revísalas antes de una entrega.

### Qué define `render.yaml`

| Recurso | Nombre | Detalle |
|---|---|---|
| Base de datos | `pixelvault-db` | PostgreSQL **16** (igual que Docker Compose y el `pg_dump` de los respaldos), base `inventario`, usuario `inventario`, región `virginia` (la más cercana a Colombia de las disponibles). |
| Backend | `pixelvault-api` | Servicio web con la imagen de `docker/backend.Dockerfile` (la misma de E10), perfil `produccion`, comprobación de salud en `/actuator/health` y región `virginia`, para hablar con la base por la red interna. |
| Frontend | `pixelvault-web` | Sitio estático: `npm ci` y `npm run build` en `frontend/` con Node 22; publica `frontend/dist` y reescribe cualquier ruta a `index.html`. |

Cada servicio se vuelve a desplegar solo cuando cambian sus archivos (`buildFilter`)
en la rama `main`. **La base no se crea en Render**: como pide la guía, se crea
con `crear-bd` desde el equipo local, igual que en cualquier otro entorno.

Variables de entorno:

| Variable | Servicio | Valor | Cómo se define |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | backend | Formato **JDBC** con el host **interno** de la base: `jdbc:postgresql://dpg-xxxxxxxx-a:5432/inventario` | A mano (`sync: false`): Render da la URL como `postgresql://usuario:clave@host/base` y Spring necesita la forma JDBC, sin usuario ni clave. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | backend | Usuario y contraseña de la base | Automático, desde la base (`fromDatabase`). |
| `JWT_SECRET` | backend | Clave aleatoria de 256 bits en base64 | Automático: Render la genera una vez (`generateValue`). |
| `CORS_ORIGINS` | backend | URL pública del frontend, por ejemplo `https://pixelvault-web.onrender.com` | A mano (`sync: false`). |
| `SPRING_PROFILES_ACTIVE`, `PORT`, `SERVER_PORT` | backend | `produccion`, `8080`, `8080` | Fijos en `render.yaml`. |
| `VITE_API_URL` | frontend | URL pública del backend, por ejemplo `https://pixelvault-api.onrender.com` | A mano (`sync: false`). Vite la incorpora al compilar: si cambia, hay que volver a desplegar el sitio. |
| `NODE_VERSION` | frontend | `22` | Fijo en `render.yaml`. |

Ningún secreto queda en el repositorio: los valores manuales se escriben en el
panel de Render y las credenciales de la base las inyecta Render.

### Paso a paso (una sola vez)

**1. Crear el Blueprint.** En el panel de Render: *New* → *Blueprint* → elegir
el repositorio de GitHub → rama `main`. Render lee `render.yaml` y pide los
valores manuales. Si aún no conoces las URL, escribe las esperadas
(`https://pixelvault-api.onrender.com`, `https://pixelvault-web.onrender.com`) y
en `SPRING_DATASOURCE_URL` un valor provisional
(`jdbc:postgresql://pendiente:5432/inventario`). Pulsa *Apply*.

**2. Copiar las URL de la base.** En *pixelvault-db* → *Connections* están la
*Internal Database URL* (para el backend) y la *External Database URL* (para tu
equipo). Son secretas: no las pegues en archivos versionados.

**3. Crear la base desde el equipo local**, en `cmd`, con el entorno activado.
Las variables `set` solo duran mientras la ventana esté abierta, y las comillas
alrededor de `NOMBRE=valor` evitan problemas con caracteres especiales:

```bat
.venv\Scripts\activate.bat
set "DATABASE_URL=postgresql://inventario:CLAVE@dpg-xxxxxxxx-a.virginia-postgres.render.com/inventario?sslmode=require"
set "ADMIN_EMAIL=admin@tu-dominio.com"
set "ADMIN_PASSWORD=una-clave-larga-solo-para-render"
python tools/cli.py crear-bd
```

Usa para Render una contraseña de administrador distinta de la local. `crear-bd`
se conecta directamente a la base `inventario` (Render no permite crear bases
ni entrar a `postgres`), aplica las migraciones, carga las semillas y verifica
el esquema. Es idempotente: repetirlo no duplica nada.

**4. Conectar el backend.** En *pixelvault-api* → *Environment*, cambia
`SPRING_DATASOURCE_URL` por la forma JDBC de la URL **interna**. Por ejemplo,
`postgresql://inventario:CLAVE@dpg-xxxxxxxx-a/inventario` se convierte en
`jdbc:postgresql://dpg-xxxxxxxx-a:5432/inventario`. Guarda y elige *Save, rebuild
and deploy*. El primer despliegue del paso 1 falla con seguridad, porque la base
aún no tenía esquema: es lo esperado.

**5. Revisar las URL.** Si Render asignó nombres con sufijo (por ejemplo,
`pixelvault-api-x1y2.onrender.com`), corrige `CORS_ORIGINS` en el backend y
`VITE_API_URL` en el frontend, y vuelve a desplegar ambos.

**6. Verificar.**

```bat
curl https://pixelvault-api.onrender.com/actuator/health
```

Debe responder `{"status":"UP"}` (la primera vez puede tardar un minuto si el
servicio estaba suspendido). Luego abre `https://pixelvault-web.onrender.com` y
entra con el administrador del paso 3.

**7. Repetir el flujo de E8 en la URL pública** (revisión de la etapa), con logs
en `logs/tests/` como cualquier otra corrida:

```bat
set "E2E_BASE_URL=https://pixelvault-web.onrender.com"
set "E2E_API_URL=https://pixelvault-api.onrender.com"
set "E2E_ADMIN_EMAIL=admin@tu-dominio.com"
set "E2E_ADMIN_PASSWORD=una-clave-larga-solo-para-render"
python tools/cli.py probar --suites e2e --e2e-entorno remoto
```

Primero despierta el backend y el sitio (espera hasta 5 minutos) y luego ejecuta
el mismo flujo de Playwright: crear producto, vincularlo al proveedor, orden de
compra, entrada, venta, pago, recibo, saldo y movimiento. Deja esos datos de
prueba en la base de Render.

**8. (Opcional) Datos de demostración**, con `DATABASE_URL`, `ADMIN_EMAIL` y
`ADMIN_PASSWORD` del paso 3 y `DEMO_PASSWORD` definidos en la consola:

```bat
set "DEMO_PASSWORD=clave-comun-de-los-usuarios-demo"
python tools/cli.py sembrar-demo --api https://pixelvault-api.onrender.com
```

### Antes de cada demostración

1. Abrir `https://pixelvault-web.onrender.com` y entrar, unos 2 minutos antes,
   para despertar el backend.
2. Revisar la fecha de caducidad de la base en el panel (*pixelvault-db*) y
   tener un respaldo reciente.

### Respaldo y restauración

Cumplen RNF-011 en la versión académica: un script de respaldo y restauración
con copias en otra ubicación (tu equipo). Ambos comandos ejecutan `pg_dump` y
`pg_restore` dentro de la imagen oficial `postgres:16-alpine`, así que **no hace
falta instalar PostgreSQL**, basta con Docker en ejecución, y la versión siempre
coincide con la de la base. La contraseña pasa al contenedor como variable de
entorno, nunca en la línea de comandos.

```bat
rem Respaldar la base de .env, o la de DATABASE_URL si está definida
python tools/cli.py respaldar
rem Respaldar y aplicar la retención (ver abajo)
python tools/cli.py respaldar --podar
rem Restaurar: reemplaza TODO el contenido de la base; exige confirmar su nombre
python tools/cli.py restaurar --archivo backups\inventario-20261021-020000.dump --confirm-db inventario
```

- `respaldar` crea `backups/<base>-<AAAAMMDD-HHMMSS>.dump` (formato comprimido
  de `pg_dump`, hora UTC). La carpeta `backups/` está en `.gitignore` porque
  contiene datos reales.
- `--podar` aplica la retención de RNF-011: conserva **todas las copias de los
  últimos 35 días** y, de las anteriores, **la primera de cada mes durante 12
  meses**; borra el resto.
- `restaurar` usa `pg_restore --clean --if-exists` en **una sola transacción**:
  si algo falla, la base queda como estaba. Al terminar verifica el esquema
  contra el modelo, igual que `crear-bd`.
- Los dos aceptan `--dry-run` y dejan su log en `logs/tools/`.
- Para respaldar la base de Docker Compose, publica su puerto o usa
  `docker compose exec db pg_dump`; los comandos del CLI apuntan a la base de
  `.env` o de `DATABASE_URL`.

**Respaldo automático diario.** RNF-011 pide una copia diaria. En Windows se
programa con el Programador de tareas. Crea, fuera del repositorio (por ejemplo,
en `C:\respaldos-pixelvault\respaldar-render.cmd`), un archivo con la URL
externa de la base:

```bat
@echo off
cd /d C:\ruta\al\Proyecto_Inventario_Videojuegos
set "DATABASE_URL=postgresql://inventario:CLAVE@dpg-xxxxxxxx-a.virginia-postgres.render.com/inventario?sslmode=require"
.venv\Scripts\python.exe tools\cli.py respaldar --podar
```

y prográmalo una vez al día, por ejemplo a las 02:00:

```bat
schtasks /create /tn "PixelVault respaldo" /sc daily /st 02:00 /tr "C:\respaldos-pixelvault\respaldar-render.cmd"
```

El equipo debe estar encendido y con Docker Desktop abierto a esa hora.

### Si la base caduca (o hay que recrearla)

1. Crear una base nueva: en el panel, *New* → *Postgres* con el mismo nombre
   `pixelvault-db`, base `inventario`, usuario `inventario`, versión 16 y región
   `virginia`, o volver a sincronizar el Blueprint.
2. Con la nueva URL externa en `DATABASE_URL`, restaurar el último respaldo:
   `python tools/cli.py restaurar --archivo backups\<último>.dump --confirm-db inventario`.
   Si no hay respaldo, `python tools/cli.py crear-bd` deja la base vacía con el
   administrador y las semillas.
3. Actualizar `SPRING_DATASOURCE_URL` del backend con el nuevo host interno y
   volver a desplegar. Si la base nueva no quedó enlazada al Blueprint, copia
   también su usuario y contraseña del panel en `SPRING_DATASOURCE_USERNAME` y
   `SPRING_DATASOURCE_PASSWORD`.

Con el respaldo a mano, todo el proceso toma unos minutos, muy por debajo de
las 3 horas de RNF-011.

## Integración continua (E9)

`.github/workflows/ci.yml` se ejecuta una vez por cambio: con `pull_request` en
los pull requests y con `push` solo en `main` (después de fusionar), para que
los checks no aparezcan duplicados. Las ramas sin pull request no ejecutan el
CI. Son cuatro trabajos en paralelo en los servidores de GitHub. Cada uno usa el mismo comando que en local,
`python tools/cli.py probar --suites <suite>`, y sube sus logs como artefacto
aunque falle.

| Trabajo | Qué comprueba | Artefacto |
|---|---|---|
| `python` | `validar`; que `generar` no produzca diferencias con lo versionado (migraciones, modelo resuelto, SQL de CRUD y metadatos del front); ruff, formato y pytest con PostgreSQL desechable. | `logs-python` |
| `backend` | `mvnw clean verify`: pruebas unitarias e integración con Testcontainers (los runners de GitHub traen Docker). | `logs-backend` |
| `frontend` | `npm ci`, lint, typecheck, Vitest y build. | `logs-frontend` |
| `e2e` | Instala Chromium con sus dependencias, construye las imágenes de E10 y ejecuta el flujo producto → entrada → venta sobre Docker Compose (`--e2e-entorno compose`). | `logs-e2e` (incluye capturas, traza si falla y los logs de los contenedores) |

Cada artefacto contiene `logs/tests/<fecha-hora>/` (`resumen.md`, `<suite>.log`
y JUnit XML) y, cuando aplica, `logs/tools/`. Se descargan desde la pestaña
**Actions** del repositorio → ejecución → sección **Artifacts**. El
`resumen.md` de cada trabajo también se muestra en la página de la ejecución
(resumen del trabajo). Si se envía otro `push` a la misma rama, la ejecución
anterior se cancela.

Verificación de la etapa: el CI pasó en verde con los cuatro trabajos y sus
artefactos, y en una rama temporal con una prueba rota a propósito el trabajo
`python` falló (la ejecución quedó en rojo y el artefacto con el log se subió
igual); esa rama se eliminó después.

Desde E10 la prueba E2E del CI se ejecuta sobre Docker Compose, como pide la
guía; así el CI también comprueba que las imágenes se construyen y arrancan.

### ¿Por qué aparece Linux en el CI?

El proyecto se desarrolla y se usa en **Windows**; tú no necesitas Linux ni
Ubuntu. Lo único que corre en Linux son los **servidores de GitHub** que
ejecutan el CI (`runs-on: ubuntu-latest` en `ci.yml`), por dos motivos:

- Las pruebas de integración, la base de pruebas y Docker Compose usan
  contenedores **Linux** (`postgres:16-alpine`, las imágenes de E10). En tu
  equipo, Docker Desktop los ejecuta sin que tengas que hacer nada; en los
  servidores Windows de GitHub, Docker solo admite contenedores Windows, así que
  esas pruebas no podrían correr allí.
- Son los servidores más rápidos y gratuitos para repositorios públicos.

Los comandos que ejecuta el CI son los mismos que usas en Windows
(`python tools/cli.py probar --suites <suite>`); `tools/test_runner.py` elige
automáticamente `mvnw.cmd` en Windows y `mvnw` en Linux.

### Hook local antes de cada push

`pre-commit install` instala dos tipos de hook (`default_install_hook_types`):

- **pre-commit:** ruff, formato y detección de secretos.
- **pre-push:** `validar`, `pytest` y las pruebas de Vitest. La E2E y el
  backend completo quedan para el CI por su duración.

Para ejecutarlos a mano: `pre-commit run --all-files` y
`pre-commit run --hook-stage pre-push --all-files`.

### Protección de la rama `main`

`main` está protegida por el *branch ruleset* **"Proteger main - CI
obligatorio."** (activo, configurado en **Settings → Rules → Rulesets**). Ningún
cambio entra en `main` sin pasar por un pull request con el CI en verde:

| Regla | Efecto |
|---|---|
| Pull request obligatorio | No se puede hacer `push` directo a `main`; todo cambio llega desde otra rama mediante un pull request. No se exigen aprobaciones de revisores (0), adecuado para un equipo de una persona. |
| Checks de estado obligatorios | La fusión se bloquea hasta que terminen en verde los cuatro trabajos del CI: `Python (ruff, pytest, validar y generado)`, `Backend (mvn verify con Testcontainers)`, `Frontend (lint, typecheck, test y build)` y `Extremo a extremo (Playwright)`. Si alguno falla o sigue en curso, el botón de fusión queda deshabilitado. |
| Sin borrado de la rama | `main` no se puede eliminar. |
| Sin *force push* | No se puede reescribir el historial de `main`. |

Métodos de fusión permitidos: *merge*, *squash* y *rebase*. No se exige que la
rama esté al día con `main` antes de fusionar (opción *Require branches to be
up to date* desactivada).

#### Flujo de trabajo

1. Crear una rama nueva desde `main` actualizada: `git switch -c mi-cambio`.
2. Hacer los cambios y probarlos en local: `python tools/cli.py probar`.
3. Publicar la rama: `git push -u origin mi-cambio`.
4. En GitHub, abrir el pull request de `mi-cambio` hacia `main` (el enlace
   aparece al hacer `push` o en la pestaña **Pull requests**).
5. Esperar a que los cuatro checks terminen en verde; si alguno falla, revisar
   su artefacto de logs, corregir en la misma rama y volver a hacer `push`.
6. Fusionar el pull request y actualizar la copia local:
   `git switch main` y `git pull`.

#### Cómo se configuró

En GitHub: **Settings → Rules → Rulesets → New branch ruleset**, con destino la
rama `main` y *Enforcement status* **Active**, activando **Restrict
deletions**, **Block force pushes**, **Require a pull request before merging**
y **Require status checks to pass** con los cuatro trabajos anteriores (sus
nombres aparecen después de la primera ejecución del CI). En repositorios
privados, las reglas requieren un plan de GitHub que las incluya; en
repositorios públicos, como este, están en el plan gratuito.

## Verificaciones

```bat
python tools/cli.py --help
python tools/cli.py validar
python tools/cli.py generar --dry-run
python tools/cli.py probar
pre-commit run --all-files
```

Cada suite también se puede ejecutar por separado:

```bat
python -m pytest
cd backend && .\mvnw.cmd clean verify && cd ..
cd frontend && npm run lint && npm run typecheck && npm test && npm run build && cd ..
```

Las pruebas de integración y la E2E necesitan Docker en ejecución; no usan la
base local de `.env`.

Los logs locales se guardan en `logs/` (ver
[Dónde quedan los logs y reportes](#dónde-quedan-los-logs-y-reportes)); las
evidencias de la corrida final se publicarán en `docs/evidencias/` (E12).

## Estructura

Estructura actual del repositorio (sin dependencias instaladas ni salidas de
compilación):

```text
.
├── README.md                     # Este documento
├── .env.example                  # Variables de entorno documentadas (sin secretos)
├── .gitignore
├── .gitattributes                # Saltos de línea LF en los archivos que usan los contenedores
├── .pre-commit-config.yaml       # Hooks: commit (ruff, secretos) y push (validar y pruebas)
├── .secrets.baseline             # Falsos positivos aceptados por detect-secrets
├── pyproject.toml                # Configuración de ruff y pytest
├── requirements.txt              # Dependencias de ejecución de tools/ (versión fija)
├── requirements-dev.txt          # requirements.txt + herramientas de desarrollo
├── docker-compose.yml            # db, db-init, backend y frontend (E10)
├── render.yaml                   # Blueprint de Render: base, backend y frontend (E11)
├── .dockerignore                 # Contexto mínimo para construir imágenes
├── .github/workflows/ci.yml      # CI con GitHub Actions (E9)
├── spec/
│   ├── REQUIREMENTS.md           # Especificación de requisitos v0.7
│   ├── GUIDE.md                  # Guía de desarrollo por etapas
│   ├── INSTRUCTIONS.md           # Instrucciones de trabajo por etapas (resumen)
│   └── modelo-conceptual.md      # E1: entidades, reglas, estados y permisos
├── db/
│   ├── model/
│   │   ├── model.schema.json     # JSON Schema del modelo
│   │   ├── modulos/              # catalogo, inventario, parametrizacion, seguridad,
│   │   │                         # terceros, transversal y ventas (.json)
│   │   └── schema-resuelto.json  # Modelo resuelto (generado)
│   ├── migrations/
│   │   ├── V001__modelo_inicial.sql          # Generada desde el modelo
│   │   ├── V002__pais_bodega.sql             # País de la bodega
│   │   └── V003__bodega_pais_obligatorio.sql # País obligatorio en bodegas
│   └── seeds/
│       ├── inicial.json          # Países, monedas, idiomas, impuestos, roles,
│       │                         # permisos, bodega y parámetros (crear-bd)
│       └── demo.json             # Datos de demostración (sembrar-demo, manual)
├── tools/
│   ├── cli.py                    # validar, generar, crear-bd, reiniciar-bd, sembrar,
│   │                             # sembrar-demo, respaldar, restaurar, probar y todo
│   ├── demo_seed.py              # Carga de datos de demostración vía API
│   ├── env_config.py             # Carga de .env sin reemplazar el entorno
│   ├── model_validator.py        # Validación del modelo JSON
│   ├── sql_generator.py          # SQL y metadatos del back y del front
│   ├── seed_loader.py            # Carga idempotente de semillas
│   ├── database.py               # Conexión, migraciones con checksum y verificación
│   ├── test_runner.py            # Comando probar: suites, logs y resumen.md
│   ├── postgres_desechable.py    # PostgreSQL temporal en Docker para pruebas
│   ├── respaldo.py               # pg_dump/pg_restore en Docker y retención (RNF-011)
│   ├── templates/migration.sql.j2
│   └── tests/                    # pytest: modelo, generador, base de datos, base real
│                                 # en contenedor y orquestador de pruebas
├── backend/
│   ├── pom.xml, mvnw, mvnw.cmd, .mvn/
│   └── src/
│       ├── main/java/com/inventario/
│       │   ├── InventarioApplication.java
│       │   ├── config/           # API, CORS, idioma, paginación, JSON (decimales
│       │   │                     # como texto), esquema y seguridad
│       │   ├── controller/       # Endpoints REST: autenticación, configuración,
│       │   │                     # catálogo, clientes, proveedores, compras, inventario,
│       │   │                     # ajustes, ventas, reportes, parámetros y referencias
│       │   ├── domain/           # Registros del dominio (Venta, Producto, Parametro…)
│       │   ├── dto/              # Solicitudes y respuestas de la API
│       │   ├── generated/        # SQL de CRUD generado; no editar a mano
│       │   ├── repository/       # Persistencia JDBC por módulo
│       │   ├── security/         # Filtro JWT, cadena de seguridad y errores 401/403
│       │   └── service/          # Reglas de negocio, transacciones y auditoría
│       ├── main/resources/       # application*.yml y mensajes es/en
│       ├── test/java/com/inventario/service/  # Unitarias e integración PostgreSQL
│       ├── test/java/com/inventario/support/  # Base Testcontainers + crear-bd
│       └── test/resources/       # docker-java.properties (API de Docker)
├── frontend/
│   ├── package.json, package-lock.json, vite.config.ts, tsconfig*.json, .oxlintrc.json
│   ├── index.html                # Página base de Vite (título, metadatos y favicon)
│   ├── public/favicon.svg        # Logo de PixelVault (bóveda en pixel art)
│   └── src/
│       ├── main.tsx, App.tsx, theme.ts, navigation.ts, index.css
│       ├── api/                  # apiClient (peticiones y CSV), useApiQuery,
│       │                         # errores y tipos comunes
│       ├── auth/                 # Sesión, identidad y renovación del token
│       ├── crud/metadata.ts      # Lectura de metadatos generados y permisos
│       ├── generated/            # entidades.json y entidades.ts (generados)
│       ├── i18n/                 # Configuración, locales es.json / en.json y su prueba
│       ├── components/
│       │   ├── AppShell, Dashboard, LoginPage, LanguageSwitcher
│       │   ├── CrudPage          # CRUD genérico (E6.2)
│       │   ├── PageHeader, CrudEntityLinks, PixelVaultLogo
│       │   ├── catalog/          # Productos y formulario dinámico (E6.3)
│       │   ├── inventory/        # Existencias, entradas, salidas, ajustes y
│       │   │                     # movimientos (E6.3)
│       │   ├── purchases/        # Órdenes de compra y productos por proveedor
│       │   ├── sales/            # Ventas, detalle y recibo (E6.4)
│       │   ├── reports/          # Existencias, movimientos y CSV (E6.4)
│       │   └── settings/         # Parámetros y configuración (E6.4)
│       ├── utils/                # Formatos Intl, decimales y referencias
│       └── test/                 # Configuración de Vitest y simulador de API
│                                 # (las pruebas *.test.ts(x) junto a cada módulo)
├── e2e/
│   ├── package.json, package-lock.json   # Playwright con versión fija
│   ├── playwright.config.ts      # URLs y credenciales por variables de entorno
│   └── tests/flujo-venta.spec.ts # Producto → orden de compra → entrada → venta →
│                                 # saldo y movimiento
├── docker/
│   ├── backend.Dockerfile        # JDK 17 (compilación) → JRE 21 (ejecución)
│   ├── frontend.Dockerfile       # Node 22 (build de Vite) → nginx
│   ├── nginx.conf                # Sitio estático con respaldo a index.html
│   └── db-init.Dockerfile        # Python 3.12 que ejecuta crear-bd
├── backups/                      # No versionado: respaldos .dump (respaldar)
├── logs/                         # No versionado: tools/ (CLI), tests/<fecha-hora>/
│                                 # (probar) y corridas manuales de revisión
└── docs/evidencias/              # Evidencias de la corrida final (E12)
```

La capa REST valida y autoriza solicitudes; los servicios aplican reglas y
transacciones; los repositorios acceden a PostgreSQL con JDBC; `domain` y `dto`
mantienen separados los datos internos y los contratos de API. El SQL asociado
a CRUD se genera desde el modelo JSON y se conserva en `generated/`.
