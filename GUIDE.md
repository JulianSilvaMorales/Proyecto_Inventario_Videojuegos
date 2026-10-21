# Guía de desarrollo — PixelVault: sistema de inventario para tienda de consolas y videojuegos

**Nombre del producto:** PixelVault — nombre comercial de la plataforma; se muestra en la interfaz y la documentación.

**Versión:** 1.0 · **Base:** Especificación de requisitos v0.7 (sección 7.3, corte vertical)

---

## 0. Cómo usar esta guía

1. El trabajo se hace **por etapas** (E0 a E12). Cada etapa tiene objetivo, entradas, tareas, entregables y criterios de aceptación.
2. Al terminar una etapa, el asistente entrega un **informe de etapa** (formato en la sección 0.2) y **espera la aprobación** de la persona que revisa antes de empezar la siguiente.
3. La persona que revisa puede pedir correcciones o editar archivos a mano. Todo debe estar hecho para que esa edición manual sea posible (ver sección 2.4).
4. **No se inventan requisitos.** Si algo no está en la especificación v0.7 o en esta guía, se pregunta o se deja anotado como supuesto en el informe.
5. **Todo valor de negocio es un parámetro**, nunca una constante en el código (principio de la especificación: parametrizable e internacional).

### 0.1 Convenciones generales

- **Nombres del dominio en español** (tablas, columnas, clases del dominio, endpoints): `producto`, `movimiento_inventario`, `Venta`. Términos técnicos en inglés (`Controller`, `Service`, `Repository`).
- **Interfaz en español e inglés** desde el primer día (español por defecto).
- **Fechas en UTC** en base de datos y API (ISO-8601); la interfaz las muestra en la zona horaria del usuario.
- **Dinero:** `NUMERIC(19,4)` en base de datos, `BigDecimal` en Java, **texto decimal** en el JSON de la API (por ejemplo `"1250.0000"`). Nunca `float` ni `double`.
- **Monedas y países** con códigos estándar: ISO 4217 (moneda), ISO 3166-1 alfa-2 (país), BCP 47 (idioma: `es`, `en`).
- **Sin secretos en el repositorio.** Todo por variables de entorno; se incluye `.env.example`.
- Versiones fijas (lockfiles) en Python, Maven y npm.

### 0.2 Formato del informe de etapa

Al cerrar cada etapa, el asistente responde con:

1. **Qué se hizo** (3-6 líneas).
2. **Archivos creados o modificados** (lista de rutas).
3. **Cómo ejecutarlo y probarlo** (comandos exactos).
4. **Resultado de las pruebas** (resumen y ruta de los logs).
5. **Supuestos y pendientes.**
6. **Pregunta de aprobación:** "¿Apruebas la etapa para continuar con la siguiente?"

---

## 1. Alcance de la primera entrega

El proyecto se entrega **funcionando** (ejecutable en local con Docker Compose y desplegado para demostración en una plataforma gratuita).

| Módulo | Requisitos | Alcance |
|---|---|---|
| Usuarios y roles | RF-006 | Inicio de sesión, permisos por rol según la matriz; bloqueo tras intentos fallidos; sesión con vencimiento. **Sin autenticación multifactor.** |
| Catálogo | RF-001, RF-023 | Productos con atributos por tipo; nombres y descripciones en es/en. **Sin atributos logísticos.** |
| Proveedores y clientes | RF-009, RF-015 | Registro y consulta básicos; el cliente tiene país y consentimiento de datos. |
| Inventario | RF-002 a RF-005, RF-007 | Consulta, entradas, salidas (venta, pérdida, daño, robo, defecto), ajustes con aprobación del administrador, historial y auditoría. **Sin traslados ni devoluciones.** |
| Parametrización internacional | RF-020 a RF-022 | Países (Colombia y Estados Unidos), monedas (COP y USD), tipos de cambio manuales, impuestos por país, parámetros con ámbito y vigencia. |
| Venta básica | RF-010 | Venta con moneda, tipo de cambio e impuesto del país; recibo simulado. **Sin crédito, promociones ni facturación electrónica real.** |
| Reportes | RF-008 | Existencias y movimientos con filtros, exportables a CSV. |

**Fase 2 (solo documentada, no se construye):** RF-011 a RF-014, RF-016 a RF-019, RF-024 a RF-026, autenticación multifactor, facturación electrónica real, canal en línea para clientes.

---

## 2. Decisiones técnicas

