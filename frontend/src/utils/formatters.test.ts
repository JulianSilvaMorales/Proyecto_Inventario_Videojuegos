import { describe, expect, it } from 'vitest'
import { formatCurrency, formatDate, formatNumber } from './formatters'

describe('internationalized formatters', () => {
  it('formats transaction currency and number with the selected locale', () => {
    expect(formatCurrency(1234567.89, 'USD', 'es')).not.toBe(
      formatCurrency(1234567.89, 'USD', 'en'),
    )
    expect(formatNumber(1234567.89, 'es')).not.toBe(formatNumber(1234567.89, 'en'))
  })

  it('formats dates with the selected locale', () => {
    const date = new Date('2026-10-20T14:30:00.000Z')
    expect(formatDate(date, 'es')).not.toBe(formatDate(date, 'en'))
  })
})
