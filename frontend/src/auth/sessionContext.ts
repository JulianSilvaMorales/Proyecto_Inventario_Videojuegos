import { createContext } from 'react'
import type { AuthIdentity } from './identity'

export interface SessionContextValue {
  user: AuthIdentity | null
  login: (email: string, password: string) => Promise<AuthIdentity>
  logout: () => Promise<void>
}

export const SessionContext = createContext<SessionContextValue | null>(null)
