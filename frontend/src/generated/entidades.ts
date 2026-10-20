// GENERADO — no editar; modifique el JSON del modelo o la plantilla.

export interface Pais {
  id: string
  codigoIso: string
  nombre: string
  monedaLocalId?: string | null
  idiomaPredeterminadoId?: string | null
  zonaHoraria?: string | null
  habilitado: boolean
}

export interface Moneda {
  id: string
  codigoIso: string
  nombre: string
  simbolo: string
  decimales: number
  formato: string
  reglaRedondeo: string
}

export interface Idioma {
  id: string
  codigoBcp47: string
  nombre: string
  habilitado: boolean
}

export interface TipoCambio {
  id: string
  monedaOrigenId: string
  monedaDestinoId: string
  tasa: number
  vigenteDesdeUtc: string
  origen: string
  actualizadoPorId: string
}

export interface Impuesto {
  id: string
  paisId: string
  categoriaId?: string | null
  nombre: string
  tasa: number
  incluidoEnPrecio: boolean
  vigenteDesde: string
  vigenteHasta?: string | null
}

export interface Categoria {
  id: string
  codigo: string
  nombre: string
  padreId?: string | null
  activo: boolean
}

export interface Bodega {
  id: string
  codigo: string
  nombre: string
  categoriaId?: string | null
  paisId: string
  activo: boolean
}

export interface Proveedor {
  id: string
  razonSocial: string
  paisId: string
  identificacionTributaria?: string | null
  personaContacto?: string | null
  correo?: string | null
  telefono?: string | null
  monedaCompraId: string
  condicionesPago?: string | null
  activo: boolean
}

export interface Cliente {
  id: string
  paisId: string
  clasificacion: string
  nombre: string
  apellidos?: string | null
  tipoDocumento?: string | null
  documento?: string | null
  codigoPostal?: string | null
  direccion?: string | null
  telefono?: string | null
  correo: string
  baseLegal: string
  consentimientoRegistrado: boolean
  consentimientoEnUtc: string
  ultimoContactoEnUtc: string
  activo: boolean
}

export type CrudEntityName = 'pais' | 'moneda' | 'idioma' | 'tipo_cambio' | 'impuesto' | 'categoria' | 'bodega' | 'proveedor' | 'cliente'
