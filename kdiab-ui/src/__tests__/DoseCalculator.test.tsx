import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import '../i18n'

const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()
vi.mock('sonner', () => ({
  Toaster: () => null,
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}))

vi.mock('../api/calcApi', () => ({
  calcApi: {
    calculateDose: vi.fn(),
  },
}))

vi.mock('../api/measuresApi', () => ({
  measuresApi: {
    listMeasures: vi.fn().mockResolvedValue({ data: { items: [], page: 0, size: 10, totalElements: 0 } }),
  },
}))

vi.mock('../api/treatmentsApi', () => ({
  treatmentsApi: {
    listTreatments: vi.fn().mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 0 } }),
    createTreatment: vi.fn(),
  },
}))

import { calcApi } from '../api/calcApi'
import type { DoseResponse } from '../api/calcApi'
import { measuresApi } from '../api/measuresApi'
import { treatmentsApi } from '../api/treatmentsApi'
import { DoseCalculator } from '../features/calc/DoseCalculator'

const mockedCalculateDose = vi.mocked(calcApi.calculateDose)
const mockedListMeasures = vi.mocked(measuresApi.listMeasures)
const mockedCreateTreatment = vi.mocked(treatmentsApi.createTreatment)

function makeDoseResponse(overrides: Partial<DoseResponse> = {}): DoseResponse {
  return {
    correctionDose: 1.5,
    carbDose: 2.0,
    trendAdjustment: 0.5,
    totalRecommended: 4.0,
    breakdown: {
      currentBgMgDl: 180,
      targetBgMgDl: 100,
      isf: 50,
      icr: 10,
      trend: 'FLAT',
      carbsGrams: 20,
    },
    profileId: 'profile-1',
    warnings: [],
    ...overrides,
  }
}

