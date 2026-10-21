import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { mockApi, page, requestsTo } from '../../test/mockApi'
import { PurchasesPage } from './PurchasesPage'

const proveedorCop = {
  id: 'prov-co',
  razonSocial: 'Distribuidora Andina',
  paisId: 'pais-co',
  monedaCompraId: 'mon-cop',
  activo: true,
}
const proveedorUsd = {
  id: 'prov-us',
  razonSocial: 'Gaming Supply Inc.',
  paisId: 'pais-us',
  monedaCompraId: 'mon-usd',
  activo: true,
}
const consola = { id: 'prod-1', codigo: 'PS5-SLIM', nombre: 'Consola PS5 Slim' }
const control = { id: 'prod-2', codigo: 'DS-EDGE', nombre: 'Control DualSense Edge' }

function orden(estado: string) {
  return {
    orden: {
      id: 'oc-1',
      numero: 'OC-123',
      proveedorId: 'prov-co',
      proveedorNombre: 'Distribuidora Andina',
      paisId: 'pais-co',
      monedaId: 'mon-cop',
      tipoCambio: '1.00000000',
      estado,
      creadaPorId: 'usr-1',
      creadaEnUtc: '2026-10-21T10:00:00Z',
    },
    lineas: [
      {
        id: 'lin-1',
        productoId: 'prod-1',
        codigoProducto: 'PS5-SLIM',
        nombreProducto: 'Consola PS5 Slim',
        cantidadPedida: 4,
        cantidadRecibida: 0,
        costoUnitario: '2100000.0000',
      },
    ],
  }
}

function baseRoutes() {
  return {
    'GET /api/v1/proveedores/prov-co/productos': () => [consola],
    'GET /api/v1/proveedores/prov-us/productos': () => [consola, control],
    'GET /api/v1/proveedores': () => page([proveedorCop, proveedorUsd]),
    'GET /api/v1/referencias/moneda': () => [
      { id: 'mon-cop', codigo: 'COP', nombre: 'Peso colombiano' },
      { id: 'mon-usd', codigo: 'USD', nombre: 'Dólar estadounidense' },
    ],
    'GET /api/v1/referencias/producto': () => [consola, control],
    'GET /api/v1/compras/contexto': () => ({ monedaBaseCodigo: 'COP' }),
    'GET /api/v1/compras/ordenes': () => page([]),
  }
}

const permissions = ['COMPRAS_GESTIONAR', 'PROVEEDORES_GESTIONAR', 'INVENTARIO_CONSULTAR']

async function choose(user: ReturnType<typeof userEvent.setup>, combo: HTMLElement, option: string) {
  await user.click(combo)
  await user.click(await screen.findByRole('option', { name: option }))
}

