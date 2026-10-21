import { parseDecimal } from '../../utils/decimal'

export const PARAMETER_TYPES = ['TEXTO', 'ENTERO', 'DECIMAL', 'BOOLEANO', 'JSON'] as const
export type ParameterType = (typeof PARAMETER_TYPES)[number]
export const PARAMETER_SCOPES = ['NEGOCIO', 'PAIS'] as const
export type ParameterScope = (typeof PARAMETER_SCOPES)[number]

/** Límites de `parametro.minimo`/`maximo`: NUMERIC(19,4). */
const LIMIT_SCALE = 4
const VALUE_SCALE = 18

/** Decimal con signo opcional, como texto normalizado. */
export function parseSignedDecimal(text: string, scale: number): string | null {
  const trimmed = text.trim()
  const negative = trimmed.startsWith('-')
  const parsed = parseDecimal(negative ? trimmed.slice(1) : trimmed, scale)
  if (parsed === null) return null
  return negative && /[1-9]/.test(parsed) ? `-${parsed}` : parsed
}

export function parseLimit(text: string): string | null | undefined {
  if (!text.trim()) return undefined
  return parseSignedDecimal(text, LIMIT_SCALE)
}

export type ParsedValue = { ok: true; value: unknown } | { ok: false }

/**
 * Convierte el valor editado al JSON que espera la API. Los decimales viajan
 * como texto; el backend los guarda como número sin pasar por `double`.
 */
export function parseParameterValue(type: ParameterType, text: string, flag: boolean): ParsedValue {
  switch (type) {
    case 'BOOLEANO':
      return { ok: true, value: flag }
    case 'TEXTO':
      return text.trim() ? { ok: true, value: text.trim() } : { ok: false }
    case 'ENTERO': {
      const value = Number(text.trim())
      return text.trim() && Number.isSafeInteger(value) ? { ok: true, value } : { ok: false }
    }
    case 'DECIMAL': {
      const value = parseSignedDecimal(text, VALUE_SCALE)
      return value === null ? { ok: false } : { ok: true, value }
    }
    case 'JSON':
      try {
        return { ok: true, value: JSON.parse(text) }
      } catch {
        return { ok: false }
      }
  }
}

export function valueToText(type: string, value: unknown): string {
  if (value === null || value === undefined) return ''
  if (type === 'JSON') return JSON.stringify(value, null, 2)
  return String(value)
}
