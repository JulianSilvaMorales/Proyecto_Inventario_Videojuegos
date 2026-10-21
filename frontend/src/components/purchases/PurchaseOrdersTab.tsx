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
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Proveedor } from '../../generated/entidades'
import { formatDate, formatDecimal } from '../../utils/formatters'
import { NewPurchaseOrderDialog } from './NewPurchaseOrderDialog'
import {
  CANCELLABLE_STATES,
  ORDER_STATES,
  RECEIVABLE_STATES,
  orderStateColor,
  type DetalleOrdenCompra,
  type OrdenCompra,
  type OrdenCompraLinea,
} from './types'

interface PurchaseOrdersTabProps {
  proveedores: Proveedor[]
  monedas: ReferenceOption[]
  monedaBaseCodigo: string | null
  onOpenInventory?: () => void
}

export function PurchaseOrdersTab({
  proveedores,
  monedas,
  monedaBaseCodigo,
  onOpenInventory,
}: PurchaseOrdersTabProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const [estado, setEstado] = useState('')
  const [proveedorId, setProveedorId] = useState('')
  const [success, setSuccess] = useState('')
  const [creating, setCreating] = useState(false)

  const params = new URLSearchParams({ size: '100' })
  if (estado) params.set('estado', estado)
  if (proveedorId) params.set('proveedorId', proveedorId)
  const ordersQuery = useApiQuery<PageResponse<OrdenCompra>>(`/api/v1/compras/ordenes?${params}`)
  const ordenes = ordersQuery.data?.content ?? []

  const [detalle, setDetalle] = useState<DetalleOrdenCompra | null>(null)
  const [detailError, setDetailError] = useState('')
  const [working, setWorking] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)

  function currencyCode(monedaId: string) {
    return monedas.find((moneda) => moneda.id === monedaId)?.codigo ?? ''
  }

  // El costo admite 4 decimales: se muestra completo, sin redondear a los de la moneda.
  function cost(amount: OrdenCompraLinea['costoUnitario'], monedaId: string) {
    return `${formatDecimal(amount, language, 4)} ${currencyCode(monedaId)}`.trim()
  }

  async function openDetail(ordenId: string) {
    setDetailError('')
    setConfirmCancel(false)
    try {
      const respuesta = await apiRequest<DetalleOrdenCompra>(`/api/v1/compras/ordenes/${ordenId}`)
      if (respuesta) setDetalle(respuesta)
    } catch (reason) {
      setSuccess('')
      setDetailError(apiErrorMessage(reason, t))
    }
  }

  async function changeState(action: 'enviar' | 'cancelar') {
    if (!detalle) return
    setWorking(true)
    setDetailError('')
    try {
      const respuesta = await apiRequest<DetalleOrdenCompra>(
        `/api/v1/compras/ordenes/${detalle.orden.id}/${action}`,
        { method: 'POST' },
      )
      if (respuesta) setDetalle(respuesta)
      setConfirmCancel(false)
      setSuccess(
        t(action === 'enviar' ? 'purchases.sentSuccess' : 'purchases.cancelledSuccess', {
          number: detalle.orden.numero,
        }),
      )
      ordersQuery.reload()
    } catch (reason) {
      setDetailError(apiErrorMessage(reason, t))
    } finally {
      setWorking(false)
    }
  }

  function handleCreated(orden: DetalleOrdenCompra) {
    setCreating(false)
    setSuccess(t('purchases.createdSuccess', { number: orden.orden.numero }))
    ordersQuery.reload()
    setDetalle(orden)
    setDetailError('')
    setConfirmCancel(false)
  }

  const estadoDetalle = detalle?.orden.estado ?? ''

  return (
    <Stack spacing={2.5}>
      {ordersQuery.error && <Alert severity="error">{ordersQuery.error}</Alert>}
      {detailError && !detalle && <Alert severity="error">{detailError}</Alert>}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' } }}
      >
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ flexGrow: 1 }}>
          <FormControl size="small" sx={{ minWidth: 220 }}>
            <InputLabel id="orders-state">{t('purchases.filterState')}</InputLabel>
            <Select
              labelId="orders-state"
              value={estado}
              label={t('purchases.filterState')}
              onChange={(event) => setEstado(event.target.value)}
            >
              <MenuItem value="">{t('common.all')}</MenuItem>
              {ORDER_STATES.map((value) => (
                <MenuItem key={value} value={value}>
                  {t(`inventory.orderStates.${value}`)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 260 }}>
            <InputLabel id="orders-supplier">{t('purchases.filterSupplier')}</InputLabel>
            <Select
              labelId="orders-supplier"
              value={proveedorId}
              label={t('purchases.filterSupplier')}
              onChange={(event) => setProveedorId(event.target.value)}
            >
              <MenuItem value="">{t('common.all')}</MenuItem>
              {proveedores.map((proveedor) => (
                <MenuItem key={proveedor.id} value={proveedor.id}>
                  {proveedor.razonSocial}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
        <Button
          variant="contained"
          startIcon={<AddRoundedIcon />}
          onClick={() => {
            setSuccess('')
            setCreating(true)
          }}
        >
          {t('purchases.newOrder')}
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
              <TableCell sx={{ fontWeight: 700 }}>{t('purchases.supplier')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.status')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('purchases.currency')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('purchases.exchangeRate')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.date')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('crud.actions')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {ordersQuery.loading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : ordenes.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
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
                      label={t(`inventory.orderStates.${orden.estado}`, {
                        defaultValue: orden.estado,
                      })}
                      color={orderStateColor(orden.estado)}
                    />
                  </TableCell>
                  <TableCell>{currencyCode(orden.monedaId)}</TableCell>
                  <TableCell align="right">{formatDecimal(orden.tipoCambio, language)}</TableCell>
                  <TableCell>{formatDate(new Date(orden.creadaEnUtc), language)}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      aria-label={`${t('purchases.viewOrder')} ${orden.numero}`}
                      onClick={() => void openDetail(orden.id)}
                    >
                      {t('purchases.viewOrder')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {creating && (
        <NewPurchaseOrderDialog
          proveedores={proveedores.filter((proveedor) => proveedor.activo)}
          monedas={monedas}
          monedaBaseCodigo={monedaBaseCodigo}
          onClose={() => setCreating(false)}
          onCreated={handleCreated}
        />
      )}

      <Dialog open={detalle !== null} onClose={() => setDetalle(null)} maxWidth="md" fullWidth>
        {detalle && (
          <>
            <DialogTitle>
              {t('purchases.orderDetail', { number: detalle.orden.numero })}
            </DialogTitle>
            <DialogContent dividers>
              <Stack spacing={2.5}>
                {detailError && <Alert severity="error">{detailError}</Alert>}
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                    gap: 1.5,
                  }}
                >
                  <Detail label={t('purchases.supplier')} value={detalle.orden.proveedorNombre} />
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      {t('inventory.status')}
                    </Typography>
                    <Box>
                      <Chip
                        size="small"
                        label={t(`inventory.orderStates.${estadoDetalle}`, {
                          defaultValue: estadoDetalle,
                        })}
                        color={orderStateColor(estadoDetalle)}
                      />
                    </Box>
                  </Box>
                  <Detail
                    label={t('purchases.currency')}
                    value={currencyCode(detalle.orden.monedaId)}
                  />
                  <Detail
                    label={t('purchases.exchangeRate')}
                    value={formatDecimal(detalle.orden.tipoCambio, language)}
                  />
                  <Detail
                    label={t('inventory.date')}
                    value={formatDate(new Date(detalle.orden.creadaEnUtc), language)}
                  />
                </Box>

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
                          {t('purchases.receivedQty')}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>
                          {t('inventory.pendingQty')}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>
                          {t('purchases.unitCost')}
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {detalle.lineas.map((linea) => (
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
                            {Math.max(0, linea.cantidadPedida - linea.cantidadRecibida)}
                          </TableCell>
                          <TableCell align="right">
                            {cost(linea.costoUnitario, detalle.orden.monedaId)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>

                {estadoDetalle === 'BORRADOR' && (
                  <Alert severity="info">{t('purchases.draftHelp')}</Alert>
                )}
                {RECEIVABLE_STATES.includes(estadoDetalle) && (
                  <Alert
                    severity="info"
                    action={
                      onOpenInventory && (
                        <Button color="inherit" size="small" onClick={onOpenInventory}>
                          {t('purchases.goToInventory')}
                        </Button>
                      )
                    }
                  >
                    {t('purchases.receiveHelp')}
                  </Alert>
                )}
                {confirmCancel && (
                  <Alert
                    severity="warning"
                    action={
                      <Stack direction="row" spacing={1}>
                        <Button
                          color="inherit"
                          size="small"
                          onClick={() => setConfirmCancel(false)}
                          disabled={working}
                        >
                          {t('purchases.keepOrder')}
                        </Button>
                        <Button
                          color="error"
                          size="small"
                          variant="contained"
                          onClick={() => void changeState('cancelar')}
                          disabled={working}
                        >
                          {t('purchases.confirmCancel')}
                        </Button>
                      </Stack>
                    }
                  >
                    {t('purchases.cancelWarning')}
                  </Alert>
                )}
              </Stack>
            </DialogContent>
            <DialogActions>
              {CANCELLABLE_STATES.includes(estadoDetalle) && !confirmCancel && (
                <Button color="error" onClick={() => setConfirmCancel(true)} disabled={working}>
                  {t('purchases.cancelOrder')}
                </Button>
              )}
              <Box sx={{ flexGrow: 1 }} />
              <Button onClick={() => setDetalle(null)} disabled={working}>
                {t('common.close')}
              </Button>
              {estadoDetalle === 'BORRADOR' && (
                <Button
                  variant="contained"
                  onClick={() => void changeState('enviar')}
                  disabled={working}
                >
                  {t('purchases.sendOrder')}
                </Button>
              )}
            </DialogActions>
          </>
        )}
      </Dialog>
    </Stack>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {value}
      </Typography>
    </Box>
  )
}
