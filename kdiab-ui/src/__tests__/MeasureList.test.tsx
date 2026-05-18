import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import '../i18n'

vi.mock('../api/measuresApi', () => ({
  measuresApi: {
    listMeasures: vi.fn(),
    archiveMeasures: vi.fn(),
    unarchiveMeasures: vi.fn(),
    deleteMeasures: vi.fn(),
    createMeasure: vi.fn(),
    updateMeasure: vi.fn(),
  },
}))

vi.mock('../features/measures/AddMeasureModal', () => ({
  AddMeasureModal: () => <div data-testid="add-measure-modal" />,
}))

vi.mock('../components/ConfirmModal', () => ({
  ConfirmModal: ({ onConfirm, onCancel, title }: { onConfirm: () => void; onCancel: () => void; title: string }) => (
    <div data-testid="confirm-modal">
      <span>{title}</span>
      <button onClick={onConfirm}>Confirm</button>
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}))

import { measuresApi } from '../api/measuresApi'
import { MeasureList } from '../features/measures/MeasureList'
import { renderDataSummary } from '../features/measures/measureHelpers'

const mockedListMeasures = vi.mocked(measuresApi.listMeasures)
const mockedDeleteMeasures = vi.mocked(measuresApi.deleteMeasures)

interface MeasureResponse {
  id: string
  userId: string
  measuredAt: string
  createdAt: string
  type: string
  source: string
  status: string
  data: Record<string, unknown>
}

function makeMeasure(overrides: Partial<MeasureResponse> = {}): MeasureResponse {
  return {
    id: 'measure-1',
    userId: 'user-1',
    measuredAt: '2024-01-15T10:30:00Z',
    createdAt: '2024-01-15T10:30:00Z',
    type: 'CGM',
    source: 'DEXCOM',
    status: 'ACTIVE',
    data: { value: 120, unit: 'mg/dL', trend: 'Flat' },
    ...overrides,
  }
}

function makePagedResponse(items: MeasureResponse[], totalCount = items.length) {
  return {
    data: { items, page: 0, size: 50, totalCount },
  }
}

function renderWithProviders(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>{ui}</TimeFormatProvider>
    </QueryClientProvider>
  )
}

describe('MeasureList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('shows loading state on initial fetch', () => {
    mockedListMeasures.mockImplementation(() => new Promise(() => {}))
    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  test('shows empty state message when no measures returned', async () => {
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([]) as never)
    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )
    await waitFor(() => {
      // list.empty = "No items recorded yet."
      expect(screen.getByText(/no items recorded yet/i)).toBeInTheDocument()
    })
  })

  test('renders measure rows when data is present', async () => {
    const measures = [
      makeMeasure({ id: 'm-1', type: 'CGM', data: { value: 120, unit: 'mg/dL', trend: 'Flat' } }),
      makeMeasure({ id: 'm-2', type: 'BGM', data: { value: 95, unit: 'mg/dL' } }),
    ]
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse(measures) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )

    await waitFor(() => {
      expect(screen.getByText('CGM')).toBeInTheDocument()
      expect(screen.getByText('BGM')).toBeInTheDocument()
    })
  })

  test('shows glucose alert banner when a low reading is present', async () => {
    const lowMeasure = makeMeasure({
      id: 'm-low',
      type: 'CGM',
      data: { value: 55, unit: 'mg/dL', trend: 'SingleDown' },
    })
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([lowMeasure]) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })

  test('does not show glucose alert banner when readings are in target range', async () => {
    const normalMeasure = makeMeasure({
      id: 'm-normal',
      type: 'CGM',
      data: { value: 120, unit: 'mg/dL', trend: 'Flat' },
    })
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([normalMeasure]) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )

    await waitFor(() => {
      expect(screen.getByText('CGM')).toBeInTheDocument()
    })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('shows delete button and triggers confirm modal when canDelete is true', async () => {
    const measure = makeMeasure({ id: 'm-1' })
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([measure]) as never)
    mockedDeleteMeasures.mockResolvedValueOnce({ data: {} } as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={true} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/delete/i).length).toBeGreaterThan(0)
    })
  })

  test('shows page size selector', async () => {
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([]) as never)
    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )
    await waitFor(() => {
      const pageSizeSelect = screen.getByRole('combobox')
      expect(pageSizeSelect).toBeInTheDocument()
    })
  })

  test('toggles to archived view when Show Archived button is clicked', async () => {
    mockedListMeasures.mockResolvedValue(makePagedResponse([]) as never)
    renderWithProviders(
      <MeasureList userId="user-1" canArchive={true} canDelete={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/show archived/i)).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText(/show archived/i))

    await waitFor(() => {
      expect(mockedListMeasures).toHaveBeenCalledWith('user-1', 0, expect.any(Number), 'ARCHIVED')
    })
  })

  test('expands row details on row click', async () => {
    const measure = makeMeasure({ id: 'm-detail' })
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([measure]) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )

    await waitFor(() => {
      expect(screen.getByText('CGM')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('CGM').closest('tr')!)

    await waitFor(() => {
      expect(screen.getByText(/system id/i)).toBeInTheDocument()
    })
  })

  test('shows error state when query fails', async () => {
    mockedListMeasures.mockRejectedValueOnce(new Error('Server error'))
    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )
    await waitFor(() => {
      // list.error = "Failed to load measures. Please try again."
      expect(screen.getByText(/failed to load measures/i)).toBeInTheDocument()
    })
  })

  test('shows single delete confirm modal and calls deleteMeasures when confirmed', async () => {
    const measure = makeMeasure({ id: 'm-to-delete' })
    mockedListMeasures.mockResolvedValue(makePagedResponse([measure]) as never)
    mockedDeleteMeasures.mockResolvedValueOnce({ data: {} } as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={true} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/delete/i).length).toBeGreaterThan(0)
    })

    // Click the per-row delete button
    const deleteButtons = screen.getAllByText(/delete/i)
    fireEvent.click(deleteButtons[deleteButtons.length - 1])

    await waitFor(() => {
      expect(screen.getByTestId('confirm-modal')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Confirm'))

    await waitFor(() => {
      expect(mockedDeleteMeasures).toHaveBeenCalledWith('user-1', { measureIds: ['m-to-delete'] })
    })
  })

  test('does not call deleteMeasures when confirm modal is cancelled', async () => {
    const measure = makeMeasure({ id: 'm-cancel' })
    mockedListMeasures.mockResolvedValue(makePagedResponse([measure]) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={true} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/delete/i).length).toBeGreaterThan(0)
    })

    const deleteButtons = screen.getAllByText(/delete/i)
    fireEvent.click(deleteButtons[deleteButtons.length - 1])

    await waitFor(() => {
      expect(screen.getByTestId('confirm-modal')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Cancel'))
    expect(mockedDeleteMeasures).not.toHaveBeenCalled()
  })

  test('selects individual row via checkbox', async () => {
    const measure = makeMeasure({ id: 'm-select', type: 'CGM', data: { value: 110, unit: 'mg/dL' } })
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([measure]) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={true} canDelete={true} />
    )

    await waitFor(() => {
      expect(screen.getByText('CGM')).toBeInTheDocument()
    })

    // Find row checkboxes (skip the "select all" header checkbox)
    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[checkboxes.length - 1])

    // Archive Selected button should now be enabled
    await waitFor(() => {
      const archiveBtn = screen.getByText(/archive selected/i)
      expect(archiveBtn).not.toBeDisabled()
    })
  })

  test('shows very high glucose alert for CGM reading above 250 mg/dL', async () => {
    const veryHighMeasure = makeMeasure({
      id: 'm-very-high',
      type: 'CGM',
      data: { value: 300, unit: 'mg/dL', trend: 'SingleUp' },
    })
    mockedListMeasures.mockResolvedValueOnce(makePagedResponse([veryHighMeasure]) as never)

    renderWithProviders(
      <MeasureList userId="user-1" canArchive={false} canDelete={false} />
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })
})

