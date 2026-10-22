# Resumen de pruebas — 20261021-210902

- Commit: `542f531` (con cambios sin confirmar)
- Estado general: **CORRECTO**

| Suite | Estado | Pruebas | Fallos | Errores | Omitidas | Duración | Log |
|---|---|---:|---:|---:|---:|---:|---|
| python | ✅ correcto | 67 | 0 | 0 | 0 | 18 s | [python.log](python.log) |
| backend | ✅ correcto | 74 | 0 | 0 | 0 | 30 s | [backend.log](backend.log) |
| frontend | ✅ correcto | 37 | 0 | 0 | 0 | 73 s | [frontend.log](frontend.log) |
| e2e | ✅ correcto | 2 | 0 | 0 | 0 | 44 s | [e2e.log](e2e.log) |

## Pasos

### python
- ruff check: ✅ — 0 s
- ruff format: ✅ — 0 s
- pytest: ✅ — 18 s

### backend
- mvn clean verify: ✅ — 30 s

### frontend
- npm ci: ✅ — 50 s
- lint: ✅ — 1 s
- typecheck: ✅ — 5 s
- vitest: ✅ — 12 s
- build: ✅ — 5 s

### e2e
- empaquetar backend: ✅ — 3 s
- build frontend: ✅ — 5 s
- npm ci e2e: ✅ — 1 s
- navegador Playwright: ✅ — 1 s
- crear-bd: ✅ — 1 s
- playwright: ✅ — 20 s

Los reportes JUnit XML de cada suite están en su subcarpeta.
