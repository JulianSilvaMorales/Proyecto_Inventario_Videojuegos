import { useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { DecimalValue, ReferenceOption } from '../../api/types'
import {
  PARAMETER_SCOPES,
  PARAMETER_TYPES,
  parseLimit,
  parseParameterValue,
  valueToText,
  type ParameterScope,
  type ParameterType,
} from './parameterValues'

export interface Parametro {
  id: string
  clave: string
  valor: unknown
  tipo: ParameterType
  minimo: DecimalValue | null
  maximo: DecimalValue | null
  ambito: ParameterScope
  paisId: string | null
  paisCodigo: string | null
  vigenteDesde: string
  vigenteHasta: string | null
}

interface ParameterFormDialogProps {
  parametro: Parametro | null
  paises: ReferenceOption[]
  onClose: () => void
  onSaved: () => void
}

export function ParameterFormDialog({ parametro, paises, onClose, onSaved }: ParameterFormDialogProps) {
  const { t } = useTranslation()
  const editing = parametro !== null
  const [clave, setClave] = useState(parametro?.clave ?? '')
  const [tipo, setTipo] = useState<ParameterType>(parametro?.tipo ?? 'TEXTO')
  const [valorTexto, setValorTexto] = useState(
    parametro ? valueToText(parametro.tipo, parametro.valor) : '',
  )
  const [valorBooleano, setValorBooleano] = useState(parametro?.valor === true)
  const [minimo, setMinimo] = useState(parametro?.minimo != null ? String(parametro.minimo) : '')
  const [maximo, setMaximo] = useState(parametro?.maximo != null ? String(parametro.maximo) : '')
  const [ambito, setAmbito] = useState<ParameterScope>(parametro?.ambito ?? 'NEGOCIO')
  const [paisId, setPaisId] = useState(parametro?.paisId ?? '')
  const [vigenteDesde, setVigenteDesde] = useState(parametro?.vigenteDesde ?? '')
  const [vigenteHasta, setVigenteHasta] = useState(parametro?.vigenteHasta ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const numeric = tipo === 'ENTERO' || tipo === 'DECIMAL'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!clave.trim() || !vigenteDesde || (ambito === 'PAIS' && !paisId)) {
      setError(t('validation.requiredFields'))
      return
    }
    if (vigenteHasta && vigenteHasta < vigenteDesde) {
      setError(t('validation.dateRange'))
      return
    }
    const valor = parseParameterValue(tipo, valorTexto, valorBooleano)
    if (!valor.ok) {
      setError(t(`settings.invalidValue.${tipo}`))
      return
    }
    const min = numeric ? parseLimit(minimo) : undefined
    const max = numeric ? parseLimit(maximo) : undefined
    if (min === null || max === null) {
      setError(t('settings.invalidLimits'))
      return
    }

    const body = {
      valor: valor.value,
      minimo: min ?? null,
      maximo: max ?? null,
      ambito,
      paisId: ambito === 'PAIS' ? paisId : null,
      vigenteDesde,
      vigenteHasta: vigenteHasta || null,
    }
    setSaving(true)
    try {
      if (parametro) {
        await apiRequest(`/api/v1/parametros/${parametro.id}`, {
          method: 'PUT',
          body: JSON.stringify(body),
        })
      } else {
        await apiRequest('/api/v1/parametros', {
          method: 'POST',
          body: JSON.stringify({ ...body, clave: clave.trim(), tipo }),
        })
      }
      onSaved()
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>{editing ? t('settings.editParameter') : t('settings.newParameter')}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              size="small"
              required
              label={t('settings.key')}
              value={clave}
              disabled={editing}
              onChange={(event) => setClave(event.target.value)}
              helperText={editing ? t('settings.keyLocked') : t('settings.keyHelp')}
              slotProps={{ htmlInput: { maxLength: 100 } }}
            />
            <FormControl size="small" fullWidth required disabled={editing}>
              <InputLabel id="parameter-type">{t('settings.type')}</InputLabel>
              <Select
                labelId="parameter-type"
                value={tipo}
                label={t('settings.type')}
                onChange={(event) => setTipo(event.target.value as ParameterType)}
              >
                {PARAMETER_TYPES.map((type) => (
                  <MenuItem key={type} value={type}>
                    {t(`settings.types.${type}`)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {tipo === 'BOOLEANO' ? (
              <FormControlLabel
                control={
                  <Switch
                    checked={valorBooleano}
                    onChange={(event) => setValorBooleano(event.target.checked)}
                  />
                }
                label={t('settings.value')}
              />
            ) : (
              <TextField
                size="small"
                required
                label={t('settings.value')}
                value={valorTexto}
                multiline={tipo === 'JSON'}
                minRows={tipo === 'JSON' ? 3 : undefined}
                onChange={(event) => setValorTexto(event.target.value)}
                helperText={t(`settings.valueHelp.${tipo}`)}
                slotProps={{
                  htmlInput: {
                    inputMode: tipo === 'ENTERO' ? 'numeric' : tipo === 'DECIMAL' ? 'decimal' : undefined,
                  },
                }}
              />
            )}

            {numeric && (
              <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                <TextField
                  size="small"
                  label={t('settings.minimum')}
                  value={minimo}
                  onChange={(event) => setMinimo(event.target.value)}
                  slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                />
                <TextField
                  size="small"
                  label={t('settings.maximum')}
                  value={maximo}
                  onChange={(event) => setMaximo(event.target.value)}
                  slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                />
              </Box>
            )}

            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
              <FormControl size="small" fullWidth required>
                <InputLabel id="parameter-scope">{t('settings.scope')}</InputLabel>
                <Select
                  labelId="parameter-scope"
                  value={ambito}
                  label={t('settings.scope')}
                  onChange={(event) => {
                    setAmbito(event.target.value as ParameterScope)
                    setPaisId('')
                  }}
                >
                  {PARAMETER_SCOPES.map((scope) => (
                    <MenuItem key={scope} value={scope}>
                      {t(`settings.scopes.${scope}`)}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              {ambito === 'PAIS' && (
                <FormControl size="small" fullWidth required>
                  <InputLabel id="parameter-country">{t('common.country')}</InputLabel>
                  <Select
                    labelId="parameter-country"
                    value={paisId}
                    label={t('common.country')}
                    onChange={(event) => setPaisId(event.target.value)}
                  >
                    {paises.map((pais) => (
                      <MenuItem key={pais.id} value={pais.id}>
                        {pais.nombre} ({pais.codigo})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )}
            </Box>

            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              <TextField
                size="small"
                type="date"
                required
                label={t('settings.validFrom')}
                value={vigenteDesde}
                onChange={(event) => setVigenteDesde(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                size="small"
                type="date"
                label={t('settings.validTo')}
                value={vigenteHasta}
                onChange={(event) => setVigenteHasta(event.target.value)}
                helperText={t('settings.validToHelp')}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>
            {t('crud.cancel')}
          </Button>
          <Button type="submit" variant="contained" disabled={saving}>
            {saving ? t('crud.saving') : t('crud.save')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
