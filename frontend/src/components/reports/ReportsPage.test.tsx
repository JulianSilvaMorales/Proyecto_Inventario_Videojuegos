import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AUTH_TOKEN_KEY } from '../../api/apiClient'
import i18n from '../../i18n'
import { mockApi, page, problem, requestsTo } from '../../test/mockApi'
import { ReportsPage } from './ReportsPage'

function routes() {
  return {
    'GET /api/v1/inventario/bodegas': () =>
      page([{ id: 'bod-1', codigo: 'BOG', nombre: 'Bodega Bogotá', paisId: 'pais-co', activo: true }]),
    'GET /api/v1/referencias/pais': () => [{ id: 'pais-co', codigo: 'CO', nombre: 'Colombia' }],
    'GET /api/v1/referencias/moneda': () => [{ id: 'cop', codigo: 'COP', nombre: 'Peso colombiano' }],
    'GET /api/v1/referencias/producto': () => [{ id: 'prod-1', codigo: 'PRD-1', nombre: 'Consola PS5' }],
    'GET /api/v1/referencias/usuario': () => problem(403, 'Sin permiso'),
    'GET /api/v1/reportes/inventario/existencias.csv': () =>
      new Response('producto,cantidad\nPRD-1,5\n', {
        headers: {
          'Content-Type': 'text/csv',
          'Content-Disposition': 'attachment; filename="existencias-2026.csv"',
        },
      }),
    'GET /api/v1/reportes/inventario/existencias': () =>
      page([
        {
          productoId: 'prod-1',
          codigoProducto: 'PRD-1',
          nombreProducto: 'Consola',
          bodegaId: 'bod-1',
          bodegaNombre: 'Bodega Bogotá',
          cantidad: 5,
          actualizadoEnUtc: '2026-10-20T10:00:00Z',
        },
      ]),
    'GET /api/v1/reportes/inventario/movimientos': () => page([]),
  }
}

describe('ReportsPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('applies the filters to the report and to the authenticated CSV export', async () => {
    const user = userEvent.setup()
    sessionStorage.setItem(AUTH_TOKEN_KEY, 'token-de-prueba')
    const fetchMock = mockApi(routes())
    const createObjectURL = vi.fn(() => 'blob:csv')
    const revokeObjectURL = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    render(<ReportsPage />)
    expect(await screen.findByText('Consola PS5')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'País' }))
    await user.click(await screen.findByRole('option', { name: /Colombia/ }))
    await user.click(screen.getByRole('button', { name: 'Exportar CSV' }))

    await waitFor(() => expect(click).toHaveBeenCalled())
    const exportCall = fetchMock.mock.calls.find(([input]) => String(input).includes('existencias.csv'))
    expect(new URL(String(exportCall?.[0])).searchParams.get('paisId')).toBe('pais-co')
    expect(new Headers(exportCall?.[1]?.headers).get('Authorization')).toBe('Bearer token-de-prueba')
    expect(createObjectURL).toHaveBeenCalled()
    const last = requestsTo(fetchMock, 'GET', '/api/v1/reportes/inventario/existencias').at(-1)
    expect(last?.url.searchParams.get('paisId')).toBe('pais-co')
  })

  it('hides filters whose references are not allowed for the role', async () => {
    const user = userEvent.setup()
    mockApi(routes())
    render(<ReportsPage />)

    await user.click(await screen.findByRole('tab', { name: 'Movimientos' }))
    expect(await screen.findByRole('combobox', { name: 'Moneda' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Usuario' })).not.toBeInTheDocument()
  })
})
