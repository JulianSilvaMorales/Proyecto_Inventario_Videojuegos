/**
 * Utilidades para importes y tasas sin pasar por `float`: los valores se
 * validan y se envían a la API como texto decimal (convención del proyecto).
 */
const DECIMAL_PATTERN = /^\d+(?:[.,]\d+)?$/

/** Devuelve el texto decimal normalizado (punto como separador) o `null` si no es válido. */
export function parseDecimal(
  input: string,
  maxScale: number,
  maxIntegerDigits = 15,
): string | null {
  const value = input.trim()
  if (!DECIMAL_PATTERN.test(value)) {
    return null
  }
  const [integerPart, fraction = ''] = value.replace(',', '.').split('.')
  if (fraction.length > maxScale) {
    return null
  }
  const integer = integerPart.replace(/^0+(?=\d)/, '')
  if (integer.length > maxIntegerDigits) {
    return null
  }
  return fraction ? `${integer}.${fraction}` : integer
}

/** Indica si el texto decimal normalizado es estrictamente mayor que cero. */
export function isPositiveDecimal(value: string): boolean {
  return /[1-9]/.test(value)
}
