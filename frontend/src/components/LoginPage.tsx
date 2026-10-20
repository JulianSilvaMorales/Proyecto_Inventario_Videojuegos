import { useState, type FormEvent } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import Inventory2RoundedIcon from '@mui/icons-material/Inventory2Rounded'
import LoginRoundedIcon from '@mui/icons-material/LoginRounded'
import { useTranslation } from 'react-i18next'
import { LanguageSwitcher } from './LanguageSwitcher'

interface LoginPageProps {
  onLogin: (email: string, password: string) => Promise<void>
  error: string
}

export function LoginPage({ onLogin, error }: LoginPageProps) {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [serverStarting, setServerStarting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setServerStarting(false)
    const timer = window.setTimeout(() => setServerStarting(true), 2200)
    try {
      await onLogin(email.trim(), password)
    } finally {
      window.clearTimeout(timer)
      setSubmitting(false)
      setServerStarting(false)
    }
  }

  return (
    <Box className="login-page">
      <Box className="login-language">
        <LanguageSwitcher />
      </Box>
      <Card className="login-card" elevation={0}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack spacing={3}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Box className="brand-mark">
                <Inventory2RoundedIcon />
              </Box>
              <Box>
                <Typography variant="h5">{t('app.name')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('app.subtitle')}
                </Typography>
              </Box>
            </Stack>

            <Box>
              <Typography variant="h4">{t('auth.title')}</Typography>
              <Typography color="text.secondary" sx={{ mt: 0.75 }}>
                {t('auth.subtitle')}
              </Typography>
            </Box>

            {error && <Alert severity="error">{error}</Alert>}
            {serverStarting && (
              <Alert severity="info">{t('auth.serverStarting')}</Alert>
            )}

            <Box component="form" onSubmit={submit} noValidate>
              <Stack spacing={2.25}>
                <TextField
                  label={t('auth.email')}
                  type="email"
                  autoComplete="username"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  fullWidth
                  autoFocus
                />
                <TextField
                  label={t('auth.password')}
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                  fullWidth
                />
                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  startIcon={<LoginRoundedIcon />}
                  disabled={submitting || !email.trim() || !password}
                >
                  {submitting ? t('auth.submitting') : t('auth.submit')}
                </Button>
              </Stack>
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