function renderWithQuery(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('DoseCalculator', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockToastSuccess.mockClear()
    mockToastError.mockClear()
  })

  test('renders the form with required fields', () => {
    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    // Labels come from i18n keys: doseCalc.currentBg = "Current Blood Glucose:"
    expect(screen.getByLabelText(/current blood glucose/i)).toBeInTheDocument()
    // doseCalc.trend = "CGM Trend:"
    expect(screen.getByLabelText(/cgm trend/i)).toBeInTheDocument()
    // doseCalc.carbsGrams = "Carbs to cover (g):"
    expect(screen.getByLabelText(/carbs to cover/i)).toBeInTheDocument()
  })

  test('calculate button is disabled when currentBg is empty', () => {
    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    // doseCalc.calculate = "Calculate"
    const button = screen.getByRole('button', { name: 'Calculate' })
    expect(button).toBeDisabled()
  })

  test('calculate button becomes enabled when currentBg is filled in', () => {
    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    const bgInput = screen.getByLabelText(/current blood glucose/i)
    fireEvent.change(bgInput, { target: { value: '150' } })
    const button = screen.getByRole('button', { name: 'Calculate' })
    expect(button).not.toBeDisabled()
  })

  test('renders dose result after successful API call', async () => {
    const doseResponse = makeDoseResponse({ totalRecommended: 4.0, correctionDose: 1.5, carbDose: 2.0, trendAdjustment: 0.5 })
    mockedCalculateDose.mockResolvedValueOnce({ data: doseResponse } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '180' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      // doseCalc.totalDose = "Recommended Dose"
      expect(screen.getByText('Recommended Dose')).toBeInTheDocument()
    })
    expect(mockedCalculateDose).toHaveBeenCalledWith('user-1', expect.objectContaining({
      currentBg: 180,
      glucoseUnit: 'mg/dL',
      trend: 'FLAT',
      activeIob: expect.any(Number),
    }))
  })

  test('shows error message on API failure', async () => {
    mockedCalculateDose.mockRejectedValueOnce(new Error('Network error'))

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '180' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      // doseCalc.error = "Calculation failed. Please try again."
      expect(screen.getByText(/calculation failed/i)).toBeInTheDocument()
    })
  })

  test('renders all CGM trend options in the select', () => {
    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    const trendSelect = screen.getByLabelText(/cgm trend/i)
    const options = trendSelect.querySelectorAll('option')
    expect(options.length).toBe(8)
  })

  test('shows warnings list when result contains warnings', async () => {
    const doseResponse = makeDoseResponse({
      warnings: ['Blood glucose is very high', 'Consider contacting your doctor'],
    })
    mockedCalculateDose.mockResolvedValueOnce({ data: doseResponse } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '300' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(screen.getByText('Blood glucose is very high')).toBeInTheDocument()
      expect(screen.getByText('Consider contacting your doctor')).toBeInTheDocument()
    })
  })

  test('warnings container has role=alert and aria-live=assertive', async () => {
    const doseResponse = makeDoseResponse({
      warnings: ['BG is hypoglycemic — treat hypo first'],
    })
    mockedCalculateDose.mockResolvedValueOnce({ data: doseResponse } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(screen.getByText('BG is hypoglycemic — treat hypo first')).toBeInTheDocument()
    })

    // The warnings container must be announced as an alert by screen readers
    const alerts = screen.getAllByRole('alert')
    const warningAlert = alerts.find(el => el.textContent?.includes('BG is hypoglycemic'))
    expect(warningAlert).toBeDefined()
    expect(warningAlert).toHaveAttribute('aria-live', 'assertive')
  })

  test('passes carbsGrams to API when carbs field is filled', async () => {
    mockedCalculateDose.mockResolvedValueOnce({ data: makeDoseResponse() } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mmol/L" />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '9.5' } })
    fireEvent.change(screen.getByLabelText(/carbs to cover/i), { target: { value: '45' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(mockedCalculateDose).toHaveBeenCalledWith('user-1', expect.objectContaining({
        currentBg: 9.5,
        glucoseUnit: 'mmol/L',
        carbsGrams: 45,
        activeIob: expect.any(Number),
      }))
    })
  })

  test('passes activeIob prop to API when provided', async () => {
    mockedCalculateDose.mockResolvedValueOnce({ data: makeDoseResponse() } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" activeIob={2.5} />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '180' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(mockedCalculateDose).toHaveBeenCalledWith('user-1', expect.objectContaining({
        activeIob: 2.5,
      }))
    })
  })

  test('displays active IOB value in form', () => {
    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" activeIob={1.75} />)
    // The IOB row shows the label and the formatted value
    expect(screen.getByText(/active iob/i)).toBeInTheDocument()
    expect(screen.getByText('1.75 units')).toBeInTheDocument()
  })

  test('shows success toast with dose amount after logging', async () => {
    const doseResponse = makeDoseResponse({ totalRecommended: 3.5 })
    mockedCalculateDose.mockResolvedValueOnce({ data: doseResponse } as never)
    mockedCreateTreatment.mockResolvedValue({ data: { id: 'treatment-1' } } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" activeIob={0} />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '180' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      // doseCalc.acceptLog = "Accept & Log {{dose}} units"
      expect(screen.getByRole('button', { name: /accept & log/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /accept & log/i }))

    await waitFor(() => {
      expect(mockToastSuccess).toHaveBeenCalledWith(
        expect.stringContaining('3.5 U'),
        expect.objectContaining({ duration: 4000 }),
      )
    })
  })

  test('shows error toast when logging fails', async () => {
    const doseResponse = makeDoseResponse({ totalRecommended: 2.0 })
    mockedCalculateDose.mockResolvedValueOnce({ data: doseResponse } as never)
    mockedCreateTreatment.mockRejectedValueOnce(new Error('Network error'))

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" activeIob={0} />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '160' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /accept & log/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /accept & log/i }))

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith(
        expect.stringContaining('Failed to save dose'),
        expect.objectContaining({ duration: 4000 }),
      )
    })
  })

  test('does not pre-fill BG or trend when CGM reading is stale (> 15 min)', async () => {
    const staleTs = new Date(Date.now() - 20 * 60_000).toISOString()
    mockedListMeasures.mockResolvedValueOnce({
      data: {
        items: [{ id: 'cgm-1', userId: 'user-1', type: 'CGM', measuredAt: staleTs, createdAt: staleTs, source: 'NIGHTSCOUT', status: 'ACTIVE', data: { value: 55, trend: 'SINGLE_DOWN' } }],
        page: 0, size: 10, totalElements: 1,
      },
    } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)

    await waitFor(() => {
      expect(screen.getByText(/min old/i)).toBeInTheDocument()
    })

    const bgInput = screen.getByLabelText(/current blood glucose/i) as HTMLInputElement
    expect(bgInput.value).toBe('')
    const trendSelect = screen.getByLabelText(/cgm trend/i) as HTMLSelectElement
    expect(trendSelect.value).toBe('FLAT')
  })

  test('pre-fills BG and trend from a fresh CGM reading (<= 15 min)', async () => {
    const freshTs = new Date(Date.now() - 5 * 60_000).toISOString()
    mockedListMeasures.mockResolvedValueOnce({
      data: {
        items: [{ id: 'cgm-2', userId: 'user-1', type: 'CGM', measuredAt: freshTs, createdAt: freshTs, source: 'NIGHTSCOUT', status: 'ACTIVE', data: { value: 180, trend: 'SINGLE_UP' } }],
        page: 0, size: 10, totalElements: 1,
      },
    } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)

    await waitFor(() => {
      const bgInput = screen.getByLabelText(/current blood glucose/i) as HTMLInputElement
      expect(bgInput.value).toBe('180')
    })
    const trendSelect = screen.getByLabelText(/cgm trend/i) as HTMLSelectElement
    expect(trendSelect.value).toBe('SINGLE_UP')
  })

  test('disclaimer is rendered when a dose result is present', async () => {
    mockedCalculateDose.mockResolvedValueOnce({ data: makeDoseResponse() } as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '180' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(screen.getByRole('note')).toBeInTheDocument()
    })
    expect(screen.getByRole('note').textContent).toMatch(/suggested dose/i)
  })

  test('disclaimer is not shown before a result is calculated', () => {
    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" />)
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })

  test('log button is disabled while mutation is in flight', async () => {
    const doseResponse = makeDoseResponse({ totalRecommended: 2.0 })
    mockedCalculateDose.mockResolvedValueOnce({ data: doseResponse } as never)
    // Never resolves so mutation stays pending
    mockedCreateTreatment.mockReturnValue(new Promise(() => undefined) as never)

    renderWithQuery(<DoseCalculator userId="user-1" glucoseUnit="mg/dL" activeIob={0} />)
    fireEvent.change(screen.getByLabelText(/current blood glucose/i), { target: { value: '160' } })
    fireEvent.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /accept & log/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /accept & log/i }))

    await waitFor(() => {
      // doseCalc.logging = "Logging..."
      expect(screen.getByRole('button', { name: /logging/i })).toBeDisabled()
    })
  })
})
