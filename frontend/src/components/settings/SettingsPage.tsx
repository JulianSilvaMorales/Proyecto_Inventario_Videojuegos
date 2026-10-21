import { useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
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
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useTranslation } from 'react-i18next'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import { accessibleCrudEntities, type CrudEntityMetadata } from '../../crud/metadata'
import { CrudEntityLinks } from '../CrudEntityLinks'
import { PageHeader } from '../PageHeader'
import { ParameterFormDialog, type Parametro } from './ParameterFormDialog'
import { PARAMETER_SCOPES } from './parameterValues'

interface SettingsPageProps {
  permissions: readonly string[]
  onSelectEntity: (entity: CrudEntityMetadata) => void
}

type SettingsTab = 'parameters' | 'catalogs'

function ParameterValue({ parametro }: { parametro: Parametro }) {
  const { t } = useTranslation()
  if (parametro.tipo === 'BOOLEANO') {
    return <>{parametro.valor === true ? t('common.yes') : t('common.no')}</>
  }
  if (parametro.tipo === 'JSON') {
    return (
      <Typography component="code" variant="body2" sx={{ fontFamily: 'monospace', wordBreak: 'break-word' }}>
        {JSON.stringify(parametro.valor)}
      </Typography>
    )
  }
  return <>{String(parametro.valor)}</>
}

function ParametersPanel() {
  const { t } = useTranslation()
  const [claveInput, setClaveInput] = useState('')
  const [clave, setClave] = useState('')
  const [ambito, setAmbito] = useState('')
  const [page, setPage] = useState(0)
  const [rowsPerPage, setRowsPerPage] = useState(20)
  // undefined: cerrado; null: nuevo; objeto: edición.
  const [editing, setEditing] = useState<Parametro | null | undefined>(undefined)
  const [formKey, setFormKey] = useState(0)
  const [success, setSuccess] = useState('')

  const params = new URLSearchParams({ page: String(page), size: String(rowsPerPage) })
  if (clave) params.set('clave', clave)
  if (ambito) params.set('ambito', ambito)
  const query = useApiQuery<PageResponse<Parametro>>(`/api/v1/parametros?${params.toString()}`)
  const paisesQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/pais')
  const parametros = query.data?.content ?? []

  function open(parametro: Parametro | null) {
    setSuccess('')
    setFormKey((value) => value + 1)
    setEditing(parametro)
  }

  function search(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setClave(claveInput.trim())
  }

  return (
    <Stack spacing={2.5}>
      {query.error && <Alert severity="error">{query.error}</Alert>}
      {success && (
        <Alert severity="success" onClose={() => setSuccess('')}>
          {success}
        </Alert>
      )}
      <Typography variant="body2" color="text.secondary">
        {t('settings.parametersHelp')}
      </Typography>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' } }}
      >
        <Box component="form" onSubmit={search} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <TextField
            size="small"
            label={t('settings.key')}
            value={claveInput}
            onChange={(event) => setClaveInput(event.target.value)}
          />
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="parameters-scope">{t('settings.scope')}</InputLabel>
            <Select
              labelId="parameters-scope"
              value={ambito}
              label={t('settings.scope')}
              onChange={(event) => {
                setAmbito(event.target.value)
                setPage(0)
              }}
            >
              <MenuItem value="">{t('common.all')}</MenuItem>
              {PARAMETER_SCOPES.map((scope) => (
                <MenuItem key={scope} value={scope}>
                  {t(`settings.scopes.${scope}`)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button type="submit" variant="outlined">
            {t('common.filter')}
          </Button>
        </Box>
        <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => open(null)}>
          {t('settings.newParameter')}
        </Button>
      </Stack>

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>{t('settings.key')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('settings.value')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('settings.type')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('settings.scope')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('settings.validity')}</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>{t('settings.limits')}</TableCell>
              <TableCell align="right" sx={{ fontWeight: 700 }}>
                {t('crud.actions')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {query.loading ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={28} aria-label={t('common.loading')} />
                </TableCell>
              </TableRow>
            ) : parametros.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  {t('settings.empty')}
                </TableCell>
              </TableRow>
            ) : (
              parametros.map((parametro) => (
                <TableRow key={parametro.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{parametro.clave}</TableCell>
                  <TableCell>
                    <ParameterValue parametro={parametro} />
                  </TableCell>
                  <TableCell>{t(`settings.types.${parametro.tipo}`, { defaultValue: parametro.tipo })}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={
                        parametro.ambito === 'PAIS'
                          ? `${t('settings.scopes.PAIS')} · ${parametro.paisCodigo ?? ''}`
                          : t('settings.scopes.NEGOCIO')
                      }
                    />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {parametro.vigenteDesde} → {parametro.vigenteHasta ?? t('settings.openEnded')}
                  </TableCell>
                  <TableCell>
                    {parametro.minimo != null || parametro.maximo != null
                      ? `${parametro.minimo ?? '—'} … ${parametro.maximo ?? '—'}`
                      : '—'}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title={t('crud.edit')}>
                      <IconButton
                        size="small"
                        aria-label={`${t('crud.edit')} ${parametro.clave}`}
                        onClick={() => open(parametro)}
                      >
                        <EditOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
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

      {editing !== undefined && (
        <ParameterFormDialog
          key={formKey}
          parametro={editing}
          paises={paisesQuery.data ?? []}
          onClose={() => setEditing(undefined)}
          onSaved={() => {
            setEditing(undefined)
            setSuccess(t('settings.savedSuccess'))
            query.reload()
          }}
        />
      )}
    </Stack>
  )
}

export function SettingsPage({ permissions, onSelectEntity }: SettingsPageProps) {
  const { t } = useTranslation()
  const canManageParameters = permissions.includes('PARAMETROS_GESTIONAR')
  const hasCatalogs = accessibleCrudEntities('settings', permissions).length > 0
  const tabs: SettingsTab[] = [
    ...(canManageParameters ? (['parameters'] as const) : []),
    ...(hasCatalogs ? (['catalogs'] as const) : []),
  ]
  const [selected, setSelected] = useState<SettingsTab>('parameters')
  const tab = tabs.includes(selected) ? selected : tabs[0]

  return (
    <Stack spacing={3}>
      <PageHeader
        section={t('modules.settings.title')}
        title={t('settings.title')}
        subtitle={t('settings.subtitle')}
      />
      {tabs.length > 1 && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tab} onChange={(_, value: SettingsTab) => setSelected(value)}>
            {tabs.map((option) => (
              <Tab key={option} value={option} label={t(`settings.tabs.${option}`)} />
            ))}
          </Tabs>
        </Box>
      )}
      {tab === 'parameters' && <ParametersPanel />}
      {tab === 'catalogs' && (
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t('settings.catalogsHelp')}
          </Typography>
          <CrudEntityLinks module="settings" permissions={permissions} onSelectEntity={onSelectEntity} />
        </Stack>
      )}
    </Stack>
  )
}
