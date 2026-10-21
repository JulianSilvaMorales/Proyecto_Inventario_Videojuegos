import { expect, request, test, type APIRequestContext, type Page } from '@playwright/test'

/**
 * Flujo de la guía (E8): iniciar sesión → crear producto → vincularlo al
 * proveedor y crear y enviar la orden de compra (pantalla de Compras) →
 * registrar la entrada → vender → verificar saldo y movimiento. Los datos
 * maestros que la interfaz administra con el CRUD genérico (tipo, categorías y
 * proveedor) se preparan por API para que la prueba sea corta.
 */

const apiUrl = process.env.E2E_API_URL ?? 'http://127.0.0.1:8080'
const email = process.env.E2E_ADMIN_EMAIL ?? ''
const password = process.env.E2E_ADMIN_PASSWORD ?? ''
const sufijo = Date.now().toString(36).toUpperCase()
const nombreProducto = `Consola E2E ${sufijo}`

interface Referencia {
  id: string
  codigo: string
  nombre: string
}

async function api(): Promise<APIRequestContext> {
  const anonimo = await request.newContext({ baseURL: apiUrl })
  const login = await anonimo.post('/api/v1/auth/login', { data: { correo: email, contrasena: password } })
  expect(login.ok(), await login.text()).toBeTruthy()
  const { token } = (await login.json()) as { token: string }
  await anonimo.dispose()
  return request.newContext({
    baseURL: apiUrl,
    extraHTTPHeaders: { Authorization: `Bearer ${token}`, 'Accept-Language': 'es' },
  })
}

async function post<T>(contexto: APIRequestContext, ruta: string, data?: unknown): Promise<T> {
  const respuesta = await contexto.post(ruta, { data })
  expect(respuesta.ok(), `${ruta}: ${await respuesta.text()}`).toBeTruthy()
  const cuerpo = await respuesta.text()
  return (cuerpo ? JSON.parse(cuerpo) : undefined) as T
}

async function referencia(contexto: APIRequestContext, entidad: string, codigo: string) {
  const respuesta = await contexto.get(`/api/v1/referencias/${entidad}`)
  const opciones = (await respuesta.json()) as Referencia[]
  const opcion = opciones.find((item) => item.codigo === codigo || item.nombre === codigo)
  expect(opcion, `${entidad} ${codigo}`).toBeDefined()
  return opcion as Referencia
}

async function elegir(page: Page, combo: string, opcion: string | RegExp) {
  await page.getByRole('combobox', { name: combo, exact: true }).click()
  await page.getByRole('option', { name: opcion }).click()
}

