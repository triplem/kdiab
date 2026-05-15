import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import React from 'react'
import '../i18n'

vi.mock('../api/usersApi', () => ({
  usersApi: {
    register: vi.fn(),
  },
}))

import { usersApi } from '../api/usersApi'
import { RegistrationForm } from '../features/users/RegistrationForm'

const mockedRegister = vi.mocked(usersApi.register)

beforeEach(() => vi.clearAllMocks())

describe('RegistrationForm', () => {
  test('shows disabled message when feature flag is off', () => {
    render(<RegistrationForm onBack={() => undefined} />)
    expect(screen.getByText(/not available/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/full name/i)).not.toBeInTheDocument()
  })

  test('shows password mismatch error', async () => {
    vi.stubEnv('VITE_SELF_REGISTRATION_ENABLED', 'true')
    render(<RegistrationForm onBack={() => undefined} />)

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Alice' } })
    fireEvent.change(screen.getByLabelText(/^email/i), { target: { value: 'alice@example.com' } })
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'Secret123!' } })
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Wrong123!' } })
    fireEvent.click(screen.getByRole('button', { name: /register/i }))

    await waitFor(() => expect(screen.getByText(/do not match/i)).toBeInTheDocument())
    expect(mockedRegister).not.toHaveBeenCalled()
    vi.unstubAllEnvs()
  })

  test('shows email taken error on 409 response', async () => {
    vi.stubEnv('VITE_SELF_REGISTRATION_ENABLED', 'true')
    mockedRegister.mockRejectedValue({ response: { status: 409 } })
    render(<RegistrationForm onBack={() => undefined} />)

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Alice' } })
    fireEvent.change(screen.getByLabelText(/^email/i), { target: { value: 'alice@example.com' } })
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'Secret123!' } })
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Secret123!' } })
    fireEvent.click(screen.getByRole('button', { name: /register/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/already registered/i))
    vi.unstubAllEnvs()
  })

  test('shows pending screen after successful registration', async () => {
    vi.stubEnv('VITE_SELF_REGISTRATION_ENABLED', 'true')
    mockedRegister.mockResolvedValue({ data: { userId: 'u1', message: 'ok' } } as never)
    render(<RegistrationForm onBack={() => undefined} />)

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Alice' } })
    fireEvent.change(screen.getByLabelText(/^email/i), { target: { value: 'alice@example.com' } })
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'Secret123!' } })
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Secret123!' } })
    fireEvent.click(screen.getByRole('button', { name: /register/i }))

    await waitFor(() => expect(screen.getByText(/account request received/i)).toBeInTheDocument())
    vi.unstubAllEnvs()
  })
})
