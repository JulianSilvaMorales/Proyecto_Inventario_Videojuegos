# PixelVault — Sistema de inventario y ventas para consolas y videojuegos

Aplicación web para administrar catálogo, inventario y ventas con soporte
internacional. El alcance y las decisiones del proyecto están documentados en
[`spec/REQUIREMENTS.md`](spec/REQUIREMENTS.md), y el proceso de desarrollo en
[`spec/GUIDE.md`](spec/GUIDE.md).

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
| E9 | Integración continua con GitHub Actions (python, backend, frontend y e2e) con logs como artefactos, control de archivos generados y hook local `pre-push`. Implementada y pendiente de revisión. |
| Pendiente | Contenedores, despliegue y cierre según E10–E12 de la guía. |

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
scripts de Python. La calidad se comprueba con 153 pruebas automáticas en
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
  del backend, las de base de datos de Python y la prueba E2E; Docker Compose se
  usará en E10
- Git

## Preparación local

Desde PowerShell, en la carpeta del proyecto:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-dev.txt
pre-commit install   # instala los hooks de commit y de push (ver E9)
```

El archivo `.env.example` documenta las variables necesarias. Crea un `.env`
local con los valores de tu entorno. No guardes credenciales ni secretos en el
repositorio.

## CLI de desarrollo

```powershell
python tools/cli.py --help
python tools/cli.py validar --help
python tools/cli.py generar --help
```

`validar` comprueba esquema, tipos, nombres, claves, índices y referencias.
`generar` produce la migración inicial y los metadatos resueltos desde el modelo
JSON. `crear-bd` crea la base si falta, aplica migraciones, carga semillas y
verifica el esquema. `sembrar` carga únicamente los datos iniciales. Todos
aceptan `--dry-run` y escriben sus logs en `logs/tools/`.
`V001` es inmutable después de aplicarse para conservar su checksum; los cambios
de esquema posteriores se agregan en una nueva migración versionada.
`V003__bodega_pais_obligatorio.sql` exige el país de cada bodega, como define el
modelo; si alguna bodega existente no tiene país, la migración se detiene con un
mensaje y debe asignarse antes de volver a ejecutar `crear-bd`. La semilla de la
bodega `GENERAL` indica su país con `pais_codigo`. `generar`
actualiza el modelo resuelto y el CRUD sin reescribir esa migración.

`reiniciar-bd` es destructivo: solo permite hosts de loopback y exige confirmar
el nombre exacto de la base con `--confirm-db`. No lo ejecutes contra datos que
necesites conservar. `probar` ejecuta todas las suites de prueba y `todo` encadena
`validar` → `generar` → `crear-bd` → `probar` (ver la sección E8).

## Backend (E5.1–E5.5)

El backend Spring Boot está en `backend/`, usa Java 17, valida el esquema al
arrancar y no modifica la base (`ddl-auto=validate`). Spring Boot no carga
`.env` automáticamente; desde la raíz del repositorio, carga sus variables en
la sesión sin imprimir los valores:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
}
```

Luego ejecuta:

```powershell
Push-Location backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

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

```powershell
python tools/cli.py validar
python tools/cli.py validar --dry-run
```

Para generar el esquema y preparar PostgreSQL:

1. Copia `.env.example` a `.env` y configura `DB_HOST`, `DB_PORT`, `DB_NAME`,
   `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL` y `ADMIN_PASSWORD`; alternativamente, <!-- pragma: allowlist secret -->
   define `DATABASE_URL` y las dos variables del administrador.
2. Genera la migración y crea/siembra la base:

```powershell
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
| `/api/v1/proveedores` | Consultar, crear y actualizar proveedores; vincular un producto con un proveedor oficial. |
| `/api/v1/compras/ordenes` | Crear borrador, enviar al proveedor, consultar y cancelar antes de recibir. |
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

Las referencias `idioma` y `moneda` también se permiten a `PRODUCTOS_GESTIONAR`
(formulario de productos del encargado de inventario), y `pais` y `moneda` a
los permisos de reportes (filtros). Los medios de pago se configuran como datos:
la semilla define `ventas.medios_pago` para todo el negocio (efectivo, tarjeta y
transferencia bancaria) y para Colombia (además PSE, Nequi y Daviplata), según
la sección de pagos de los requisitos. Otro país o medio se agrega con un
parámetro, sin cambiar código.

## Frontend (E6.1–E6.4)

El cliente React + TypeScript está en `frontend/`. Con el backend iniciado en
`http://localhost:8080`, desde PowerShell ejecuta:

```powershell
Push-Location frontend
npm ci
npm run dev
```

El servidor de desarrollo usa `http://localhost:5173`; el backend permite ese
origen por defecto. Si la API está en otra dirección, configura
`VITE_API_URL` en `frontend/.env.local` (por ejemplo,
`VITE_API_URL=http://localhost:8080`). No incluyas credenciales en variables
`VITE_*`: se incorporan al bundle del navegador.

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

