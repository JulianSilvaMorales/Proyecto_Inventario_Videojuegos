import type { ReferenceOption } from '../api/types'

/** Nombre visible de una referencia; si no está disponible usa el texto alternativo. */
export function referenceName(
  options: readonly ReferenceOption[],
  id: string | null | undefined,
  fallback = '',
): string {
  return options.find((option) => option.id === id)?.nombre ?? fallback
}
