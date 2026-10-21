import { describe, expect, it } from 'vitest'
import { parseParameterValue } from '../components/settings/parameterValues'
import { isPositiveDecimal, parseDecimal } from './decimal'

describe('decimal text without float', () => {
  it('normalizes decimal separators and keeps every digit', () => {
    expect(parseDecimal('1999999,99', 4)).toBe('1999999.99')
    expect(parseDecimal('  007.5000 ', 4)).toBe('7.5000')
    expect(parseDecimal('123456789012345.1234', 4)).toBe('123456789012345.1234')
  })

  it('rejects invalid text, extra decimals and too many integer digits', () => {
    expect(parseDecimal('', 4)).toBeNull()
    expect(parseDecimal('1e3', 4)).toBeNull()
    expect(parseDecimal('-1', 4)).toBeNull()
    expect(parseDecimal('1.23456', 4)).toBeNull()
    expect(parseDecimal('1234567890123456', 4)).toBeNull()
    expect(parseDecimal('123456789012', 8, 11)).toBeNull()
  })

  it('detects positive values', () => {
    expect(isPositiveDecimal('0.00000001')).toBe(true)
    expect(isPositiveDecimal('0.0000')).toBe(false)
  })

  it('converts parameter values by type', () => {
    expect(parseParameterValue('DECIMAL', '-0,5', false)).toEqual({ ok: true, value: '-0.5' })
    expect(parseParameterValue('ENTERO', '60', false)).toEqual({ ok: true, value: 60 })
    expect(parseParameterValue('ENTERO', '6.5', false)).toEqual({ ok: false })
    expect(parseParameterValue('BOOLEANO', '', true)).toEqual({ ok: true, value: true })
    expect(parseParameterValue('JSON', '["EFECTIVO"]', false)).toEqual({ ok: true, value: ['EFECTIVO'] })
    expect(parseParameterValue('TEXTO', '   ', false)).toEqual({ ok: false })
  })
})