### 2.1 Tecnología

| Capa | Tecnología |
|---|---|
| Front | React + Material UI + TypeScript (Vite). Textos con `react-i18next`; formatos con `Intl`. |
| Back | Java 17 (LTS) + Spring Boot 3.x + Maven. Spring Data JPA, Spring Security (JWT con la librería estándar de Spring), Bean Validation, Actuator, springdoc-openapi. |
| Base de datos | PostgreSQL |
| Generadores y base de datos | Python 3.12+: `jinja2`, `jsonschema`, `psycopg` (v3), `pytest`, `ruff` |
| Pruebas | Back: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL). Front: Vitest + React Testing Library. Extremo a extremo: Playwright (un flujo). Python: pytest. |
| CI | GitHub Actions |
| Contenedores | Docker + Docker Compose |
| Despliegue demo | Render: back como contenedor Docker, front como sitio estático, PostgreSQL gestionado. Se define con un `render.yaml`. |

### 2.2 Estructura del repositorio

```text
inventario/
├── spec/                      # requisitos v0.7 y esta guía
├── db/
│   ├── model/                 # JSON del modelo (fuente única), por módulo
│   │   ├── model.schema.json  # JSON Schema que valida el modelo
│   │   └── modulos/*.json
│   ├── seeds/*.json           # datos iniciales (países, monedas, roles, permisos, parámetros…)
│   └── migrations/            # SQL generado (V001__…sql), versionado
├── tools/                     # scripts de Python (CLI único: tools/cli.py)
│   ├── templates/             # plantillas Jinja2
│   └── tests/                 # pruebas de los scripts
├── backend/                   # Spring Boot
├── frontend/                  # React + MUI
├── e2e/                       # Playwright
├── docker/                    # Dockerfiles
├── docker-compose.yml
├── render.yaml
├── .github/workflows/ci.yml
├── logs/                      # logs locales de pruebas y scripts (no se versiona, salvo la evidencia final)
├── docs/evidencias/           # logs de la corrida final de pruebas (se versiona)
├── .env.example
└── README.md
```

### 2.3 Base de datos y modelo (decisiones)

- **El JSON del modelo es la fuente única.** De él salen el SQL, los metadatos de las entidades para el back y los metadatos de pantallas para el front.
- **Sin Flyway.** El propio script de Python aplica las migraciones y registra su historial y checksum en la tabla `migracion_historial`. El back solo **valida** que el esquema coincida (`ddl-auto=validate`); nunca lo modifica.
- **Atributos de producto por tipo:** columna `atributos` de tipo `JSONB`, validada contra las definiciones configurables `atributo_definicion` (por tipo de producto: nombre, tipo de dato, obligatorio, opciones).
- **Inmutabilidad:** las tablas marcadas `inmutable` en el modelo (`movimiento_inventario`, `auditoria`) reciben un trigger que rechaza `UPDATE` y `DELETE`.
- **Integridad del inventario:** `CHECK (cantidad >= 0)` en el saldo, y movimiento + saldo se actualizan en la misma transacción con bloqueo (`SELECT … FOR UPDATE`).
- **Parámetros:** tabla `parametro` con clave, valor, tipo, mínimo, máximo, valor por defecto, ámbito (`NEGOCIO` o `PAIS`), país (si aplica), vigencia desde y hasta. Prevalece el ámbito más específico con vigencia activa. Caché de máximo 5 minutos.
- **Internacional:** tablas `pais`, `moneda`, `idioma`, `tipo_cambio`, `impuesto` (por país, categoría y vigencia), `precio_producto` (por moneda y vigencia), `producto_traduccion`.

### 2.4 Regla de corrección manual

- Los archivos generados llevan la cabecera `GENERADO — no editar; modifique el JSON del modelo o la plantilla`. Están en carpetas `generated/`.
- El código con lógica de negocio es **manual** y vive fuera de `generated/`.
- Si la persona que revisa edita un archivo generado, lo mueve fuera de `generated/` y quita la cabecera; el generador **no sobrescribe** archivos sin la cabecera.
- Todos los scripts: `--help`, `--dry-run`, mensajes claros, código de salida distinto de cero ante error, **idempotentes**, y log en `logs/tools/`.

### 2.5 CLI de Python

`python tools/cli.py <comando>`:

