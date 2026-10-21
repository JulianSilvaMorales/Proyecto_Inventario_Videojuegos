import { useState } from 'react'
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
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
import { useTranslation } from 'react-i18next'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Bodega } from '../../generated/entidades'
import { formatDate } from '../../utils/formatters'
import { referenceName } from '../../utils/references'
import { dayBoundaryIso, MOVEMENT_TYPES } from './movementTypes'

interface MovimientoInventario {
  id: string
  productoId: string
  codigoProducto: string
  bodegaNombre: string
  tipo: string
  cantidad: number
  fechaUtc: string
  observacion: string | null
}

interface StockMovementsTabProps {
  bodegas: Bodega[]
  productos: ReferenceOption[]
}

function signedQuantity(tipo: string, cantidad: number) {
  if (tipo === 'ENTRADA') return `+${cantidad}`
  if (tipo.startsWith('SALIDA')) return `-${cantidad}`
  return String(cantidad)
}

export function StockMovementsTab({ bodegas, productos }: StockMovementsTabProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const [bodegaId, setBodegaId] = useState('')
  const [productoId, setProductoId] = useState('')
  const [tipo, setTipo] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const invalidRange = Boolean(desde && hasta && desde > hasta)

  const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) })
  if (bodegaId) params.set('bodegaId', bodegaId)
  if (productoId) params.set('productoId', productoId)
  if (tipo) params.set('tipo', tipo)
  if (desde) params.set('desde', dayBoundaryIso(desde, false))
  if (hasta) params.set('hasta', dayBoundaryIso(hasta, true))
  const query = useApiQuery<PageResponse<MovimientoInventario>>(
    invalidRange ? null : `/api/v1/inventario/movimientos?${params.toString()}`,
  )
  const movimientos = query.data?.content ?? []

  function updateFilter(update: () => void) {
    update()
    setPage(0)
  }

  return (
    <Stack spacing={2.5}>
      {query.error && <Alert severity="error">{query.error}</Alert>}
      {invalidRange && <Alert severity="warning">{t('validation.dateRange')}</Alert>}

      <Paper elevation={0} sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(5, 1fr)' },
            gap: 2,
          }}
        >
          <FormControl fullWidth size="small">
            <InputLabel id="movements-warehouse">{t('inventory.warehouse')}</InputLabel>
            <Select
              labelId="movements-warehouse"
              value={bodegaId}
              label={t('inventory.warehouse')}
              onChange={(event) => updateFilter(() => setBodegaId(event.target.value))}
            >
              <MenuItem value="">{t('inventory.allWarehouses')}</MenuItem>
              {bodegas.map((bodega) => (
                <MenuItem key={bodega.id} value={bodega.id}>
                  {bodega.nombre} ({bodega.codigo})
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl fullWidth size="small">
            <InputLabel id="movements-product">{t('inventory.product')}</InputLabel>
            <Select
              labelId="movements-product"
              value={productoId}
              label={t('inventory.product')}
              onChange={(event) => updateFilter(() => setProductoId(event.target.value))}
            >
              <MenuItem value="">{t('common.all')}</MenuItem>
              {productos.map((producto) => (
                <MenuItem key={producto.id} value={producto.id}>
                  {producto.nombre}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl fullWidth size="small">
            <InputLabel id="movements-type">{t('inventory.movementType')}</InputLabel>
            <Select
              labelId="movements-type"
              value={tipo}
              label={t('inventory.movementType')}
              onChange={(event) => updateFilter(() => setTipo(event.target.value))}
            >
              <MenuItem value="">{t('common.all')}</MenuItem>
              {MOVEMENT_TYPES.map((movementType) => (
                <MenuItem key={movementType} value={movementType}>
                  {t(`inventory.movementTypes.${movementType}`)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            size="small"
            type="date"
            label={t('common.from')}
            value={desde}
            onChange={(event) => updateFilter(() => setDesde(event.target.value))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            size="small"
            type="date"
            label={t('common.to')}
            value={hasta}
            onChange={(event) => updateFilter(() => setHasta(event.target.value))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Box>
      </Paper>

      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid', borderColor: 'divider' }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.date')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.movementType')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.product')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.warehouse')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('inventory.movementQuantity')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.notes')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {query.loading ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : movimientos.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  {t('inventory.emptyMovements')}
                </TableCell>
              </TableRow>
            ) : (
              movimientos.map((movimiento) => {
                const entrada = movimiento.tipo === 'ENTRADA'
                const salida = movimiento.tipo.startsWith('SALIDA')
                return (
                  <TableRow key={movimiento.id} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {formatDate(new Date(movimiento.fechaUtc), language)}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        variant="outlined"
                        color={entrada ? 'success' : salida ? 'error' : 'default'}
                        label={t(`inventory.movementTypes.${movimiento.tipo}`, {
                          defaultValue: movimiento.tipo,
                        })}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {referenceName(productos, movimiento.productoId, movimiento.codigoProducto)}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {movimiento.codigoProducto}
                      </Typography>
                    </TableCell>
                    <TableCell>{movimiento.bodegaNombre}</TableCell>
                    <TableCell align="right">
                      <Typography
                        variant="body2"
                        sx={{
                          fontWeight: 700,
                          color: entrada ? 'success.main' : salida ? 'error.main' : 'text.primary',
                        }}
                      >
                        {signedQuantity(movimiento.tipo, movimiento.cantidad)}
                      </Typography>
                    </TableCell>
                    <TableCell>{movimiento.observacion || '—'}</TableCell>
                  </TableRow>
                )
              })
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
    </Stack>
  )
}
