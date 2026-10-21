import { describe, expect, it } from 'vitest'
import { formatDate, formatMoney, formatNumber } from './formatters'

describe('internationalized formatters', () => {
  it('formats transaction currency and number with the selected locale', () => {
    expect(formatMoney('1234567.89', 'USD', 'es')).not.toBe(formatMoney('1234567.89', 'USD', 'en'))
    expect(formatNumber(1234567.89, 'es')).not.toBe(formatNumber(1234567.89, 'en'))
  })

  it('formats decimal text amounts in the transaction currency without losing digits', () => {
    expect(formatMoney('12345678901234.5678', 'USD', 'en', 4)).toBe('$12,345,678,901,234.5678')
    expect(formatMoney('1250.0000', 'COP', 'es', 2)).not.toBe(formatMoney('1250.0000', 'COP', 'en', 2))
  })

  it('formats dates with the selected locale', () => {
    const date = new Date('2026-10-20T14:30:00.000Z')
    expect(formatDate(date, 'es')).not.toBe(formatDate(date, 'en'))
  })
})
