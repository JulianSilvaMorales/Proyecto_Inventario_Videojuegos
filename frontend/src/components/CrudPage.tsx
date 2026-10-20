import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  MenuItem,
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
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import { useTranslation } from 'react-i18next'
import { ApiError, apiRequest } from '../api/apiClient'
import {
  userCanManageCrudEntity,
  type CrudEntityMetadata,
  type CrudFieldMetadata,
} from '../crud/metadata'

interface CrudPageResult {
  content: Record<string, unknown>[]
  totalElements: number
}

interface ReferenceOption {
  id: string
  codigo: string
  nombre: string
}

interface CrudPageProps {
  entity: CrudEntityMetadata
  permissions: readonly string[]
  onBack: () => void
}

type FormValue = string | boolean

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parsePageResult(value: unknown): CrudPageResult {
  if (
    !isRecord(value) ||
    !Array.isArray(value.content) ||
    typeof value.totalElements !== 'number'
  ) {
    throw new ApiError(502, 'The server returned an invalid page.')
  }
  return {
    content: value.content.filter(isRecord),
    totalElements: value.totalElements,
  }
}

function parseReferenceOptions(value: unknown): ReferenceOption[] {
  if (!Array.isArray(value)) {
    throw new ApiError(502, 'The server returned invalid reference options.')
  }
  return value.filter(
    (option): option is ReferenceOption =>
      isRecord(option) &&
      typeof option.id === 'string' &&
      typeof option.codigo === 'string' &&
      typeof option.nombre === 'string',
  )
}

function localized(labels: { es: string; en: string }, language: string) {
  return language.toLowerCase().startsWith('en') ? labels.en : labels.es
}

function initialForm(
  entity: CrudEntityMetadata,
  row?: Record<string, unknown>,
): Record<string, FormValue> {
  return Object.fromEntries(
    entity.campos
      .filter((field) => !field.soloLectura)
      .map((field) => {
        const value = row ? row[field.apiName] : field.default
        if (field.tipo === 'booleano') {
          return [field.apiName, typeof value === 'boolean' ? value : false]
        }
        if (value == null) {
          return [field.apiName, '']
        }
        if (field.tipo === 'fecha_hora_utc') {
          return [field.apiName, new Date(String(value)).toISOString().slice(0, 16)]
        }
        return [field.apiName, field.tipo === 'json' ? JSON.stringify(value) : String(value)]
      }),
  )
}

function renderValue(field: CrudFieldMetadata, value: unknown, language: string) {
  if (value == null || value === '') {
    return '—'
  }
  if (field.tipo === 'booleano') {
    if (value) {
      return language.toLowerCase().startsWith('en') ? 'Yes' : 'Sí'
    }
    return 'No'
  }
  if (field.tipo === 'fecha_hora_utc') {
    const date = new Date(String(value))
    return Number.isNaN(date.getTime())
      ? String(value)
      : new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
  }
  if (field.tipo === 'json') {
    return JSON.stringify(value)
  }
  return String(value)
}

