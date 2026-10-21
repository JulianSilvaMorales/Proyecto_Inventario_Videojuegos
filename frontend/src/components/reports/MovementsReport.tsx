import { useState } from 'react'
import {
  Alert,
  Box,
  CircularProgress,
  Paper,
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
import type { PageResponse } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import { formatDate } from '../../utils/formatters'
import { referenceName } from '../../utils/references'
import { dayBoundaryIso, MOVEMENT_TYPES } from '../inventory/movementTypes'
import { ExportButton } from './ExportButton'
import { ReferenceFilter } from './ReferenceFilter'
import type { ReportReferences } from './ReportsPage'

interface MovimientoInventario {
  id: string
  productoId: string
  codigoProducto: string
  bodegaNombre: string
  tipo: string
  cantidad: number
  usuarioId: string
  fechaUtc: string
  observacion: string | null
}

export function MovementsReport({ references }: { references: ReportReferences }) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const [productoId, setProductoId] = useState('')
  const [bodegaId, setBodegaId] = useState('')
  const [usuarioId, setUsuarioId] = useState('')
  const [tipo, setTipo] = useState('')
  const [paisId, setPaisId] = useState('')
  const [monedaId, setMonedaId] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(20)
  const invalidRange = Boolean(desde && hasta && desde > hasta)

  const filters = new URLSearchParams()
  if (productoId) filters.set('productoId', productoId)
  if (bodegaId) filters.set('bodegaId', bodegaId)
  if (usuarioId) filters.set('usuarioId', usuarioId)
  if (tipo) filters.set('tipo', tipo)
  if (paisId) filters.set('paisId', paisId)
  if (monedaId) filters.set('monedaId', monedaId)
  if (desde) filters.set('desde', dayBoundaryIso(desde, false))
  if (hasta) filters.set('hasta', dayBoundaryIso(hasta, true))
  const pageParams = new URLSearchParams(filters)
  pageParams.set('page', String(page))
  pageParams.set('size', String(rowsPerPage))
  const query = useApiQuery<PageResponse<MovimientoInventario>>(
    invalidRange ? null : `/api/v1/reportes/inventario/movimientos?${pageParams.toString()}`,
  )
  const movimientos = query.data?.content ?? []

  function filter(update: () => void) {
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
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
            gap: 2,
          }}
        >
          <TextField
            size="small"
            type="date"
            label={t('common.from')}
            value={desde}
            onChange={(event) => filter(() => setDesde(event.target.value))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            size="small"
            type="date"
            label={t('common.to')}
            value={hasta}
            onChange={(event) => filter(() => setHasta(event.target.value))}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <ReferenceFilter
            id="movement-report-type"
            label={t('inventory.movementType')}
            value={tipo}
            options={MOVEMENT_TYPES.map((type) => ({
              id: type,
              label: t(`inventory.movementTypes.${type}`),
            }))}
            onChange={(value) => filter(() => setTipo(value))}
          />
          <ReferenceFilter
            id="movement-report-product"
            label={t('reports.product')}
            value={productoId}
            options={references.productos.map((p) => ({ id: p.id, label: p.nombre }))}
            onChange={(value) => filter(() => setProductoId(value))}
          />
          <ReferenceFilter
            id="movement-report-warehouse"
            label={t('reports.warehouse')}
            value={bodegaId}
            options={references.bodegas.map((b) => ({ id: b.id, label: `${b.nombre} (${b.codigo})` }))}
            onChange={(value) => filter(() => setBodegaId(value))}
          />
          <ReferenceFilter
            id="movement-report-user"
            label={t('reports.user')}
            value={usuarioId}
            options={references.usuarios.map((u) => ({ id: u.id, label: `${u.nombre} (${u.codigo})` }))}
            onChange={(value) => filter(() => setUsuarioId(value))}
          />
          <ReferenceFilter
            id="movement-report-country"
            label={t('common.country')}
            value={paisId}
            options={references.paises.map((p) => ({ id: p.id, label: `${p.nombre} (${p.codigo})` }))}
            onChange={(value) => filter(() => setPaisId(value))}
          />
          <ReferenceFilter
            id="movement-report-currency"
            label={t('reports.currency')}
            value={monedaId}
            options={references.monedas.map((m) => ({ id: m.id, label: `${m.codigo} — ${m.nombre}` }))}
            onChange={(value) => filter(() => setMonedaId(value))}
          />
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
          {t('reports.saleFiltersHelp')}
        </Typography>
      </Paper>

      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Typography variant="body2" color="text.secondary">
          {t('reports.results', { count: query.data?.totalElements ?? 0 })}
        </Typography>
        <ExportButton
          path={`/api/v1/reportes/inventario/movimientos.csv?${filters.toString()}`}
          fileName="movimientos.csv"
          disabled={invalidRange}
        />
      </Stack>

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.date')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.movementType')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('reports.product')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('reports.warehouse')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('inventory.movementQuantity')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('reports.user')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.notes')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {query.loading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : movimientos.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  {t('reports.empty')}
                </TableCell>
              </TableRow>
            ) : (
              movimientos.map((movimiento) => (
                <TableRow key={movimiento.id} hover>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {formatDate(new Date(movimiento.fechaUtc), language)}
                  </TableCell>
                  <TableCell>
                    {t(`inventory.movementTypes.${movimiento.tipo}`, { defaultValue: movimiento.tipo })}
                  </TableCell>
                  <TableCell>
                    {referenceName(references.productos, movimiento.productoId, movimiento.codigoProducto)}
                  </TableCell>
                  <TableCell>{movimiento.bodegaNombre}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {movimiento.cantidad}
                  </TableCell>
                  <TableCell>{referenceName(references.usuarios, movimiento.usuarioId, '—')}</TableCell>
                  <TableCell>{movimiento.observacion || '—'}</TableCell>
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
          rowsPerPageOptions={[10, 20, 50, 100]}
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
