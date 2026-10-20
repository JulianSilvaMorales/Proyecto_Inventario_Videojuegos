import { useCallback, useEffect, useState, type PropsWithChildren } from 'react'
import {
  ApiError,
  apiRequest,
  AUTH_TOKEN_KEY,
  SESSION_EXPIRED_EVENT,
} from '../api/apiClient'
import { isAuthIdentity, isLoginResponse, type AuthIdentity } from './identity'
import { SessionContext } from './sessionContext'

const IDENTITY_KEY = 'inventario:identity'

function clearStoredSession() {
  sessionStorage.removeItem(AUTH_TOKEN_KEY)
  sessionStorage.removeItem(IDENTITY_KEY)
}

function restoreIdentity(): AuthIdentity | null {
  const token = sessionStorage.getItem(AUTH_TOKEN_KEY)
  const identity = sessionStorage.getItem(IDENTITY_KEY)
  if (!token || !identity) {
    clearStoredSession()
    return null
  }

  try {
    const parsed: unknown = JSON.parse(identity)
    if (isAuthIdentity(parsed)) {
      return parsed
    }
  } catch {
    clearStoredSession()
    return null
  }
  clearStoredSession()
  return null
}

export function SessionProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AuthIdentity | null>(restoreIdentity)

  useEffect(() => {
    const expireSession = () => {
      clearStoredSession()
      setUser(null)
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, expireSession)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expireSession)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const response = await apiRequest<unknown>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ correo: email, contrasena: password }),
    })
    if (!isLoginResponse(response)) {
      throw new ApiError(502, 'The login response was invalid.')
    }

    sessionStorage.setItem(AUTH_TOKEN_KEY, response.token)
    sessionStorage.setItem(IDENTITY_KEY, JSON.stringify(response.usuario))
    setUser(response.usuario)
    return response.usuario
  }, [])

  const logout = useCallback(async () => {
    try {
      if (sessionStorage.getItem(AUTH_TOKEN_KEY)) {
        await apiRequest<unknown>('/api/v1/auth/logout', { method: 'POST' })
      }
    } finally {
      clearStoredSession()
      setUser(null)
    }
  }, [])

  return (
    <SessionContext.Provider value={{ user, login, logout }}>
      {children}
    </SessionContext.Provider>
  )
}
