import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, AUTH_TOKEN_KEY, ApiError } from './apiClient'

afterEach(() => {
  sessionStorage.clear()
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('apiRequest', () => {
  it('sends the current token and stores the refreshed token', async () => {
    sessionStorage.setItem(AUTH_TOKEN_KEY, 'old-token')
    localStorage.setItem('inventario:language', 'en')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          'X-Auth-Token': 'refreshed-token',
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest<{ ok: boolean }>('/api/v1/private')).resolves.toEqual({
      ok: true,
    })

    const [, request] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(request.headers)
    expect(headers.get('Authorization')).toBe('Bearer old-token')
    expect(headers.get('Accept-Language')).toBe('en')
    expect(sessionStorage.getItem(AUTH_TOKEN_KEY)).toBe('refreshed-token')
  })

  it('surfaces localized problem details as ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ title: 'Forbidden', detail: 'Country access denied.' }),
          {
            status: 403,
            headers: { 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )

    await expect(apiRequest('/api/v1/private')).rejects.toMatchObject({
      status: 403,
      message: 'Country access denied.',
      title: 'Forbidden',
    } satisfies Partial<ApiError>)
  })
})
