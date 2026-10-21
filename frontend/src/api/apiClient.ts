export const AUTH_TOKEN_KEY = 'inventario:token'
export const SESSION_EXPIRED_EVENT = 'inventario:session-expired'

const apiBaseUrl = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080')
  .replace(/\/+$/, '')

interface ProblemDetails {
  title?: string
  detail?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly title?: string

  constructor(
    status: number,
    message: string,
    title?: string,
  ) {
    super(message)
    this.status = status
    this.title = title
    this.name = 'ApiError'
  }
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY)
  const language =
    localStorage.getItem('inventario:language') ?? navigator.language

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (!headers.has('Accept-Language')) {
    headers.set('Accept-Language', language.toLowerCase().startsWith('en') ? 'en' : 'es')
  }
  if (token && path !== '/api/v1/auth/login') {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, '')
  }

  const refreshedToken = response.headers.get('X-Auth-Token')
  if (refreshedToken) {
    sessionStorage.setItem(AUTH_TOKEN_KEY, refreshedToken)
  }
  if (response.status === 401) {
    sessionStorage.removeItem(AUTH_TOKEN_KEY)
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
  }

  if (!response.ok) {
    const body = await response.text()
    let problem: ProblemDetails = {}
    if (body) {
      try {
        problem = JSON.parse(body) as ProblemDetails
      } catch {
        // Un cuerpo que no es Problem Details (por ejemplo, HTML de un proxy) no se muestra.
        problem = {}
      }
    }
    throw new ApiError(
      response.status,
      problem.detail ?? '',
      problem.title,
    )
  }
  return response
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T | undefined> {
  const response = await send(path, init)

  if (response.status === 204) {
    return undefined
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new ApiError(response.status, '')
  }
  return (await response.json()) as T
}

/** Descarga un archivo autenticado (por ejemplo, un CSV) y lo entrega al navegador. */
export async function apiDownload(path: string, fallbackFileName: string): Promise<void> {
  const response = await send(path, {})
  const disposition = response.headers.get('content-disposition') ?? ''
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  const fileName = match ? decodeURIComponent(match[1]) : fallbackFileName
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