| Comando | Qué hace |
|---|---|
| `validar` | Valida el JSON del modelo contra su esquema y revisa referencias. |
| `generar` | Genera SQL de migración, metadatos para back y front. |
| `crear-bd` | Crea la base si no existe, aplica migraciones, carga semillas y verifica. |
| `reiniciar-bd` | Borra y recrea la base (solo entornos locales). |
| `sembrar` | Carga solo las semillas. |
| `probar` | Ejecuta todas las pruebas y guarda los logs. |
| `todo` | `validar` → `generar` → `crear-bd` → `probar`. |

---

### 2.6 Requisitos del entorno de desarrollo y pruebas

- **Docker en ejecución** (Docker Desktop en Windows y macOS, Docker Engine en Linux): es obligatorio para las pruebas de integración del back (Testcontainers), las pruebas de base de datos de Python, la prueba de extremo a extremo y, desde E10, Docker Compose. Sin Docker, las integraciones del back y de Python se omiten y la suite E2E de `probar` falla indicando el motivo.
- Las pruebas **no usan la base local** de `.env`: cada ejecución crea un PostgreSQL 16 desechable (imagen `postgres:16-alpine`) y lo construye con `python tools/cli.py crear-bd`, igual que cualquier otro entorno. El contenedor se elimina al terminar.
- Docker Engine 29 o superior exige una versión mínima de su API; el back la fija en `backend/src/test/resources/docker-java.properties` (`api.version`).
- Para la prueba E2E se necesitan además Java 17, Node LTS y el navegador de Playwright, que `probar` instala (`npx playwright install chromium`).
- Python se toma de la variable `PYTHON` o del entorno virtual `.venv` del proyecto.

## 3. Etapas

### E0 — Preparación del repositorio
**Objetivo:** dejar el repositorio listo y reproducible.
**Tareas:** crear la estructura de 2.2; `README` inicial; `.gitignore`; `.env.example`; `pre-commit` con `ruff`, formato y revisión de secretos; copiar los requisitos a `spec/`.
**Entregables:** repositorio con estructura, README con pasos de instalación y requisitos previos (Docker, Java 17, Node LTS, Python 3.12+).
**Aceptación:** un clon limpio muestra la estructura y `python tools/cli.py --help` lista los comandos (aunque aún no hagan nada).
**Revisión:** clonar el repo en otra carpeta y seguir el README.

### E1 — Análisis y modelo conceptual
**Objetivo:** pasar de requisitos a un modelo de dominio claro.
**Tareas:** listar las entidades del corte vertical (Anexo B); definir relaciones, reglas de integridad, estados (venta, ajuste) y casos de uso por rol; diagrama entidad-relación en Mermaid.
**Entregables:** `spec/modelo-conceptual.md` con diagrama, reglas, estados y matriz de permisos por rol (Anexo D).
**Aceptación:** cada requisito del corte vertical (sección 1) tiene al menos una entidad y un caso de uso asociados (tabla de trazabilidad).
**Revisión:** leer el diagrama y confirmar que representa la tienda; marcar lo que sobre o falte.

### E2 — Modelo de datos en JSON y su validación
**Objetivo:** describir toda la base de datos en JSON, validable.
**Tareas:** definir `model.schema.json`; escribir `db/model/modulos/*.json` (formato del Anexo A); `tools/cli.py validar` con revisión de tipos, claves foráneas, nombres duplicados y banderas (`inmutable`, `generar_crud`).
**Entregables:** esquema, módulos, comando `validar`, pruebas pytest.
**Aceptación:** `validar` pasa con el modelo correcto y falla con mensaje claro ante errores inyectados (referencia inexistente, tipo inválido, nombre repetido).
**Revisión:** introducir un error a mano en un JSON y comprobar que `validar` lo detecta.

