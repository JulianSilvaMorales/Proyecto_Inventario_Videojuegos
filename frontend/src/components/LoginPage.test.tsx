import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../i18n'
import { LoginPage } from './LoginPage'

describe('LoginPage', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('es')
  })

  it('submits the email and password without exposing the password in the UI', async () => {
    const user = userEvent.setup()
    const onLogin = vi.fn().mockResolvedValue(undefined)
    render(<LoginPage onLogin={onLogin} error="" />)

    await user.type(screen.getByRole('textbox', { name: 'Correo electrónico' }), 'user@example.com')
    await user.type(screen.getByLabelText(/Contraseña/), 'secret-password')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(onLogin).toHaveBeenCalledWith('user@example.com', 'secret-password')
    expect(screen.getByLabelText(/Contraseña/)).toHaveAttribute('type', 'password')
  })
})
