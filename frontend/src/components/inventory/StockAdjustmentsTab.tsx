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
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded'
import DoneAllRoundedIcon from '@mui/icons-material/DoneAllRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Bodega } from '../../generated/entidades'
import { formatDate } from '../../utils/formatters'
import { referenceName } from '../../utils/references'

type AdjustmentType = 'OPERATIVO' | 'TECNICO'

interface AjusteInventario {
  id: string
  productoId: string
  codigoProducto: string
  bodegaNombre: string
  tipo: AdjustmentType
  cantidadSistema: number
  cantidadContada: number
  diferencia: number
  motivo: string
  estado: string
  solicitadoEnUtc: string
}

interface StockAdjustmentsTabProps {
  bodegas: Bodega[]
  productos: ReferenceOption[]
  permissions: readonly string[]
  roles: readonly string[]
}

/** Estados de `ajuste_inventario` definidos en el modelo. */
const ADJUSTMENT_STATES = ['PENDIENTE_DE_APROBACION', 'APROBADO', 'APLICADO'] as const

const STATE_COLORS: Record<string, 'warning' | 'info' | 'success'> = {
  PENDIENTE_DE_APROBACION: 'warning',
  APROBADO: 'info',
  APLICADO: 'success',
}

export function StockAdjustmentsTab({
  bodegas,
  productos,
  permissions,
  roles,
}: StockAdjustmentsTabProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [estadoFiltro, setEstadoFiltro] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [actingId, setActingId] = useState<string | null>(null)

  // El backend exige el permiso y el rol correspondiente para cada solicitud.
  const canApprove = permissions.includes('AJUSTES_APROBAR')
  const requestTypes: AdjustmentType[] = [
    ...(permissions.includes('AJUSTE_OPERATIVO_SOLICITAR') && roles.includes('ENCARGADO_INVENTARIO')
      ? (['OPERATIVO'] as const)
      : []),
    ...(permissions.includes('AJUSTE_TECNICO_SOLICITAR') && roles.includes('EXPERTO_TECNICO')
      ? (['TECNICO'] as const)
      : []),
  ]

  const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) })
  if (estadoFiltro) params.set('estado', estadoFiltro)
  const query = useApiQuery<PageResponse<AjusteInventario>>(
    `/api/v1/inventario/ajustes?${params.toString()}`,
  )
  const ajustes = query.data?.content ?? []

  const [dialogOpen, setDialogOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [tipoAjuste, setTipoAjuste] = useState<AdjustmentType>('OPERATIVO')
  const [bodegaId, setBodegaId] = useState('')
  const [productoId, setProductoId] = useState('')
  const [cantidadContada, setCantidadContada] = useState('')
  const [motivo, setMotivo] = useState('')

  function openDialog() {
    setTipoAjuste(requestTypes[0] ?? 'OPERATIVO')
    setBodegaId('')
    setProductoId('')
    setCantidadContada('')
    setMotivo('')
    setFormError('')
    setDialogOpen(true)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError('')
    const count = Number(cantidadContada)
    if (!bodegaId || !productoId || !cantidadContada.trim() || !motivo.trim()) {
      setFormError(t('validation.requiredFields'))
      return
    }
    if (!Number.isInteger(count) || count < 0) {
      setFormError(t('validation.nonNegativeInteger', { field: t('inventory.countedQuantity') }))
      return
    }

    setSaving(true)
    try {
      await apiRequest(
        tipoAjuste === 'OPERATIVO'
          ? '/api/v1/inventario/ajustes/operativos'
          : '/api/v1/inventario/ajustes/tecnicos',
        {
          method: 'POST',
          body: JSON.stringify({
            bodegaId,
            productoId,
            cantidadContada: count,
            motivo: motivo.trim(),
          }),
        },
      )
      setSuccess(t('inventory.adjustmentSuccess'))
      setDialogOpen(false)
      query.reload()
    } catch (reason) {
      setFormError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  async function runAction(id: string, action: 'aprobar' | 'aplicar' | 'revalidar') {
    setError('')
    setSuccess('')
    setActingId(id)
    try {
      await apiRequest(`/api/v1/inventario/ajustes/${id}/${action}`, { method: 'POST' })
      setSuccess(t(`inventory.adjustmentActions.${action}`))
      query.reload()
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setActingId(null)
    }
  }

  const columns = canApprove ? 10 : 9

  return (
    <Stack spacing={2.5}>
      {(error || query.error) && <Alert severity="error">{error || query.error}</Alert>}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}
      >
        <FormControl size="small" sx={{ width: 240 }}>
          <InputLabel id="adjustments-state">{t('inventory.status')}</InputLabel>
          <Select
            labelId="adjustments-state"
            value={estadoFiltro}
            label={t('inventory.status')}
            onChange={(event) => {
              setEstadoFiltro(event.target.value)
              setPage(0)
            }}
          >
            <MenuItem value="">{t('common.all')}</MenuItem>
            {ADJUSTMENT_STATES.map((state) => (
              <MenuItem key={state} value={state}>
                {t(`inventory.adjustmentStates.${state}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {requestTypes.length > 0 && (
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openDialog}>
            {t('inventory.requestAdjustment')}
          </Button>
        )}
      </Stack>

      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid', borderColor: 'divider' }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.product')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.warehouse')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.adjustmentType')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('inventory.systemQuantity')}
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('inventory.countedQuantity')}
              </TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('inventory.difference')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.reason')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.status')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.date')}</TableCell>
              {canApprove && (
                <TableCell align="right" sx={{ fontWeight: 700 }}>
                  {t('crud.actions')}
                </TableCell>
              )}
            </TableRow>
          </TableHead>
          <TableBody>
            {query.loading ? (
              <TableRow>
                <TableCell colSpan={columns} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : ajustes.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns}
                  align="center"
                  sx={{ py: 4, color: 'text.secondary' }}
                >
                  {t('inventory.emptyAdjustments')}
                </TableCell>
              </TableRow>
            ) : (
              ajustes.map((ajuste) => (
                <TableRow key={ajuste.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {referenceName(productos, ajuste.productoId, ajuste.codigoProducto)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {ajuste.codigoProducto}
                    </Typography>
                  </TableCell>
                  <TableCell>{ajuste.bodegaNombre}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      color={ajuste.tipo === 'OPERATIVO' ? 'primary' : 'secondary'}
                      label={t(`inventory.adjustmentTypes.${ajuste.tipo}`)}
                    />
                  </TableCell>
                  <TableCell align="right">{ajuste.cantidadSistema}</TableCell>
                  <TableCell align="right">{ajuste.cantidadContada}</TableCell>
                  <TableCell align="right">
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: 700,
                        color:
                          ajuste.diferencia > 0
                            ? 'success.main'
                            : ajuste.diferencia < 0
                              ? 'error.main'
                              : 'text.secondary',
                      }}
                    >
                      {ajuste.diferencia > 0 ? `+${ajuste.diferencia}` : ajuste.diferencia}
                    </Typography>
                  </TableCell>
                  <TableCell>{ajuste.motivo}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={STATE_COLORS[ajuste.estado] ?? 'default'}
                      label={t(`inventory.adjustmentStates.${ajuste.estado}`, {
                        defaultValue: ajuste.estado,
                      })}
                    />
                  </TableCell>
                  <TableCell>{formatDate(new Date(ajuste.solicitadoEnUtc), language)}</TableCell>
                  {canApprove && (
                    <TableCell align="right">
                      <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        {ajuste.estado === 'PENDIENTE_DE_APROBACION' && (
                          <Button
                            size="small"
                            variant="outlined"
                            color="success"
                            startIcon={<CheckCircleOutlineRoundedIcon />}
                            disabled={actingId === ajuste.id}
                            onClick={() => void runAction(ajuste.id, 'aprobar')}
                          >
                            {t('inventory.approve')}
                          </Button>
                        )}
                        {ajuste.estado === 'APROBADO' && (
                          <>
                            <Button
                              size="small"
                              variant="contained"
                              startIcon={<DoneAllRoundedIcon />}
                              disabled={actingId === ajuste.id}
                              onClick={() => void runAction(ajuste.id, 'aplicar')}
                            >
                              {t('inventory.apply')}
                            </Button>
                            <Button
                              size="small"
                              color="inherit"
                              startIcon={<RefreshRoundedIcon />}
                              disabled={actingId === ajuste.id}
                              onClick={() => void runAction(ajuste.id, 'revalidar')}
                            >
                              {t('inventory.revalidate')}
                            </Button>
                          </>
                        )}
                      </Stack>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={query.data?.totalElements ?? 0}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(_, newPage) => setPage(newPage)}
          onRowsPerPageChange={(event) => {
            setRowsPerPage(Number(event.target.value))
            setPage(0)
          }}
          labelRowsPerPage={t('crud.rowsPerPage')}
        />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit} noValidate>
          <DialogTitle>{t('inventory.requestAdjustment')}</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2.5}>
              {formError && <Alert severity="error">{formError}</Alert>}
              <FormControl fullWidth size="small" required>
                <InputLabel id="adjustment-type">{t('inventory.adjustmentType')}</InputLabel>
                <Select
                  labelId="adjustment-type"
                  value={tipoAjuste}
                  label={t('inventory.adjustmentType')}
                  onChange={(event) => setTipoAjuste(event.target.value as AdjustmentType)}
                >
                  {requestTypes.map((type) => (
                    <MenuItem key={type} value={type}>
                      {t(`inventory.adjustmentTypes.${type}`)}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                  gap: 2,
                }}
              >
                <FormControl fullWidth size="small" required>
                  <InputLabel id="adjustment-warehouse">{t('inventory.warehouse')}</InputLabel>
                  <Select
                    labelId="adjustment-warehouse"
                    value={bodegaId}
                    label={t('inventory.warehouse')}
                    onChange={(event) => setBodegaId(event.target.value)}
                  >
                    {bodegas.map((bodega) => (
                      <MenuItem key={bodega.id} value={bodega.id}>
                        {bodega.nombre} ({bodega.codigo})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <FormControl fullWidth size="small" required>
                  <InputLabel id="adjustment-product">{t('inventory.product')}</InputLabel>
                  <Select
                    labelId="adjustment-product"
                    value={productoId}
                    label={t('inventory.product')}
                    onChange={(event) => setProductoId(event.target.value)}
                  >
                    {productos.map((producto) => (
                      <MenuItem key={producto.id} value={producto.id}>
                        {producto.nombre}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Box>

              <TextField
                fullWidth
                size="small"
                required
                type="number"
                label={t('inventory.countedQuantity')}
                value={cantidadContada}
                onChange={(event) => setCantidadContada(event.target.value)}
                slotProps={{ htmlInput: { min: 0, step: 1 } }}
              />

              <TextField
                fullWidth
                multiline
                rows={3}
                size="small"
                required
                label={t('inventory.reason')}
                value={motivo}
                onChange={(event) => setMotivo(event.target.value)}
                slotProps={{ htmlInput: { maxLength: 1000 } }}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={saving}>
              {t('crud.cancel')}
            </Button>
            <Button type="submit" variant="contained" disabled={saving}>
              {saving ? t('crud.saving') : t('crud.save')}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Stack>
  )
}
