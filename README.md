# Sistema de inventario para tienda de consolas y videojuegos

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
| E6.2 | CRUD genérico generado desde metadatos para parametrización, catálogo, bodegas, proveedores y clientes; API básica de clientes y referencias. Implementada y pendiente de revisión. |
| Pendiente | Pantallas operativas de catálogo/inventario/ventas, pruebas E2E y despliegue según E6.3–E12 de la guía. |

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
- Docker y Docker Compose para la ejecución contenerizada (etapas posteriores)
- Git

## Preparación local

Desde PowerShell, en la carpeta del proyecto:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-dev.txt
pre-commit install
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
de esquema posteriores se agregan en una nueva migración versionada. `generar`
actualiza el modelo resuelto y el CRUD sin reescribir esa migración.

`reiniciar-bd` es destructivo: solo permite hosts de loopback y exige confirmar
el nombre exacto de la base con `--confirm-db`. No lo ejecutes contra datos que
necesites conservar. `probar` y `todo` se habilitarán en etapas posteriores.

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
al abrir el archivo en una hoja de cálculo.

## Frontend (E6.1–E6.2)

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

Para comprobar los archivos de producción y ejecutar los controles:

```powershell
npm run lint
npm run typecheck
npm test
npm run build
Pop-Location
```

## Verificaciones

```powershell
python tools/cli.py --help
python tools/cli.py validar
python tools/cli.py generar --dry-run
python -m pytest
ruff check tools
ruff format --check tools
pre-commit run --all-files
Push-Location backend
.\mvnw.cmd clean verify
Pop-Location
```

Las pruebas de integración PostgreSQL están condicionadas a
`RUN_POSTGRES_INTEGRATION=true`; requieren cargar previamente las variables de
`.env` y conectarse a PostgreSQL 16.

Los logs locales se guardan en `logs/`; las evidencias de la corrida final se
publican en `docs/evidencias/`.

## Estructura

```text
.
├── README.md, REQUIREMENTS.md, GUIDE.md, INSTRUCTIONS.md
├── spec/                         # Requisitos, guía y modelo conceptual
├── db/
│   ├── model/modulos/            # Modelo JSON modular por dominio
│   ├── model/schema-resuelto.json
│   ├── migrations/               # SQL generado para PostgreSQL
│   └── seeds/inicial.json        # Catálogos, roles, permisos y parámetros
├── tools/
│   ├── cli.py                    # validar, generar, crear-bd, sembrar, reiniciar-bd
│   ├── model_validator.py
│   ├── sql_generator.py
│   ├── seed_loader.py
│   ├── database.py
│   ├── templates/
│   └── tests/
├── backend/
│   ├── src/main/java/com/inventario/
│   │   ├── config/               # Configuración Spring, API y seguridad
│   │   ├── controller/           # Endpoints REST
│   │   ├── domain/               # Modelos y resultados del dominio
│   │   ├── dto/                  # Solicitudes y respuestas de API
│   │   ├── generated/            # SQL generado; no editar manualmente
│   │   ├── repository/           # Persistencia JDBC
│   │   ├── security/             # Filtro JWT y autorización
│   │   └── service/              # Reglas de negocio y transacciones
│   ├── src/main/resources/       # Configuración y mensajes es/en
│   ├── src/test/java/            # Pruebas unitarias e integración PostgreSQL
│   └── pom.xml
├── frontend/                     # React + TypeScript (E6.1–E6.2)
├── e2e/                          # Pendiente de integración E2E
├── docker/                       # Contenedores, pendiente de completar
└── docs/evidencias/              # Evidencias finales versionables
```

La capa REST valida y autoriza solicitudes; los servicios aplican reglas y
transacciones; los repositorios acceden a PostgreSQL con JDBC; `domain` y `dto`
mantienen separados los datos internos y los contratos de API. El SQL asociado
a CRUD se genera desde el modelo JSON y se conserva en `generated/`.
