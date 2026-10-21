import generatedMetadata from '../generated/entidades.json'
import type { CrudEntityName } from '../generated/entidades'

export interface CrudFieldMetadata {
  nombre: string
  apiName: string
  tipo: 'uuid' | 'texto' | 'entero' | 'decimal' | 'booleano' | 'fecha' | 'fecha_hora_utc' | 'json'
  typescript: string
  obligatorio: boolean
  soloLectura: boolean
  min: number | null
  max: number | null
  default: unknown
  valores: string[]
  referencia: string | null
  etiquetas: { es: string; en: string }
}

export interface CrudEntityMetadata {
  nombre: CrudEntityName
  modulo: string
  rutaApi: string
  permisosConsulta: string[]
  permisosGestion: string[]
  eliminacion: 'delete' | 'deactivate' | 'none'
  validacionesCondicionales: Array<{
    campo: string
    valor: string | null
    camposRequeridos: string[]
    requiereVerdadero: boolean
    soloCrear: boolean
  }>
  etiquetas: { es: string; en: string }
  campos: CrudFieldMetadata[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isLocalizedLabels(value: unknown): value is { es: string; en: string } {
  return (
    isRecord(value) &&
    typeof value.es === 'string' &&
    typeof value.en === 'string'
  )
}

function isCrudField(value: unknown): value is CrudFieldMetadata {
  return (
    isRecord(value) &&
    typeof value.nombre === 'string' &&
    typeof value.apiName === 'string' &&
    typeof value.tipo === 'string' &&
    typeof value.obligatorio === 'boolean' &&
    typeof value.soloLectura === 'boolean' &&
    Array.isArray(value.valores) &&
    isLocalizedLabels(value.etiquetas)
  )
}

function isCrudEntity(value: unknown): value is CrudEntityMetadata {
  return (
    isRecord(value) &&
    typeof value.nombre === 'string' &&
    typeof value.modulo === 'string' &&
    typeof value.rutaApi === 'string' &&
    Array.isArray(value.permisosConsulta) &&
    Array.isArray(value.permisosGestion) &&
    Array.isArray(value.validacionesCondicionales) &&
    ['delete', 'deactivate', 'none'].includes(String(value.eliminacion)) &&
    isLocalizedLabels(value.etiquetas) &&
    Array.isArray(value.campos) &&
    value.campos.every(isCrudField)
  )
}

function readEntities(value: unknown): CrudEntityMetadata[] {
  if (
    !isRecord(value) ||
    !Array.isArray(value.entidades) ||
    !value.entidades.every(isCrudEntity)
  ) {
    throw new Error('Generated CRUD metadata is invalid.')
  }
  return value.entidades
}

export const crudEntities = readEntities(generatedMetadata)

export function crudEntityByName(name: CrudEntityName) {
  const entity = crudEntities.find((candidate) => candidate.nombre === name)
  if (!entity) {
    throw new Error(`Generated CRUD entity '${name}' was not found.`)
  }
  return entity
}

export function userCanAccessCrudEntity(
  entity: CrudEntityMetadata,
  permissions: readonly string[],
) {
  return entity.permisosConsulta.some((permission) => permissions.includes(permission))
}

export function userCanManageCrudEntity(
  entity: CrudEntityMetadata,
  permissions: readonly string[],
) {
  return entity.permisosGestion.some((permission) => permissions.includes(permission))
}

export function accessibleCrudEntities(module: string, permissions: readonly string[]) {
  return crudEntities.filter(
    (entity) => entity.modulo === module && userCanAccessCrudEntity(entity, permissions),
  )
}
