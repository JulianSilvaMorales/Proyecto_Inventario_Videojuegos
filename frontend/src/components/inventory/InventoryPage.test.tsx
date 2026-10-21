import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { mockApi, page, problem, requestsTo } from '../../test/mockApi'
import { InventoryPage } from './InventoryPage'

const bodega = { id: 'bod-1', codigo: 'BOG', nombre: 'Bodega Bogotá', paisId: 'pais-co', activo: true }
const producto = { id: 'prod-1', codigo: 'PRD-1', nombre: 'Consola PS5' }
const ajustePendiente = {
  id: 'aj-1',
  productoId: 'prod-1',
  codigoProducto: 'PRD-1',
  bodegaNombre: 'Bodega Bogotá',
  tipo: 'OPERATIVO',
  cantidadSistema: 10,
  cantidadContada: 8,
  diferencia: -2,
  motivo: 'Conteo físico',
  estado: 'PENDIENTE_DE_APROBACION',
  solicitadoEnUtc: '2026-10-20T10:00:00Z',
}

function baseRoutes() {
  return {
    'GET /api/v1/inventario/bodegas': () => page([bodega]),
    'GET /api/v1/referencias/pais': () => [{ id: 'pais-co', codigo: 'CO', nombre: 'Colombia' }],
    'GET /api/v1/referencias/producto': () => [producto],
    'GET /api/v1/proveedores': () => page([]),
    'GET /api/v1/inventario/saldos': () =>
      page([
        {
          productoId: 'prod-1',
          codigoProducto: 'PRD-1',
          nombreProducto: 'Consola',
          bodegaId: 'bod-1',
          bodegaCodigo: 'BOG',
          bodegaNombre: 'Bodega Bogotá',
          cantidad: 5,
          actualizadoEnUtc: '2026-10-20T10:00:00Z',
        },
      ]),
    'GET /api/v1/inventario/ajustes': () => page([ajustePendiente]),
    'GET /api/v1/inventario/movimientos': () => page([]),
    'POST /api/v1/inventario/ajustes/aj-1/aprobar': () => ({ ...ajustePendiente, estado: 'APROBADO' }),
  }
}

describe('InventoryPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('lets the administrator approve adjustments using the backend states', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi(baseRoutes())
    render(
      <InventoryPage
        permissions={['INVENTARIO_CONSULTAR', 'COMPRAS_GESTIONAR', 'AJUSTES_APROBAR']}
        roles={['ADMINISTRADOR']}
        canOpenWarehouses
        onOpenCrudEntity={vi.fn()}
      />,
    )

    expect(await screen.findByText('Consola PS5')).toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Ajustes' }))
    expect(await screen.findByText('Pendiente de aprobación')).toBeInTheDocument()
    // El administrador aprueba, pero no solicita ajustes (Anexo D).
    expect(screen.queryByRole('button', { name: 'Solicitar ajuste' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Aprobar' }))
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/inventario/ajustes/aj-1/aprobar')).toHaveLength(1),
    )
    expect(await screen.findByText('Ajuste aprobado.')).toBeInTheDocument()
  })

  it('works for the technical expert even when optional references are forbidden', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi({
      ...baseRoutes(),
      'GET /api/v1/referencias/pais': () => problem(403, 'Sin permiso'),
    })
    render(
      <InventoryPage
        permissions={['INVENTARIO_CONSULTAR', 'CATALOGO_CONSULTAR', 'AJUSTE_TECNICO_SOLICITAR']}
        roles={['EXPERTO_TECNICO']}
        canOpenWarehouses={false}
        onOpenCrudEntity={vi.fn()}
      />,
    )

    expect(await screen.findByText('Consola PS5')).toBeInTheDocument()
    expect(screen.queryByText('Sin permiso')).not.toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'Entradas de compra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'Salidas' })).not.toBeInTheDocument()
    expect(requestsTo(fetchMock, 'GET', '/api/v1/proveedores')).toHaveLength(0)

    await user.click(screen.getByRole('tab', { name: 'Ajustes' }))
    await user.click(await screen.findByRole('button', { name: 'Solicitar ajuste' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('combobox', { name: 'Tipo de ajuste' })).toHaveTextContent('Técnico')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))
    expect(
      await within(dialog).findByText('Completa todos los campos obligatorios.'),
    ).toBeInTheDocument()
  })

  it('filters movements with the model movement types and a date range', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi(baseRoutes())
    render(
      <InventoryPage
        permissions={['INVENTARIO_CONSULTAR']}
        roles={[]}
        canOpenWarehouses={false}
        onOpenCrudEntity={vi.fn()}
      />,
    )

    await user.click(await screen.findByRole('tab', { name: 'Movimientos' }))
    await user.click(await screen.findByRole('combobox', { name: 'Tipo de movimiento' }))
    expect(screen.queryByRole('option', { name: /Entrada por compra/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: 'Entrada' }))
    await user.type(screen.getByLabelText('Desde'), '2026-10-01')

    await waitFor(() => {
      const last = requestsTo(fetchMock, 'GET', '/api/v1/inventario/movimientos').at(-1)
      expect(last?.url.searchParams.get('tipo')).toBe('ENTRADA')
      expect(last?.url.searchParams.get('desde')).toMatch(/^2026-(09-30|10-01)T/)
    })
  })
})
