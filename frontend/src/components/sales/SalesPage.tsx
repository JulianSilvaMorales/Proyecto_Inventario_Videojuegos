import { useState } from 'react'
import {
  Alert,
  Button,
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
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useTranslation } from 'react-i18next'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { CrudEntityMetadata } from '../../crud/metadata'
import type { Bodega } from '../../generated/entidades'
import { formatDate, formatMoney } from '../../utils/formatters'
import { CrudEntityLinks } from '../CrudEntityLinks'
import { PageHeader } from '../PageHeader'
import { SaleDetailDialog } from './SaleDetailDialog'
import { SaleFormDialog } from './SaleFormDialog'
import {
  SALE_STATE_COLORS,
  SALE_STATES,
  type PaisVenta,
  type RespuestaVenta,
  type Venta,
} from './salesTypes'

interface SalesPageProps {
  permissions: readonly string[]
  onSelectEntity: (entity: CrudEntityMetadata) => void
}

export function SalesPage({ permissions, onSelectEntity }: SalesPageProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const canSell = permissions.includes('VENTAS_GESTIONAR')
  const canCancel = permissions.includes('VENTAS_ANULAR_PENDIENTES')
  const canSeeClients = permissions.includes('CLIENTES_GESTIONAR')
  const [estado, setEstado] = useState('')
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [formKey, setFormKey] = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const params = new URLSearchParams({
    page: String(page),
    size: String(rowsPerPage),
  })
  if (estado) params.set('estado', estado)
  const ventasQuery = useApiQuery<PageResponse<Venta>>(
    canSell ? `/api/v1/ventas?${params.toString()}` : null,
  )
  const paisesQuery = useApiQuery<PaisVenta[]>(canSell ? '/api/v1/ventas/paises' : null)
  const bodegasQuery = useApiQuery<PageResponse<Bodega>>(
    canSell ? '/api/v1/inventario/bodegas?size=100' : null,
  )
  const productosQuery = useApiQuery<ReferenceOption[]>(
    canSell ? '/api/v1/referencias/producto' : null,
  )
  const paises = paisesQuery.data ?? []
  const productos = productosQuery.data ?? []
  const ventas = ventasQuery.data?.content ?? []
  const loadError = ventasQuery.error || paisesQuery.error || bodegasQuery.error

  function handleCreated(respuesta: RespuestaVenta) {
    setFormKey(null)
    setSelectedId(respuesta.venta.id)
    ventasQuery.reload()
  }

  const decimalsOf = (venta: Venta) =>
    paises.find((pais) => pais.paisId === venta.paisId)?.monedaDecimales

  return (
    <Stack spacing={3}>
      <PageHeader
        section={t('modules.sales.title')}
        title={t('sales.title')}
        subtitle={t('sales.subtitle')}
        actions={
          canSell && (
            <Button
              variant="contained"
              startIcon={<AddRoundedIcon />}
              onClick={() => setFormKey(Date.now())}
            >
              {t('sales.newSale')}
            </Button>
          )
        }
      />

      <CrudEntityLinks module="sales" permissions={permissions} onSelectEntity={onSelectEntity} />

      {loadError && <Alert severity="error">{loadError}</Alert>}

      {canSell && (
        <>
          <FormControl size="small" sx={{ width: 260 }}>
            <InputLabel id="sales-state">{t('sales.status')}</InputLabel>
            <Select
              labelId="sales-state"
              value={estado}
              label={t('sales.status')}
              onChange={(event) => {
                setEstado(event.target.value)
                setPage(0)
              }}
            >
              <MenuItem value="">{t('common.all')}</MenuItem>
              {SALE_STATES.map((state) => (
                <MenuItem key={state} value={state}>
                  {t(`sales.states.${state}`)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TableContainer
            component={Paper}
            elevation={0}
            sx={{ border: '1px solid', borderColor: 'divider' }}
          >
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>{t('sales.number')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('sales.date')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('common.country')}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {t('sales.total')}
                  </TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('sales.status')}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {t('crud.actions')}
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {ventasQuery.loading ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                      <CircularProgress size={28} aria-label={t('common.loading')} />
                    </TableCell>
                  </TableRow>
                ) : ventas.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      {t('sales.empty')}
                    </TableCell>
                  </TableRow>
                ) : (
                  ventas.map((venta) => (
                    <TableRow key={venta.id} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{venta.numero}</TableCell>
                      <TableCell>{formatDate(new Date(venta.creadaEnUtc), language)}</TableCell>
                      <TableCell>{venta.paisCodigo}</TableCell>
                      <TableCell align="right">
                        {formatMoney(venta.total, venta.monedaCodigo, language, decimalsOf(venta))}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={SALE_STATE_COLORS[venta.estado] ?? 'default'}
                          label={t(`sales.states.${venta.estado}`, { defaultValue: venta.estado })}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          startIcon={<VisibilityOutlinedIcon />}
                          onClick={() => setSelectedId(venta.id)}
                        >
                          {t('sales.view')}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={ventasQuery.data?.totalElements ?? 0}
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
        </>
      )}

      {formKey !== null && (
        <SaleFormDialog
          key={formKey}
          paises={paises}
          bodegas={bodegasQuery.data?.content ?? []}
          productos={productos}
          canSearchClients={canSeeClients}
          onClose={() => setFormKey(null)}
          onCreated={handleCreated}
        />
      )}

      {selectedId && (
        <SaleDetailDialog
          key={selectedId}
          ventaId={selectedId}
          paises={paises}
          productos={productos}
          canCancel={canCancel}
          canSeeClients={canSeeClients}
          onClose={() => setSelectedId(null)}
          onChanged={ventasQuery.reload}
        />
      )}
    </Stack>
  )
}
