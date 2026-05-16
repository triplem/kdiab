import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en.json'
import de from './locales/de.json'

const localeMap: Record<string, string> = { en: 'en-GB', de: 'de-DE' }
const detectedLang = navigator.language.split('-')[0]
// Upgrade to a regional locale so date/time inputs render in 24h / local format
document.documentElement.lang = localeMap[detectedLang] ?? navigator.language

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    de: { translation: de },
  },
  lng: detectedLang,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

const observer = new MutationObserver(() => {
  const lang = document.documentElement.lang.split('-')[0]
  if (lang && lang !== i18n.language) {
    void i18n.changeLanguage(lang)
  }
})
observer.observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] })

export default i18n
