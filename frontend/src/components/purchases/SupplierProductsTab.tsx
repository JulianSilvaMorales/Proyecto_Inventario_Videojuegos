import { useState } from 'react'
import {
  Alert,
  Button,
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
  TableRow,
  Typography,
} from '@mui/material'
import LinkRoundedIcon from '@mui/icons-material/LinkRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Proveedor } from '../../generated/entidades'

interface SupplierProductsTabProps {
  proveedores: Proveedor[]
  canManage: boolean
}

/** Productos oficiales de cada proveedor: solo ellos pueden pedirse en una orden de compra. */
export function SupplierProductsTab({ proveedores, canManage }: SupplierProductsTabProps) {
  const { t } = useTranslation()
  const [proveedorId, setProveedorId] = useState('')
  const [productoId, setProductoId] = useState('')
  const [linking, setLinking] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const vinculadosQuery = useApiQuery<ReferenceOption[]>(
    proveedorId ? `/api/v1/proveedores/${proveedorId}/productos` : null,
  )
  const catalogoQuery = useApiQuery<ReferenceOption[]>(
    canManage ? '/api/v1/referencias/producto' : null,
  )
  const vinculados = vinculadosQuery.data ?? []
  const proveedor = proveedores.find((item) => item.id === proveedorId)
  const disponibles = (catalogoQuery.data ?? []).filter(
    (producto) => !vinculados.some((vinculado) => vinculado.id === producto.id),
  )

  function selectSupplier(id: string) {
    setProveedorId(id)
    setProductoId('')
    setError('')
    setSuccess('')
  }

  async function link() {
    if (!proveedorId || !productoId) return
    setLinking(true)
    setError('')
    setSuccess('')
    try {
      await apiRequest(`/api/v1/proveedores/${proveedorId}/productos/${productoId}`, {
        method: 'POST',
      })
      const producto = disponibles.find((item) => item.id === productoId)
      setSuccess(t('purchases.linkSuccess', { product: producto?.nombre ?? '' }))
      setProductoId('')
      vinculadosQuery.reload()
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setLinking(false)
    }
  }

  return (
    <Stack spacing={2.5}>
      <Typography color="text.secondary">{t('purchases.supplierProductsHelp')}</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}
      {vinculadosQuery.error && <Alert severity="error">{vinculadosQuery.error}</Alert>}
      {catalogoQuery.error && <Alert severity="error">{catalogoQuery.error}</Alert>}

      <FormControl size="small" sx={{ maxWidth: 420 }}>
        <InputLabel id="supplier-products-supplier">{t('purchases.supplier')}</InputLabel>
        <Select
          labelId="supplier-products-supplier"
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

      {proveedor && canManage && proveedor.activo && (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <FormControl size="small" sx={{ minWidth: 320 }}>
            <InputLabel id="supplier-products-product">{t('purchases.productToLink')}</InputLabel>
            <Select
              labelId="supplier-products-product"
              value={productoId}
              label={t('purchases.productToLink')}
              onChange={(event) => setProductoId(event.target.value)}
            >
              {disponibles.map((producto) => (
                <MenuItem key={producto.id} value={producto.id}>
                  {producto.nombre} ({producto.codigo})
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button
            variant="contained"
            startIcon={<LinkRoundedIcon />}
            disabled={!productoId || linking}
            onClick={() => void link()}
          >
            {t('purchases.linkProduct')}
          </Button>
        </Stack>
      )}
      {proveedor && !proveedor.activo && (
        <Alert severity="warning">{t('purchases.inactiveSupplier')}</Alert>
      )}

      {proveedor && (
        <TableContainer
          component={Paper}
          elevation={0}
          sx={{ border: '1px solid', borderColor: 'divider' }}
        >
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>{t('purchases.productCode')}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{t('inventory.product')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {vinculadosQuery.loading ? (
                <TableRow>
                  <TableCell colSpan={2} align="center" sx={{ py: 4 }}>
                    <CircularProgress size={28} aria-label={t('common.loading')} />
                  </TableCell>
                </TableRow>
              ) : vinculados.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={2} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    {t('purchases.emptyLinkedProducts')}
                  </TableCell>
                </TableRow>
              ) : (
                vinculados.map((producto) => (
                  <TableRow key={producto.id} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{producto.codigo}</TableCell>
                    <TableCell>{producto.nombre}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Stack>
  )
}
