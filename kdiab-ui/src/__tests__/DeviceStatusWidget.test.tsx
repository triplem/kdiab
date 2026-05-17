import { render, screen, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import { DeviceStatusWidget } from '../features/treatments/DeviceStatusWidget'
import '../i18n'

vi.mock('../api/treatmentsApi', () => ({
  treatmentsApi: {
    getLatestDeviceStatus: vi.fn(),
    listTreatments: vi.fn(),
    createTreatment: vi.fn(),
  },
}))

const { treatmentsApi } = await import('../api/treatmentsApi')
const mockGetLatestDeviceStatus = vi.mocked(treatmentsApi.getLatestDeviceStatus)

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <TimeFormatProvider>
        {children}
      </TimeFormatProvider>
    </QueryClientProvider>
  )
}

describe('DeviceStatusWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders nothing when query returns null', async () => {
    mockGetLatestDeviceStatus.mockResolvedValue({ data: null } as never)
    const { container } = render(<DeviceStatusWidget userId="u1" />, { wrapper })
    await waitFor(() => {
      expect(mockGetLatestDeviceStatus).toHaveBeenCalledWith('u1')
    })
    expect(container.firstChild).toBeNull()
  })

  test('renders nothing when query throws', async () => {
    mockGetLatestDeviceStatus.mockRejectedValue(new Error('network error'))
    const { container } = render(<DeviceStatusWidget userId="u1" />, { wrapper })
    await waitFor(() => {
      expect(mockGetLatestDeviceStatus).toHaveBeenCalled()
    })
    expect(container.firstChild).toBeNull()
  })

  test('renders device name when data is available', async () => {
    mockGetLatestDeviceStatus.mockResolvedValue({
      data: {
        id: '1',
        userId: 'u1',
        recordedAt: '2024-06-01T10:00:00Z',
        device: 'Medtronic 670G',
      },
    } as never)
    render(<DeviceStatusWidget userId="u1" />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('Medtronic 670G')).toBeDefined()
    })
  })

  test('renders pumpName when provided', async () => {
    mockGetLatestDeviceStatus.mockResolvedValue({
      data: {
        id: '1',
        userId: 'u1',
        recordedAt: '2024-06-01T10:00:00Z',
        device: 'Device X',
        pumpName: 'Omnipod 5',
      },
    } as never)
    render(<DeviceStatusWidget userId="u1" />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('Omnipod 5')).toBeDefined()
    })
  })

  test('renders reservoir and battery when provided', async () => {
    mockGetLatestDeviceStatus.mockResolvedValue({
      data: {
        id: '1',
        userId: 'u1',
        recordedAt: '2024-06-01T10:00:00Z',
        device: 'Pump A',
        reservoirUnits: 150.5,
        batteryLevel: 80,
      },
    } as never)
    render(<DeviceStatusWidget userId="u1" />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('150.5 U')).toBeDefined()
      expect(screen.getByText('80%')).toBeDefined()
    })
  })

  test('does not render pumpName section when absent', async () => {
    mockGetLatestDeviceStatus.mockResolvedValue({
      data: {
        id: '1',
        userId: 'u1',
        recordedAt: '2024-06-01T10:00:00Z',
        device: 'Device X',
      },
    } as never)
    render(<DeviceStatusWidget userId="u1" />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('Device X')).toBeDefined()
    })
    expect(screen.queryByText('Omnipod 5')).toBeNull()
  })
})