test('producto → orden de compra → entrada → venta en COP → saldo y movimiento', async ({ page }) => {
  expect(email, 'Defina E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD').not.toBe('')
  const contexto = await api()

  // Preparación por API: tipo, categoría, subcategoría y proveedor oficial.
  await post(contexto, '/api/v1/catalogo/tipos-producto', {
    codigo: `E2E-T-${sufijo}`,
    nombre: `Videoconsola ${sufijo}`,
    activo: true,
  })
  const categoria = await post<{ id: string }>(contexto, '/api/v1/catalogo/categorias', {
    codigo: `E2E-C-${sufijo}`,
    nombre: `Consolas ${sufijo}`,
    padreId: null,
    activo: true,
  })
  await post(contexto, '/api/v1/catalogo/categorias', {
    codigo: `E2E-S-${sufijo}`,
    nombre: `Sobremesa ${sufijo}`,
    padreId: categoria.id,
    activo: true,
  })
  const colombia = await referencia(contexto, 'pais', 'CO')
  const cop = await referencia(contexto, 'moneda', 'COP')
  await post(contexto, '/api/v1/proveedores', {
    razonSocial: `Proveedor E2E ${sufijo}`,
    paisId: colombia.id,
    monedaCompraId: cop.id,
    activo: true,
  })

  // 1. Inicio de sesión.
  await page.goto('/')
  await page.getByLabel('Correo electrónico').fill(email)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('heading', { level: 4 })).toBeVisible()

  // 2. Crear el producto desde el catálogo.
  await page.getByRole('button', { name: 'Catálogo' }).click()
  await page.getByRole('button', { name: 'Nuevo producto' }).click()
  const formulario = page.getByRole('dialog')
  await elegir(page, 'Tipo de producto', `Videoconsola ${sufijo}`)
  await elegir(page, 'Categoría', `Consolas ${sufijo}`)
  await elegir(page, 'Subcategoría', `Sobremesa ${sufijo}`)
  for (const nombre of await formulario.getByLabel('Nombre', { exact: true }).all()) {
    await nombre.fill(nombreProducto)
  }
  await elegir(page, 'Moneda', /COP/)
  await formulario.getByLabel(/Importe/).fill('150000')
  await formulario.getByRole('button', { name: 'Guardar' }).click()
  await expect(page.getByText('Producto guardado.')).toBeVisible()
  await expect(page.getByText(nombreProducto)).toBeVisible()
  const producto = await referencia(contexto, 'producto', nombreProducto)
  const nombreProveedor = `Proveedor E2E ${sufijo}`

  // 3. Compras: vincular el producto al proveedor y crear y enviar la orden.
  await page.getByRole('button', { name: 'Compras' }).click()
  await page.getByRole('tab', { name: 'Productos por proveedor' }).click()
  await elegir(page, 'Proveedor', nombreProveedor)
  await elegir(page, 'Producto a vincular', new RegExp(nombreProducto))
  await page.getByRole('button', { name: 'Vincular producto' }).click()
  await expect(page.getByText(/quedó vinculado como producto oficial/)).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: nombreProducto })).toBeVisible()

  await page.getByRole('tab', { name: 'Órdenes de compra' }).click()
  await page.getByRole('button', { name: 'Nueva orden de compra' }).click()
  const nuevaOrden = page.getByRole('dialog')
  await elegir(page, 'Proveedor', nombreProveedor)
  await expect(nuevaOrden.getByRole('textbox', { name: 'Tipo de cambio' })).toHaveValue('1')
  await elegir(page, 'Producto de la línea 1', new RegExp(nombreProducto))
  await nuevaOrden.getByRole('spinbutton', { name: 'Cantidad de la línea 1' }).fill('5')
  await nuevaOrden.getByRole('textbox', { name: /Costo unitario de la línea 1/ }).fill('90000')
  await nuevaOrden.getByRole('button', { name: 'Crear borrador' }).click()
  const detalleOrden = page.getByRole('dialog', { name: /^Orden OC-/ })
  await expect(detalleOrden.getByText('Borrador', { exact: true })).toBeVisible()
  const numeroOrden = ((await detalleOrden.getByRole('heading').textContent()) ?? '').replace(
    /^Orden /,
    '',
  )
  expect(numeroOrden).toMatch(/^OC-/)
  await detalleOrden.getByRole('button', { name: 'Enviar al proveedor' }).click()
  await expect(detalleOrden.getByText('Enviada al proveedor', { exact: true })).toBeVisible()
  await detalleOrden.getByRole('button', { name: 'Cerrar' }).click()

  // 4. Registrar la entrada de inventario.
  await page.getByRole('button', { name: 'Inventario' }).click()
  await page.getByRole('tab', { name: 'Entradas de compra' }).click()
  await page.getByRole('button', { name: 'Registrar entrada' }).click()
  const entrada = page.getByRole('dialog')
  await elegir(page, 'Orden de compra (enviada o parcial)', new RegExp(numeroOrden))
  await expect(entrada.getByRole('combobox', { name: 'Bodega' })).toHaveText(/GENERAL/)
  await entrada.getByLabel(/Documento o factura/).fill(`FAC-${sufijo}`)
  await entrada.getByRole('button', { name: 'Guardar' }).click()
  await expect(page.getByText('Entrada de inventario registrada.')).toBeVisible()

  // 5. Vender 2 unidades en Colombia (COP, IVA 19 % no incluido) y confirmar el pago.
  await page.getByRole('button', { name: 'Ventas' }).click()
  await page.getByRole('button', { name: 'Nueva venta' }).click()
  await page.getByRole('combobox', { name: /Producto 1/ }).fill(nombreProducto)
  await page.getByRole('option', { name: new RegExp(nombreProducto) }).click()
  await elegir(page, 'Bodega', /GENERAL/)
  await page.getByRole('dialog').getByLabel(/Cantidad/).fill('2')
  await page.getByRole('button', { name: 'Calcular venta' }).click()
  const detalle = page.getByRole('dialog', { name: /^Venta / })
  await expect(detalle.getByTestId('sale-total')).toHaveText(/357\.000/)
  await expect(detalle.getByText(/IVA/).first()).toBeVisible()
  await elegir(page, 'Medio de pago', 'Efectivo')
  await detalle.getByRole('button', { name: 'Confirmar pago' }).click()
  await expect(page.getByText(/Pago confirmado/)).toBeVisible()
  await detalle.getByRole('button', { name: 'Ver recibo' }).click()
  await expect(page.getByText(/Recibo simulado/)).toBeVisible()
  await page.getByRole('button', { name: 'Cerrar' }).click()

  // 6. Verificar el saldo (5 recibidas − 2 vendidas) y el movimiento de venta.
  await page.getByRole('button', { name: 'Inventario' }).click()
  const filaSaldo = page.getByRole('row').filter({ hasText: nombreProducto })
  await expect(filaSaldo).toContainText('3')
  await page.getByRole('tab', { name: 'Movimientos' }).click()
  await expect(
    page.getByRole('row').filter({ hasText: nombreProducto }).filter({ hasText: 'Salida por venta' }),
  ).toContainText('-2')

  const saldos = await contexto.get(`/api/v1/inventario/saldos?productoId=${producto.id}`)
  const pagina = (await saldos.json()) as { content: { cantidad: number }[] }
  expect(pagina.content.map((saldo) => saldo.cantidad)).toEqual([3])
  await contexto.dispose()
})
