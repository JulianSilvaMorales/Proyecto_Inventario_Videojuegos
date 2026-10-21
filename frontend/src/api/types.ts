export interface PageResponse<T> {
  content: T[]
  totalElements: number
}

/** Opción devuelta por `/api/v1/referencias/{entidad}`. */
export interface ReferenceOption {
  id: string
  codigo: string
  nombre: string
}

/** Importe decimal tal como llega de la API (texto o número JSON). */
export type DecimalValue = string | number
