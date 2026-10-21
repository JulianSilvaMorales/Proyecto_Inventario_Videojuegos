import { useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { DecimalValue, PageResponse } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Bodega } from '../../generated/entidades'
import { isPositiveDecimal, parseDecimal } from '../../utils/decimal'
import { formatDate } from '../../utils/formatters'

interface OrdenCompraResumen {
  id: string
  numero: string
  proveedorNombre: string
  paisId: string
  tipoCambio: DecimalValue
  estado: string
  creadaEnUtc: string
}

interface OrdenCompraLinea {
  id: string
  codigoProducto: string
  nombreProducto: string
  cantidadPedida: number
  cantidadRecibida: number
}

interface DetalleOrdenCompra {
  orden: OrdenCompraResumen
  lineas: OrdenCompraLinea[]
}

interface StockEntriesTabProps {
  bodegas: Bodega[]
}

const RECEIVABLE_STATES = ['ENVIADA_AL_PROVEEDOR', 'RECIBIDA_PARCIAL']
const RATE_SCALE = 8
const RATE_INTEGER_DIGITS = 11

function pendingQuantity(linea: OrdenCompraLinea) {
  return Math.max(0, linea.cantidadPedida - linea.cantidadRecibida)
}

export function StockEntriesTab({ bodegas }: StockEntriesTabProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const ordersQuery = useApiQuery<PageResponse<OrdenCompraResumen>>('/api/v1/compras/ordenes?size=100')
  const ordenes = ordersQuery.data?.content ?? []
  const [success, setSuccess] = useState('')

  const [dialogOpen, setDialogOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [ordenDetalle, setOrdenDetalle] = useState<DetalleOrdenCompra | null>(null)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [bodegaId, setBodegaId] = useState('')
  const [numeroDocumento, setNumeroDocumento] = useState('')
  const [tipoCambio, setTipoCambio] = useState('')
  const [cantidades, setCantidades] = useState<Record<string, string>>({})

  const bodegasDeLaOrden = ordenDetalle
    ? bodegas.filter((bodega) => bodega.paisId === ordenDetalle.orden.paisId)
    : []

  function openDialog() {
    setOrdenDetalle(null)
    setBodegaId('')
    setNumeroDocumento('')
    setTipoCambio('')
    setCantidades({})
    setFormError('')
    setSuccess('')
    setDialogOpen(true)
  }

  async function selectOrder(ordenId: string) {
    setFormError('')
    setLoadingDetail(true)
    try {
      const detalle = await apiRequest<DetalleOrdenCompra>(`/api/v1/compras/ordenes/${ordenId}`)
      if (!detalle) return
      setOrdenDetalle(detalle)
      setTipoCambio(String(detalle.orden.tipoCambio))
      setBodegaId(bodegas.find((bodega) => bodega.paisId === detalle.orden.paisId)?.id ?? '')
      setCantidades(
        Object.fromEntries(
          detalle.lineas.map((linea) => [linea.id, String(pendingQuantity(linea))]),
        ),
      )
    } catch (reason) {
      setFormError(apiErrorMessage(reason, t))
    } finally {
      setLoadingDetail(false)
    }
  }

  function validate(): { tasa: string; lineas: { ordenCompraLineaId: string; cantidad: number }[] } | null {
    if (!ordenDetalle || !bodegaId || !numeroDocumento.trim()) {
      setFormError(t('validation.requiredFields'))
      return null
    }
    const tasa = parseDecimal(tipoCambio, RATE_SCALE, RATE_INTEGER_DIGITS)
    if (tasa === null || !isPositiveDecimal(tasa)) {
      setFormError(t('validation.positiveDecimal', { field: t('inventory.exchangeRateApplied') }))
      return null
    }
    const lineas = []
    for (const linea of ordenDetalle.lineas) {
      const texto = (cantidades[linea.id] ?? '').trim()
      if (!texto) continue
      const cantidad = Number(texto)
      if (!Number.isInteger(cantidad) || cantidad < 0 || cantidad > pendingQuantity(linea)) {
        setFormError(t('inventory.invalidReceivedQuantity', { product: linea.nombreProducto }))
        return null
      }
      if (cantidad > 0) lineas.push({ ordenCompraLineaId: linea.id, cantidad })
    }
    if (lineas.length === 0) {
      setFormError(t('inventory.receiveAtLeastOne'))
      return null
    }
    return { tasa, lineas }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError('')
    const valid = validate()
    if (!valid || !ordenDetalle) return

    setSaving(true)
    try {
      await apiRequest('/api/v1/compras/entradas', {
        method: 'POST',
        body: JSON.stringify({
          ordenCompraId: ordenDetalle.orden.id,
          bodegaId,
          numeroDocumento: numeroDocumento.trim(),
          tipoCambioAplicado: valid.tasa,
          lineas: valid.lineas,
        }),
      })
      setSuccess(t('inventory.entrySuccess'))
      setDialogOpen(false)
      ordersQuery.reload()
    } catch (reason) {
      setFormError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack spacing={2.5}>
      {ordersQuery.error && <Alert severity="error">{ordersQuery.error}</Alert>}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6">{t('inventory.purchaseOrders')}</Typography>
        <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openDialog}>
          {t('inventory.registerEntry')}
        </Button>
      </Stack>

      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid', borderColor: 'divider' }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.orderNumber')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.supplier')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.status')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.date')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {ordersQuery.loading ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : ordenes.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  {t('inventory.emptyOrders')}
                </TableCell>
              </TableRow>
            ) : (
              ordenes.map((orden) => (
                <TableRow key={orden.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{orden.numero}</TableCell>
                  <TableCell>{orden.proveedorNombre}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={t(`inventory.orderStates.${orden.estado}`, { defaultValue: orden.estado })}
                      color={
                        orden.estado === 'RECIBIDA_COMPLETA'
                          ? 'success'
                          : RECEIVABLE_STATES.includes(orden.estado)
                            ? 'primary'
                            : 'default'
                      }
                    />
                  </TableCell>
                  <TableCell>{formatDate(new Date(orden.creadaEnUtc), language)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <form onSubmit={handleSubmit} noValidate>
          <DialogTitle>{t('inventory.registerEntry')}</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2.5}>
              {formError && <Alert severity="error">{formError}</Alert>}
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                  gap: 2,
                }}
              >
                <FormControl fullWidth size="small" required>
                  <InputLabel id="entry-order">{t('inventory.purchaseOrder')}</InputLabel>
                  <Select
                    labelId="entry-order"
                    value={ordenDetalle?.orden.id ?? ''}
                    label={t('inventory.purchaseOrder')}
                    onChange={(event) => void selectOrder(event.target.value)}
                  >
                    {ordenes
                      .filter((orden) => RECEIVABLE_STATES.includes(orden.estado))
                      .map((orden) => (
                        <MenuItem key={orden.id} value={orden.id}>
                          {orden.numero} — {orden.proveedorNombre}
                        </MenuItem>
                      ))}
                  </Select>
                </FormControl>

                <FormControl fullWidth size="small" required disabled={!ordenDetalle}>
                  <InputLabel id="entry-warehouse">{t('inventory.warehouse')}</InputLabel>
                  <Select
                    labelId="entry-warehouse"
                    value={bodegaId}
                    label={t('inventory.warehouse')}
                    onChange={(event) => setBodegaId(event.target.value)}
                  >
                    {bodegasDeLaOrden.map((bodega) => (
                      <MenuItem key={bodega.id} value={bodega.id}>
                        {bodega.nombre} ({bodega.codigo})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  fullWidth
                  size="small"
                  required
                  label={t('inventory.documentNumber')}
                  value={numeroDocumento}
                  onChange={(event) => setNumeroDocumento(event.target.value)}
                  slotProps={{ htmlInput: { maxLength: 120 } }}
                />

                <TextField
                  fullWidth
                  size="small"
                  required
                  label={t('inventory.exchangeRateApplied')}
                  value={tipoCambio}
                  onChange={(event) => setTipoCambio(event.target.value)}
                  helperText={t('inventory.exchangeRateHelp')}
                  slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                />
              </Box>

              {loadingDetail && <CircularProgress size={24} aria-label={t('common.loading')} />}

              {ordenDetalle && (
                <>
                  <Divider />
                  <Typography variant="subtitle2" color="primary">
                    {t('inventory.linesToReceive')}
                  </Typography>
                  <TableContainer
                    component={Paper}
                    elevation={0}
                    sx={{ border: '1px solid', borderColor: 'divider' }}
                  >
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell sx={{ fontWeight: 700 }}>{t('inventory.product')}</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>
                            {t('inventory.orderedQty')}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>
                            {t('inventory.previouslyReceivedQty')}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>
                            {t('inventory.pendingQty')}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700, width: 140 }}>
                            {t('inventory.receivedQty')}
                          </TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {ordenDetalle.lineas.map((linea) => (
                          <TableRow key={linea.id}>
                            <TableCell>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {linea.nombreProducto}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {linea.codigoProducto}
                              </Typography>
                            </TableCell>
                            <TableCell align="right">{linea.cantidadPedida}</TableCell>
                            <TableCell align="right">{linea.cantidadRecibida}</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 700 }}>
                              {pendingQuantity(linea)}
                            </TableCell>
                            <TableCell align="right">
                              <TextField
                                size="small"
                                type="number"
                                value={cantidades[linea.id] ?? ''}
                                onChange={(event) =>
                                  setCantidades((previous) => ({
                                    ...previous,
                                    [linea.id]: event.target.value,
                                  }))
                                }
                                slotProps={{
                                  htmlInput: {
                                    min: 0,
                                    max: pendingQuantity(linea),
                                    step: 1,
                                    'aria-label': `${t('inventory.receivedQty')} ${linea.nombreProducto}`,
                                  },
                                }}
                              />
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={saving}>
              {t('crud.cancel')}
            </Button>
            <Button type="submit" variant="contained" disabled={saving || !ordenDetalle}>
              {saving ? t('crud.saving') : t('crud.save')}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Stack>
  )
}
