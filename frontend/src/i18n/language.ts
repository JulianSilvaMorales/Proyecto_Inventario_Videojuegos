export type LanguageCode = 'es' | 'en'

export function normalizeLanguage(language?: string | null): LanguageCode {
  return language?.toLowerCase().startsWith('en') ? 'en' : 'es'
}

export function localeFor(language: string): string {
  return normalizeLanguage(language) === 'en' ? 'en-US' : 'es-CO'
}
