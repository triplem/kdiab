import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import '../i18n'

vi.mock('../api/treatmentsApi', () => ({
  treatmentsApi: {
    listTreatments: vi.fn(),
    createTreatment: vi.fn(),
    deleteTreatments: vi.fn(),
    archiveTreatments: vi.fn(),
    unarchiveTreatments: vi.fn(),
    updateTreatment: vi.fn(),
  },
}))

vi.mock('../features/treatments/AddTreatmentModal', () => ({
  AddTreatmentModal: () => <div data-testid="add-treatment-modal" />,
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

import { treatmentsApi } from '../api/treatmentsApi'
import type { TreatmentResponse, PagedTreatments } from '../api/treatmentsApi'
import { TreatmentList } from '../features/treatments/TreatmentList'

const mockedListTreatments = vi.mocked(treatmentsApi.listTreatments)
const mockedDeleteTreatments = vi.mocked(treatmentsApi.deleteTreatments)
const mockedArchiveTreatments = vi.mocked(treatmentsApi.archiveTreatments)

function makeTreatment(overrides: Partial<TreatmentResponse> = {}): TreatmentResponse {
  return {
    id: 'treatment-1',
    userId: 'user-1',
    treatedAt: '2024-01-15T10:30:00Z',
    createdAt: '2024-01-15T10:30:00Z',
    type: 'BOLUS',
    status: 'ACTIVE',
    data: { insulin: 4.5, insulinType: 'Fiasp' },
    ...overrides,
  }
}

function makePagedResponse(items: TreatmentResponse[], totalCount = items.length): { data: PagedTreatments } {
  return {
    data: { items, page: 0, size: 25, totalCount },
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

describe('TreatmentList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('shows loading state on initial fetch', () => {
    mockedListTreatments.mockImplementation(() => new Promise(() => {}))
    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  test('shows empty state when no treatments returned', async () => {
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([]) as never)
    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )
    await waitFor(() => {
      // list.empty = "No items recorded yet."
      expect(screen.getByText(/no items recorded yet/i)).toBeInTheDocument()
    })
  })

  test('renders treatment rows with type badges when data is present', async () => {
    const treatments = [
      makeTreatment({ id: 't-1', type: 'BOLUS', data: { insulin: 4.5, insulinType: 'Fiasp' } }),
      makeTreatment({ id: 't-2', type: 'CARBS', data: { carbs: 30 } }),
    ]
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse(treatments) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      // treatmentModal.types.BOLUS = "Bolus", treatmentModal.types.CARBS = "Carbohydrates"
      expect(screen.getByText('Bolus')).toBeInTheDocument()
      expect(screen.getByText('Carbohydrates')).toBeInTheDocument()
    })
  })

  test('does not render checkbox column when canDelete and canArchive are both false', async () => {
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([]) as never)
    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )
    await waitFor(() => {
      expect(screen.queryByRole('checkbox')).toBeNull()
    })
  })

  test('renders checkbox column when canDelete is true', async () => {
    const treatment = makeTreatment({ id: 't-1' })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={true} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getAllByRole('checkbox').length).toBeGreaterThan(0)
    })
  })

  test('shows delete button when canDelete is true', async () => {
    const treatment = makeTreatment({ id: 't-1' })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={true} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/delete/i).length).toBeGreaterThan(0)
    })
  })

  test('shows archive button when canArchive is true', async () => {
    const treatment = makeTreatment({ id: 't-1' })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={true} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/archive/i).length).toBeGreaterThan(0)
    })
  })

  test('expands row details on row click', async () => {
    const treatment = makeTreatment({ id: 't-detail', type: 'BOLUS', data: { insulin: 4.5 } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      // treatmentModal.types.BOLUS = "Bolus"
      expect(screen.getByText('Bolus')).toBeInTheDocument()
    })

    // Click the tr element that has role="button"
    const rowButtons = screen.getAllByRole('button')
    const trButton = rowButtons.find(el => el.tagName === 'TR')
    fireEvent.click(trButton!)

    await waitFor(() => {
      // list.systemId = "System ID"
      expect(screen.getByText(/system id/i)).toBeInTheDocument()
    })
  })

  test('toggles to archived view when Show Archived button is clicked', async () => {
    mockedListTreatments.mockResolvedValue(makePagedResponse([]) as never)
    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={true} />
    )

    await waitFor(() => {
      expect(screen.getByText(/show archived/i)).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText(/show archived/i))

    await waitFor(() => {
      expect(mockedListTreatments).toHaveBeenCalledWith('user-1', 'ARCHIVED', expect.any(Number), expect.any(Number))
    })
  })

  test('shows confirm modal and calls deleteTreatments when single delete is confirmed', async () => {
    const treatment = makeTreatment({ id: 't-delete-me' })
    mockedListTreatments.mockResolvedValue(makePagedResponse([treatment]) as never)
    mockedDeleteTreatments.mockResolvedValueOnce({ data: {} } as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={true} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/delete/i).length).toBeGreaterThan(0)
    })

    const deleteButtons = screen.getAllByText(/delete/i)
    // Click the per-row delete button (last one that is not the bulk delete)
    fireEvent.click(deleteButtons[deleteButtons.length - 1])

    await waitFor(() => {
      expect(screen.getByTestId('confirm-modal')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Confirm'))

    await waitFor(() => {
      expect(mockedDeleteTreatments).toHaveBeenCalledWith('user-1', { treatmentIds: ['t-delete-me'] })
    })
  })

  test('renders pagination controls', async () => {
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([], 0) as never)
    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )
    await waitFor(() => {
      expect(screen.getByText(/previous/i)).toBeInTheDocument()
      expect(screen.getByText(/next/i)).toBeInTheDocument()
    })
  })

  test('renders treatment data summary for BOLUS type', async () => {
    const treatment = makeTreatment({ type: 'BOLUS', data: { insulin: 3.5, insulinType: 'NovoRapid' } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/3\.5 U/)).toBeInTheDocument()
    })
  })

  test('does not call deleteTreatments when confirm modal is cancelled', async () => {
    const treatment = makeTreatment({ id: 't-cancel' })
    mockedListTreatments.mockResolvedValue(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={true} canArchive={false} />
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

    expect(mockedDeleteTreatments).not.toHaveBeenCalled()
  })

  test('renders CARBS treatment data summary with absorption time', async () => {
    const treatment = makeTreatment({ type: 'CARBS', data: { carbs: 45, absorptionTime: 2 } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/45 g/)).toBeInTheDocument()
    })
  })

  test('renders TEMP_BASAL treatment data summary', async () => {
    const treatment = makeTreatment({ type: 'TEMP_BASAL', data: { rate: 0.8, duration: 90 } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/0\.8 U\/h/)).toBeInTheDocument()
    })
  })

  test('renders EXERCISE treatment data summary', async () => {
    const treatment = makeTreatment({ type: 'EXERCISE', data: { duration: 45, intensity: 'Moderate' } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/45 min.*Moderate/)).toBeInTheDocument()
    })
  })

  test('renders NOTE treatment data summary', async () => {
    const treatment = makeTreatment({ type: 'NOTE', data: { text: 'Feeling well today' } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText('Feeling well today')).toBeInTheDocument()
    })
  })

  test('renders treatment with notes field', async () => {
    const treatment = makeTreatment({
      id: 't-with-notes',
      type: 'BOLUS',
      data: { insulin: 3.0 },
      notes: 'Pre-meal bolus',
    })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      // Notes are rendered as "— <notes text>" in a separate span
      expect(screen.getByText(/pre-meal bolus/i)).toBeInTheDocument()
    })
  })

  test('shows error state when query fails', async () => {
    mockedListTreatments.mockRejectedValueOnce(new Error('Server error'))
    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )
    await waitFor(() => {
      // list.error = "Failed to load measures. Please try again."
      expect(screen.getByText(/failed to load/i)).toBeInTheDocument()
    })
  })

  test('calls archiveTreatments when single archive button is clicked', async () => {
    const treatment = makeTreatment({ id: 't-archive' })
    mockedListTreatments.mockResolvedValue(makePagedResponse([treatment]) as never)
    mockedArchiveTreatments.mockResolvedValueOnce({ data: {} } as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={true} />
    )

    await waitFor(() => {
      expect(screen.getAllByText(/archive/i).length).toBeGreaterThan(0)
    })

    const archiveButtons = screen.getAllByText(/^Archive$/)
    fireEvent.click(archiveButtons[0])

    await waitFor(() => {
      expect(mockedArchiveTreatments).toHaveBeenCalledWith('user-1', { treatmentIds: ['t-archive'] })
    })
  })

  test('selects all rows when select-all checkbox is clicked', async () => {
    const treatments = [
      makeTreatment({ id: 't-1' }),
      makeTreatment({ id: 't-2', type: 'CARBS', data: { carbs: 20 } }),
    ]
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse(treatments) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={true} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getAllByRole('checkbox').length).toBeGreaterThan(1)
    })

    const allCheckboxes = screen.getAllByRole('checkbox')
    const selectAllCheckbox = allCheckboxes[0]
    fireEvent.click(selectAllCheckbox)

    await waitFor(() => {
      const deleteBtn = screen.getByText(/delete selected/i)
      expect(deleteBtn).not.toBeDisabled()
    })
  })

  test('renders CORRECTION_BOLUS data summary', async () => {
    const treatment = makeTreatment({ type: 'CORRECTION_BOLUS', data: { insulin: 1.5, insulinType: 'Fiasp' } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/1\.5 U/)).toBeInTheDocument()
    })
  })

  test('renders SITE_CHANGE treatment data summary', async () => {
    const treatment = makeTreatment({ type: 'SITE_CHANGE', data: { location: 'Left abdomen' } })
    mockedListTreatments.mockResolvedValueOnce(makePagedResponse([treatment]) as never)

    renderWithProviders(
      <TreatmentList userId="user-1" canDelete={false} canArchive={false} />
    )

    await waitFor(() => {
      expect(screen.getByText(/Left abdomen/)).toBeInTheDocument()
    })
  })
})