### E3 — Generación del SQL y creación de la base de datos
**Objetivo:** crear la base completa solo con scripts de Python.
**Tareas:** `generar` produce `db/migrations/V001__…sql` (tablas, claves, índices, `CHECK`, triggers de inmutabilidad) y un `db/model/schema-resuelto.json`; `crear-bd` ejecuta: (1) conectar, (2) crear la base si no existe, (3) aplicar migraciones pendientes registrando checksum en `migracion_historial`, (4) cargar semillas (Anexo C), (5) verificar con consultas (tablas, restricciones, conteos), (6) escribir el log. Configuración por variables de entorno (`DATABASE_URL` o `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).
**Entregables:** migraciones, semillas JSON, comandos `generar`, `crear-bd`, `reiniciar-bd`, `sembrar`, pruebas.
**Aceptación:** ejecutar `crear-bd` dos veces no cambia nada la segunda vez; `reiniciar-bd` recrea todo; una inserción con saldo negativo falla; un `UPDATE` o `DELETE` sobre `movimiento_inventario` falla.
**Revisión:** ejecutar `crear-bd` dos veces y revisar el SQL generado y el log.

### E4 — Arquitectura del back y esqueleto
**Objetivo:** proyecto Spring Boot ejecutable con la estructura final.
**Tareas:** paquetes `controller`, `service`, `repository`, `domain`, `dto`, `config`, `security`, `generated`; API bajo `/api/v1`; errores en formato Problem Details con mensajes internacionalizados; paginación (`page`, `size`); perfiles `local` y `produccion`; `actuator/health`; `springdoc` en `/swagger-ui`; `ddl-auto=validate`; pool de conexiones pequeño (máx. 5).
**Entregables:** back que arranca contra la base de la E3 y expone salud y documentación.
**Aceptación:** `mvn verify` pasa; `/actuator/health` responde `UP`; el arranque falla con mensaje claro si el esquema no coincide.
**Revisión:** abrir `/swagger-ui` y `/actuator/health`.

### E5 — Back: módulos de negocio (cinco subetapas)
Cada subetapa se entrega con sus pruebas (unitarias e integración).

- **E5.1 Seguridad y parámetros:** inicio de sesión con JWT; contraseñas con BCrypt; roles y permisos según la matriz; bloqueo tras N intentos (parámetro `seguridad.intentos_fallidos_max`, por defecto 5) y desbloqueo por el administrador; vencimiento de sesión (`sesion.inactividad_minutos`, 60); servicio `ParametroService` con resolución por ámbito y vigencia y caché (5 minutos). Las sesiones son en memoria; reiniciar el backend las revoca y las respuestas autenticadas entregan el JWT renovado en `X-Auth-Token`.
- **E5.2 País, moneda, idioma, tipo de cambio e impuestos:** CRUD (generado) y servicios: `ConversionMonedaService` (tipo de cambio vigente, redondeo según decimales de la moneda), `ImpuestoService` (tarifa por país, categoría y vigencia; precio con impuesto incluido o no).
- **E5.3 Catálogo:** tipos de producto, definiciones de atributos, categorías y subcategorías, productos con validación de `atributos` contra su definición, traducciones es/en y precios por moneda. Solo se desactiva, nunca se elimina.
- **E5.4 Inventario:** bodegas, saldos, **entradas** (con proveedor, documento y costo), **salidas** (pérdida, daño, robo, defecto), **ajustes** (solicitud → aprobación del administrador → aplicación), movimientos inmutables, historial con filtros y auditoría del 100% de las operaciones.
- **E5.5 Ventas y reportes:** venta (estados: pendiente de pago → confirmada; anulada solo si está pendiente); al confirmar descuenta inventario, registra movimiento, calcula impuestos del país, guarda moneda y tipo de cambio de ese momento y genera recibo simulado; reportes de existencias y movimientos con filtros (periodo, país, moneda, producto, usuario) y exportación CSV.

**Aceptación general E5:** cada regla de negocio del Anexo B tiene al menos una prueba; ningún flujo deja el inventario parcialmente actualizado si algo falla.
**Revisión por subetapa:** probar con Swagger usando un usuario por rol y comprobar permisos.

### E6 — Front (cuatro subetapas)
- **E6.1 Base:** proyecto Vite + React + TypeScript + Material UI; tema; diseño de pantalla (menú por permisos); inicio de sesión; `react-i18next` (es/en) con selector de idioma; formatos de moneda, número y fecha con `Intl` según idioma del usuario y moneda de la transacción; manejo de errores de la API; mensaje "el servidor se está despertando" cuando la primera petición tarda.
- **E6.2 CRUD genérico dirigido por metadatos:** `generar` produce `frontend/src/generated/entidades.json` (campos, tipos, validaciones, etiquetas es/en) y tipos TypeScript; un componente `CrudPage` genérico (lista con paginación, búsqueda, formulario, validación) se alimenta de ese JSON para las entidades simples (países, monedas, idiomas, tipos de cambio, impuestos, categorías, bodegas, proveedores, clientes).
- **E6.3 Catálogo e inventario:** pantallas de productos (formulario dinámico según atributos del tipo y traducciones), existencias, entradas, salidas, ajustes con aprobación, historial.
- **E6.4 Ventas, reportes y parámetros:** venta con selección de cliente (país), cálculo y desglose de impuestos y total en la moneda de la venta, recibo, reporte con exportación CSV, pantalla de parámetros (valor, ámbito, vigencia).

**Aceptación general E6:** `npm run lint`, `npm run typecheck`, `npm test` y `npm run build` pasan; cambiar el idioma o la moneda cambia textos y formatos sin recargar.
**Revisión:** recorrer el flujo completo con un usuario por rol.

### E7 — Parametrización e internacionalización (verificación transversal)
**Objetivo:** comprobar que nada de negocio está fijado en el código.
**Tareas:** buscar constantes de negocio en back y front (porcentajes, plazos, monedas, textos); moverlas a parámetros o traducciones; prueba que agrega un país y una moneda **solo con datos** (sin tocar código) y verifica la venta con ellos.
**Aceptación:** prueba automatizada "agregar un país/moneda por configuración" en verde; no quedan textos de interfaz fuera de los archivos de traducción.

### E8 — Pruebas y logs
**Objetivo:** cobertura útil y evidencia guardada.
**Tareas:**
- **Python:** pytest de validación, generación, idempotencia de `crear-bd`.
- **Back:** unitarias (parámetros, impuestos, conversión, reglas de inventario) e integración (repositorios y API con Testcontainers).
- **Front:** Vitest + React Testing Library (formularios, formatos por idioma y moneda, permisos en el menú).
- **Extremo a extremo (Playwright):** iniciar sesión → crear producto → registrar entrada → vender → verificar saldo y movimiento.
- `python tools/cli.py probar` ejecuta todo y guarda en `logs/tests/<AAAAMMDD-HHMMSS>/` los reportes (JUnit XML, salida de consola) por suite y un `resumen.md` con totales.
**Requisito:** Docker en ejecución (ver 2.6). `probar` crea las bases desechables, levanta el back y el front en puertos libres para la E2E y los detiene al terminar.
**Aceptación:** todas las pruebas pasan; el `resumen.md` lista suite por suite; los logs quedan guardados.
**Revisión:** ejecutar `probar` y abrir `resumen.md`.

### E9 — Integración continua (GitHub Actions)
**Objetivo:** que no se suba código con errores.
**Tareas:** `.github/workflows/ci.yml` en `push` y `pull_request` con trabajos: **python** (ruff, pytest, `validar`, y verificación de que `generar` no produce diferencias con lo versionado), **back** (`mvn verify` con Testcontainers), **front** (`npm ci`, lint, typecheck, test, build), **e2e** (Docker Compose + Playwright). Cada trabajo **sube los logs y reportes como artefactos**. Hook local `pre-push` que ejecuta `validar` y las pruebas rápidas. Documentar en el README cómo activar la protección de la rama principal para exigir que CI esté en verde antes de fusionar (si el plan de GitHub lo permite).
**Aceptación:** un cambio que rompe una prueba hace fallar el CI; uno correcto lo pasa; los artefactos están descargables.
**Revisión:** abrir una rama con un error intencional y comprobar que CI falla.

### E10 — Contenedores
**Objetivo:** ejecutar todo con un solo comando.
**Tareas:** `docker/backend.Dockerfile` (multi-etapa: compilación con Maven → ejecución con JRE 21; opciones de JVM para poca memoria, por ejemplo `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1`); `docker/frontend.Dockerfile` (compilación → servidor estático); `docker-compose.yml` con `db`, `db-init` (imagen de Python que ejecuta `crear-bd`), `backend` y `frontend`, con `healthcheck` y orden de arranque.
**Aceptación:** `docker compose up --build` en una máquina limpia deja el sistema usable; reiniciar no duplica datos.
**Revisión:** ejecutar desde cero y entrar con el usuario administrador.

### E11 — Despliegue de demostración (Render)
**Objetivo:** una URL pública para demostrar.
**Tareas:** `render.yaml` con el servicio web del back (Docker), el sitio estático del front y la base PostgreSQL; variables de entorno (`SPRING_DATASOURCE_URL` en formato JDBC, usuario, contraseña, secreto JWT, `CORS_ORIGINS`, URL del back para el front); ejecutar `crear-bd` **desde el equipo local** contra la URL externa de la base de Render; verificación con `/actuator/health`; guía en el README.
**Notas obligatorias (verificar condiciones vigentes en la documentación de Render):** los servicios gratuitos se suspenden tras un periodo sin tráfico y tardan en despertar; la base de datos gratuita caduca, por lo que `crear-bd` y un script de respaldo y restauración (`pg_dump`/`pg_restore` envueltos en el CLI) deben poder recrearla rápido; abrir la aplicación antes de cada demostración.
**Aceptación:** front y back responden en las URLs públicas y el flujo completo funciona contra la base de Render.
**Revisión:** repetir el flujo de la E8 en la URL pública.

### E12 — Datos de demostración, documentación y entrega
**Tareas:** semillas de demostración (usuarios por rol, productos de cada tipo, entradas, una venta en COP y otra en USD); README final (instalación, comandos, usuarios de prueba, URLs, decisiones técnicas, cómo corregir a mano); copiar a `docs/evidencias/` los logs de la corrida final; lista de lo que queda en fase 2.
**Aceptación:** checklist de la sección 5 completo.

---

## 4. Plan de tiempo (lunes 19 a jueves 22 de octubre de 2026)

| Día | Etapas | Meta del día |
|---|---|---|
| Lunes | E0 a E3 | Repositorio, modelo en JSON y base creada con scripts. |
| Martes | E4 y E5 | Back completo con pruebas. |
| Miércoles | E6 a E8 | Front, parametrización verificada y pruebas con logs. |
| Jueves (mañana) | E9 a E12 | CI, contenedores, Render, evidencias y entrega. |

**Si hay retraso, se recorta en este orden:** (1) exportación CSV, (2) pantalla de parámetros (queda por API), (3) prueba de extremo a extremo (queda manual). **No se recorta:** modelo en JSON, base por scripts, inventario, inicio de sesión con roles, venta con moneda e impuesto, pruebas con logs y CI.

---

## 5. Checklist final de entrega

- [ ] `python tools/cli.py todo` termina sin errores.
- [ ] `docker compose up --build` deja el sistema funcionando.
- [ ] Inicio de sesión con un usuario de cada rol y permisos correctos.
- [ ] Flujo: producto → entrada → venta (COP y USD) → saldo y movimiento correctos.
- [ ] Cambio de idioma (es/en) y de moneda sin recargar.
- [ ] Un país y una moneda nuevos agregados solo con datos.
- [ ] CI en verde y artefactos de logs descargables.
- [ ] `docs/evidencias/` con los logs de la corrida final.
- [ ] URLs públicas de demostración funcionando.
- [ ] README completo; sin secretos en el repositorio.

---

## 6. Riesgos y plan B

| Riesgo | Plan B |
|---|---|
| El back arranca lento o se queda sin memoria en el plan gratuito | Ajustar opciones de JVM, reducir el pool, desactivar inicialización de componentes no usados. |
| La base gratuita caduca | Recrear con `crear-bd` y restaurar con el script de respaldo. |
| Primera petición lenta tras la suspensión | Abrir la aplicación antes de demostrar; el front muestra el aviso de espera. |
| Testcontainers no funciona en el equipo local | Ejecutar esas pruebas solo en CI y usar la base de Docker Compose en local. |
| Docker no está iniciado o su API no es compatible | Iniciar Docker Desktop antes de `probar`; mantener `api.version` en `docker-java.properties` acorde con la versión de Docker Engine. |
| Falta de tiempo | Recortar según la sección 4. |

---

## Anexo A — Formato de `db/model/modulos/*.json`

```json
{
  "modulo": "inventario",
  "entidades": [
    {
      "nombre": "movimiento_inventario",
      "descripcion": "Movimiento de entrada o salida de inventario",
      "inmutable": true,
      "generar_crud": false,
      "campos": [
        {"nombre": "id", "tipo": "uuid", "pk": true},
        {"nombre": "producto_id", "tipo": "uuid", "obligatorio": true,
         "ref": {"entidad": "producto", "campo": "id"}},
        {"nombre": "bodega_id", "tipo": "uuid", "obligatorio": true,
         "ref": {"entidad": "bodega", "campo": "id"}},
        {"nombre": "tipo", "tipo": "texto", "obligatorio": true,
         "valores": ["ENTRADA", "SALIDA_VENTA", "SALIDA_PERDIDA", "SALIDA_DANO",
                     "SALIDA_ROBO", "SALIDA_DEFECTO", "AJUSTE"]},
        {"nombre": "cantidad", "tipo": "entero", "obligatorio": true, "min": 1},
        {"nombre": "usuario_id", "tipo": "uuid", "obligatorio": true,
         "ref": {"entidad": "usuario", "campo": "id"}},
        {"nombre": "fecha_utc", "tipo": "fecha_hora_utc", "obligatorio": true},
        {"nombre": "referencia", "tipo": "texto"}
      ],
      "indices": [["producto_id", "fecha_utc"]],
      "etiquetas": {"es": "Movimiento de inventario", "en": "Inventory movement"}
    }
  ]
}
```

Tipos admitidos: `uuid`, `texto`, `entero`, `decimal` (con `precision` y `escala`), `booleano`, `fecha`, `fecha_hora_utc`, `json`.

## Anexo B — Entidades del corte vertical y reglas

| Grupo | Entidades |
|---|---|
| Parametrización | `parametro`, `pais`, `moneda`, `idioma`, `tipo_cambio`, `impuesto` |
| Seguridad | `usuario`, `rol`, `usuario_rol`, `permiso`, `rol_permiso` |
| Catálogo | `categoria` (con padre para subcategoría), `tipo_producto`, `atributo_definicion`, `producto`, `producto_traduccion`, `precio_producto` |
| Terceros | `proveedor`, `cliente` |
| Inventario | `bodega`, `saldo_inventario`, `entrada_inventario` (+ líneas), `movimiento_inventario`, `ajuste_inventario` |
| Ventas | `venta`, `venta_linea` |
| Transversal | `auditoria`, `migracion_historial` |

**Reglas que deben tener prueba:** inventario nunca negativo ni decimal; producto solo se desactiva; ajuste sin aprobación del administrador no cambia el saldo; venta anulable solo si está pendiente de pago; venta confirmada descuenta inventario y conserva moneda, tipo de cambio e impuestos de ese momento; un país sin tarifa de impuesto no admite ventas; cliente infrecuente solo con datos mínimos; movimientos y auditoría inmutables; cada operación crítica deja registro de auditoría.

## Anexo C — Semillas y parámetros iniciales

- **Países:** Colombia (`CO`) y Estados Unidos (`US`). **Monedas:** COP y USD (con sus decimales). **Idiomas:** `es` y `en`.
- **Impuestos:** Colombia, IVA 19%. Estados Unidos: tarifa de **ejemplo** editable. **Tipo de cambio inicial:** valor de **ejemplo** editable.
- **Roles y permisos:** los de la matriz de RF-006 (Anexo D).
- **Parámetros:** `sesion.inactividad_minutos=60`, `seguridad.intentos_fallidos_max=5`, `cliente.umbral_frecuente=2`, `cliente.retencion_infrecuente_meses=12`, `auditoria.retencion_anios=10`, `movimientos.retencion_anios=8`, `parametros.cache_minutos=5`.
- **Usuario administrador inicial:** correo y contraseña tomados de variables de entorno; nunca en el repositorio.

## Anexo D — Permisos por rol (resumen del corte vertical)

| Operación | Administrador | Encargado de inventario | Experto técnico | Vendedor/cajero | Gerente/propietario |
|---|---|---|---|---|---|
| Usuarios, roles, parametrización, tipos de cambio | ✔ | — | — | — | — |
| Crear, modificar y desactivar productos | ✔ | ✔ | — | — | — |
| Consultar catálogo e inventario | ✔ | ✔ | ✔ | ✔ | ✔ |
| Entradas y proveedores | ✔ | ✔ | — | — | Lectura (proveedores) |
| Registrar ventas y anularlas (pendientes) | ✔ | — | — | ✔ | — |
| Clientes | ✔ | — | — | ✔ | — |
| Solicitar conteo y ajuste | — | ✔ | ✔ (técnicos) | — | — |
| Aprobar ajustes | ✔ | — | — | — | — |
| Reportes | ✔ | Solo inventario | — | — | ✔ |
| Auditoría (solo lectura) | ✔ | Solo inventario | Solo servicio técnico | — | — |

## Anexo E — Variables de entorno

`DATABASE_URL` o (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`); `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; `JWT_SECRET`; `CORS_ORIGINS`; `ADMIN_EMAIL`, `ADMIN_PASSWORD`; `VITE_API_URL`; `SPRING_PROFILES_ACTIVE`. <!-- pragma: allowlist secret -->