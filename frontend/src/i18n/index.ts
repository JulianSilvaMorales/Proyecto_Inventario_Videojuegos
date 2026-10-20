import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { normalizeLanguage } from './language'
import en from './locales/en.json'
import es from './locales/es.json'

const savedLanguage = localStorage.getItem('inventario:language')
const browserLanguage = navigator.language

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    es: { translation: es },
  },
  lng: normalizeLanguage(savedLanguage ?? browserLanguage),
  fallbackLng: 'es',
  supportedLngs: ['en', 'es'],
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (language) => {
  document.documentElement.lang = normalizeLanguage(language)
})

export default i18n
