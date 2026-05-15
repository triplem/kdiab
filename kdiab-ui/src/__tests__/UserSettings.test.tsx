import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../i18n'

vi.mock('../api/usersApi', () => ({
  usersApi: {
    getMe: vi.fn(),
    patchMySettings: vi.fn(),
  },
}))

import { usersApi } from '../api/usersApi'
import { UserSettings } from '../features/users/UserSettings'

const mockedGetMe = vi.mocked(usersApi.getMe)
const mockedPatch = vi.mocked(usersApi.patchMySettings)

function makeSettings(overrides = {}) {
  return {
    timezone: 'Europe/Berlin',
    language: 'en',
    timeFormat: 24 as const,
    glucoseUnit: 'mg/dL' as const,
    weightUnit: 'kg' as const,
    alarmUrgentHigh: 260,
    alarmHigh: 180,
    alarmLow: 70,
    alarmUrgentLow: 54,
    updatedAt: '2024-01-01T00:00:00Z',
    jwtBackedNote: null,
    ...overrides,
  }
}

function makeUser(settingsOverrides = {}) {
  return {
    userId: 'user-1',
    email: 'test@example.com',
    displayName: 'Test User',
    roles: ['PATIENT'],
    settings: makeSettings(settingsOverrides),
  }
}

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

import React from 'react'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('UserSettings', () => {
  test('renders all 9 setting fields', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => expect(screen.getByLabelText(/timezone/i)).toBeInTheDocument())
    expect(screen.getByLabelText(/language/i)).toBeInTheDocument()
    expect(screen.getByText(/time format/i)).toBeInTheDocument()
    expect(screen.getByText(/glucose unit/i)).toBeInTheDocument()
    expect(screen.getByText(/weight unit/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/urgent high/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^high/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^low/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/urgent low/i)).toBeInTheDocument()
  })

  test('shows success toast after successful save', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    mockedPatch.mockResolvedValue({ data: makeSettings() } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => screen.getByLabelText(/timezone/i))

    fireEvent.change(screen.getByLabelText(/timezone/i), { target: { value: 'UTC' } })
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/saved/i))
  })

  test('shows jwt-backed hint when glucose unit changes', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser({ glucoseUnit: 'mg/dL' }) } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => screen.getByText(/glucose unit/i))

    const mmolRadio = screen.getByDisplayValue('mmol/L')
    fireEvent.click(mmolRadio)

    expect(screen.getByText(/next login/i)).toBeInTheDocument()
  })

  test('shows alarm order validation error when urgentLow >= low', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => screen.getByLabelText(/urgent low/i))

    fireEvent.change(screen.getByLabelText(/urgent low/i), { target: { value: '80' } })
    fireEvent.change(screen.getByLabelText(/^low/i), { target: { value: '70' } })
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(screen.getByText(/urgent low < low/i)).toBeInTheDocument())
    expect(mockedPatch).not.toHaveBeenCalled()
  })
})
