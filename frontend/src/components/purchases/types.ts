import type { DecimalValue } from '../../api/types'

export interface OrdenCompra {
  id: string
  numero: string
  proveedorId: string
  proveedorNombre: string
  paisId: string
  monedaId: string
  tipoCambio: DecimalValue
  estado: string
  creadaPorId: string
  creadaEnUtc: string
}

export interface OrdenCompraLinea {
  id: string
  productoId: string
  codigoProducto: string
  nombreProducto: string
  cantidadPedida: number
  cantidadRecibida: number
  costoUnitario: DecimalValue
}

export interface DetalleOrdenCompra {
  orden: OrdenCompra
  lineas: OrdenCompraLinea[]
}

export interface ContextoCompras {
  monedaBaseCodigo: string
}

export const ORDER_STATES = [
  'BORRADOR',
  'ENVIADA_AL_PROVEEDOR',
  'RECIBIDA_PARCIAL',
  'RECIBIDA_COMPLETA',
  'CANCELADA',
] as const

/** Estados en los que el backend permite registrar entradas (Inventario → Entradas de compra). */
export const RECEIVABLE_STATES = ['ENVIADA_AL_PROVEEDOR', 'RECIBIDA_PARCIAL']

/** Estados en los que el backend permite cancelar (si la orden aún no tiene entradas). */
export const CANCELLABLE_STATES = ['BORRADOR', 'ENVIADA_AL_PROVEEDOR']

export function orderStateColor(estado: string) {
  if (estado === 'RECIBIDA_COMPLETA') return 'success'
  if (RECEIVABLE_STATES.includes(estado)) return 'primary'
  if (estado === 'BORRADOR') return 'warning'
  return 'default'
}
