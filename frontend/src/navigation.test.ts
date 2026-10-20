import { describe, expect, it } from 'vitest'
import { visibleNavigation } from './navigation'

describe('visibleNavigation', () => {
  it('shows only modules allowed by the user permissions', () => {
    const items = visibleNavigation([
      'INVENTARIO_CONSULTAR',
      'PROVEEDORES_CONSULTAR',
    ])

    expect(items.map((item) => item.id)).toEqual([
      'home',
      'inventory',
      'suppliers',
    ])
  })

  it('always includes the home page without granting business access', () => {
    expect(visibleNavigation([]).map((item) => item.id)).toEqual(['home'])
  })
})
