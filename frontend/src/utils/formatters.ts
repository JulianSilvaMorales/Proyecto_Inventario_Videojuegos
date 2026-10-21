import { localeFor } from '../i18n/language'
import type { DecimalValue } from '../api/types'

/**
 * Formatea un importe de la API en su moneda. El texto decimal se pasa tal
 * cual a `Intl`, que lo admite sin convertirlo a `float`.
 */
export function formatMoney(
  amount: DecimalValue,
  currency: string,
  language: string,
  fractionDigits?: number,
): string {
  return new Intl.NumberFormat(localeFor(language), {
    style: 'currency',
    currency,
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(String(amount) as Intl.StringNumericLiteral)
}

export function formatDecimal(
  amount: DecimalValue,
  language: string,
  maximumFractionDigits = 8,
): string {
  return new Intl.NumberFormat(localeFor(language), {
    maximumFractionDigits,
  }).format(String(amount) as Intl.StringNumericLiteral)
}

export function formatNumber(amount: number, language: string): string {
  return new Intl.NumberFormat(localeFor(language), {
    maximumFractionDigits: 2,
  }).format(amount)
}

export function formatDate(date: Date, language: string): string {
  return new Intl.DateTimeFormat(localeFor(language), {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}
