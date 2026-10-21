import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { mockApi, page, problem, requestsTo } from '../../test/mockApi'
import { CatalogPage } from './CatalogPage'

const producto = {
  id: 'prod-1',
  codigoInterno: 'PRD-1',
  tipoProductoId: 'tipo-1',
  categoriaId: 'cat-1',
  subcategoriaId: 'cat-2',
  atributos: { plataforma: 'PS5' },
  activo: true,
}

function routes(overrides: Record<string, () => unknown> = {}) {
  return {
    'GET /api/v1/catalogo/tipos-producto': () =>
      page([{ id: 'tipo-1', codigo: 'JUEGO', nombre: 'Videojuego', activo: true }]),
    'GET /api/v1/catalogo/categorias': () =>
      page([
        { id: 'cat-1', codigo: 'JUE', nombre: 'Juegos', padreId: null, activo: true },
        { id: 'cat-2', codigo: 'ACC', nombre: 'Acción', padreId: 'cat-1', activo: true },
      ]),
    'GET /api/v1/referencias/producto': () => [{ id: 'prod-1', codigo: 'PRD-1', nombre: 'Juego de prueba' }],
    'GET /api/v1/referencias/idioma': () => [
      { id: 'es', codigo: 'es', nombre: 'Español' },
      { id: 'en', codigo: 'en', nombre: 'English' },
    ],
    'GET /api/v1/referencias/moneda': () => [{ id: 'cop', codigo: 'COP', nombre: 'Peso colombiano' }],
    'GET /api/v1/catalogo/definiciones-atributos': () =>
      page([{ id: 'def-1', nombre: 'plataforma', tipoDato: 'TEXTO', obligatorio: true, opciones: null }]),
    'GET /api/v1/catalogo/productos/prod-1': () => ({
      producto,
      traducciones: [{ id: 'tr-es', idiomaId: 'es', nombre: 'Juego de prueba', descripcion: null }],
      precios: [
        {
          id: 'pr-1',
          monedaId: 'cop',
          monedaCodigo: 'COP',
          importe: '250000.0000',
          vigenteDesde: '2026-01-01',
          vigenteHasta: null,
        },
      ],
    }),
    'GET /api/v1/catalogo/productos': () => page([producto]),
    'PUT /api/v1/catalogo/productos/prod-1': () => ({}),
    'PUT /api/v1/catalogo/productos/prod-1/traducciones/tr-es': () => ({}),
    'POST /api/v1/catalogo/productos/prod-1/traducciones': () => ({
      id: 'tr-en',
      idiomaId: 'en',
      nombre: 'Test game',
      descripcion: null,
    }),
    ...overrides,
  }
}

async function openEditor() {
  const user = userEvent.setup()
  render(
    <CatalogPage
      permissions={['CATALOGO_CONSULTAR', 'PRODUCTOS_GESTIONAR']}
      canOpenCategories
      onOpenCrudEntity={vi.fn()}
    />,
  )
  expect(await screen.findByText('Juego de prueba')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Editar PRD-1' }))
  const dialog = await screen.findByRole('dialog')
  await within(dialog).findByDisplayValue('Juego de prueba')
  return { user, dialog }
}

describe('CatalogPage', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  beforeEach(async () => {
    await i18n.changeLanguage('es')
    sessionStorage.clear()
  })

  it('updates existing translations with PUT and creates missing ones with POST', async () => {
    const fetchMock = mockApi(routes())
    const { user, dialog } = await openEditor()

    // Los precios registrados se muestran pero no se reenvían.
    expect(within(dialog).getByRole('table', { name: 'Precios registrados' })).toBeInTheDocument()
    const nombres = within(dialog).getAllByLabelText('Nombre')
    await user.clear(nombres[0])
    await user.type(nombres[0], 'Juego renombrado')
    await user.type(nombres[1], 'Test game')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(requestsTo(fetchMock, 'PUT', '/api/v1/catalogo/productos/prod-1/traducciones/tr-es')[0].body)
      .toEqual({ idiomaId: 'es', nombre: 'Juego renombrado', descripcion: null })
    expect(requestsTo(fetchMock, 'POST', '/api/v1/catalogo/productos/prod-1/traducciones')[0].body)
      .toEqual({ idiomaId: 'en', nombre: 'Test game', descripcion: null })
    expect(requestsTo(fetchMock, 'POST', '/api/v1/catalogo/productos/prod-1/precios')).toHaveLength(0)
    expect(screen.getByText('Producto guardado.')).toBeInTheDocument()
  })

  it('shows save errors instead of reporting success', async () => {
    mockApi(
      routes({
        'PUT /api/v1/catalogo/productos/prod-1/traducciones/tr-es': () =>
          problem(409, 'Ya existe una traducción para ese idioma.'),
      }),
    )
    const { user, dialog } = await openEditor()

    const nombres = within(dialog).getAllByLabelText('Nombre')
    await user.type(nombres[0], ' 2')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    expect(
      await within(dialog).findByText('Ya existe una traducción para ese idioma.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Producto guardado.')).not.toBeInTheDocument()
  })

  it('sends new prices as decimal text', async () => {
    const fetchMock = mockApi({
      ...routes(),
      'POST /api/v1/catalogo/productos/prod-1/precios': () => ({
        id: 'pr-2',
        monedaId: 'cop',
        monedaCodigo: 'COP',
        importe: '1999999.9900',
        vigenteDesde: '2026-11-01',
        vigenteHasta: null,
      }),
    })
    const { user, dialog } = await openEditor()

    await user.click(within(dialog).getByRole('button', { name: 'Agregar precio' }))
    await user.click(within(dialog).getByRole('combobox', { name: 'Moneda' }))
    await user.click(await screen.findByRole('option', { name: /COP/ }))
    await user.type(within(dialog).getByLabelText(/Importe/), '1999999,99')
    await user.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/v1/catalogo/productos/prod-1/precios')).toHaveLength(1),
    )
    expect(requestsTo(fetchMock, 'POST', '/api/v1/catalogo/productos/prod-1/precios')[0].body).toMatchObject({
      monedaId: 'cop',
      importe: '1999999.99',
    })
  })
})
