import { render, screen, waitFor, fireEvent, within } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import '../i18n'

vi.mock('../api/usersApi', () => ({
  usersApi: {
    listUsers: vi.fn(),
    createUser: vi.fn(),
    updateUser: vi.fn(),
    deleteUser: vi.fn(),
  },
}))

import { usersApi } from '../api/usersApi'
import { AdminUserList } from '../features/users/AdminUserList'

const mockedList = vi.mocked(usersApi.listUsers)
const mockedCreate = vi.mocked(usersApi.createUser)
const mockedDelete = vi.mocked(usersApi.deleteUser)

function makeUser(n: number) {
  return {
    userId: `user-${n}`,
    email: `user${n}@example.com`,
    displayName: `User ${n}`,
    roles: ['PATIENT'],
  }
}

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

beforeEach(() => vi.clearAllMocks())

describe('AdminUserList', () => {
  test('renders user list', async () => {
    mockedList.mockResolvedValue({ data: [makeUser(1), makeUser(2)] } as never)
    render(<AdminUserList />, { wrapper })
    await waitFor(() => expect(screen.getByText('User 1')).toBeInTheDocument())
    expect(screen.getByText('User 2')).toBeInTheDocument()
  })

  test('shows empty state when no users', async () => {
    mockedList.mockResolvedValue({ data: [] } as never)
    render(<AdminUserList />, { wrapper })
    await waitFor(() => expect(screen.getByText(/no users/i)).toBeInTheDocument())
  })

  test('opens create modal on button click', async () => {
    mockedList.mockResolvedValue({ data: [] } as never)
    render(<AdminUserList />, { wrapper })
    await waitFor(() => screen.getByRole('button', { name: /create user/i }))
    fireEvent.click(screen.getByRole('button', { name: /create user/i }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument()
  })

  test('delete button is disabled until email matches', async () => {
    mockedList.mockResolvedValue({ data: [makeUser(1)] } as never)
    render(<AdminUserList />, { wrapper })
    await waitFor(() => screen.getByText('User 1'))
    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])

    const dialog = screen.getByRole('dialog')
    const deleteBtn = within(dialog).getByRole('button', { name: /^delete$/i })
    expect(deleteBtn).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('user1@example.com'), {
      target: { value: 'user1@example.com' },
    })
    expect(deleteBtn).not.toBeDisabled()
  })

  test('calls deleteUser and shows success toast', async () => {
    mockedList.mockResolvedValue({ data: [makeUser(1)] } as never)
    mockedDelete.mockResolvedValue({} as never)
    render(<AdminUserList />, { wrapper })
    await waitFor(() => screen.getByText('User 1'))

    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/i })[0])
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByPlaceholderText('user1@example.com'), {
      target: { value: 'user1@example.com' },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(mockedDelete).toHaveBeenCalledWith('user-1'))
  })

  test('calls createUser on form submit', async () => {
    mockedList.mockResolvedValue({ data: [] } as never)
    mockedCreate.mockResolvedValue({ data: makeUser(99) } as never)
    render(<AdminUserList />, { wrapper })
    await waitFor(() => screen.getByRole('button', { name: /create user/i }))

    fireEvent.click(screen.getByRole('button', { name: /create user/i }))
    fireEvent.change(screen.getByLabelText(/display name/i), { target: { value: 'New User' } })
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'Secret123!' } })
    fireEvent.click(screen.getByRole('button', { name: /^create$/i }))

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled())
    expect(mockedCreate.mock.calls[0][0]).toMatchObject({
      displayName: 'New User',
      email: 'new@example.com',
      password: 'Secret123!',
      role: 'PATIENT',
    })
  })
})
