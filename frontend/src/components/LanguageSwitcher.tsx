import { FormControl, MenuItem, Select } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { normalizeLanguage } from '../i18n/language'

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation()
  const language = normalizeLanguage(i18n.resolvedLanguage)

  function changeLanguage(value: string) {
    const nextLanguage = normalizeLanguage(value)
    localStorage.setItem('inventario:language', nextLanguage)
    void i18n.changeLanguage(nextLanguage)
  }

  return (
    <FormControl size="small" sx={{ minWidth: 118 }}>
      <Select
        value={language}
        onChange={(event) => changeLanguage(event.target.value)}
        inputProps={{ 'aria-label': t('common.language') }}
        sx={{
          color: 'inherit',
          '.MuiOutlinedInput-notchedOutline': { borderColor: 'divider' },
          '.MuiSvgIcon-root': { color: 'inherit' },
        }}
      >
        <MenuItem value="es">{t('common.spanish')}</MenuItem>
        <MenuItem value="en">{t('common.english')}</MenuItem>
      </Select>
    </FormControl>
  )
}
