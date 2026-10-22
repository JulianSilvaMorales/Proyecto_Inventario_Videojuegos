# Inicio de sesión por rol y permisos (Anexo D)

- API: `http://localhost:8080` — 2026-10-21 21:12
- Generado con `python tools/cli.py verificar-roles`. En las tablas, 200 = acceso y 403 = acceso denegado; ✅ indica que coincide con el Anexo D.

- ✅ ADMINISTRADOR: sesión iniciada con `ADMIN_EMAIL` (roles: ADMINISTRADOR).
- ✅ ENCARGADO_INVENTARIO: sesión iniciada con `encargado.inventario@pixelvault.example` (roles: ENCARGADO_INVENTARIO).
- ✅ EXPERTO_TECNICO: sesión iniciada con `experto.tecnico@pixelvault.example` (roles: EXPERTO_TECNICO).
- ✅ VENDEDOR_CAJERO: sesión iniciada con `vendedor.bogota@pixelvault.example` (roles: VENDEDOR_CAJERO).
- ✅ GERENTE_PROPIETARIO: sesión iniciada con `gerente@pixelvault.example` (roles: GERENTE_PROPIETARIO).

## Accesos a la API

| Operación | ADMINISTRADOR | ENCARGADO_INVENTARIO | EXPERTO_TECNICO | VENDEDOR_CAJERO | GERENTE_PROPIETARIO |
|---|---|---|---|---|---|
| Parametrización | ✅ 200 | ✅ 403 | ✅ 403 | ✅ 403 | ✅ 403 |
| Consultar catálogo | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 200 |
| Consultar inventario | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 200 | ✅ 200 |
| Proveedores | ✅ 200 | ✅ 200 | ✅ 403 | ✅ 403 | ✅ 200 |
| Compras y entradas | ✅ 200 | ✅ 200 | ✅ 403 | ✅ 403 | ✅ 403 |
| Ventas | ✅ 200 | ✅ 403 | ✅ 403 | ✅ 200 | ✅ 403 |
| Clientes | ✅ 200 | ✅ 403 | ✅ 403 | ✅ 200 | ✅ 403 |
| Reportes de inventario | ✅ 200 | ✅ 200 | ✅ 403 | ✅ 403 | ✅ 200 |

## Permisos de la sesión

| Operación | ADMINISTRADOR | ENCARGADO_INVENTARIO | EXPERTO_TECNICO | VENDEDOR_CAJERO | GERENTE_PROPIETARIO |
|---|---|---|---|---|---|
| Aprobar ajustes (`AJUSTES_APROBAR`) | ✅ sí | ✅ no | ✅ no | ✅ no | ✅ no |
| Solicitar ajuste operativo (`AJUSTE_OPERATIVO_SOLICITAR`) | ✅ no | ✅ sí | ✅ no | ✅ no | ✅ no |
| Solicitar ajuste técnico (`AJUSTE_TECNICO_SOLICITAR`) | ✅ no | ✅ no | ✅ sí | ✅ no | ✅ no |
| Salidas por pérdida, daño, robo o defecto (`SALIDAS_INVENTARIO_GESTIONAR`) | ✅ sí | ✅ sí | ✅ no | ✅ no | ✅ no |
| Crear y modificar productos (`PRODUCTOS_GESTIONAR`) | ✅ sí | ✅ sí | ✅ no | ✅ no | ✅ no |
| Anular ventas pendientes (`VENTAS_ANULAR_PENDIENTES`) | ✅ sí | ✅ no | ✅ no | ✅ sí | ✅ no |
| Auditoría de inventario (`AUDITORIA_INVENTARIO_LEER`) | ✅ sí | ✅ sí | ✅ no | ✅ no | ✅ no |
| Auditoría técnica (`AUDITORIA_TECNICA_LEER`) | ✅ sí | ✅ no | ✅ sí | ✅ no | ✅ no |

**Resultado:** todo coincide con el Anexo D.
