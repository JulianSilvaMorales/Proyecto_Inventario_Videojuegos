import type { DecimalValue } from '../../api/types'
import { localeFor } from '../../i18n/language'

/** País en el que el usuario puede vender, con su moneda local y medios de pago. */
export interface PaisVenta {
  paisId: string
  paisCodigo: string
  paisNombre: string
  monedaId: string
  monedaCodigo: string
  monedaDecimales: number
  mediosPago: string[]
}

export interface Venta {
  id: string
  numero: string
  clienteId: string | null
  paisId: string
  paisCodigo: string
  monedaId: string
  monedaCodigo: string
  tipoCambioAplicado: DecimalValue
  subtotal: DecimalValue
  totalImpuestos: DecimalValue
  total: DecimalValue
  totalMonedaBase: DecimalValue
  estado: string
  estadoPago: string
  medioPago: string | null
  creadaEnUtc: string
  confirmadaEnUtc: string | null
  anuladaEnUtc: string | null
}

export interface VentaLinea {
  id: string
  productoId: string
  codigoProducto: string
  nombreProducto: string | null
  bodegaNombre: string
  cantidad: number
  precioUnitario: DecimalValue
  nombreImpuestoAplicado: string
  tasaImpuestoAplicada: DecimalValue
  importeImpuesto: DecimalValue
  subtotalLinea: DecimalValue
  totalLinea: DecimalValue
}

export interface RespuestaVenta {
  venta: Venta
  lineas: VentaLinea[]
  reciboSimulado: boolean
}

export interface ClienteResumen {
  id: string
  paisId: string
  nombre: string
  apellidos: string | null
  documento: string | null
  activo: boolean
}

export const SALE_STATES = ['PENDIENTE_DE_PAGO', 'CONFIRMADA', 'ANULADA'] as const

export const SALE_STATE_COLORS: Record<string, 'warning' | 'success' | 'default'> = {
  PENDIENTE_DE_PAGO: 'warning',
  CONFIRMADA: 'success',
  ANULADA: 'default',
}

export function clientName(cliente: ClienteResumen) {
  return [cliente.nombre, cliente.apellidos].filter(Boolean).join(' ')
}

/** La tasa se guarda como fracción (0.19); se muestra como porcentaje sin usar float. */
export function ratePercent(rate: DecimalValue, language: string) {
  return new Intl.NumberFormat(localeFor(language), {
    style: 'percent',
    maximumFractionDigits: 4,
  }).format(String(rate) as Intl.StringNumericLiteral)
}
