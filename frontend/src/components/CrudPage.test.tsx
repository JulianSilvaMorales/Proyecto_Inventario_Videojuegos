import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../i18n'
import { crudEntityByName } from '../crud/metadata'
import { CrudPage } from './CrudPage'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('CrudPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('loads and renders paged records from the configured API route', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      jsonResponse({
        content: [
          {
            id: 'currency-id',
            codigoIso: 'COP',
            nombre: 'Peso colombiano',
            simbolo: '$',
            decimales: 2,
            formato: 'es-CO',
            reglaRedondeo: 'HALF_UP',
          },
        ],
        totalElements: 1,
      }),
    )
    render(
      <CrudPage
        entity={crudEntityByName('moneda')}
        permissions={['PARAMETROS_GESTIONAR']}
        onBack={vi.fn()}
      />,
    )

    expect(await screen.findByText('Peso colombiano')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/configuracion/monedas?page=0&size=20',
      expect.any(Object),
    )
    await user.clear(screen.getByLabelText('Buscar'))
    await user.type(screen.getByLabelText('Buscar'), 'COP')
    await user.click(screen.getAllByRole('button', { name: 'Buscar' })[0])
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[1]?.[0]).toContain('buscar=COP')
  })

  it('validates required fields and submits the metadata-driven form', async () => {
    const user = userEvent.setup()
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ content: [], totalElements: 0 }))
      .mockResolvedValueOnce(jsonResponse({ id: 'new-currency' }, 201))
      .mockResolvedValueOnce(jsonResponse({ content: [], totalElements: 0 }))
    render(
      <CrudPage
        entity={crudEntityByName('moneda')}
        permissions={['PARAMETROS_GESTIONAR']}
        onBack={vi.fn()}
      />,
    )

    await screen.findByText('No hay registros para mostrar.')
    await user.click(screen.getByRole('button', { name: 'Crear' }))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findAllByText('Este campo es obligatorio.')).not.toHaveLength(0)

    await user.type(screen.getByLabelText(/Código ISO/), 'COP')
    await user.type(screen.getByLabelText(/Nombre/), 'Peso colombiano')
    await user.type(screen.getByLabelText(/Símbolo/), '$')
    await user.type(screen.getByLabelText(/Decimales/), '2')
    await user.type(screen.getByLabelText(/Formato/), 'es-CO')
    await user.type(screen.getByLabelText(/Regla de redondeo/), 'HALF_UP')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))
    const createCall = fetchMock.mock.calls[1]
    expect(createCall?.[1]?.method).toBe('POST')
    expect(JSON.parse(String(createCall?.[1]?.body))).toMatchObject({
      codigoIso: 'COP',
      nombre: 'Peso colombiano',
      simbolo: '$',
      decimales: 2,
      formato: 'es-CO',
      reglaRedondeo: 'HALF_UP',
    })
  })

  it('hides mutation controls when the user only has read permission', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ content: [], totalElements: 0 }),
    )
    render(
      <CrudPage
        entity={crudEntityByName('bodega')}
        permissions={['INVENTARIO_CONSULTAR']}
        onBack={vi.fn()}
      />,
    )

    await screen.findByText('No hay registros para mostrar.')
    expect(screen.queryByRole('button', { name: 'Crear' })).not.toBeInTheDocument()
    expect(screen.queryByText('Acciones')).not.toBeInTheDocument()
  })

  it('validates consent and classification-specific customer fields', async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      if (String(input).includes('/api/v1/referencias/pais')) {
        return jsonResponse([
          { id: 'country-id', codigo: 'CO', nombre: 'Colombia' },
        ])
      }
      return jsonResponse({ content: [], totalElements: 0 })
    })
    render(
      <CrudPage
        entity={crudEntityByName('cliente')}
        permissions={['CLIENTES_GESTIONAR']}
        onBack={vi.fn()}
      />,
    )

    await screen.findByText('No hay registros para mostrar.')
    await user.click(screen.getByRole('button', { name: 'Crear' }))
    await user.click(screen.getByLabelText(/Clasificación/))
    await user.click(await screen.findByRole('option', { name: 'FRECUENTE' }))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Debes registrar la autorización del cliente.')).toBeInTheDocument()
    expect(screen.getByLabelText(/Apellidos/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/Documento/)).toHaveAttribute('aria-invalid', 'false')
  })
})
