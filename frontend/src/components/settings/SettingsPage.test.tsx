import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { mockApi, page, requestsTo } from '../../test/mockApi'
import { SettingsPage } from './SettingsPage'

const parametro = {
  id: 'par-1',
  clave: 'ventas.medios_pago',
  valor: ['EFECTIVO', 'NEQUI'],
  tipo: 'JSON',
  minimo: null,
  maximo: null,
  ambito: 'PAIS',
  paisId: 'pais-co',
  paisCodigo: 'CO',
  vigenteDesde: '2026-01-01',
  vigenteHasta: null,
}

function routes() {
  return {
    'GET /api/v1/parametros': () => page([parametro]),
    'GET /api/v1/referencias/pais': () => [{ id: 'pais-co', codigo: 'CO', nombre: 'Colombia' }],
    'POST /api/v1/parametros': () => ({ ...parametro, id: 'par-2' }),
    'PUT /api/v1/parametros/par-1': () => parametro,
  }
}

describe('SettingsPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('lists parameters with scope and validity and links the configuration CRUDs', async () => {
    mockApi(routes())
    const onSelectEntity = vi.fn()
    const user = userEvent.setup()
    render(
      <SettingsPage
        permissions={['PARAMETROS_GESTIONAR', 'TIPOS_CAMBIO_GESTIONAR']}
        onSelectEntity={onSelectEntity}
      />,
    )

    expect(await screen.findByText('ventas.medios_pago')).toBeInTheDocument()
    expect(screen.getByText('País · CO')).toBeInTheDocument()
    expect(screen.getByText(/2026-01-01 → sin fin/)).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Países, monedas e impuestos' }))
    await user.click(screen.getByRole('button', { name: 'Tipo de cambio' }))
    expect(onSelectEntity).toHaveBeenCalledWith(expect.objectContaining({ nombre: 'tipo_cambio' }))
  })

  it('creates a country decimal parameter sending the value as decimal text', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi(routes())
    render(<SettingsPage permissions={['PARAMETROS_GESTIONAR']} onSelectEntity={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'Nuevo parámetro' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Clave/), 'ventas.recargo')
    await user.click(within(dialog).getByRole('combobox', { name: 'Tipo' }))
    await user.click(await screen.findByRole('option', { name: 'Decimal' }))
    await user.type(within(dialog).getByLabelText(/Valor/), '0,1234567890123456')
    await user.click(within(dialog).getByRole('combobox', { name: 'Ámbito' }))
    await user.click(await screen.findByRole('option', { name: 'País' }))
    await user.click(within(dialog).getByRole('combobox', { name: 'País' }))
    await user.click(await screen.findByRole('option', { name: /Colombia/ }))
    await user.type(within(dialog).getByLabelText(/Vigente desde/), '2026-11-01')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(requestsTo(fetchMock, 'POST', '/api/v1/parametros')).toHaveLength(1))
    expect(requestsTo(fetchMock, 'POST', '/api/v1/parametros')[0].body).toEqual({
      clave: 'ventas.recargo',
      tipo: 'DECIMAL',
      valor: '0.1234567890123456',
      minimo: null,
      maximo: null,
      ambito: 'PAIS',
      paisId: 'pais-co',
      vigenteDesde: '2026-11-01',
      vigenteHasta: null,
    })
    expect(await screen.findByText('Parámetro guardado.')).toBeInTheDocument()
  })

  it('validates JSON values before calling the API', async () => {
    const user = userEvent.setup()
    const fetchMock = mockApi(routes())
    render(<SettingsPage permissions={['PARAMETROS_GESTIONAR']} onSelectEntity={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'Editar ventas.medios_pago' }))
    const dialog = await screen.findByRole('dialog')
    const value = within(dialog).getByLabelText(/Valor/)
    await user.clear(value)
    await user.type(value, '[[EFECTIVO')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    expect(await within(dialog).findByText('Ingresa un JSON válido.')).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'PUT', '/api/v1/parametros/par-1')).toHaveLength(0)
  })
})
