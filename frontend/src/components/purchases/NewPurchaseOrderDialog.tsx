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
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Proveedor } from '../../generated/entidades'
import { isPositiveDecimal, parseDecimal } from '../../utils/decimal'
import type { DetalleOrdenCompra } from './types'

interface NewPurchaseOrderDialogProps {
  proveedores: Proveedor[]
  monedas: ReferenceOption[]
  monedaBaseCodigo: string | null
  onClose: () => void
  onCreated: (orden: DetalleOrdenCompra) => void
}

interface LineDraft {
  key: number
  productoId: string
  cantidad: string
  costo: string
}

// Mismos límites que valida el backend (SolicitudOrdenCompra / SolicitudLineaOrdenCompra).
const RATE_SCALE = 8
const RATE_INTEGER_DIGITS = 11
const COST_SCALE = 4
const COST_INTEGER_DIGITS = 15

let nextLineKey = 1

function emptyLine(): LineDraft {
  nextLineKey += 1
  return { key: nextLineKey, productoId: '', cantidad: '1', costo: '' }
}

export function NewPurchaseOrderDialog({
  proveedores,
  monedas,
  monedaBaseCodigo,
  onClose,
  onCreated,
}: NewPurchaseOrderDialogProps) {
  const { t } = useTranslation()
  const [proveedorId, setProveedorId] = useState('')
  const [tipoCambio, setTipoCambio] = useState('')
  const [lineas, setLineas] = useState<LineDraft[]>(() => [emptyLine()])
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const productosQuery = useApiQuery<ReferenceOption[]>(
    proveedorId ? `/api/v1/proveedores/${proveedorId}/productos` : null,
  )
  const productos = productosQuery.data ?? []
  const proveedor = proveedores.find((item) => item.id === proveedorId)
  const moneda = monedas.find((item) => item.id === proveedor?.monedaCompraId)
  // En la moneda base la tasa es exactamente 1 (lo exige el backend).
  const monedaBase = moneda?.codigo === monedaBaseCodigo

  function selectSupplier(id: string) {
    setProveedorId(id)
    setTipoCambio('')
    setLineas([emptyLine()])
    setFormError('')
  }

  function updateLine(key: number, field: keyof Omit<LineDraft, 'key'>, value: string) {
    setLineas((previous) =>
      previous.map((linea) => (linea.key === key ? { ...linea, [field]: value } : linea)),
    )
  }

  function validate() {
    if (!proveedor) {
      setFormError(t('validation.requiredFields'))
      return null
    }
    const tasa = monedaBase ? '1' : parseDecimal(tipoCambio, RATE_SCALE, RATE_INTEGER_DIGITS)
    if (tasa === null || !isPositiveDecimal(tasa)) {
      setFormError(t('validation.positiveDecimal', { field: t('purchases.exchangeRate') }))
      return null
    }
    const vistos = new Set<string>()
    const resultado = []
    for (const [index, linea] of lineas.entries()) {
      const numero = index + 1
      if (!linea.productoId) {
        setFormError(t('purchases.lineProductRequired', { line: numero }))
        return null
      }
      if (vistos.has(linea.productoId)) {
        setFormError(t('purchases.duplicateProduct', { line: numero }))
        return null
      }
      vistos.add(linea.productoId)
      const cantidad = Number(linea.cantidad.trim())
      if (!linea.cantidad.trim() || !Number.isInteger(cantidad) || cantidad <= 0) {
        setFormError(
          t('validation.positiveInteger', { field: `${t('purchases.quantity')} (${numero})` }),
        )
        return null
      }
      const costo = parseDecimal(linea.costo, COST_SCALE, COST_INTEGER_DIGITS)
      if (costo === null) {
        setFormError(t('purchases.invalidCost', { line: numero }))
        return null
      }
      resultado.push({ productoId: linea.productoId, cantidadPedida: cantidad, costoUnitario: costo })
    }
    return { tasa, lineas: resultado }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError('')
    const valid = validate()
    if (!valid) return

    setSaving(true)
    try {
      const creada = await apiRequest<DetalleOrdenCompra>('/api/v1/compras/ordenes', {
        method: 'POST',
        body: JSON.stringify({
          proveedorId,
          tipoCambio: valid.tasa,
          lineas: valid.lineas,
        }),
      })
      if (creada) onCreated(creada)
    } catch (reason) {
      setFormError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  const noLinkedProducts =
    Boolean(proveedorId) && !productosQuery.loading && !productosQuery.error && productos.length === 0

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>{t('purchases.newOrder')}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <Typography variant="body2" color="text.secondary">
              {t('purchases.newOrderHelp')}
            </Typography>
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1fr' },
                gap: 2,
              }}
            >
              <FormControl fullWidth size="small" required>
                <InputLabel id="new-order-supplier">{t('purchases.supplier')}</InputLabel>
                <Select
                  labelId="new-order-supplier"
                  value={proveedorId}
                  label={t('purchases.supplier')}
                  onChange={(event) => selectSupplier(event.target.value)}
                >
                  {proveedores.map((item) => (
                    <MenuItem key={item.id} value={item.id}>
                      {item.razonSocial}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                size="small"
                label={t('purchases.currency')}
                value={moneda ? `${moneda.codigo} — ${moneda.nombre}` : ''}
                slotProps={{ input: { readOnly: true } }}
              />
              <TextField
                size="small"
                required
                label={t('purchases.exchangeRate')}
                value={monedaBase ? '1' : tipoCambio}
                disabled={!proveedor || monedaBase}
                onChange={(event) => setTipoCambio(event.target.value)}
                helperText={
                  monedaBase
                    ? t('purchases.baseCurrencyRate', { currency: monedaBaseCodigo })
                    : t('purchases.exchangeRateHelp', { currency: monedaBaseCodigo ?? '' })
                }
                slotProps={{ htmlInput: { inputMode: 'decimal' } }}
              />
            </Box>

            {productosQuery.error && <Alert severity="error">{productosQuery.error}</Alert>}
            {noLinkedProducts && (
              <Alert severity="warning">{t('purchases.noLinkedProducts')}</Alert>
            )}

            {proveedor && productos.length > 0 && (
              <Stack spacing={1.5}>
                <Typography variant="subtitle2" color="primary">
                  {t('purchases.lines')}
                </Typography>
                {lineas.map((linea, index) => (
                  <Box
                    key={linea.key}
                    sx={{
                      display: 'grid',
                      gridTemplateColumns: { xs: '1fr', sm: '3fr 1fr 1.5fr auto' },
                      gap: 1.5,
                      alignItems: 'center',
                    }}
                  >
                    <FormControl fullWidth size="small" required>
                      <InputLabel id={`line-product-${linea.key}`}>
                        {t('purchases.lineProduct', { line: index + 1 })}
                      </InputLabel>
                      <Select
                        labelId={`line-product-${linea.key}`}
                        value={linea.productoId}
                        label={t('purchases.lineProduct', { line: index + 1 })}
                        onChange={(event) =>
                          updateLine(linea.key, 'productoId', event.target.value)
                        }
                      >
                        {productos.map((producto) => (
                          <MenuItem key={producto.id} value={producto.id}>
                            {producto.nombre} ({producto.codigo})
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <TextField
                      size="small"
                      required
                      type="number"
                      label={t('purchases.lineQuantity', { line: index + 1 })}
                      value={linea.cantidad}
                      onChange={(event) => updateLine(linea.key, 'cantidad', event.target.value)}
                      slotProps={{ htmlInput: { min: 1, step: 1 } }}
                    />
                    <TextField
                      size="small"
                      required
                      label={t('purchases.lineCost', {
                        line: index + 1,
                        currency: moneda?.codigo ?? '',
                      })}
                      value={linea.costo}
                      onChange={(event) => updateLine(linea.key, 'costo', event.target.value)}
                      slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                    />
                    <IconButton
                      aria-label={t('purchases.removeLine', { line: index + 1 })}
                      disabled={lineas.length === 1}
                      onClick={() =>
                        setLineas((previous) => previous.filter((item) => item.key !== linea.key))
                      }
                    >
                      <DeleteOutlineRoundedIcon />
                    </IconButton>
                  </Box>
                ))}
                <Button
                  startIcon={<AddRoundedIcon />}
                  sx={{ alignSelf: 'flex-start' }}
                  disabled={lineas.length >= productos.length}
                  onClick={() => setLineas((previous) => [...previous, emptyLine()])}
                >
                  {t('purchases.addLine')}
                </Button>
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>
            {t('crud.cancel')}
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={saving || !proveedor || productos.length === 0}
          >
            {saving ? t('crud.saving') : t('purchases.createDraft')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
