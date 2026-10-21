import { useEffect, useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../api/apiClient'
import { apiErrorMessage } from '../../api/errors'
import type { DecimalValue, PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Categoria } from '../../generated/entidades'
import { parseDecimal } from '../../utils/decimal'
import { formatMoney } from '../../utils/formatters'

export interface TipoProducto {
  id: string
  codigo: string
  nombre: string
  activo: boolean
}

interface DefinicionAtributo {
  id: string
  nombre: string
  tipoDato: 'TEXTO' | 'ENTERO' | 'DECIMAL' | 'BOOLEANO' | 'JSON'
  obligatorio: boolean
  opciones: unknown[] | null
}

export interface ProductoResumen {
  id: string
  codigoInterno: string
  tipoProductoId: string
  categoriaId: string
  subcategoriaId: string
  atributos: Record<string, unknown>
  activo: boolean
}

interface ProductoTraduccion {
  id: string
  idiomaId: string
  nombre: string
  descripcion: string | null
}

interface PrecioProducto {
  id: string
  monedaId: string
  monedaCodigo: string
  importe: DecimalValue
  vigenteDesde: string
  vigenteHasta: string | null
}

interface DetalleProducto {
  producto: ProductoResumen
  traducciones: ProductoTraduccion[]
  precios: PrecioProducto[]
}

interface TranslationDraft {
  id?: string
  nombre: string
  descripcion: string
  original?: { nombre: string; descripcion: string }
}

interface PriceDraft {
  key: number
  monedaId: string
  importe: string
  vigenteDesde: string
  vigenteHasta: string
}

interface ProductFormDialogProps {
  productoId: string | null
  tiposProducto: TipoProducto[]
  categorias: Categoria[]
  onClose: () => void
  onSaved: () => void
}

const PRICE_SCALE = 4

function today() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

let priceKey = 0
function emptyPrice(): PriceDraft {
  priceKey += 1
  return { key: priceKey, monedaId: '', importe: '', vigenteDesde: today(), vigenteHasta: '' }
}

function attributeText(value: unknown, tipoDato: DefinicionAtributo['tipoDato']) {
  if (value === undefined || value === null) return ''
  if (tipoDato === 'JSON') return JSON.stringify(value)
  return String(value)
}

/** Formulario de creación o edición; se monta de nuevo cada vez que se abre. */
export function ProductFormDialog({
  productoId,
  tiposProducto,
  categorias,
  onClose,
  onSaved,
}: ProductFormDialogProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const editing = productoId !== null
  const [loadingDetail, setLoadingDetail] = useState(editing)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const [tipoProductoId, setTipoProductoId] = useState('')
  const [categoriaId, setCategoriaId] = useState('')
  const [subcategoriaId, setSubcategoriaId] = useState('')
  const [atributos, setAtributos] = useState<Record<string, string | boolean>>({})
  const [traducciones, setTraducciones] = useState<Record<string, TranslationDraft>>({})
  const [preciosVigentes, setPreciosVigentes] = useState<PrecioProducto[]>([])
  const [preciosNuevos, setPreciosNuevos] = useState<PriceDraft[]>(editing ? [] : [emptyPrice()])

  const idiomasQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/idioma')
  const monedasQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/moneda')
  const definicionesQuery = useApiQuery<PageResponse<DefinicionAtributo>>(
    tipoProductoId
      ? `/api/v1/catalogo/definiciones-atributos?tipoProductoId=${tipoProductoId}&size=100`
      : null,
  )
  const idiomas = idiomasQuery.data ?? []
  const monedas = monedasQuery.data ?? []
  const definiciones = definicionesQuery.data?.content ?? []
  const referenceError = idiomasQuery.error || monedasQuery.error || definicionesQuery.error

  useEffect(() => {
    if (!productoId) return
    let active = true
    apiRequest<DetalleProducto>(`/api/v1/catalogo/productos/${productoId}`)
      .then((detalle) => {
        if (!active || !detalle) return
        const producto = detalle.producto
        setTipoProductoId(producto.tipoProductoId)
        setCategoriaId(producto.categoriaId)
        setSubcategoriaId(producto.subcategoriaId)
        setAtributos(
          Object.fromEntries(
            Object.entries(producto.atributos ?? {}).map(([name, value]) => [
              name,
              typeof value === 'boolean'
                ? value
                : typeof value === 'object'
                  ? JSON.stringify(value)
                  : String(value),
            ]),
          ),
        )
        setTraducciones(
          Object.fromEntries(
            detalle.traducciones.map((traduccion) => {
              const draft = { nombre: traduccion.nombre, descripcion: traduccion.descripcion ?? '' }
              return [traduccion.idiomaId, { id: traduccion.id, ...draft, original: draft }]
            }),
          ),
        )
        setPreciosVigentes(detalle.precios)
        setLoadingDetail(false)
      })
      .catch((reason: unknown) => {
        if (!active) return
        setError(apiErrorMessage(reason, t))
        setLoadingDetail(false)
      })
    return () => {
      active = false
    }
  }, [productoId, t])

  const raices = categorias.filter((categoria) => !categoria.padreId && categoria.activo)
  const subcategorias = categorias.filter(
    (categoria) => categoria.padreId === categoriaId && categoria.activo,
  )

  function translation(idiomaId: string): TranslationDraft {
    return traducciones[idiomaId] ?? { nombre: '', descripcion: '' }
  }

  function updateTranslation(idiomaId: string, field: 'nombre' | 'descripcion', value: string) {
    setTraducciones((previous) => ({
      ...previous,
      [idiomaId]: { ...translation(idiomaId), ...previous[idiomaId], [field]: value },
    }))
  }

  function updatePrice(key: number, field: keyof Omit<PriceDraft, 'key'>, value: string) {
    setPreciosNuevos((previous) =>
      previous.map((precio) => (precio.key === key ? { ...precio, [field]: value } : precio)),
    )
  }

  function buildAttributes(): Record<string, unknown> | null {
    const result: Record<string, unknown> = {}
    for (const definicion of definiciones) {
      const raw = atributos[definicion.nombre]
      if (definicion.tipoDato === 'BOOLEANO') {
        if (raw !== undefined) result[definicion.nombre] = Boolean(raw)
        else if (definicion.obligatorio) result[definicion.nombre] = false
        continue
      }
      const text = typeof raw === 'string' ? raw.trim() : ''
      if (!text) {
        if (definicion.obligatorio) {
          setError(t('catalog.attributeRequired', { name: definicion.nombre }))
          return null
        }
        continue
      }
      if (definicion.tipoDato === 'ENTERO' || definicion.tipoDato === 'DECIMAL') {
        const number = Number(text)
        if (
          !Number.isFinite(number) ||
          (definicion.tipoDato === 'ENTERO' && !Number.isInteger(number))
        ) {
          setError(t('catalog.attributeInvalid', { name: definicion.nombre }))
          return null
        }
        result[definicion.nombre] = number
      } else if (definicion.tipoDato === 'JSON') {
        try {
          result[definicion.nombre] = JSON.parse(text)
        } catch {
          setError(t('catalog.attributeInvalid', { name: definicion.nombre }))
          return null
        }
      } else {
        result[definicion.nombre] = text
      }
    }
    return result
  }

  function buildPrices() {
    const precios = []
    for (const precio of preciosNuevos) {
      const importe = parseDecimal(precio.importe, PRICE_SCALE)
      if (!precio.monedaId || importe === null || !precio.vigenteDesde) {
        setError(t('catalog.priceInvalid'))
        return null
      }
      if (precio.vigenteHasta && precio.vigenteHasta < precio.vigenteDesde) {
        setError(t('validation.dateRange'))
        return null
      }
      precios.push({
        key: precio.key,
        body: {
          monedaId: precio.monedaId,
          importe,
          vigenteDesde: precio.vigenteDesde,
          vigenteHasta: precio.vigenteHasta || null,
        },
      })
    }
    return precios
  }

  async function saveChanges(productId: string, atributosValidos: Record<string, unknown>,
    precios: NonNullable<ReturnType<typeof buildPrices>>) {
    await apiRequest(`/api/v1/catalogo/productos/${productId}`, {
      method: 'PUT',
      body: JSON.stringify({ tipoProductoId, categoriaId, subcategoriaId, atributos: atributosValidos }),
    })

    for (const [idiomaId, draft] of Object.entries(traducciones)) {
      const body = JSON.stringify({
        idiomaId,
        nombre: draft.nombre.trim(),
        descripcion: draft.descripcion.trim() || null,
      })
      let id = draft.id
      if (id) {
        const changed =
          draft.nombre !== draft.original?.nombre || draft.descripcion !== draft.original?.descripcion
        if (!changed) continue
        await apiRequest(`/api/v1/catalogo/productos/${productId}/traducciones/${id}`, {
          method: 'PUT',
          body,
        })
      } else {
        if (!draft.nombre.trim()) continue
        const created = await apiRequest<ProductoTraduccion>(
          `/api/v1/catalogo/productos/${productId}/traducciones`,
          { method: 'POST', body },
        )
        id = created?.id
      }
      // Lo guardado se conserva para que un reintento no lo repita.
      const saved: TranslationDraft = {
        ...draft,
        id,
        original: { nombre: draft.nombre, descripcion: draft.descripcion },
      }
      setTraducciones((previous) => ({ ...previous, [idiomaId]: saved }))
    }

    for (const precio of precios) {
      const created = await apiRequest<PrecioProducto>(
        `/api/v1/catalogo/productos/${productId}/precios`,
        { method: 'POST', body: JSON.stringify(precio.body) },
      )
      setPreciosNuevos((previous) => previous.filter((draft) => draft.key !== precio.key))
      if (created) setPreciosVigentes((previous) => [...previous, created])
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!tipoProductoId || !categoriaId || !subcategoriaId) {
      setError(t('validation.requiredFields'))
      return
    }
    const atributosValidos = buildAttributes()
    if (!atributosValidos) return
    const nombres = Object.values(traducciones).filter((draft) => draft.nombre.trim())
    if (nombres.length === 0) {
      setError(t('catalog.nameRequired'))
      return
    }
    const precios = buildPrices()
    if (!precios) return
    if (!editing && precios.length === 0) {
      setError(t('catalog.priceRequired'))
      return
    }

    setSaving(true)
    try {
      if (productoId) {
        await saveChanges(productoId, atributosValidos, precios)
      } else {
        await apiRequest('/api/v1/catalogo/productos', {
          method: 'POST',
          body: JSON.stringify({
            tipoProductoId,
            categoriaId,
            subcategoriaId,
            atributos: atributosValidos,
            traducciones: Object.entries(traducciones)
              .filter(([, draft]) => draft.nombre.trim())
              .map(([idiomaId, draft]) => ({
                idiomaId,
                nombre: draft.nombre.trim(),
                descripcion: draft.descripcion.trim() || null,
              })),
            precios: precios.map((precio) => precio.body),
          }),
        })
      }
      onSaved()
    } catch (reason) {
      setError(apiErrorMessage(reason, t))
    } finally {
      setSaving(false)
    }
  }

  const monedaCodigo = (id: string) => monedas.find((moneda) => moneda.id === id)?.codigo

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={handleSubmit} noValidate>
        <DialogTitle>{editing ? t('catalog.editProduct') : t('catalog.createProduct')}</DialogTitle>
        <DialogContent dividers>
          {loadingDetail ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
              <CircularProgress aria-label={t('common.loading')} />
            </Box>
          ) : (
            <Stack spacing={3}>
              {(error || referenceError) && (
                <Alert severity="error">{error || referenceError}</Alert>
              )}

              <Typography variant="subtitle2" color="primary">
                {t('catalog.sections.classification')}
              </Typography>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' },
                  gap: 2,
                }}
              >
                <FormControl fullWidth required size="small">
                  <InputLabel id="product-type">{t('catalog.type')}</InputLabel>
                  <Select
                    labelId="product-type"
                    value={tipoProductoId}
                    label={t('catalog.type')}
                    onChange={(event) => {
                      setTipoProductoId(event.target.value)
                      setAtributos({})
                    }}
                  >
                    {tiposProducto
                      .filter((tipo) => tipo.activo || tipo.id === tipoProductoId)
                      .map((tipo) => (
                        <MenuItem key={tipo.id} value={tipo.id}>
                          {tipo.nombre}
                        </MenuItem>
                      ))}
                  </Select>
                </FormControl>

                <FormControl fullWidth required size="small">
                  <InputLabel id="product-category">{t('catalog.category')}</InputLabel>
                  <Select
                    labelId="product-category"
                    value={categoriaId}
                    label={t('catalog.category')}
                    onChange={(event) => {
                      setCategoriaId(event.target.value)
                      setSubcategoriaId('')
                    }}
                  >
                    {raices.map((categoria) => (
                      <MenuItem key={categoria.id} value={categoria.id}>
                        {categoria.nombre}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <FormControl
                  fullWidth
                  required
                  size="small"
                  disabled={!categoriaId || subcategorias.length === 0}
                >
                  <InputLabel id="product-subcategory">{t('catalog.subcategory')}</InputLabel>
                  <Select
                    labelId="product-subcategory"
                    value={subcategoriaId}
                    label={t('catalog.subcategory')}
                    onChange={(event) => setSubcategoriaId(event.target.value)}
                  >
                    {subcategorias.map((categoria) => (
                      <MenuItem key={categoria.id} value={categoria.id}>
                        {categoria.nombre}
                      </MenuItem>
                    ))}
                  </Select>
                  {categoriaId && subcategorias.length === 0 && (
                    <FormHelperText>{t('catalog.noSubcategories')}</FormHelperText>
                  )}
                </FormControl>
              </Box>

              <Divider />
              <Typography variant="subtitle2" color="primary">
                {t('catalog.sections.attributes')}
              </Typography>
              {!tipoProductoId ? (
                <Typography variant="body2" color="text.secondary">
                  {t('catalog.selectTypeFirst')}
                </Typography>
              ) : definicionesQuery.loading ? (
                <CircularProgress size={24} aria-label={t('common.loading')} />
              ) : definiciones.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  {t('catalog.noAttributes')}
                </Typography>
              ) : (
                <Box
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                    gap: 2,
                  }}
                >
                  {definiciones.map((definicion) => {
                    const value = atributos[definicion.nombre]
                    if (definicion.tipoDato === 'BOOLEANO') {
                      return (
                        <FormControlLabel
                          key={definicion.id}
                          control={
                            <Switch
                              checked={value === true}
                              onChange={(event) =>
                                setAtributos((previous) => ({
                                  ...previous,
                                  [definicion.nombre]: event.target.checked,
                                }))
                              }
                            />
                          }
                          label={definicion.nombre}
                        />
                      )
                    }
                    const text = typeof value === 'string' ? value : attributeText(value, definicion.tipoDato)
                    const setText = (next: string) =>
                      setAtributos((previous) => ({ ...previous, [definicion.nombre]: next }))
                    if (definicion.opciones && definicion.opciones.length > 0) {
                      return (
                        <FormControl
                          key={definicion.id}
                          fullWidth
                          size="small"
                          required={definicion.obligatorio}
                        >
                          <InputLabel id={`attribute-${definicion.id}`}>{definicion.nombre}</InputLabel>
                          <Select
                            labelId={`attribute-${definicion.id}`}
                            value={text}
                            label={definicion.nombre}
                            onChange={(event) => setText(event.target.value)}
                          >
                            {!definicion.obligatorio && <MenuItem value="">—</MenuItem>}
                            {definicion.opciones.map((option) => (
                              <MenuItem key={String(option)} value={String(option)}>
                                {String(option)}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      )
                    }
                    return (
                      <TextField
                        key={definicion.id}
                        fullWidth
                        size="small"
                        label={definicion.nombre}
                        required={definicion.obligatorio}
                        value={text}
                        multiline={definicion.tipoDato === 'JSON'}
                        onChange={(event) => setText(event.target.value)}
                        slotProps={{
                          htmlInput: {
                            inputMode:
                              definicion.tipoDato === 'ENTERO'
                                ? 'numeric'
                                : definicion.tipoDato === 'DECIMAL'
                                  ? 'decimal'
                                  : undefined,
                          },
                        }}
                      />
                    )
                  })}
                </Box>
              )}

              <Divider />
              <Typography variant="subtitle2" color="primary">
                {t('catalog.translations')}
              </Typography>
              {idiomas.map((idioma) => {
                const draft = translation(idioma.id)
                return (
                  <Box
                    key={idioma.id}
                    sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}
                  >
                    <Typography variant="caption" sx={{ fontWeight: 700, mb: 1, display: 'block' }}>
                      {idioma.nombre} ({idioma.codigo})
                    </Typography>
                    <Box
                      sx={{
                        display: 'grid',
                        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
                        gap: 2,
                      }}
                    >
                      <TextField
                        fullWidth
                        size="small"
                        label={t('catalog.name')}
                        value={draft.nombre}
                        onChange={(event) => updateTranslation(idioma.id, 'nombre', event.target.value)}
                        slotProps={{ htmlInput: { maxLength: 200 } }}
                      />
                      <TextField
                        fullWidth
                        size="small"
                        label={t('catalog.description')}
                        value={draft.descripcion}
                        onChange={(event) =>
                          updateTranslation(idioma.id, 'descripcion', event.target.value)
                        }
                        slotProps={{ htmlInput: { maxLength: 4000 } }}
                      />
                    </Box>
                  </Box>
                )
              })}

              <Divider />
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="subtitle2" color="primary">
                  {t('catalog.prices')}
                </Typography>
                <Button
                  size="small"
                  startIcon={<AddRoundedIcon />}
                  onClick={() => setPreciosNuevos((previous) => [...previous, emptyPrice()])}
                >
                  {t('catalog.addPrice')}
                </Button>
              </Stack>
              {editing && (
                <Typography variant="body2" color="text.secondary">
                  {t('catalog.priceHistoryHelp')}
                </Typography>
              )}

              {preciosVigentes.length > 0 && (
                <Table size="small" aria-label={t('catalog.registeredPrices')}>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('catalog.currency')}</TableCell>
                      <TableCell align="right">{t('catalog.amount')}</TableCell>
                      <TableCell>{t('catalog.validFrom')}</TableCell>
                      <TableCell>{t('catalog.validTo')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {preciosVigentes.map((precio) => (
                      <TableRow key={precio.id}>
                        <TableCell>{precio.monedaCodigo}</TableCell>
                        <TableCell align="right">
                          {formatMoney(precio.importe, precio.monedaCodigo, language)}
                        </TableCell>
                        <TableCell>{precio.vigenteDesde}</TableCell>
                        <TableCell>{precio.vigenteHasta ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}

              {preciosNuevos.map((precio) => (
                <Box
                  key={precio.key}
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr 1fr auto' },
                    gap: 2,
                    alignItems: 'center',
                  }}
                >
                  <FormControl fullWidth size="small" required>
                    <InputLabel id={`price-currency-${precio.key}`}>{t('catalog.currency')}</InputLabel>
                    <Select
                      labelId={`price-currency-${precio.key}`}
                      value={precio.monedaId}
                      label={t('catalog.currency')}
                      onChange={(event) => updatePrice(precio.key, 'monedaId', event.target.value)}
                    >
                      {monedas.map((moneda) => (
                        <MenuItem key={moneda.id} value={moneda.id}>
                          {moneda.codigo} — {moneda.nombre}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField
                    fullWidth
                    size="small"
                    required
                    label={t('catalog.amount')}
                    value={precio.importe}
                    onChange={(event) => updatePrice(precio.key, 'importe', event.target.value)}
                    helperText={monedaCodigo(precio.monedaId)}
                    slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                  />
                  <TextField
                    fullWidth
                    size="small"
                    required
                    type="date"
                    label={t('catalog.validFrom')}
                    value={precio.vigenteDesde}
                    onChange={(event) => updatePrice(precio.key, 'vigenteDesde', event.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                  <TextField
                    fullWidth
                    size="small"
                    type="date"
                    label={t('catalog.validTo')}
                    value={precio.vigenteHasta}
                    onChange={(event) => updatePrice(precio.key, 'vigenteHasta', event.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                  <Tooltip title={t('catalog.removePrice')}>
                    <IconButton
                      aria-label={t('catalog.removePrice')}
                      onClick={() =>
                        setPreciosNuevos((previous) =>
                          previous.filter((draft) => draft.key !== precio.key),
                        )
                      }
                    >
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              ))}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>
            {t('crud.cancel')}
          </Button>
          <Button type="submit" variant="contained" disabled={saving || loadingDetail}>
            {saving ? t('crud.saving') : t('crud.save')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
