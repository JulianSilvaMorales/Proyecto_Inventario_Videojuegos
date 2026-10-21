import type { TFunction } from 'i18next'
import { ApiError } from './apiClient'

/** Mensaje para el usuario: el detalle localizado del servidor o un texto traducido. */
export function apiErrorMessage(reason: unknown, t: TFunction): string {
  if (reason instanceof ApiError && reason.status === 0) {
    return t('errors.network')
  }
  if (reason instanceof Error && reason.message) {
    return reason.message
  }
  return t('errors.unexpected')
}
