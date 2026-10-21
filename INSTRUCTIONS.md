# Instrucciones de trabajo para el asistente — PixelVault: sistema de inventario (resumen)

**Nombre del producto:** PixelVault — nombre comercial de la plataforma; se muestra en la interfaz y la documentación.

Este documento resume la "Guía de desarrollo" y se usa junto con la **Especificación de requisitos v0.7** (adjunta). Si hay contradicción, prevalece la especificación; si algo falta, **pregunta antes de inventar**.

## 1. Tu rol y forma de trabajo

Eres el asistente que construye el proyecto. Trabajas **por etapas** (E0 a E12). Al cerrar cada etapa entregas un informe con: (1) qué hiciste, (2) archivos creados o modificados, (3) comandos para ejecutarlo y probarlo, (4) resultado de pruebas y ruta de logs, (5) supuestos y pendientes, y (6) la pregunta "¿Apruebas la etapa para continuar?". **No avances sin aprobación.** La persona que revisa puede corregir archivos a mano, así que todo debe ser legible, parametrizable y reproducible por scripts.

## 2. Qué se construye

Sistema de inventario y ventas para una tienda de consolas y videojuegos, **parametrizable e internacional** (países, monedas, idiomas, impuestos configurables; nada de negocio fijado en el código). Se entrega **funcionando**, en local con Docker Compose y desplegado para demostración en Render (gratuito).

**Incluye (corte vertical):** usuarios y roles (RF-006, sin autenticación multifactor); catálogo con atributos por tipo y nombres es/en (RF-001, RF-023); proveedores y clientes básicos (RF-009, RF-015); inventario: consulta, entradas, salidas, ajustes con aprobación del administrador, historial y auditoría (RF-002 a RF-005, RF-007); parametrización internacional: países CO y US, monedas COP y USD, tipos de cambio manuales, impuestos por país, parámetros con ámbito y vigencia (RF-020 a RF-022); venta básica con moneda, tipo de cambio e impuesto del país y recibo simulado (RF-010); reportes de existencias y movimientos con CSV (RF-008).

**No incluye (fase 2):** traslados, devoluciones, envíos, aduanas y aranceles, importaciones, preventas, recompra, crédito, promociones, alertas de stock, garantías, facturación electrónica real, autenticación multifactor, canal en línea para clientes.

## 3. Tecnología y convenciones

- **Front:** React + Material UI + TypeScript (Vite), `react-i18next` (es/en), formatos con `Intl`.
- **Back:** Java 17, Spring Boot 3.x, Maven, Spring Data JPA, Spring Security con JWT, Bean Validation, Actuator, springdoc.
- **Base de datos:** PostgreSQL. Esquema con `ddl-auto=validate` (el back nunca lo modifica).
- **Python 3.12+** (`jinja2`, `jsonschema`, `psycopg`, `pytest`, `ruff`): generan y crean la base de datos.
- **Pruebas:** JUnit 5 + Testcontainers; Vitest + React Testing Library; Playwright (un flujo); pytest.
- **CI:** GitHub Actions. **Contenedores:** Docker y Docker Compose. **Despliegue:** Render con `render.yaml` (back Docker, front estático, PostgreSQL gestionado).
- **Convenciones:** dominio en español (`producto`, `Venta`); términos técnicos en inglés; fechas en UTC (ISO-8601); dinero con `NUMERIC(19,4)` / `BigDecimal` y **texto decimal** en la API, nunca `float`; códigos ISO 4217 (moneda), ISO 3166-1 (país), BCP 47 (idioma); versiones fijas en lockfiles; **sin secretos en el repositorio** (variables de entorno y `.env.example`).

## 4. Reglas de diseño que debes respetar

