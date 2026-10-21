import { useState } from 'react'
import {
  Alert,
  Box,
  CircularProgress,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Paper,
  Select,
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
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Bodega } from '../../generated/entidades'
import { formatDate } from '../../utils/formatters'
import { referenceName } from '../../utils/references'

interface SaldoInventario {
  productoId: string
  codigoProducto: string
  nombreProducto: string
  bodegaId: string
  bodegaCodigo: string
  bodegaNombre: string
  cantidad: number
  actualizadoEnUtc: string
}

interface StockBalancesTabProps {
  bodegas: Bodega[]
  paises: ReferenceOption[]
  productos: ReferenceOption[]
}

export function StockBalancesTab({ bodegas, paises, productos }: StockBalancesTabProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const [bodegaId, setBodegaId] = useState('')
  const [paisId, setPaisId] = useState('')
  const [soloConExistencia, setSoloConExistencia] = useState(false)
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)

  const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) })
  if (bodegaId) params.set('bodegaId', bodegaId)
  if (paisId) params.set('paisId', paisId)
  if (soloConExistencia) params.set('soloConExistencia', 'true')
  const query = useApiQuery<PageResponse<SaldoInventario>>(
    `/api/v1/inventario/saldos?${params.toString()}`,
  )
  const saldos = query.data?.content ?? []

  return (
    <Stack spacing={2.5}>
      {query.error && <Alert severity="error">{query.error}</Alert>}

      <Paper elevation={0} sx={{ p: 2, border: '1px solid', borderColor: 'divider' }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' },
            gap: 2,
            alignItems: 'center',
          }}
        >
          <FormControl fullWidth size="small">
            <InputLabel id="balances-warehouse">{t('inventory.warehouse')}</InputLabel>
            <Select
              labelId="balances-warehouse"
              value={bodegaId}
              label={t('inventory.warehouse')}
              onChange={(event) => {
                setBodegaId(event.target.value)
                setPage(0)
              }}
            >
              <MenuItem value="">{t('inventory.allWarehouses')}</MenuItem>
              {bodegas.map((bodega) => (
                <MenuItem key={bodega.id} value={bodega.id}>
                  {bodega.nombre} ({bodega.codigo})
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {paises.length > 0 && (
            <FormControl fullWidth size="small">
              <InputLabel id="balances-country">{t('common.country')}</InputLabel>
              <Select
                labelId="balances-country"
                value={paisId}
                label={t('common.country')}
                onChange={(event) => {
                  setPaisId(event.target.value)
                  setPage(0)
                }}
              >
                <MenuItem value="">{t('common.allCountries')}</MenuItem>
                {paises.map((pais) => (
                  <MenuItem key={pais.id} value={pais.id}>
                    {pais.nombre} ({pais.codigo})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          <FormControlLabel
            control={
              <Switch
                checked={soloConExistencia}
                onChange={(event) => {
                  setSoloConExistencia(event.target.checked)
                  setPage(0)
                }}
              />
            }
            label={t('inventory.onlyWithStock')}
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
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.product')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('inventory.warehouse')}</TableCell>
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
                  {t('inventory.emptyBalances')}
                </TableCell>
              </TableRow>
            ) : (
              saldos.map((saldo) => (
                <TableRow key={`${saldo.productoId}-${saldo.bodegaId}`} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {referenceName(productos, saldo.productoId, saldo.nombreProducto)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {saldo.codigoProducto}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {saldo.bodegaNombre} ({saldo.bodegaCodigo})
                  </TableCell>
                  <TableCell align="right">
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: 700,
                        color: saldo.cantidad > 0 ? 'success.main' : 'text.secondary',
                      }}
                    >
                      {saldo.cantidad}
                    </Typography>
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
