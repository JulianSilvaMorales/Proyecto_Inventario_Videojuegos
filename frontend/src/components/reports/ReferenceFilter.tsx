import { FormControl, InputLabel, MenuItem, Select } from '@mui/material'
import { useTranslation } from 'react-i18next'

interface ReferenceFilterProps {
  id: string
  label: string
  value: string
  options: { id: string; label: string }[]
  onChange: (value: string) => void
}

/** Selector de filtro con la opción "Todos"; no se muestra si no hay opciones. */
export function ReferenceFilter({ id, label, value, options, onChange }: ReferenceFilterProps) {
  const { t } = useTranslation()
  if (options.length === 0) {
    return null
  }
  return (
    <FormControl fullWidth size="small">
      <InputLabel id={id}>{label}</InputLabel>
      <Select labelId={id} value={value} label={label} onChange={(event) => onChange(event.target.value)}>
        <MenuItem value="">{t('common.all')}</MenuItem>
        {options.map((option) => (
          <MenuItem key={option.id} value={option.id}>
            {option.label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}
