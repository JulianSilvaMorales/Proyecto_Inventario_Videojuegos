import { useState } from 'react'
import {
  Alert,
  Box,
  CircularProgress,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { PageResponse } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import { formatDate } from '../../utils/formatters'
import { referenceName } from '../../utils/references'
import { ExportButton } from './ExportButton'
import { ReferenceFilter } from './ReferenceFilter'
import type { ReportReferences } from './ReportsPage'

interface SaldoInventario {
  productoId: string
  codigoProducto: string
  nombreProducto: string
  bodegaId: string
  bodegaNombre: string
  cantidad: number
  actualizadoEnUtc: string
}

export function StockReport({ references }: { references: ReportReferences }) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const [productoId, setProductoId] = useState('')
  const [bodegaId, setBodegaId] = useState('')
  const [paisId, setPaisId] = useState('')
  const [soloConExistencia, setSoloConExistencia] = useState(false)
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(20)

  const filters = new URLSearchParams()
  if (productoId) filters.set('productoId', productoId)
  if (bodegaId) filters.set('bodegaId', bodegaId)
  if (paisId) filters.set('paisId', paisId)
  if (soloConExistencia) filters.set('soloConExistencia', 'true')
  const pageParams = new URLSearchParams(filters)
  pageParams.set('page', String(page))
  pageParams.set('size', String(rowsPerPage))
  const query = useApiQuery<PageResponse<SaldoInventario>>(
    `/api/v1/reportes/inventario/existencias?${pageParams.toString()}`,
  )
  const saldos = query.data?.content ?? []

  function filter(update: () => void) {
    update()
    setPage(0)
  }

  return (
    <Stack spacing={2.5}>
      {query.error && <Alert severity="error">{query.error}</Alert>}
      <Paper elevation={0} sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
            gap: 2,
            alignItems: 'center',
          }}
        >
          <ReferenceFilter
            id="stock-report-product"
            label={t('reports.product')}
            value={productoId}
            options={references.productos.map((p) => ({ id: p.id, label: p.nombre }))}
            onChange={(value) => filter(() => setProductoId(value))}
          />
          <ReferenceFilter
            id="stock-report-warehouse"
            label={t('reports.warehouse')}
            value={bodegaId}
            options={references.bodegas.map((b) => ({ id: b.id, label: `${b.nombre} (${b.codigo})` }))}
            onChange={(value) => filter(() => setBodegaId(value))}
          />
          <ReferenceFilter
            id="stock-report-country"
            label={t('common.country')}
            value={paisId}
            options={references.paises.map((p) => ({ id: p.id, label: `${p.nombre} (${p.codigo})` }))}
            onChange={(value) => filter(() => setPaisId(value))}
          />
          <FormControlLabel
            control={
              <Switch
                checked={soloConExistencia}
                onChange={(event) => filter(() => setSoloConExistencia(event.target.checked))}
              />
            }
            label={t('inventory.onlyWithStock')}
          />
        </Box>
      </Paper>

      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Typography variant="body2" color="text.secondary">
          {t('reports.results', { count: query.data?.totalElements ?? 0 })}
        </Typography>
        <ExportButton
          path={`/api/v1/reportes/inventario/existencias.csv?${filters.toString()}`}
          fileName="existencias.csv"
        />
      </Stack>

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('reports.product')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('reports.warehouse')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('inventory.quantity')}
              </TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.lastUpdated')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {query.loading ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : saldos.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  {t('reports.empty')}
                </TableCell>
              </TableRow>
            ) : (
              saldos.map((saldo) => (
                <TableRow key={`${saldo.productoId}-${saldo.bodegaId}`} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {referenceName(references.productos, saldo.productoId, saldo.nombreProducto)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {saldo.codigoProducto}
                    </Typography>
                  </TableCell>
                  <TableCell>{saldo.bodegaNombre}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {saldo.cantidad}
                  </TableCell>
                  <TableCell>{formatDate(new Date(saldo.actualizadoEnUtc), language)}</TableCell>
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