1. **El JSON del modelo (`db/model/`) es la fuente única.** De él salen el SQL de migración, los metadatos del back y los metadatos de pantallas del front.
2. **La base se crea solo con scripts de Python** (`python tools/cli.py crear-bd`): crea la base si no existe, aplica migraciones registrando checksum en `migracion_historial` (**sin Flyway**), carga semillas, verifica y deja log en `logs/tools/`. Debe ser **idempotente**.
3. **CLI único** `python tools/cli.py`: `validar`, `generar`, `crear-bd`, `reiniciar-bd`, `sembrar`, `probar`, `todo`. Todos con `--help` y `--dry-run`, código de salida distinto de cero ante error.
4. **Corrección manual posible:** archivos generados en carpetas `generated/` con la cabecera `GENERADO — no editar; modifique el JSON del modelo o la plantilla`; la lógica de negocio es manual y vive fuera de `generated/`; el generador no sobrescribe archivos que hayan perdido la cabecera.
5. **Parametrización:** tabla `parametro` (clave, valor, tipo, mínimo, máximo, valor por defecto, ámbito `NEGOCIO` o `PAIS`, vigencia desde y hasta). Prevalece el ámbito más específico con vigencia activa; caché máxima de 5 minutos. Parámetros iniciales: `sesion.inactividad_minutos=60`, `seguridad.intentos_fallidos_max=5`, `cliente.umbral_frecuente=2`, `cliente.retencion_infrecuente_meses=12`, `auditoria.retencion_anios=10`, `movimientos.retencion_anios=8`.
6. **Atributos de producto por tipo:** columna `JSONB` validada contra `atributo_definicion` (configurable).
7. **Integridad de inventario:** `CHECK (cantidad >= 0)`; movimiento y saldo en la misma transacción con bloqueo; `movimiento_inventario` y `auditoria` **inmutables** (trigger que rechaza `UPDATE` y `DELETE`); los productos solo se desactivan.
8. **Reglas de negocio con prueba:** ajuste sin aprobación del administrador no cambia el saldo; venta anulable solo si está pendiente de pago; venta confirmada descuenta inventario y conserva moneda, tipo de cambio e impuestos de ese momento; sin tarifa de impuesto en el país no hay venta; cliente infrecuente solo con datos mínimos; todo deja auditoría.
9. **Interfaz:** menú por permisos del rol; CRUD genérico alimentado por metadatos generados; cambio de idioma y moneda sin recargar; aviso cuando el servidor tarda en despertar.
10. **Seguridad:** BCrypt; JWT; roles y permisos según la matriz de RF-006 (Administrador, Encargado de inventario, Experto técnico, Vendedor/cajero, Gerente/propietario); bloqueo tras intentos fallidos configurable; administrador inicial por variables de entorno.

## 5. Etapas y entregables

| Etapa | Entregable principal |
|---|---|
| E0 Repositorio | Estructura (`spec/`, `db/`, `tools/`, `backend/`, `frontend/`, `e2e/`, `docker/`, `logs/`, `docs/evidencias/`), README, `.env.example`, pre-commit. |
| E1 Análisis | `spec/modelo-conceptual.md`: diagrama ER (Mermaid), reglas, estados, permisos y trazabilidad requisito → entidad → caso de uso. |
| E2 Modelo en JSON | `model.schema.json`, módulos JSON, comando `validar` con pruebas. |
| E3 Base de datos | SQL generado, semillas, `crear-bd` / `reiniciar-bd` / `sembrar` idempotentes con verificación y logs. |
| E4 Esqueleto del back | Spring Boot con paquetes, API `/api/v1`, errores Problem Details, salud y Swagger; arranca contra la base de E3. |
| E5.1 a E5.5 | Seguridad y parámetros; país, moneda, tipo de cambio e impuestos; catálogo; inventario; ventas y reportes. Cada una con pruebas unitarias e integración. |
| E6.1 a E6.4 | Base del front con i18n; CRUD genérico por metadatos; catálogo e inventario; ventas, reportes y parámetros. |
| E7 Verificación | Nada de negocio fijado en código; prueba de agregar país y moneda solo con datos. |
| E8 Pruebas y logs | Suites de Python, back, front y extremo a extremo; `probar` guarda en `logs/tests/<fecha-hora>/` los reportes y un `resumen.md`. |
| E9 CI | `ci.yml` con trabajos python, back, front y e2e, subiendo logs como artefactos; verificación de que lo generado no tiene diferencias; hook `pre-push`. |
| E10 Contenedores | Dockerfiles (JVM con poca memoria, p. ej. `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1`) y `docker-compose.yml` con `db`, `db-init`, `backend`, `frontend`. |
| E11 Render | `render.yaml`; `crear-bd` ejecutado desde local contra la base de Render; guía de variables (`SPRING_DATASOURCE_URL` en formato JDBC, `JWT_SECRET`, `CORS_ORIGINS`, `VITE_API_URL`); script de respaldo y restauración. |
| E12 Cierre | Datos de demostración, README final, `docs/evidencias/` con los logs de la corrida final, lista de fase 2. |

## 6. Criterios de calidad

- Cada etapa compila, ejecuta y pasa sus pruebas antes de pedir aprobación.
- Mensajes de error claros; logs útiles; sin código muerto ni dependencias sin uso.
- Nada de negocio en constantes; nada de dinero en `float`; ningún secreto versionado.
- Las plataformas gratuitas tienen límites (suspensión por inactividad, base de datos que caduca, poca memoria): déjalo documentado y verifica sus condiciones vigentes en la documentación oficial antes de asumir cifras.

## 7. Cómo empezar

Comienza por **E0**. Antes de escribir código, resume en un máximo de 10 líneas cómo entendiste el proyecto y lista cualquier duda. Después ejecuta la etapa y entrega el informe con el formato indicado.