describe('PurchasesPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('creates a draft in the base currency with rate 1 and sends it to the supplier', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi({
      ...baseRoutes(),
      'POST /api/v1/compras/ordenes/oc-1/enviar': () => orden('ENVIADA_AL_PROVEEDOR'),
      'POST /api/v1/compras/ordenes': () => orden('BORRADOR'),
    })
    const onOpenInventory = vi.fn()
    render(<PurchasesPage permissions={permissions} onOpenInventory={onOpenInventory} />)

    expect(await screen.findByText('No hay órdenes de compra registradas.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Nueva orden de compra' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'GET', '/api/v1/proveedores')).toHaveLength(1),
    )
    await choose(user, within(dialog).getByRole('combobox', { name: 'Proveedor' }), 'Distribuidora Andina')

    const rate = within(dialog).getByRole('textbox', { name: 'Tipo de cambio' })
    expect(rate).toHaveValue('1')
    expect(rate).toBeDisabled()
    await choose(
      user,
      await within(dialog).findByRole('combobox', { name: 'Producto de la línea 1' }),
      'Consola PS5 Slim (PS5-SLIM)',
    )
    const quantity = within(dialog).getByRole('spinbutton', { name: 'Cantidad de la línea 1' })
    await user.clear(quantity)
    await user.type(quantity, '4')
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Costo unitario de la línea 1 (COP)' }),
      '2100000',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Crear borrador' }))

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes')).toHaveLength(1),
    )
    expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes')[0].body).toEqual({
      proveedorId: 'prov-co',
      tipoCambio: '1',
      lineas: [{ productoId: 'prod-1', cantidadPedida: 4, costoUnitario: '2100000' }],
    })
    expect(await screen.findByText('Orden OC-123 creada en borrador.')).toBeInTheDocument()

    const detail = await screen.findByRole('dialog', { name: 'Orden OC-123' })
    expect(within(detail).getByText('Borrador')).toBeInTheDocument()
    await user.click(within(detail).getByRole('button', { name: 'Enviar al proveedor' }))
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes/oc-1/enviar')).toHaveLength(1),
    )
    expect(await within(detail).findByText('Enviada al proveedor')).toBeInTheDocument()
    await user.click(within(detail).getByRole('button', { name: 'Ir a Inventario' }))
    expect(onOpenInventory).toHaveBeenCalled()
  })

  it('requires an exchange rate when the supplier buys in another currency', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi({
      ...baseRoutes(),
      'POST /api/v1/compras/ordenes': () => orden('BORRADOR'),
    })
    render(<PurchasesPage permissions={permissions} />)

    await user.click(await screen.findByRole('button', { name: 'Nueva orden de compra' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'GET', '/api/v1/proveedores')).toHaveLength(1),
    )
    await choose(user, within(dialog).getByRole('combobox', { name: 'Proveedor' }), 'Gaming Supply Inc.')
    await choose(
      user,
      await within(dialog).findByRole('combobox', { name: 'Producto de la línea 1' }),
      'Control DualSense Edge (DS-EDGE)',
    )
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Costo unitario de la línea 1 (USD)' }),
      '199,99',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Crear borrador' }))
    expect(
      await within(dialog).findByText(/Tipo de cambio debe ser un número decimal mayor que cero/),
    ).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes')).toHaveLength(0)

    await user.type(within(dialog).getByRole('textbox', { name: 'Tipo de cambio' }), '4100,5')
    await user.click(within(dialog).getByRole('button', { name: 'Crear borrador' }))
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes')).toHaveLength(1),
    )
    expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes')[0].body).toEqual({
      proveedorId: 'prov-us',
      tipoCambio: '4100.5',
      lineas: [{ productoId: 'prod-2', cantidadPedida: 1, costoUnitario: '199.99' }],
    })
  })

  it('cancels a sent order after confirmation', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi({
      ...baseRoutes(),
      'GET /api/v1/compras/ordenes/oc-1': () => orden('ENVIADA_AL_PROVEEDOR'),
      'GET /api/v1/compras/ordenes': () => page([orden('ENVIADA_AL_PROVEEDOR').orden]),
      'POST /api/v1/compras/ordenes/oc-1/cancelar': () => orden('CANCELADA'),
    })
    render(<PurchasesPage permissions={permissions} />)

    await user.click(await screen.findByRole('button', { name: 'Ver detalle OC-123' }))
    const detail = await screen.findByRole('dialog', { name: 'Orden OC-123' })
    expect(within(detail).getByText('2.100.000 COP')).toBeInTheDocument()
    await user.click(within(detail).getByRole('button', { name: 'Cancelar orden' }))
    await user.click(within(detail).getByRole('button', { name: 'Sí, cancelar' }))
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/compras/ordenes/oc-1/cancelar')).toHaveLength(1),
    )
    expect(await within(detail).findByText('Cancelada')).toBeInTheDocument()
    expect(within(detail).queryByRole('button', { name: 'Cancelar orden' })).not.toBeInTheDocument()
  })

  it('lists and links the official products of a supplier', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi({
      ...baseRoutes(),
      'POST /api/v1/proveedores/prov-co/productos/prod-2': () => new Response(null, { status: 204 }),
    })
    render(<PurchasesPage permissions={permissions} />)

    await user.click(await screen.findByRole('tab', { name: 'Productos por proveedor' }))
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'GET', '/api/v1/proveedores')).toHaveLength(1),
    )
    await choose(user, screen.getByRole('combobox', { name: 'Proveedor' }), 'Distribuidora Andina')
    expect(await screen.findByText('PS5-SLIM')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'Producto a vincular' }))
    // Los productos ya vinculados no se ofrecen de nuevo.
    expect(screen.queryByRole('option', { name: 'Consola PS5 Slim (PS5-SLIM)' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: 'Control DualSense Edge (DS-EDGE)' }))
    await user.click(screen.getByRole('button', { name: 'Vincular producto' }))

    await waitFor(() =>
      expect(
        requestsTo(fetchMock, 'POST', '/api/v1/proveedores/prov-co/productos/prod-2'),
      ).toHaveLength(1),
    )
    expect(
      await screen.findByText('«Control DualSense Edge» quedó vinculado como producto oficial del proveedor.'),
    ).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'GET', '/api/v1/proveedores/prov-co/productos')).toHaveLength(2)
  })

  it('hides linking for roles that can only consult suppliers', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi(baseRoutes())
    render(<PurchasesPage permissions={['COMPRAS_GESTIONAR', 'PROVEEDORES_CONSULTAR']} />)

    await user.click(await screen.findByRole('tab', { name: 'Productos por proveedor' }))
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'GET', '/api/v1/proveedores')).toHaveLength(1),
    )
    await choose(user, screen.getByRole('combobox', { name: 'Proveedor' }), 'Distribuidora Andina')
    expect(await screen.findByText('PS5-SLIM')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Vincular producto' })).not.toBeInTheDocument()
    expect(requestsTo(fetchMock, 'GET', '/api/v1/referencias/producto')).toHaveLength(0)
  })
})
