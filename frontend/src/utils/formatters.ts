import { localeFor } from '../i18n/language'

export function formatCurrency(
  amount: number,
  currency: string,
  language: string,
): string {
  return new Intl.NumberFormat(localeFor(language), {
    style: 'currency',
    currency,
  }).format(amount)
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
