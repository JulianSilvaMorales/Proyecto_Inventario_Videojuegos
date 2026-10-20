import { useState } from 'react'
import {
  Box,
  Card,
  CardContent,
  Chip,
  FormControl,
  MenuItem,
  Select,
  Stack,
  Typography,
} from '@mui/material'
import LanguageRoundedIcon from '@mui/icons-material/LanguageRounded'
import PaymentsRoundedIcon from '@mui/icons-material/PaymentsRounded'
import ShieldRoundedIcon from '@mui/icons-material/ShieldRounded'
import { useTranslation } from 'react-i18next'
import type { ReactNode } from 'react'
import type { AuthIdentity } from '../auth/identity'
import { formatCurrency, formatDate, formatNumber } from '../utils/formatters'

interface DashboardProps {
  user: AuthIdentity
}

const currencyOptions = ['COP', 'USD', 'EUR']
const sampleDate = new Date('2026-10-20T14:30:00.000Z')

export function Dashboard({ user }: DashboardProps) {
  const { t, i18n } = useTranslation()
  const [currency, setCurrency] = useState('COP')
  const language = i18n.resolvedLanguage ?? user.idioma

  return (
    <Stack spacing={3}>
      <Box>
        <Typography
          variant="overline"
          color="primary.main"
          sx={{ fontWeight: 700, letterSpacing: '0.12em' }}
        >
          {t('dashboard.eyebrow')}
        </Typography>
        <Typography variant="h4" sx={{ mt: 0.5 }}>
          {t('dashboard.welcome', { name: user.nombre })}
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          {t('dashboard.subtitle')}
        </Typography>
      </Box>

      <Box className="dashboard-grid">
        <Card elevation={0}>
          <CardContent>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Box className="card-icon card-icon-blue">
                <ShieldRoundedIcon color="primary" />
              </Box>
              <Box>
                <Typography variant="h6">{t('dashboard.accessTitle')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {user.roles.join(', ')}
                </Typography>
              </Box>
            </Stack>
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ mt: 2.5, flexWrap: 'wrap' }}
            >
              {user.paises.length > 0 ? (
                user.paises.map((country) => (
                  <Chip key={country} size="small" label={country} color="primary" />
                ))
              ) : (
                <Chip size="small" label={t('common.allCountries')} variant="outlined" />
              )}
            </Stack>
          </CardContent>
        </Card>

        <Card elevation={0}>
          <CardContent>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Box className="card-icon card-icon-teal">
                <LanguageRoundedIcon color="secondary" />
              </Box>
              <Box>
                <Typography variant="h6">{t('dashboard.permissions')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {user.permisos.length}
                </Typography>
              </Box>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 2.5 }}>
              {user.correo}
            </Typography>
          </CardContent>
        </Card>
      </Box>

      <Card elevation={0}>
        <CardContent sx={{ p: { xs: 2.5, sm: 3 } }}>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={1}
            sx={{
              justifyContent: 'space-between',
              alignItems: { xs: 'flex-start', sm: 'center' },
            }}
          >
            <Box>
              <Typography variant="h6">{t('dashboard.formatTitle')}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {t('dashboard.formatDescription')}
              </Typography>
            </Box>
            <FormControl size="small" sx={{ minWidth: 130 }}>
              <Select
                value={currency}
                onChange={(event) => setCurrency(event.target.value)}
                inputProps={{ 'aria-label': t('common.currency') }}
              >
                {currencyOptions.map((option) => (
                  <MenuItem key={option} value={option}>
                    {option}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>

          <Box className="format-grid">
            <FormatSample
              label={t('dashboard.amount')}
              icon={<PaymentsRoundedIcon fontSize="small" />}
              value={formatCurrency(1250000.5, currency, language)}
            />
            <FormatSample
              label={t('dashboard.number')}
              icon={<LanguageRoundedIcon fontSize="small" />}
              value={formatNumber(1234567.89, language)}
            />
            <FormatSample
              label={t('dashboard.date')}
              icon={<LanguageRoundedIcon fontSize="small" />}
              value={formatDate(sampleDate, language)}
            />
          </Box>
        </CardContent>
      </Card>
    </Stack>
  )
}

interface FormatSampleProps {
  label: string
  icon: ReactNode
  value: string
}

function FormatSample({ label, icon, value }: FormatSampleProps) {
  return (
    <Box className="format-sample">
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', color: 'text.secondary' }}
      >
        {icon}
        <Typography variant="body2">{label}</Typography>
      </Stack>
      <Typography variant="h6" sx={{ mt: 1.25, overflowWrap: 'anywhere' }}>
        {value}
      </Typography>
    </Box>
  )
}
