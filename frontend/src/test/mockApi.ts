import { vi } from 'vitest'

export function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function problem(status: number, detail: string) {
  return jsonResponse({ title: 'Error', detail }, status)
}

type Handler = (url: URL, init: RequestInit | undefined) => Response | unknown | Promise<unknown>

/**
 * Simula `fetch` resolviendo cada petición con la primera ruta cuyo prefijo
 * (`MÉTODO /ruta`) coincide. Las rutas no declaradas responden 404. Una ruta puede
 * devolver una promesa para simular un servidor lento.
 */
export function mockApi(routes: Record<string, Handler>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input))
    const method = (init?.method ?? 'GET').toUpperCase()
    const key = Object.keys(routes)
      .filter((route) => {
        const [routeMethod, routePath] = route.split(' ')
        return routeMethod === method && url.pathname.startsWith(routePath)
      })
      .sort((a, b) => b.length - a.length)[0]
    if (!key) {
      return problem(404, `No mock for ${method} ${url.pathname}`)
    }
    const result = await routes[key](url, init)
    return result instanceof Response ? result : jsonResponse(result)
  })
}

export function requestsTo(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls
    .filter(([input, init]) => {
      const url = new URL(String(input))
      return (init?.method ?? 'GET').toUpperCase() === method && url.pathname === path
    })
    .map(([input, init]) => ({
      url: new URL(String(input)),
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
    }))
}

export function page<T>(content: T[]) {
  return { content, totalElements: content.length }
}