Los logs de la corrida están en `logs/e7/` (no versionados).

## Pruebas y logs (E8)

### Requisitos

- **Docker en ejecución** (Docker Desktop en Windows). Las pruebas no usan tu
  base local: cada corrida crea un PostgreSQL 16 desechable y lo elimina al
  terminar (requisito documentado en la sección 2.6 de la guía).
- El entorno `.venv` del proyecto con `requirements-dev.txt` instalado.
- Java 17, Node LTS y npm. El navegador de Playwright lo instala `probar`.

### Comando `probar`

```powershell
python tools/cli.py probar                       # todas las suites
python tools/cli.py probar --suites python,frontend
python tools/cli.py probar --dry-run             # muestra qué se ejecutaría
python tools/cli.py todo                         # validar → generar → crear-bd → probar
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
| `e2e` | Base desechable creada con `crear-bd`, backend (JAR) y front (`vite preview`) en puertos libres, y Playwright con Chromium. |

### Dónde quedan los logs y reportes

Nada de `logs/` se versiona (salvo las carpetas vacías); las evidencias de la
corrida final se copiarán a `docs/evidencias/` en E12.

| Origen | Ubicación |
|---|---|
| `python tools/cli.py probar` | `logs/tests/<AAAAMMDD-HHMMSS>/resumen.md` (resumen), `<suite>.log` (consola) y `<suite>/` (JUnit XML) |
| Capturas y trazas de la E2E cuando falla | `logs/tests/<AAAAMMDD-HHMMSS>/e2e/artefactos/` (abrir la traza con `npx playwright show-trace <trace.zip>` desde `e2e/`) |
| Comandos del CLI (`validar`, `generar`, `crear-bd`, `sembrar`, `reiniciar-bd`) | `logs/tools/<comando>-<marca de tiempo>.log` |
| `mvnw verify` ejecutado a mano | `backend/target/surefire-reports/` |
| Playwright ejecutado a mano | `e2e/reports/` |
| Corridas manuales de revisión de etapas | `logs/e6.4/`, `logs/e7/` |

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
| python | `tools/tests/test_test_runner.py` | Conteo de JUnit, `resumen.md` y opciones de `probar`. |
| backend | `src/test/java/.../service/*Test.java` | Unitarias: autenticación, JWT, sesiones, parámetros, impuestos, conversión, catálogo, clientes, inventario, ventas, referencias y reportes. |
| backend | `src/test/java/.../service/*PostgresIntegrationTest.java` | Integración con API y base: permisos, alcance por país, catálogo, clientes, compras, ventas, ajustes, parámetros y país/moneda agregados solo con datos. |
| frontend | `src/**/*.test.ts(x)` | Inicio de sesión, cliente HTTP, navegación por permisos, CRUD genérico, catálogo, inventario, ventas, reportes, configuración, formatos, decimales y traducciones. |
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
sesión → crear producto con precio en COP → registrar la entrada de 5 unidades →
vender 2 unidades (total 357.000 COP con IVA del 19 %) → confirmar el pago y ver
el recibo → comprobar existencia 3 y el movimiento "Salida por venta". El tipo
de producto, las categorías, el proveedor y la orden de compra se preparan por
API porque aún no tienen pantalla propia.

### Resultado de la última corrida completa (`logs/tests/20261021-040346/`)

| Suite | Pruebas |
|---|---:|
| python | 48 |
| backend | 73 |
| frontend | 31 |
| e2e | 1 |
| **Total** | **153, todas en verde** |

## Integración continua (E9)

`.github/workflows/ci.yml` se ejecuta en cada `push` y `pull_request` con cuatro
trabajos en paralelo sobre Ubuntu. Cada uno usa el mismo comando que en local,
`python tools/cli.py probar --suites <suite>`, y sube sus logs como artefacto
aunque falle.

| Trabajo | Qué comprueba | Artefacto |
|---|---|---|
| `python` | `validar`; que `generar` no produzca diferencias con lo versionado (migraciones, modelo resuelto, SQL de CRUD y metadatos del front); ruff, formato y pytest con PostgreSQL desechable. | `logs-python` |
| `backend` | `mvnw clean verify`: pruebas unitarias e integración con Testcontainers (los runners de GitHub traen Docker). | `logs-backend` |
| `frontend` | `npm ci`, lint, typecheck, Vitest y build. | `logs-frontend` |
| `e2e` | Instala Chromium con sus dependencias y ejecuta el flujo producto → entrada → venta con la base desechable, el backend y el front. | `logs-e2e` (incluye capturas y traza si falla) |

Cada artefacto contiene `logs/tests/<fecha-hora>/` (`resumen.md`, `<suite>.log`
y JUnit XML) y, cuando aplica, `logs/tools/`. Se descargan desde la pestaña
**Actions** del repositorio → ejecución → sección **Artifacts**. Si se envía
otro `push` a la misma rama, la ejecución anterior se cancela.

La prueba E2E levanta el backend (JAR) y el front (`vite preview`) desde
`probar`; cuando exista Docker Compose (E10) podrá ejecutarse sobre esos
contenedores.

### Hook local antes de cada push

`pre-commit install` instala dos tipos de hook (`default_install_hook_types`):

- **pre-commit:** ruff, formato y detección de secretos.
- **pre-push:** `validar`, `pytest` y las pruebas de Vitest. La E2E y el
  backend completo quedan para el CI por su duración.

Para ejecutarlos a mano: `pre-commit run --all-files` y
`pre-commit run --hook-stage pre-push --all-files`.

### Exigir el CI en verde antes de fusionar

En GitHub: **Settings → Branches → Add branch ruleset** (o *Add rule* en la
protección clásica) para la rama `main`:

1. Activar **Require a pull request before merging**.
2. Activar **Require status checks to pass** y agregar los cuatro trabajos:
   `Python (ruff, pytest, validar y generado)`, `Backend (mvn verify con
   Testcontainers)`, `Frontend (lint, typecheck, test y build)` y `Extremo a
   extremo (Playwright)`. Los nombres aparecen después de la primera ejecución.
3. Opcional: **Require branches to be up to date before merging**.

En repositorios privados, las reglas de protección requieren un plan de GitHub
que las incluya (por ejemplo, Pro o Team); en repositorios públicos están
disponibles en el plan gratuito.

## Verificaciones

```powershell
python tools/cli.py --help
python tools/cli.py validar
python tools/cli.py generar --dry-run
python tools/cli.py probar          # todas las suites, con logs y resumen
pre-commit run --all-files
```

Cada suite también se puede ejecutar por separado:

```powershell
python -m pytest
Push-Location backend; .\mvnw.cmd clean verify; Pop-Location
Push-Location frontend; npm run lint; npm run typecheck; npm test; npm run build; Pop-Location
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
├── REQUIREMENTS.md, GUIDE.md, INSTRUCTIONS.md   # Copias de referencia en la raíz
├── .env.example                  # Variables de entorno documentadas (sin secretos)
├── .gitignore
├── .pre-commit-config.yaml       # Hooks: commit (ruff, secretos) y push (validar y pruebas)
├── .secrets.baseline             # Falsos positivos aceptados por detect-secrets
├── pyproject.toml                # Configuración de ruff y pytest
├── requirements-dev.txt          # Dependencias de Python con versión fija
├── .github/workflows/ci.yml      # CI con GitHub Actions (E9)
├── spec/
│   ├── REQUIREMENTS.md           # Especificación de requisitos v0.7
│   ├── GUIDE.md                  # Guía de desarrollo por etapas
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
│   └── seeds/inicial.json        # Países, monedas, idiomas, impuestos, roles,
│                                 # permisos, bodega y parámetros
├── tools/
│   ├── cli.py                    # validar, generar, crear-bd, reiniciar-bd, sembrar,
│   │                             # probar y todo
│   ├── env_config.py             # Carga de .env sin reemplazar el entorno
│   ├── model_validator.py        # Validación del modelo JSON
│   ├── sql_generator.py          # SQL y metadatos del back y del front
│   ├── seed_loader.py            # Carga idempotente de semillas
│   ├── database.py               # Conexión, migraciones con checksum y verificación
│   ├── test_runner.py            # Comando probar: suites, logs y resumen.md
│   ├── postgres_desechable.py    # PostgreSQL temporal en Docker para pruebas
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
│   ├── index.html, public/
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
│       │   ├── PageHeader, CrudEntityLinks
│       │   ├── catalog/          # Productos y formulario dinámico (E6.3)
│       │   ├── inventory/        # Existencias, entradas, salidas, ajustes y
│       │   │                     # movimientos (E6.3)
│       │   ├── sales/            # Ventas, detalle y recibo (E6.4)
│       │   ├── reports/          # Existencias, movimientos y CSV (E6.4)
│       │   └── settings/         # Parámetros y configuración (E6.4)
│       ├── utils/                # Formatos Intl, decimales y referencias
│       └── test/                 # Configuración de Vitest y simulador de API
│                                 # (las pruebas *.test.ts(x) junto a cada módulo)
├── e2e/
│   ├── package.json, package-lock.json   # Playwright con versión fija
│   ├── playwright.config.ts      # URLs y credenciales por variables de entorno
│   └── tests/flujo-venta.spec.ts # Producto → entrada → venta → saldo y movimiento
├── docker/                       # Dockerfiles (E10)
├── logs/                         # No versionado: tools/ (CLI), tests/<fecha-hora>/
│                                 # (probar) y corridas manuales de revisión
└── docs/evidencias/              # Evidencias de la corrida final (E12)
```

La capa REST valida y autoriza solicitudes; los servicios aplican reglas y
transacciones; los repositorios acceden a PostgreSQL con JDBC; `domain` y `dto`
mantienen separados los datos internos y los contratos de API. El SQL asociado
a CRUD se genera desde el modelo JSON y se conserva en `generated/`.
