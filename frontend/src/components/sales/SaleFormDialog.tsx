import { useState, type FormEvent } from 'react'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { PageResponse, ReferenceOption } from '../../api/types'
import type { Bodega } from '../../generated/entidades'
import { clientName, type ClienteResumen, type PaisVenta, type RespuestaVenta } from './salesTypes'

interface SaleFormDialogProps {
  paises: PaisVenta[]
  bodegas: Bodega[]
  productos: ReferenceOption[]
  canSearchClients: boolean
  onClose: () => void
  onCreated: (venta: RespuestaVenta) => void
}

interface LineDraft {
  key: number
  productoId: string
  bodegaId: string
  cantidad: string
}

/** Límite de líneas por venta definido en `SolicitudVenta` (backend). */
const MAX_LINES = 100

let lineKey = 0
function emptyLine(): LineDraft {
  lineKey += 1
  return { key: lineKey, productoId: '', bodegaId: '', cantidad: '1' }
}

export function SaleFormDialog({
  paises,
  bodegas,
  productos,
  canSearchClients,
  onClose,
  onCreated,
}: SaleFormDialogProps) {
  const { t } = useTranslation()
  const [paisId, setPaisId] = useState(paises.length === 1 ? paises[0].paisId : '')
  const [cliente, setCliente] = useState<ClienteResumen | null>(null)
  const [clientOptions, setClientOptions] = useState<ClienteResumen[]>([])
  const [searchingClients, setSearchingClients] = useState(false)
  const [lineas, setLineas] = useState<LineDraft[]>([emptyLine()])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const pais = paises.find((candidate) => candidate.paisId === paisId)
  const bodegasDelPais = bodegas.filter((bodega) => bodega.paisId === paisId && bodega.activo)

  function changeCountry(nextPaisId: string) {
    setPaisId(nextPaisId)
    setCliente(null)
    setClientOptions([])
    setLineas((previous) => previous.map((linea) => ({ ...linea, bodegaId: '' })))
  }

  async function searchClients(text: string) {
    if (!paisId || text.trim().length < 2) {
      setClientOptions([])
      return
    }
    setSearchingClients(true)
    try {
      const params = new URLSearchParams({ buscar: text.trim(), size: '20' })
      const page = await apiRequest<PageResponse<ClienteResumen>>(`/api/v1/clientes?${params}`)
      setClientOptions(
        (page?.content ?? []).filter((option) => option.activo && option.paisId === paisId),
      )
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setSearchingClients(false)
    }
  }

  function updateLine(key: number, field: keyof Omit<LineDraft, 'key'>, value: string) {
    setLineas((previous) =>
      previous.map((linea) => (linea.key === key ? { ...linea, [field]: value } : linea)),
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!pais) {
      setError(t('validation.requiredFields'))
      return
    }
    const solicitudLineas = []
    for (const linea of lineas) {
      const cantidad = Number(linea.cantidad)
      if (!linea.productoId || !linea.bodegaId) {
        setError(t('sales.lineIncomplete'))
        return
      }
      if (!Number.isInteger(cantidad) || cantidad <= 0) {
        setError(t('validation.positiveInteger', { field: t('sales.quantity') }))
        return
      }
      solicitudLineas.push({ productoId: linea.productoId, bodegaId: linea.bodegaId, cantidad })
    }
    if (solicitudLineas.length === 0) {
      setError(t('sales.linesRequired'))
      return
    }

    setSaving(true)
    try {
      const creada = await apiRequest<RespuestaVenta>('/api/v1/ventas', {
        method: 'POST',
        body: JSON.stringify({
          clienteId: cliente?.id ?? null,
          paisId: pais.paisId,
          monedaId: pais.monedaId,
          lineas: solicitudLineas,
        }),
      })
      if (creada) onCreated(creada)
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t('sales.newSale')}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}
            {paises.length === 0 && <Alert severity="warning">{t('sales.noCountries')}</Alert>}

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                gap: 2,
              }}
            >
              <FormControl fullWidth size="small" required>
                <InputLabel id="sale-country">{t('common.country')}</InputLabel>
                <Select
                  labelId="sale-country"
                  value={paisId}
                  label={t('common.country')}
                  onChange={(event) => changeCountry(event.target.value)}
                >
                  {paises.map((option) => (
                    <MenuItem key={option.paisId} value={option.paisId}>
                      {option.paisNombre} ({option.paisCodigo})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                size="small"
                label={t('sales.currency')}
                value={pais ? pais.monedaCodigo : ''}
                helperText={t('sales.currencyHelp')}
                slotProps={{ input: { readOnly: true } }}
              />
            </Box>

            {canSearchClients && (
              <Autocomplete
                size="small"
                disabled={!paisId}
                options={clientOptions}
                value={cliente}
                loading={searchingClients}
                filterOptions={(options) => options}
                getOptionLabel={(option) =>
                  option.documento ? `${clientName(option)} — ${option.documento}` : clientName(option)
                }
                isOptionEqualToValue={(option, value) => option.id === value.id}
                onChange={(_, value) => setCliente(value)}
                onInputChange={(_, value, reason) => {
                  if (reason === 'input') void searchClients(value)
                }}
                noOptionsText={t('sales.clientSearchHelp')}
                loadingText={t('common.loading')}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={t('sales.client')}
                    helperText={t('sales.clientOptional')}
                  />
                )}
              />
            )}

            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="subtitle2" color="primary">
                {t('sales.lines')}
              </Typography>
              <Button
                size="small"
                startIcon={<AddRoundedIcon />}
                disabled={lineas.length >= MAX_LINES}
                onClick={() => setLineas((previous) => [...previous, emptyLine()])}
              >
                {t('sales.addLine')}
              </Button>
            </Stack>

            {lineas.map((linea, index) => (
              <Box
                key={linea.key}
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: '2fr 1.4fr 0.8fr auto' },
                  gap: 2,
                  alignItems: 'center',
                }}
              >
                <Autocomplete
                  size="small"
                  options={productos}
                  value={productos.find((producto) => producto.id === linea.productoId) ?? null}
                  getOptionLabel={(option) => `${option.nombre} (${option.codigo})`}
                  isOptionEqualToValue={(option, value) => option.id === value.id}
                  onChange={(_, value) => updateLine(linea.key, 'productoId', value?.id ?? '')}
                  renderInput={(params) => (
                    <TextField {...params} required label={`${t('sales.product')} ${index + 1}`} />
                  )}
                />
                <FormControl fullWidth size="small" required disabled={!paisId}>
                  <InputLabel id={`sale-warehouse-${linea.key}`}>{t('sales.warehouse')}</InputLabel>
                  <Select
                    labelId={`sale-warehouse-${linea.key}`}
                    value={linea.bodegaId}
                    label={t('sales.warehouse')}
                    onChange={(event) => updateLine(linea.key, 'bodegaId', event.target.value)}
                  >
                    {bodegasDelPais.map((bodega) => (
                      <MenuItem key={bodega.id} value={bodega.id}>
                        {bodega.nombre} ({bodega.codigo})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  size="small"
                  required
                  type="number"
                  label={t('sales.quantity')}
                  value={linea.cantidad}
                  onChange={(event) => updateLine(linea.key, 'cantidad', event.target.value)}
                  slotProps={{ htmlInput: { min: 1, step: 1 } }}
                />
                <Tooltip title={t('sales.removeLine')}>
                  <span>
                    <IconButton
                      aria-label={`${t('sales.removeLine')} ${index + 1}`}
                      disabled={lineas.length === 1}
                      onClick={() =>
                        setLineas((previous) => previous.filter((item) => item.key !== linea.key))
                      }
                    >
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              </Box>
            ))}

            <Typography variant="body2" color="text.secondary">
              {t('sales.pendingHelp')}
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>
            {t('crud.cancel')}
          </Button>
          <Button type="submit" variant="contained" disabled={saving || !pais}>
            {saving ? t('crud.saving') : t('sales.calculate')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
