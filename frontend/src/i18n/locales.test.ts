import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import es from './locales/es.json'

type Messages = { [key: string]: string | Messages }

function flatten(messages: Messages, prefix = ''): string[] {
  return Object.entries(messages).flatMap(([key, value]) =>
    typeof value === 'string' ? [`${prefix}${key}`] : flatten(value, `${prefix}${key}.`),
  )
}

// Código fuente de la interfaz (sin pruebas), cargado como texto por Vite.
const sources = Object.entries(
  import.meta.glob<string>('../**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true }),
)
  .filter(([path]) => !path.includes('.test.'))
  .map(([, source]) => source)
const esKeys = new Set(flatten(es as Messages))
const enKeys = new Set(flatten(en as Messages))

describe('translations', () => {
  it('have the same keys in Spanish and English', () => {
    expect([...esKeys].filter((key) => !enKeys.has(key))).toEqual([])
    expect([...enKeys].filter((key) => !esKeys.has(key))).toEqual([])
  })

  it('define every static key used by the interface', () => {
    const used = new Set<string>()
    for (const source of sources) {
      for (const match of source.matchAll(/\bt\(\s*'([^']+)'/g)) {
        used.add(match[1])
      }
    }
    expect(used.size).toBeGreaterThan(100)
    expect([...used].filter((key) => !esKeys.has(key))).toEqual([])
  })
})
