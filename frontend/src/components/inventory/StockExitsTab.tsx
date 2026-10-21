import { useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import RemoveCircleOutlineRoundedIcon from '@mui/icons-material/RemoveCircleOutlineRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { ReferenceOption } from '../../api/types'
import type { Bodega, Proveedor } from '../../generated/entidades'

interface StockExitsTabProps {
  bodegas: Bodega[]
  proveedores: Proveedor[]
  productos: ReferenceOption[]
}

const EXIT_TYPES = ['PERDIDA', 'DANO', 'ROBO', 'DEFECTO'] as const
type ExitType = (typeof EXIT_TYPES)[number]

export function StockExitsTab({ bodegas, proveedores, productos }: StockExitsTabProps) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [bodegaId, setBodegaId] = useState('')
  const [productoId, setProductoId] = useState('')
  const [tipo, setTipo] = useState<ExitType>('PERDIDA')
  const [cantidad, setCantidad] = useState('1')
  const [proveedorId, setProveedorId] = useState('')
  const [numeroReporteProveedor, setNumeroReporteProveedor] = useState('')
  const [observacion, setObservacion] = useState('')

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setSuccess('')

    const quantity = Number(cantidad)
    if (!bodegaId || !productoId || !proveedorId || !numeroReporteProveedor.trim()) {
      setError(t('validation.requiredFields'))
      return
    }
    if (!Number.isInteger(quantity) || quantity <= 0) {
      setError(t('validation.positiveInteger', { field: t('inventory.exitQuantity') }))
      return
    }

    setSaving(true)
    try {
      await apiRequest('/api/v1/inventario/salidas', {
        method: 'POST',
        body: JSON.stringify({
          bodegaId,
          productoId,
          tipo,
          cantidad: quantity,
          proveedorId,
          numeroReporteProveedor: numeroReporteProveedor.trim(),
          observacion: observacion.trim() || null,
        }),
      })
      setSuccess(t('inventory.exitSuccess'))
      setNumeroReporteProveedor('')
      setObservacion('')
      setCantidad('1')
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 800 }}>
      <Box>
        <Typography variant="h6">{t('inventory.tabs.exits')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t('inventory.exitsDescription')}
        </Typography>
      </Box>

      {error && <Alert severity="error">{error}</Alert>}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}

      <Paper
        component="form"
        onSubmit={handleSubmit}
        noValidate
        elevation={0}
        sx={{ p: 3, border: '1px solid', borderColor: 'divider' }}
      >
        <Stack spacing={2.5}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
              gap: 2.5,
            }}
          >
            <FormControl fullWidth size="small" required>
              <InputLabel id="exit-warehouse">{t('inventory.warehouse')}</InputLabel>
              <Select
                labelId="exit-warehouse"
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
              <InputLabel id="exit-product">{t('inventory.product')}</InputLabel>
              <Select
                labelId="exit-product"
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

            <FormControl fullWidth size="small" required>
              <InputLabel id="exit-type">{t('inventory.exitType')}</InputLabel>
              <Select
                labelId="exit-type"
                value={tipo}
                label={t('inventory.exitType')}
                onChange={(event) => setTipo(event.target.value as ExitType)}
              >
                {EXIT_TYPES.map((exitType) => (
                  <MenuItem key={exitType} value={exitType}>
                    {t(`inventory.exitTypes.${exitType}`)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              fullWidth
              size="small"
              required
              type="number"
              label={t('inventory.exitQuantity')}
              value={cantidad}
              onChange={(event) => setCantidad(event.target.value)}
              slotProps={{ htmlInput: { min: 1, step: 1 } }}
            />

            <FormControl fullWidth size="small" required>
              <InputLabel id="exit-supplier">{t('inventory.supplier')}</InputLabel>
              <Select
                labelId="exit-supplier"
                value={proveedorId}
                label={t('inventory.supplier')}
                onChange={(event) => setProveedorId(event.target.value)}
              >
                {proveedores.map((proveedor) => (
                  <MenuItem key={proveedor.id} value={proveedor.id}>
                    {proveedor.razonSocial}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              fullWidth
              size="small"
              required
              label={t('inventory.supplierReport')}
              value={numeroReporteProveedor}
              onChange={(event) => setNumeroReporteProveedor(event.target.value)}
              slotProps={{ htmlInput: { maxLength: 120 } }}
            />
          </Box>

          <TextField
            fullWidth
            multiline
            rows={3}
            size="small"
            label={t('inventory.notes')}
            value={observacion}
            onChange={(event) => setObservacion(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />

          <Box>
            <Button
              type="submit"
              variant="contained"
              color="error"
              startIcon={<RemoveCircleOutlineRoundedIcon />}
              disabled={saving}
            >
              {saving ? t('crud.saving') : t('inventory.registerExit')}
            </Button>
          </Box>
        </Stack>
      </Paper>
    </Stack>
  )
}
