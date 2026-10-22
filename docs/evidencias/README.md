# Evidencias de la entrega (E12)

Logs y reportes copiados de `logs/` (que Git no versiona) para que la entrega
se pueda revisar sin volver a ejecutar nada. Los reportes JUnit del backend se
renombraron solo con el nombre de la clase de prueba (Surefire los llama
`TEST-com.inventario.service.<Clase>.xml`) para que ninguna ruta del
repositorio supere el límite de 260 caracteres de Windows al clonarlo. Se revisó que no contienen
contraseñas, tokens, URL de bases reales ni correos personales. Las rutas
absolutas del equipo se reemplazaron por rutas relativas al proyecto.

## Corrida final: `python tools/cli.py todo` (21-10-2026, 21:09, hora de Colombia)

Encadena `validar` → `generar` → `crear-bd` → `probar` (las cuatro suites; la
E2E con base desechable, backend JAR y `vite preview`). Terminó sin errores.

| Paso | Resultado | Evidencia |
|---|---|---|
| `validar` | Modelo válido: 33 entidades. | [`final/tools/validar-…log`](final/tools/) |
| `generar` | Sin diferencias con lo versionado (`git status` limpio después). | [`final/tools/generar-…log`](final/tools/) |
| `crear-bd` | Base local al día: ninguna migración ni semilla pendiente, esquema verificado. | [`final/tools/crear-bd-…log`](final/tools/) |
| `probar` | **180 pruebas, 0 fallos, 0 errores, 0 omitidas.** | [`final/resumen.md`](final/resumen.md) |

| Suite | Pruebas | Log | Reportes JUnit |
|---|---:|---|---|
| python | 67 | [`python.log`](final/python.log) | [`python/`](final/python/) |
| backend | 74 | [`backend.log`](final/backend.log) | [`backend/`](final/backend/) |
| frontend | 37 | [`frontend.log`](final/frontend.log) | [`frontend/`](final/frontend/) |
| e2e (COP y USD) | 2 | [`e2e.log`](final/e2e.log) | [`e2e/`](final/e2e/) |

La salida completa de la consola está en [`final/todo.txt`](final/todo.txt).

## Inicio de sesión por rol y permisos

[`roles-y-permisos.md`](roles-y-permisos.md): resultado de
`python tools/cli.py verificar-roles` contra la pila de Docker Compose con los
datos de demostración. Los 5 roles inician sesión y las 8 consultas y los 8
permisos revisados coinciden con el Anexo D de la guía.

## E2E contra las URL públicas de Render

[`render/resumen.md`](render/resumen.md): `python tools/cli.py probar
--suites e2e --e2e-entorno remoto` contra
https://pixelvault-web-ceen.onrender.com y https://pixelvault-api-ljq6.onrender.com
(21-10-2026, 20:16). Pasó el flujo en COP: creación del producto, Compras,
entrada, venta, pago, recibo, saldo y movimiento. Esa corrida es anterior a la
prueba en USD, que se agregó en E12 y pasa en la corrida final.

## Integración continua

Cada ejecución de GitHub Actions publica sus logs como artefactos `logs-python`,
`logs-backend`, `logs-frontend` y `logs-e2e` (pestaña *Actions* del
repositorio → una ejecución → *Artifacts*). `main` exige que los cuatro
trabajos pasen antes de fusionar.
