export const navigationItems = [
  { id: 'home', permissionAny: [] },
  { id: 'catalog', permissionAny: ['CATALOGO_CONSULTAR'] },
  { id: 'inventory', permissionAny: ['INVENTARIO_CONSULTAR'] },
  { id: 'purchases', permissionAny: ['COMPRAS_GESTIONAR'] },
  {
    id: 'suppliers',
    permissionAny: ['PROVEEDORES_CONSULTAR', 'PROVEEDORES_GESTIONAR'],
  },
  { id: 'sales', permissionAny: ['VENTAS_GESTIONAR', 'CLIENTES_GESTIONAR'] },
  { id: 'reports', permissionAny: ['REPORTES_INVENTARIO', 'REPORTES_TODOS'] },
  {
    id: 'settings',
    permissionAny: ['PARAMETROS_GESTIONAR', 'TIPOS_CAMBIO_GESTIONAR'],
  },
] as const

export type NavigationId = (typeof navigationItems)[number]['id']

export function visibleNavigation(permissions: readonly string[]) {
  return navigationItems.filter(
    (item) =>
      item.permissionAny.length === 0 ||
      item.permissionAny.some((permission) => permissions.includes(permission)),
  )
}