export function CrudPage({ entity, permissions, onBack }: CrudPageProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const canManage = userCanManageCrudEntity(entity, permissions)
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(20)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [result, setResult] = useState<CrudPageResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Record<string, unknown> | null>(null)
  const [formValues, setFormValues] = useState<Record<string, FormValue>>({})
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [references, setReferences] = useState<Record<string, ReferenceOption[]>>({})
  const [referencesLoading, setReferencesLoading] = useState(false)
  const [confirming, setConfirming] = useState<Record<string, unknown> | null>(null)

  const visibleFields = useMemo(() => entity.campos, [entity.campos])
  const referencedNames = useMemo(
    () =>
      Array.from(
        new Set(
          entity.campos
            .map((field) => field.referencia)
            .filter((reference): reference is string => Boolean(reference)),
        ),
      ),
    [entity.campos],
  )

  useEffect(() => {
    let mounted = true
    const query = new URLSearchParams({
      page: String(page),
      size: String(rowsPerPage),
    })
    if (search) {
      query.set('buscar', search)
    }
    void apiRequest<unknown>(`${entity.rutaApi}?${query.toString()}`)
      .then((response) => {
        const pageResult = parsePageResult(response)
        if (mounted) {
          setResult(pageResult)
          setError('')
          setLoading(false)
        }
      })
      .catch((reason: unknown) => {
        if (mounted) {
          setError(reason instanceof Error ? reason.message : t('errors.unexpected'))
          setLoading(false)
        }
      })
    return () => {
      mounted = false
    }
  }, [entity.rutaApi, page, rowsPerPage, search, refresh, t])

  async function openForm(row?: Record<string, unknown>) {
    setEditing(row ?? null)
    setFormValues(initialForm(entity, row))
    setFieldErrors({})
    setFormError('')
    setError('')
    setFormOpen(true)
    if (referencedNames.length === 0) {
      return
    }
    setReferencesLoading(true)
    try {
      const loadedReferences = await Promise.all(
        referencedNames.map(async (name) => [
          name,
          parseReferenceOptions(
            await apiRequest<unknown>(`/api/v1/referencias/${encodeURIComponent(name)}`),
          ),
        ] as const),
      )
      setReferences(Object.fromEntries(loadedReferences))
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : t('errors.unexpected'))
    } finally {
      setReferencesLoading(false)
    }
  }

  function validateForm() {
    const nextErrors: Record<string, string> = {}
    for (const field of entity.campos) {
      if (field.soloLectura) {
        continue
      }
      const value = formValues[field.apiName]
      const empty = value === '' || value == null
      if (field.obligatorio && empty) {
        nextErrors[field.apiName] = t('crud.validation.required')
        continue
      }
      if (empty || typeof value === 'boolean') {
        continue
      }
      if (field.tipo === 'decimal' || field.tipo === 'entero') {
        const number = Number(value)
        if (!Number.isFinite(number)) {
          nextErrors[field.apiName] = t('crud.validation.number')
        } else if (field.tipo === 'entero' && !Number.isInteger(number)) {
          nextErrors[field.apiName] = t('crud.validation.integer')
        } else if (field.min != null && number < field.min) {
          nextErrors[field.apiName] = t('crud.validation.minimum', { value: field.min })
        } else if (field.max != null && number > field.max) {
          nextErrors[field.apiName] = t('crud.validation.maximum', { value: field.max })
        }
      } else if (field.tipo === 'json') {
        try {
          JSON.parse(value)
        } catch {
          nextErrors[field.apiName] = t('crud.validation.json')
        }
      } else if (field.valores.length > 0 && !field.valores.includes(value)) {
        nextErrors[field.apiName] = t('crud.validation.option')
      }
    }
    for (const rule of entity.validacionesCondicionales) {
      if (rule.soloCrear && editing) {
        continue
      }
      if (rule.valor != null && formValues[rule.campo] !== rule.valor) {
        continue
      }
      if (rule.requiereVerdadero && formValues[rule.campo] !== true) {
        nextErrors[rule.campo] = t('crud.validation.consent')
      }
      for (const name of rule.camposRequeridos) {
        const field = entity.campos.find((candidate) => candidate.nombre === name)
        const value = field ? formValues[field.apiName] : undefined
        if (value == null || value === '') {
          const apiName = field?.apiName ?? name
          nextErrors[apiName] = t('crud.validation.required')
        }
      }
    }
    setFieldErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  function requestBody() {
    const body: Record<string, unknown> = {}
    for (const field of entity.campos) {
      if (field.soloLectura) {
        continue
      }
      const value = formValues[field.apiName]
      if (value === '' || value == null) {
        body[field.apiName] = null
      } else if (field.tipo === 'decimal' || field.tipo === 'entero') {
        body[field.apiName] = Number(value)
      } else if (field.tipo === 'json' && typeof value === 'string') {
        body[field.apiName] = JSON.parse(value) as unknown
      } else if (field.tipo === 'fecha_hora_utc') {
        body[field.apiName] = new Date(String(value)).toISOString()
      } else {
        body[field.apiName] = value
      }
    }
    return body
  }

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!validateForm()) {
      return
    }
    try {
      const path = editing
        ? `${entity.rutaApi}/${encodeURIComponent(String(editing.id))}`
        : entity.rutaApi
      await apiRequest<unknown>(path, {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify(requestBody()),
      })
      setFormOpen(false)
      setError('')
      setFormError('')
      setLoading(true)
      setRefresh((value) => value + 1)
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : t('errors.unexpected'))
    }
  }

  async function removeRow(row: Record<string, unknown>) {
    try {
      const path = `${entity.rutaApi}/${encodeURIComponent(String(row.id))}`
      if (entity.eliminacion === 'delete') {
        await apiRequest<unknown>(path, { method: 'DELETE' })
      } else {
        const values = Object.fromEntries(
          entity.campos
            .filter((field) => !field.soloLectura)
            .map((field) => [field.apiName, row[field.apiName] ?? null]),
        )
        values.activo = false
        await apiRequest<unknown>(path, {
          method: 'PUT',
          body: JSON.stringify(values),
        })
      }
      setConfirming(null)
      setLoading(true)
      setRefresh((value) => value + 1)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t('errors.unexpected'))
      setConfirming(null)
    }
  }

  function changeSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPage(0)
    setLoading(true)
    setSearch(searchInput.trim())
  }

  const entityLabel = localized(entity.etiquetas, language)

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { sm: 'center' }, mb: 2.5 }}
      >
        <Button
          aria-label={t('crud.back')}
          startIcon={<ArrowBackRoundedIcon />}
          onClick={onBack}
        >
          {t('crud.back')}
        </Button>
        <Typography variant="h4" sx={{ flexGrow: 1 }}>
          {entityLabel}
        </Typography>
        {canManage && (
          <Button
            variant="contained"
            startIcon={<AddRoundedIcon />}
            onClick={() => void openForm()}
          >
            {t('crud.create')}
          </Button>
        )}
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>
          {error}
        </Alert>
      )}

      <Paper variant="outlined">
        <Box component="form" onSubmit={changeSearch} sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <TextField
              fullWidth
              size="small"
              label={t('crud.search')}
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
            <Button type="submit" variant="outlined" startIcon={<SearchRoundedIcon />}>
              {t('crud.search')}
            </Button>
          </Stack>
        </Box>
        <TableContainer>
          <Table size="small" aria-label={entityLabel}>
            <TableHead>
              <TableRow>
                {visibleFields.map((field) => (
                  <TableCell key={field.apiName}>{localized(field.etiquetas, language)}</TableCell>
                ))}
                {canManage && <TableCell align="right">{t('crud.actions')}</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {loading && (
                <TableRow>
                  <TableCell colSpan={visibleFields.length + (canManage ? 1 : 0)} align="center">
                    <CircularProgress size={24} aria-label={t('common.loading')} />
                  </TableCell>
                </TableRow>
              )}
              {!loading && result?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={visibleFields.length + (canManage ? 1 : 0)} align="center">
                    {t('crud.empty')}
                  </TableCell>
                </TableRow>
              )}
              {!loading &&
                result?.content.map((row) => (
                  <TableRow key={String(row.id)} hover>
                    {visibleFields.map((field) => (
                      <TableCell key={field.apiName}>
                        {renderValue(field, row[field.apiName], language)}
                      </TableCell>
                    ))}
                    {canManage && (
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        <Tooltip title={t('crud.edit')}>
                          <IconButton
                            aria-label={`${t('crud.edit')} ${String(row.id)}`}
                            onClick={() => void openForm(row)}
                          >
                            <EditOutlinedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        {entity.eliminacion !== 'none' && (
                          <Tooltip
                            title={
                              entity.eliminacion === 'delete'
                                ? t('crud.delete')
                                : t('crud.deactivate')
                            }
                          >
                            <IconButton
                              aria-label={`${t(entity.eliminacion === 'delete' ? 'crud.delete' : 'crud.deactivate')} ${String(row.id)}`}
                              onClick={() => setConfirming(row)}
                            >
                              <DeleteOutlineRoundedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={result?.totalElements ?? 0}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(_, nextPage) => {
            setLoading(true)
            setPage(nextPage)
          }}
          onRowsPerPageChange={(event) => {
            setLoading(true)
            setRowsPerPage(Number(event.target.value))
            setPage(0)
          }}
          rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage={t('crud.rowsPerPage')}
          labelDisplayedRows={({ from, to, count }) =>
            t('crud.displayedRows', { from, to, count })
          }
        />
      </Paper>

      <Dialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        fullWidth
        maxWidth="md"
        aria-labelledby="crud-form-title"
      >
        <Box component="form" noValidate onSubmit={submitForm}>
          <DialogTitle id="crud-form-title">
            {editing
              ? t('crud.editEntity', { entity: entityLabel })
              : t('crud.createEntity', { entity: entityLabel })}
          </DialogTitle>
          <DialogContent dividers>
            {formError && (
              <Alert severity="error" sx={{ mb: 2 }} onClose={() => setFormError('')}>
                {formError}
              </Alert>
            )}
            {referencesLoading && <CircularProgress size={24} aria-label={t('common.loading')} />}
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
                gap: 2,
                pt: 1,
              }}
            >
              {entity.campos
                .filter((field) => !field.soloLectura)
                .map((field) => (
                  <CrudField
                    key={field.apiName}
                    field={field}
                    language={language}
                    value={formValues[field.apiName] ?? ''}
                    error={fieldErrors[field.apiName]}
                    references={field.referencia ? references[field.referencia] ?? [] : []}
                    disabled={referencesLoading}
                    onChange={(value) =>
                      setFormValues((current) => ({ ...current, [field.apiName]: value }))
                    }
                  />
                ))}
            </Box>
          </DialogContent>
          <DialogActions sx={{ p: 2 }}>
            <Button onClick={() => setFormOpen(false)}>{t('crud.cancel')}</Button>
            <Button type="submit" variant="contained" disabled={referencesLoading}>
              {t('crud.save')}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      <Dialog
        open={confirming !== null}
        onClose={() => setConfirming(null)}
        aria-labelledby="crud-confirm-title"
      >
        <DialogTitle id="crud-confirm-title">
          {entity.eliminacion === 'delete' ? t('crud.confirmDelete') : t('crud.confirmDeactivate')}
        </DialogTitle>
        <DialogContent>
          <Typography color="text.secondary">
            {t('crud.confirmMessage', { entity: entityLabel })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirming(null)}>{t('crud.cancel')}</Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => confirming && void removeRow(confirming)}
          >
            {entity.eliminacion === 'delete' ? t('crud.delete') : t('crud.deactivate')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

interface CrudFieldProps {
  field: CrudFieldMetadata
  language: string
  value: FormValue
  error?: string
  references: ReferenceOption[]
  disabled: boolean
  onChange: (value: FormValue) => void
}

function CrudField({
  field,
  language,
  value,
  error,
  references,
  disabled,
  onChange,
}: CrudFieldProps) {
  const { t } = useTranslation()
  const label = localized(field.etiquetas, language)

  if (field.tipo === 'booleano') {
    return (
      <FormControl error={Boolean(error)}>
        <FormControlLabel
          control={
            <Switch
              checked={Boolean(value)}
              disabled={disabled}
              onChange={(event) => onChange(event.target.checked)}
            />
          }
          label={label}
        />
        {error && <FormHelperText>{error}</FormHelperText>}
      </FormControl>
    )
  }

  if (field.valores.length > 0 || field.referencia) {
    const options = field.referencia
      ? references.map((option) => ({
          value: option.id,
          label: `${option.codigo} — ${option.nombre}`,
        }))
      : field.valores.map((option) => ({ value: option, label: option }))
    return (
      <TextField
        select
        fullWidth
        required={field.obligatorio}
        label={label}
        value={value}
        disabled={disabled}
        error={Boolean(error)}
        helperText={error}
        onChange={(event) => onChange(event.target.value)}
      >
        {!field.obligatorio && <MenuItem value="">{t('crud.none')}</MenuItem>}
        {options.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
      </TextField>
    )
  }

  const inputType =
    field.tipo === 'entero' || field.tipo === 'decimal'
      ? 'number'
      : field.tipo === 'fecha'
        ? 'date'
        : field.tipo === 'fecha_hora_utc'
          ? 'datetime-local'
          : 'text'
  return (
    <TextField
      fullWidth
      required={field.obligatorio}
      label={label}
      type={inputType}
      value={value}
      error={Boolean(error)}
      helperText={error}
      multiline={field.tipo === 'json'}
      minRows={field.tipo === 'json' ? 3 : undefined}
      slotProps={{
        htmlInput: {
          min: field.min ?? undefined,
          max: field.max ?? undefined,
          step: field.tipo === 'entero' ? 1 : field.tipo === 'decimal' ? 'any' : undefined,
        },
        inputLabel: field.tipo === 'fecha' || field.tipo === 'fecha_hora_utc'
          ? { shrink: true }
          : undefined,
      }}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
