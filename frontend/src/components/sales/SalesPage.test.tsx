import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { mockApi, page, requestsTo } from '../../test/mockApi'
import { formatMoney } from '../../utils/formatters'
import { SalesPage } from './SalesPage'

const colombia = {
  paisId: 'pais-co',
  paisCodigo: 'CO',
  paisNombre: 'Colombia',
  monedaId: 'cop',
  monedaCodigo: 'COP',
  monedaDecimales: 2,
  mediosPago: ['EFECTIVO', 'NEQUI'],
}

/** Texto visible de un importe (el DOM normaliza el espacio de no separación). */
function money(language: string) {
  return formatMoney('238000.0000', 'COP', language, 2).replace(/\s/g, ' ')
}

function venta(estado: string, estadoPago = 'SIN_PAGO') {
  return {
    venta: {
      id: 'venta-1',
      numero: 'V-0001',
      clienteId: null,
      paisId: 'pais-co',
      paisCodigo: 'CO',
      monedaId: 'cop',
      monedaCodigo: 'COP',
      tipoCambioAplicado: '1.00000000',
      subtotal: '200000.0000',
      totalImpuestos: '38000.0000',
      total: '238000.0000',
      totalMonedaBase: '238000.0000',
      estado,
      estadoPago,
      medioPago: estado === 'CONFIRMADA' ? 'NEQUI' : null,
      creadaEnUtc: '2026-10-20T15:00:00Z',
      confirmadaEnUtc: estado === 'CONFIRMADA' ? '2026-10-20T15:05:00Z' : null,
      anuladaEnUtc: null,
    },
    lineas: [
      {
        id: 'linea-1',
        productoId: 'prod-1',
        codigoProducto: 'PRD-1',
        nombreProducto: 'Consola',
        bodegaNombre: 'Bodega Bogotá',
        cantidad: 2,
        precioUnitario: '100000.0000',
        nombreImpuestoAplicado: 'IVA',
        tasaImpuestoAplicada: '0.1900',
        importeImpuesto: '38000.0000',
        subtotalLinea: '200000.0000',
        totalLinea: '238000.0000',
      },
    ],
    reciboSimulado: estado === 'CONFIRMADA',
  }
}

function routes() {
  let estado = 'PENDIENTE_DE_PAGO'
  return {
    'GET /api/v1/ventas/paises': () => [colombia],
    'GET /api/v1/inventario/bodegas': () =>
      page([{ id: 'bod-1', codigo: 'BOG', nombre: 'Bodega Bogotá', paisId: 'pais-co', activo: true }]),
    'GET /api/v1/referencias/producto': () => [{ id: 'prod-1', codigo: 'PRD-1', nombre: 'Consola PS5' }],
    'GET /api/v1/ventas/venta-1': () => venta(estado, estado === 'CONFIRMADA' ? 'PAGADO' : 'SIN_PAGO'),
    'GET /api/v1/ventas': () => page([]),
    'POST /api/v1/ventas/venta-1/confirmar': () => {
      estado = 'CONFIRMADA'
      return venta(estado, 'PAGADO')
    },
    'POST /api/v1/ventas': () => venta('PENDIENTE_DE_PAGO'),
  }
}

describe('SalesPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('creates a sale in the local currency, shows the tax breakdown and confirms payment', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi(routes())
    render(
      <SalesPage permissions={['VENTAS_GESTIONAR', 'CATALOGO_CONSULTAR']} onSelectEntity={vi.fn()} />,
    )

    await user.click(await screen.findByRole('button', { name: 'Nueva venta' }))
    const form = await screen.findByRole('dialog')
    // Con un único país disponible se selecciona y muestra su moneda local.
    expect(within(form).getByDisplayValue('COP')).toBeInTheDocument()
    await user.click(within(form).getByRole('combobox', { name: /Producto 1/ }))
    await user.click(await screen.findByRole('option', { name: /Consola PS5/ }))
    await user.click(within(form).getByRole('combobox', { name: 'Bodega' }))
    await user.click(await screen.findByRole('option', { name: /Bodega Bogotá/ }))
    await user.clear(within(form).getByLabelText(/Cantidad/))
    await user.type(within(form).getByLabelText(/Cantidad/), '2')
    await user.click(within(form).getByRole('button', { name: 'Calcular venta' }))

    await waitFor(() => expect(requestsTo(fetchMock, 'POST', '/api/v1/ventas')).toHaveLength(1))
    expect(requestsTo(fetchMock, 'POST', '/api/v1/ventas')[0].body).toEqual({
      clienteId: null,
      paisId: 'pais-co',
      monedaId: 'cop',
      lineas: [{ productoId: 'prod-1', bodegaId: 'bod-1', cantidad: 2 }],
    })

    const detail = await screen.findByRole('dialog', { name: 'Venta V-0001' })
    expect(await within(detail).findByTestId('sale-total')).toHaveTextContent(money('es'))
    expect(within(detail).getByText(/IVA/)).toBeInTheDocument()
    // Sin el permiso de anulación no se ofrece anular.
    expect(within(detail).queryByRole('button', { name: 'Anular venta' })).not.toBeInTheDocument()

    await user.click(within(detail).getByRole('combobox', { name: 'Medio de pago' }))
    await user.click(await screen.findByRole('option', { name: 'Nequi' }))
    await user.click(within(detail).getByRole('button', { name: 'Confirmar pago' }))

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/ventas/venta-1/confirmar')[0]?.body).toEqual({
        medioPago: 'NEQUI',
      }),
    )
    expect(await within(detail).findByRole('button', { name: 'Ver recibo' })).toBeInTheDocument()
  })

  it('formats the same amount differently when the language changes, without reloading', async () => {
    const user = userEvent.setup()
    mockApi({
      ...routes(),
      'GET /api/v1/ventas': () => page([venta('CONFIRMADA', 'PAGADO').venta]),
      'GET /api/v1/ventas/venta-1': () => venta('CONFIRMADA', 'PAGADO'),
    })
    render(<SalesPage permissions={['VENTAS_GESTIONAR']} onSelectEntity={vi.fn()} />)

    expect(await screen.findByText(money('es'))).toBeInTheDocument()
    expect(screen.getByText('Confirmada')).toBeInTheDocument()

    await i18n.changeLanguage('en')
    expect(await screen.findByText(money('en'))).toBeInTheDocument()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'View' }))
    expect(await screen.findByRole('button', { name: 'View receipt' })).toBeInTheDocument()
  })
})
