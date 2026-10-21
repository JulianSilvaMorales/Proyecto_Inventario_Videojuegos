import { useState } from 'react'
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
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import PrintRoundedIcon from '@mui/icons-material/PrintRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import { formatDate } from '../../utils/formatters'
import { SaleLinesTable } from './SaleLinesTable'
import { SaleReceipt } from './SaleReceipt'
import {
  clientName,
  SALE_STATE_COLORS,
  type ClienteResumen,
  type PaisVenta,
  type RespuestaVenta,
} from './salesTypes'

interface SaleDetailDialogProps {
  ventaId: string
  paises: PaisVenta[]
  productos: ReferenceOption[]
  canCancel: boolean
  canSeeClients: boolean
  onClose: () => void
  onChanged: () => void
}

export function SaleDetailDialog({
  ventaId,
  paises,
  productos,
  canCancel,
  canSeeClients,
  onClose,
  onChanged,
}: SaleDetailDialogProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const query = useApiQuery<RespuestaVenta>(`/api/v1/ventas/${ventaId}`)
  const detalle = query.data
  const venta = detalle?.venta
  const pais = paises.find((candidate) => candidate.paisId === venta?.paisId)
  const clienteQuery = useApiQuery<ClienteResumen>(
    canSeeClients && venta?.clienteId ? `/api/v1/clientes/${venta.clienteId}` : null,
  )
  const clienteNombre = clienteQuery.data ? clientName(clienteQuery.data) : undefined
  const [showReceipt, setShowReceipt] = useState(false)
  const reciboQuery = useApiQuery<RespuestaVenta>(
    showReceipt ? `/api/v1/ventas/${ventaId}/recibo` : null,
  )

  const [medioPago, setMedioPago] = useState('')
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const pendiente = venta?.estado === 'PENDIENTE_DE_PAGO' && venta.estadoPago === 'SIN_PAGO'
  const mediosPago = pais?.mediosPago ?? []

  async function confirmSale() {
    setError('')
    setSuccess('')
    if (!medioPago.trim()) {
      setError(t('sales.paymentMethodRequired'))
      return
    }
    setWorking(true)
    try {
      await apiRequest(`/api/v1/ventas/${ventaId}/confirmar`, {
        method: 'POST',
        body: JSON.stringify({ medioPago: medioPago.trim() }),
      })
      setSuccess(t('sales.confirmedSuccess'))
      query.reload()
      onChanged()
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setWorking(false)
    }
  }

  async function cancelSale() {
    setError('')
    setSuccess('')
    setWorking(true)
    try {
      await apiRequest(`/api/v1/ventas/${ventaId}/anular`, { method: 'POST' })
      setConfirmingCancel(false)
      setSuccess(t('sales.cancelledSuccess'))
      query.reload()
      onChanged()
    } catch (reason) {
      setConfirmingCancel(false)
      setError(apiErrorMessage(reason, t))
    } finally {
      setWorking(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        {showReceipt
          ? t('sales.receiptTitle')
          : venta
            ? t('sales.saleNumber', { number: venta.numero })
            : t('sales.saleDetail')}
      </DialogTitle>
      <DialogContent dividers>
        {query.loading && !detalle ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress aria-label={t('common.loading')} />
          </Box>
        ) : query.error ? (
          <Alert severity="error">{query.error}</Alert>
        ) : detalle && venta && showReceipt ? (
          reciboQuery.data ? (
            <SaleReceipt
              recibo={reciboQuery.data}
              productos={productos}
              clienteNombre={clienteNombre}
              decimales={pais?.monedaDecimales}
            />
          ) : reciboQuery.error ? (
            <Alert severity="error">{reciboQuery.error}</Alert>
          ) : (
            <CircularProgress aria-label={t('common.loading')} />
          )
        ) : detalle && venta ? (
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}
            {success && <Alert severity="success">{success}</Alert>}

            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' },
                gap: 1.5,
              }}
            >
              <Typography variant="body2" component="div">
                <strong>{t('sales.status')}:</strong>{' '}
                <Chip
                  size="small"
                  color={SALE_STATE_COLORS[venta.estado] ?? 'default'}
                  label={t(`sales.states.${venta.estado}`, { defaultValue: venta.estado })}
                />
              </Typography>
              <Typography variant="body2">
                <strong>{t('sales.paymentStatus')}:</strong>{' '}
                {t(`sales.paymentStates.${venta.estadoPago}`, { defaultValue: venta.estadoPago })}
              </Typography>
              <Typography variant="body2">
                <strong>{t('sales.date')}:</strong> {formatDate(new Date(venta.creadaEnUtc), language)}
              </Typography>
              <Typography variant="body2">
                <strong>{t('common.country')}:</strong> {pais?.paisNombre ?? venta.paisCodigo}
              </Typography>
              <Typography variant="body2">
                <strong>{t('sales.currency')}:</strong> {venta.monedaCodigo}
              </Typography>
              <Typography variant="body2">
                <strong>{t('sales.client')}:</strong>{' '}
                {venta.clienteId ? (clienteNombre ?? '…') : t('sales.noClient')}
              </Typography>
            </Box>

            <SaleLinesTable detalle={detalle} productos={productos} decimales={pais?.monedaDecimales} />

            {pendiente && (
              <Box
                sx={{
                  p: 2,
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 1,
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: '1fr auto' },
                  gap: 2,
                  alignItems: 'center',
                }}
              >
                {mediosPago.length > 0 ? (
                  <FormControl size="small" fullWidth>
                    <InputLabel id="sale-payment-method">{t('sales.paymentMethod')}</InputLabel>
                    <Select
                      labelId="sale-payment-method"
                      value={medioPago}
                      label={t('sales.paymentMethod')}
                      onChange={(event) => setMedioPago(event.target.value)}
                    >
                      {mediosPago.map((medio) => (
                        <MenuItem key={medio} value={medio}>
                          {t(`sales.paymentMethods.${medio}`, { defaultValue: medio })}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                ) : (
                  <TextField
                    size="small"
                    label={t('sales.paymentMethod')}
                    value={medioPago}
                    onChange={(event) => setMedioPago(event.target.value)}
                    helperText={t('sales.paymentMethodFreeText')}
                    slotProps={{ htmlInput: { maxLength: 40 } }}
                  />
                )}
                <Button variant="contained" disabled={working} onClick={() => void confirmSale()}>
                  {t('sales.confirmPayment')}
                </Button>
              </Box>
            )}
          </Stack>
        ) : null}
      </DialogContent>
      <DialogActions>
        {showReceipt ? (
          <>
            <Button startIcon={<PrintRoundedIcon />} onClick={() => window.print()}>
              {t('sales.print')}
            </Button>
            <Button onClick={() => setShowReceipt(false)}>{t('crud.back')}</Button>
          </>
        ) : (
          <>
            {pendiente && canCancel && (
              <Button color="error" disabled={working} onClick={() => setConfirmingCancel(true)}>
                {t('sales.cancelSale')}
              </Button>
            )}
            {venta?.estado === 'CONFIRMADA' && (
              <Button onClick={() => setShowReceipt(true)}>{t('sales.viewReceipt')}</Button>
            )}
          </>
        )}
        <Button variant="outlined" onClick={onClose}>
          {t('common.close')}
        </Button>
      </DialogActions>

      <Dialog open={confirmingCancel} onClose={() => setConfirmingCancel(false)}>
        <DialogTitle>{t('sales.cancelTitle')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2">{t('sales.cancelMessage')}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmingCancel(false)} disabled={working}>
            {t('crud.cancel')}
          </Button>
          <Button color="error" variant="contained" disabled={working} onClick={() => void cancelSale()}>
            {t('sales.cancelSale')}
          </Button>
        </DialogActions>
      </Dialog>
    </Dialog>
  )
}
