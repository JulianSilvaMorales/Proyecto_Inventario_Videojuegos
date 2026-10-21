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
  IconButton,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import BlockRoundedIcon from '@mui/icons-material/BlockRounded'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Categoria, CrudEntityName } from '../../generated/entidades'
import { referenceName } from '../../utils/references'
import { PageHeader } from '../PageHeader'
import { ProductFormDialog, type ProductoResumen, type TipoProducto } from './ProductFormDialog'

interface CatalogPageProps {
  permissions: readonly string[]
  canOpenCategories: boolean
  onOpenCrudEntity: (entityName: CrudEntityName) => void
}

type CatalogTab = 'products' | 'categories'

export function CatalogPage({ permissions, canOpenCategories, onOpenCrudEntity }: CatalogPageProps) {
  const { t } = useTranslation()
  const canManage = permissions.includes('PRODUCTOS_GESTIONAR')
  const [activeTab, setActiveTab] = useState<CatalogTab>('products')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(10)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  // `null` cierra el formulario; '' crea un producto; un id lo edita.
  const [formProductId, setFormProductId] = useState<string | null>(null)
  const [formKey, setFormKey] = useState(0)
  const [deactivateId, setDeactivateId] = useState<string | null>(null)
  const [deactivating, setDeactivating] = useState(false)

  const tiposQuery = useApiQuery<PageResponse<TipoProducto>>('/api/v1/catalogo/tipos-producto?size=100')
  const categoriasQuery = useApiQuery<PageResponse<Categoria>>('/api/v1/catalogo/categorias?size=100')
  const nombresQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/producto')
  const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) })
  if (search) params.set('buscar', search)
  const productosQuery = useApiQuery<PageResponse<ProductoResumen>>(
    `/api/v1/catalogo/productos?${params.toString()}`,
  )

  const tipos = tiposQuery.data?.content ?? []
  const categorias = categoriasQuery.data?.content ?? []
  const nombres = nombresQuery.data ?? []
  const productos = productosQuery.data?.content ?? []
  const loadError = productosQuery.error || tiposQuery.error || categoriasQuery.error

  function openForm(productId: string) {
    setSuccess('')
    setFormKey((value) => value + 1)
    setFormProductId(productId)
  }

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  function refreshProducts() {
    productosQuery.reload()
    nombresQuery.reload()
  }

  async function confirmDeactivate() {
    if (!deactivateId) return
    setDeactivating(true)
    setError('')
    try {
      await apiRequest(`/api/v1/catalogo/productos/${deactivateId}`, { method: 'DELETE' })
      setSuccess(t('catalog.deactivatedSuccess'))
      setDeactivateId(null)
      refreshProducts()
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setDeactivating(false)
    }
  }

  const nameOf = (id: string, fallback: string) => referenceName(nombres, id, fallback)
  const categoryName = (id: string) => categorias.find((categoria) => categoria.id === id)?.nombre ?? id
  const productoADesactivar = productos.find((producto) => producto.id === deactivateId)

  return (
    <Stack spacing={3}>
      <PageHeader
        section={t('modules.catalog.title')}
        title={t('catalog.title')}
        subtitle={t('catalog.subtitle')}
      />

      {(error || loadError) && (
        <Alert severity="error" onClose={error ? () => setError('') : undefined}>
          {error || loadError}
        </Alert>
      )}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={activeTab} onChange={(_, value: CatalogTab) => setActiveTab(value)}>
          <Tab value="products" label={t('catalog.tabProducts')} />
          {canOpenCategories && <Tab value="categories" label={t('catalog.tabCategories')} />}
        </Tabs>
      </Box>

      {activeTab === 'products' && (
        <Stack spacing={2}>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={2}
            sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}
          >
            <Box
              component="form"
              onSubmit={handleSearch}
              sx={{ display: 'flex', gap: 1, flex: 1, maxWidth: 450 }}
            >
              <TextField
                size="small"
                fullWidth
                placeholder={t('catalog.searchByCode')}
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                slotProps={{
                  input: { startAdornment: <SearchRoundedIcon color="action" sx={{ mr: 1 }} /> },
                  htmlInput: { 'aria-label': t('catalog.searchByCode') },
                }}
              />
              <Button type="submit" variant="outlined" size="small">
                {t('common.filter')}
              </Button>
            </Box>
            {canManage && (
              <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => openForm('')}>
                {t('catalog.createProduct')}
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
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.name')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.type')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.category')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.subcategory')}</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>{t('catalog.status')}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {t('crud.actions')}
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {productosQuery.loading ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                      <CircularProgress size={28} aria-label={t('common.loading')} />
                    </TableCell>
                  </TableRow>
                ) : productos.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      {t('catalog.empty')}
                    </TableCell>
                  </TableRow>
                ) : (
                  productos.map((producto) => (
                    <TableRow key={producto.id} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {nameOf(producto.id, producto.codigoInterno)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {producto.codigoInterno}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {tipos.find((tipo) => tipo.id === producto.tipoProductoId)?.nombre ??
                          producto.tipoProductoId}
                      </TableCell>
                      <TableCell>{categoryName(producto.categoriaId)}</TableCell>
                      <TableCell>{categoryName(producto.subcategoriaId)}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={producto.activo ? t('catalog.active') : t('catalog.inactive')}
                          color={producto.activo ? 'success' : 'default'}
                          variant={producto.activo ? 'filled' : 'outlined'}
                        />
                      </TableCell>
                      <TableCell align="right">
                        {canManage && (
                          <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                            <Tooltip title={t('crud.edit')}>
                              <IconButton
                                size="small"
                                aria-label={`${t('crud.edit')} ${producto.codigoInterno}`}
                                onClick={() => openForm(producto.id)}
                              >
                                <EditOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            {producto.activo && (
                              <Tooltip title={t('crud.deactivate')}>
                                <IconButton
                                  size="small"
                                  color="error"
                                  aria-label={`${t('crud.deactivate')} ${producto.codigoInterno}`}
                                  onClick={() => setDeactivateId(producto.id)}
                                >
                                  <BlockRoundedIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            )}
                          </Stack>
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={productosQuery.data?.totalElements ?? 0}
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
      )}

      {activeTab === 'categories' && canOpenCategories && (
        <Paper
          elevation={0}
          sx={{ p: 4, border: '1px solid', borderColor: 'divider', textAlign: 'center' }}
        >
          <Typography variant="h6">{t('catalog.tabCategories')}</Typography>
          <Typography color="text.secondary" sx={{ mt: 1, mb: 3 }}>
            {t('catalog.categoriesDescription')}
          </Typography>
          <Button variant="contained" onClick={() => onOpenCrudEntity('categoria')}>
            {t('catalog.openCategories')}
          </Button>
        </Paper>
      )}

      {formProductId !== null && (
        <ProductFormDialog
          key={formKey}
          productoId={formProductId || null}
          tiposProducto={tipos}
          categorias={categorias}
          onClose={() => setFormProductId(null)}
          onSaved={() => {
            setFormProductId(null)
            setSuccess(t('catalog.savedSuccess'))
            refreshProducts()
          }}
        />
      )}

      <Dialog open={Boolean(deactivateId)} onClose={() => setDeactivateId(null)}>
        <DialogTitle>{t('catalog.deactivateTitle')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            {t('catalog.deactivateMessage', {
              code: productoADesactivar
                ? nameOf(productoADesactivar.id, productoADesactivar.codigoInterno)
                : '',
            })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeactivateId(null)} disabled={deactivating}>
            {t('crud.cancel')}
          </Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => void confirmDeactivate()}
            disabled={deactivating}
          >
            {t('crud.deactivate')}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
