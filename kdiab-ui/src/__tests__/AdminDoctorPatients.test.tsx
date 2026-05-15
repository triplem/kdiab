import { render, screen, waitFor, fireEvent, within } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import '../i18n'

vi.mock('../api/usersApi', () => ({
  usersApi: {
    listUsers: vi.fn(),
    getPatients: vi.fn(),
    assignPatient: vi.fn(),
    removePatient: vi.fn(),
  },
}))

import { usersApi } from '../api/usersApi'
import { AdminDoctorPatients } from '../features/users/AdminDoctorPatients'

const mockedList = vi.mocked(usersApi.listUsers)
const mockedGetPatients = vi.mocked(usersApi.getPatients)
const mockedAssign = vi.mocked(usersApi.assignPatient)
const mockedRemove = vi.mocked(usersApi.removePatient)

const doctor = { userId: 'doc-1', email: 'doc@example.com', displayName: 'Dr. House', roles: ['DOCTOR'] }
const patient = { userId: 'pat-1', email: 'pat@example.com', displayName: 'Sarah', roles: ['PATIENT'] }

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

beforeEach(() => vi.clearAllMocks())

describe('AdminDoctorPatients', () => {
  test('renders doctor list', async () => {
    mockedList.mockResolvedValue({ data: [doctor, patient] } as never)
    render(<AdminDoctorPatients />, { wrapper })
    await waitFor(() => expect(screen.getByText('Dr. House')).toBeInTheDocument())
  })

  test('shows patient list on doctor select', async () => {
    mockedList.mockResolvedValue({ data: [doctor, patient] } as never)
    mockedGetPatients.mockResolvedValue({
      data: [{ doctorId: 'doc-1', patientId: 'pat-1', createdAt: '2024-01-01T00:00:00Z' }],
    } as never)
    render(<AdminDoctorPatients />, { wrapper })
    await waitFor(() => screen.getByText('Dr. House'))
    fireEvent.click(screen.getByText('Dr. House'))
    await waitFor(() => expect(screen.getByText('Sarah')).toBeInTheDocument())
  })

  test('assign modal opens and calls assignPatient', async () => {
    mockedList.mockResolvedValue({ data: [doctor, patient] } as never)
    mockedGetPatients.mockResolvedValue({ data: [] } as never)
    mockedAssign.mockResolvedValue({} as never)
    render(<AdminDoctorPatients />, { wrapper })
    await waitFor(() => screen.getByText('Dr. House'))
    fireEvent.click(screen.getByText('Dr. House'))
    await waitFor(() => screen.getByRole('button', { name: /assign patient/i }))
    fireEvent.click(screen.getByRole('button', { name: /assign patient/i }))

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument())
    fireEvent.click(screen.getByText(/Sarah/))
    await waitFor(() =>
      expect(mockedAssign).toHaveBeenCalledWith('doc-1', 'pat-1'),
    )
  })

  test('remove confirmation calls removePatient', async () => {
    mockedList.mockResolvedValue({ data: [doctor, patient] } as never)
    mockedGetPatients.mockResolvedValue({
      data: [{ doctorId: 'doc-1', patientId: 'pat-1', createdAt: '2024-01-01T00:00:00Z' }],
    } as never)
    mockedRemove.mockResolvedValue({} as never)
    render(<AdminDoctorPatients />, { wrapper })
    await waitFor(() => screen.getByText('Dr. House'))
    fireEvent.click(screen.getByText('Dr. House'))
    await waitFor(() => screen.getByText('Sarah'))

    fireEvent.click(screen.getByRole('button', { name: /^remove$/i }))
    const dialog = await waitFor(() => screen.getByRole('dialog'))
    fireEvent.click(within(dialog).getByRole('button', { name: /^remove$/i }))

    await waitFor(() => expect(mockedRemove).toHaveBeenCalledWith('doc-1', 'pat-1'))
  })
})
