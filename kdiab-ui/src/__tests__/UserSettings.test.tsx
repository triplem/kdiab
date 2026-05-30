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
import type { UserSettings as UserSettingsType } from '../api/usersApi'
import { UserSettings } from '../features/users/UserSettings'

const mockedGetMe = vi.mocked(usersApi.getMe)
const mockedPatch = vi.mocked(usersApi.patchMySettings)

function makeSettings(overrides: Partial<UserSettingsType> = {}): UserSettingsType {
  return {
    locale: { timezone: 'Europe/Berlin', language: 'en', timeFormat: 24 },
    units: { glucoseUnit: 'mg/dL', weightUnit: 'kg' },
    alarms: { urgentHigh: 260, high: 180, low: 70, urgentLow: 54 },
    diabetes: { sensorDurationHours: 240 },
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  }
}

function makeUser(settingsOverrides: Partial<UserSettingsType> = {}) {
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
  test('renders all setting fields including sensorDurationHours', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => expect(screen.getByLabelText(/timezone/i)).toBeInTheDocument())
    expect(screen.getByLabelText(/language/i)).toBeInTheDocument()
    expect(screen.getByText(/time format/i)).toBeInTheDocument()
    expect(screen.getByText(/glucose unit/i)).toBeInTheDocument()
    expect(screen.getByText(/weight unit/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/sensor lifespan/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/urgent high/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^high/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^low/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/urgent low/i)).toBeInTheDocument()
  })

  test('pre-fills sensorDurationHours from stored settings', async () => {
    mockedGetMe.mockResolvedValue({
      data: makeUser({ diabetes: { sensorDurationHours: 336 } }),
    } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => expect(screen.getByLabelText(/sensor lifespan/i)).toBeInTheDocument())
    expect(screen.getByLabelText(/sensor lifespan/i)).toHaveValue(336)
  })

  test('includes sensorDurationHours in PATCH payload on save', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    mockedPatch.mockResolvedValue({ data: makeSettings() } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => screen.getByLabelText(/sensor lifespan/i))

    fireEvent.change(screen.getByLabelText(/sensor lifespan/i), { target: { value: '168' } })
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(mockedPatch).toHaveBeenCalledWith(
      expect.objectContaining({
        diabetes: expect.objectContaining({ sensorDurationHours: 168 }),
      })
    ))
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

  test('includes glucoseUnit in PATCH payload when changed', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    mockedPatch.mockResolvedValue({ data: makeSettings() } as never)
    render(<UserSettings />, { wrapper })
    await waitFor(() => screen.getByText(/glucose unit/i))

    const mmolRadio = screen.getByDisplayValue('mmol/L')
    fireEvent.click(mmolRadio)
    fireEvent.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(mockedPatch).toHaveBeenCalledWith(
      expect.objectContaining({
        units: expect.objectContaining({ glucoseUnit: 'mmol/L' }),
      })
    ))
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

  test('localeOnly=true hides glucose unit, weight unit, alarms, and sensor duration', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    render(<UserSettings localeOnly />, { wrapper })
    await waitFor(() => expect(screen.getByLabelText(/timezone/i)).toBeInTheDocument())

    // These locale fields should still be visible
    expect(screen.getByLabelText(/timezone/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/language/i)).toBeInTheDocument()

    // These non-locale fields should be hidden
    expect(screen.queryByText(/glucose unit/i)).toBeNull()
    expect(screen.queryByText(/weight unit/i)).toBeNull()
    expect(screen.queryByText(/time format/i)).toBeNull()
    expect(screen.queryByLabelText(/sensor lifespan/i)).toBeNull()
    expect(screen.queryByLabelText(/urgent high/i)).toBeNull()
  })

  test('localeOnly=false (default) shows all fields', async () => {
    mockedGetMe.mockResolvedValue({ data: makeUser() } as never)
    render(<UserSettings localeOnly={false} />, { wrapper })
    await waitFor(() => expect(screen.getByLabelText(/timezone/i)).toBeInTheDocument())

    expect(screen.getByText(/glucose unit/i)).toBeInTheDocument()
    expect(screen.getByText(/weight unit/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/sensor lifespan/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/urgent high/i)).toBeInTheDocument()
  })
})
