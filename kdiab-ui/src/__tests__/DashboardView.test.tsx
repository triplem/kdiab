import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import '../i18n'

vi.mock('../features/dashboard/useDashboardData', () => ({
  useDashboardData: vi.fn(),
}))

// Heavy sub-components that perform their own API calls — stub them out
vi.mock('../features/treatments/DeviceStatusWidget', () => ({
  DeviceStatusWidget: () => null,
}))
vi.mock('../features/dashboard/DeviceUsageCard', () => ({
  DeviceUsageCard: () => null,
}))
vi.mock('../features/dashboard/GlucoseTrendChart', () => ({
  GlucoseTrendChart: () => null,
}))
vi.mock('../features/dashboard/BasalRateChart', () => ({
  BasalRateChart: () => null,
}))

import { useDashboardData } from '../features/dashboard/useDashboardData'
import { DashboardView } from '../features/dashboard/DashboardView'

const mockUseDashboardData = vi.mocked(useDashboardData)

/** Minimal valid return value so the hook callers inside DashboardView don't throw */
function makeHookReturn(overrides: Partial<ReturnType<typeof useDashboardData>> = {}): ReturnType<typeof useDashboardData> {
  return {
    windowKey: '6h',
    setWindowKey: vi.fn(),
    windowEndOffset: 0,
    setWindowEndOffset: vi.fn(),
    windowHours: 6,
    windowMs: 6 * 60 * 60 * 1000,
    windowEnd: new Date('2024-06-01T12:00:00Z'),
    windowFrom: '2024-06-01T06:00:00Z',
    windowTo: '2024-06-01T12:00:00Z',
    atNow: true,
    recentTimeline: undefined,
    windowTimeline: undefined,
    isLoading: false,
    isError: false,
    activeProfile: undefined,
    deviceAge: undefined,
    deviceStatus: undefined,
    userMe: undefined,
    ...overrides,
  }
}

function renderWithQuery(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders error banner with role=alert when isError is true and not loading', () => {
    mockUseDashboardData.mockReturnValue(
      makeHookReturn({ isError: true, isLoading: false }),
    )

    renderWithQuery(<DashboardView userId="user-1" glucoseUnit="mg/dL" />)

    const alert = screen.getByRole('alert')
    expect(alert).toBeDefined()
    expect(alert.textContent).toContain('Could not load glucose data')
  })

  test('does not render error banner when isLoading is true', () => {
    mockUseDashboardData.mockReturnValue(
      makeHookReturn({ isError: true, isLoading: true }),
    )

    renderWithQuery(<DashboardView userId="user-1" glucoseUnit="mg/dL" />)

    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('does not render error banner when isError is false', () => {
    mockUseDashboardData.mockReturnValue(
      makeHookReturn({ isError: false, isLoading: false }),
    )

    renderWithQuery(<DashboardView userId="user-1" glucoseUnit="mg/dL" />)

    expect(screen.queryByRole('alert')).toBeNull()
  })
})
