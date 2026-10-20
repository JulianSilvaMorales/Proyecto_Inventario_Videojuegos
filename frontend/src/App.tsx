import { useState } from 'react'
import { Alert } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { ApiError } from './api/apiClient'
import { useSession } from './auth/useSession'
import { AppShell } from './components/AppShell'
import { LoginPage } from './components/LoginPage'
import { normalizeLanguage } from './i18n/language'
import './App.css'

function App() {
  const { t, i18n } = useTranslation()
  const { user, login, logout } = useSession()
  const [loginError, setLoginError] = useState('')
  const [logoutError, setLogoutError] = useState('')

  async function handleLogin(email: string, password: string) {
    setLoginError('')
    setLogoutError('')
    try {
      const identity = await login(email, password)
      const language = normalizeLanguage(identity.idioma)
      await i18n.changeLanguage(language)
      localStorage.setItem('inventario:language', language)
    } catch (error) {
      setLoginError(
        error instanceof ApiError && error.status === 0
          ? t('errors.network')
          : error instanceof Error
            ? error.message
            : t('errors.unexpected'),
      )
    }
  }

  async function handleLogout() {
    setLogoutError('')
    try {
      await logout()
    } catch (error) {
      setLogoutError(
        error instanceof ApiError && error.status === 0
          ? t('errors.logoutNetwork')
          : error instanceof Error
            ? error.message
            : t('errors.unexpected'),
      )
    }
  }

  if (!user) {
    return <LoginPage onLogin={handleLogin} error={loginError || logoutError} />
  }

  return (
    <>
      {logoutError && (
        <Alert
          className="global-alert"
          severity="warning"
          onClose={() => setLogoutError('')}
        >
          {logoutError}
        </Alert>
      )}
      <AppShell user={user} onLogout={handleLogout} />
    </>
  )
}

export default App
