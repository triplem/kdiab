import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import '../i18n'

vi.mock('../api/treatmentsApi', () => ({
  treatmentsApi: {
    createTreatment: vi.fn(),
  },
}))

// sonner toast is called by QuickLogButtons — mock it so tests don't rely on DOM
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

import { treatmentsApi } from '../api/treatmentsApi'
import { toast } from 'sonner'
import { QuickLogButtons } from '../features/treatments/QuickLogButtons'

const mockedCreate = vi.mocked(treatmentsApi.createTreatment)
const mockedToastSuccess = vi.mocked(toast.success)
const mockedToastError = vi.mocked(toast.error)

describe('QuickLogButtons', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders quick-log label and three pump event buttons', () => {
    render(<QuickLogButtons userId="user-1" />)
    // The label text
    expect(screen.getByText(/quick log/i)).toBeInTheDocument()
    // Three buttons — their accessible labels come from treatmentModal.types.*
    expect(screen.getByRole('button', { name: /change insulin cartridge/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /site change/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /insert cgm sensor/i })).toBeInTheDocument()
  })

  test('calls createTreatment with INSULIN_CHANGE when button clicked', async () => {
    mockedCreate.mockResolvedValueOnce({ data: {} } as never)
    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /change insulin cartridge/i }))

    await waitFor(() => {
      expect(mockedCreate).toHaveBeenCalledWith('user-1', expect.objectContaining({ type: 'INSULIN_CHANGE' }))
    })
  })

  test('calls createTreatment with SITE_CHANGE when button clicked', async () => {
    mockedCreate.mockResolvedValueOnce({ data: {} } as never)
    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /site change/i }))

    await waitFor(() => {
      expect(mockedCreate).toHaveBeenCalledWith('user-1', expect.objectContaining({ type: 'SITE_CHANGE' }))
    })
  })

  test('calls createTreatment with SENSOR_INSERT when button clicked', async () => {
    mockedCreate.mockResolvedValueOnce({ data: {} } as never)
    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /insert cgm sensor/i }))

    await waitFor(() => {
      expect(mockedCreate).toHaveBeenCalledWith('user-1', expect.objectContaining({ type: 'SENSOR_INSERT' }))
    })
  })

  test('shows success toast after successful log', async () => {
    mockedCreate.mockResolvedValueOnce({ data: {} } as never)
    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /change insulin cartridge/i }))

    await waitFor(() => {
      expect(mockedToastSuccess).toHaveBeenCalled()
    })
  })

  test('shows error toast when createTreatment fails', async () => {
    mockedCreate.mockRejectedValueOnce(new Error('Network error'))
    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /site change/i }))

    await waitFor(() => {
      expect(mockedToastError).toHaveBeenCalled()
    })
  })

  test('calls onLogged callback after successful log', async () => {
    mockedCreate.mockResolvedValueOnce({ data: {} } as never)
    const onLogged = vi.fn()
    render(<QuickLogButtons userId="user-1" onLogged={onLogged} />)

    fireEvent.click(screen.getByRole('button', { name: /insert cgm sensor/i }))

    await waitFor(() => {
      expect(onLogged).toHaveBeenCalledOnce()
    })
  })

  test('does not call onLogged when createTreatment fails', async () => {
    mockedCreate.mockRejectedValueOnce(new Error('Server error'))
    const onLogged = vi.fn()
    render(<QuickLogButtons userId="user-1" onLogged={onLogged} />)

    fireEvent.click(screen.getByRole('button', { name: /site change/i }))

    await waitFor(() => {
      expect(mockedToastError).toHaveBeenCalled()
    })
    expect(onLogged).not.toHaveBeenCalled()
  })

  test('disables all buttons while a log is in progress', async () => {
    let resolve: (value: unknown) => void = () => {}
    mockedCreate.mockImplementationOnce(() => new Promise((res) => { resolve = res }))

    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /change insulin cartridge/i }))

    // While in-flight all three buttons should be disabled
    await waitFor(() => {
      const buttons = screen.getAllByRole('button')
      expect(buttons.every((btn) => (btn as HTMLButtonElement).disabled)).toBe(true)
    })

    // Resolve and verify buttons re-enable
    resolve({ data: {} })
    await waitFor(() => {
      const buttons = screen.getAllByRole('button')
      expect(buttons.some((btn) => !(btn as HTMLButtonElement).disabled)).toBe(true)
    })
  })

  test('sends treatedAt as valid ISO string close to now', async () => {
    const before = Date.now()
    mockedCreate.mockResolvedValueOnce({ data: {} } as never)
    render(<QuickLogButtons userId="user-1" />)

    fireEvent.click(screen.getByRole('button', { name: /insert cgm sensor/i }))

    await waitFor(() => {
      expect(mockedCreate).toHaveBeenCalled()
    })

    const call = mockedCreate.mock.calls[0]
    const payload = call[1] as { treatedAt: string }
    const logged = new Date(payload.treatedAt).getTime()
    expect(logged).toBeGreaterThanOrEqual(before)
    expect(logged).toBeLessThanOrEqual(Date.now())
  })
})
