import { defineConfig, devices } from '@playwright/test'

/**
 * Las URL y credenciales las define `python tools/cli.py probar`, que levanta
 * una base desechable, el backend y el front antes de ejecutar estas pruebas.
 */
const reportDir = process.env.E2E_REPORT_DIR ?? 'reports'

export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['junit', { outputFile: `${reportDir}/e2e-junit.xml` }],
  ],
  outputDir: `${reportDir}/artefactos`,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], locale: 'es-CO' } }],
})