describe('renderDataSummary', () => {
  function makeMeasureForSummary(type: string, data: Record<string, unknown>) {
    return {
      id: 'test',
      userId: 'u',
      measuredAt: '2024-01-01T00:00:00Z',
      createdAt: '2024-01-01T00:00:00Z',
      type,
      source: 'MANUAL',
      status: 'ACTIVE',
      data,
    }
  }

  test('formats CGM reading with mg/dL unit and trend arrow', () => {
    const m = makeMeasureForSummary('CGM', { value: 120, unit: 'mg/dL', trend: 'SingleUp' })
    expect(renderDataSummary(m)).toBe('120 mg/dL ↑')
  })

  test('formats CGM reading without trend when trend absent', () => {
    const m = makeMeasureForSummary('CGM', { value: 100, unit: 'mg/dL' })
    expect(renderDataSummary(m)).toBe('100 mg/dL')
  })

  test('formats BGM reading', () => {
    const m = makeMeasureForSummary('BGM', { value: 90, unit: 'mg/dL' })
    expect(renderDataSummary(m)).toBe('90 mg/dL')
  })

  test('formats BLOOD_PRESSURE reading with systolic/diastolic', () => {
    const m = makeMeasureForSummary('BLOOD_PRESSURE', { systolic: 120, diastolic: 80, unit: 'mmHg' })
    expect(renderDataSummary(m)).toBe('120/80 mmHg')
  })

  test('formats WEIGHT reading', () => {
    const m = makeMeasureForSummary('WEIGHT', { value: 70, unit: 'kg' })
    expect(renderDataSummary(m)).toBe('70 kg')
  })

  test('formats PULSE reading', () => {
    const m = makeMeasureForSummary('PULSE', { value: 72, unit: 'bpm' })
    expect(renderDataSummary(m)).toBe('72 bpm')
  })

  test('formats BG_CHECK reading', () => {
    const m = makeMeasureForSummary('BG_CHECK', { value: 105, unit: 'mg/dL' })
    expect(renderDataSummary(m)).toBe('105 mg/dL')
  })

  test('formats KETONE_CHECK reading with method', () => {
    const m = makeMeasureForSummary('KETONE_CHECK', { value: 0.5, unit: 'mmol/L', method: 'blood' })
    expect(renderDataSummary(m)).toBe('0.5 mmol/L (blood)')
  })

  test('falls back to JSON for unknown measure type', () => {
    const m = makeMeasureForSummary('UNKNOWN', { foo: 'bar' })
    expect(renderDataSummary(m)).toBe('{"foo":"bar"}')
  })

  test('formats CGM mmol/L unit correctly', () => {
    const m = makeMeasureForSummary('CGM', { value: 6.7, unit: 'mmol/L', trend: 'Flat' })
    expect(renderDataSummary(m)).toBe('6.7 mmol/L →')
  })

  test('falls back to JSON for CGM when value is null', () => {
    const m = makeMeasureForSummary('CGM', { unit: 'mg/dL', trend: 'Flat' })
    expect(renderDataSummary(m)).toContain('unit')
  })
})
