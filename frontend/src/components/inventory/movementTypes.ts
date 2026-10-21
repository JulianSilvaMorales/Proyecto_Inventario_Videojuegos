/** Tipos de `movimiento_inventario` definidos en el modelo (db/model/modulos/inventario.json). */
export const MOVEMENT_TYPES = [
  'ENTRADA',
  'SALIDA_VENTA',
  'SALIDA_PERDIDA',
  'SALIDA_DANO',
  'SALIDA_ROBO',
  'SALIDA_DEFECTO',
  'AJUSTE',
] as const

/** Convierte una fecha `yyyy-mm-dd` en el inicio (o el final) de ese día local, en ISO-8601 UTC. */
export function dayBoundaryIso(day: string, endOfDay: boolean): string {
  const [year, month, date] = day.split('-').map(Number)
  const boundary = endOfDay
    ? new Date(year, month - 1, date, 23, 59, 59, 999)
    : new Date(year, month - 1, date, 0, 0, 0, 0)
  return boundary.toISOString()
}
