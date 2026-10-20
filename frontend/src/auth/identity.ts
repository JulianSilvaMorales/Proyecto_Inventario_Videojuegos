export interface AuthIdentity {
  usuarioId: string
  correo: string
  nombre: string
  idioma: string
  roles: string[]
  permisos: string[]
  paises: string[]
}

export interface LoginResponse {
  tipoToken: string
  token: string
  venceEnUtc: string
  usuario: AuthIdentity
}

export function isAuthIdentity(value: unknown): value is AuthIdentity {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const identity = value as Partial<AuthIdentity>
  return (
    typeof identity.usuarioId === 'string' &&
    typeof identity.correo === 'string' &&
    typeof identity.nombre === 'string' &&
    typeof identity.idioma === 'string' &&
    Array.isArray(identity.roles) &&
    Array.isArray(identity.permisos) &&
    Array.isArray(identity.paises)
  )
}

export function isLoginResponse(value: unknown): value is LoginResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const response = value as Partial<LoginResponse>
  return (
    response.tipoToken === 'Bearer' &&
    typeof response.token === 'string' &&
    typeof response.venceEnUtc === 'string' &&
    isAuthIdentity(response.usuario)
  )
}
