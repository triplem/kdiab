import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import '../i18n'

vi.mock('../api/profilesApi', () => ({
  profilesApi: {
    listProfiles: vi.fn(),
    acceptProposedProfile: vi.fn(),
    rejectProposedProfile: vi.fn(),
    activateProfile: vi.fn(),
  },
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

import { profilesApi } from '../api/profilesApi'
import { toast } from 'sonner'
import { ProfileDiffView } from '../features/profiles/ProfileDiffView'
import type { Profile } from '../api/profilesApi'

const mockedAccept = vi.mocked(profilesApi.acceptProposedProfile)
const mockedReject = vi.mocked(profilesApi.rejectProposedProfile)
const mockedToastSuccess = vi.mocked(toast.success)
const mockedToastError = vi.mocked(toast.error)

function makeProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 'profile-1',
    userId: 'user-1',
    name: 'Test Profile',
    status: 'ACTIVE',
    insulinType: 'Fiasp',
    durationOfAction: 180,
    basal: [{ startTime: '00:00', value: 0.8 }],
    icr: [{ startTime: '00:00', value: 10 }],
    isf: [{ startTime: '00:00', value: 50 }],
    targets: [],
    ...overrides,
  }
}

function renderWithQuery(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>{ui}</TimeFormatProvider>
    </QueryClientProvider>,
  )
}

describe('ProfileDiffView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders the diff section heading', () => {
    const active = makeProfile({ id: 'active-1', status: 'ACTIVE' })
    const proposed = makeProfile({ id: 'proposed-1', status: 'PROPOSED', name: 'Doctor Plan' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByText('Review Proposed Profile')).toBeInTheDocument()
  })

  test('renders Current and Proposed column headers', () => {
    const active = makeProfile({ id: 'active-1', status: 'ACTIVE' })
    const proposed = makeProfile({ id: 'proposed-1', status: 'PROPOSED' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByText('Current (Active)')).toBeInTheDocument()
    expect(screen.getByText('Proposed')).toBeInTheDocument()
  })

  test('highlights changed name row', () => {
    const active = makeProfile({ id: 'active-1', name: 'Old Name' })
    const proposed = makeProfile({ id: 'proposed-1', name: 'New Name' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByText('Old Name')).toBeInTheDocument()
    expect(screen.getByText('New Name')).toBeInTheDocument()
  })

  test('does not highlight unchanged rows (no diff-highlight style when same)', () => {
    const active = makeProfile({ id: 'active-1', name: 'Same Name' })
    const proposed = makeProfile({ id: 'proposed-1', name: 'Same Name' })
    const { container } = renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    const nameRow = screen.getByText('Name').closest('tr')!
    expect(nameRow.style.background).not.toContain('#fff8c5')
    expect(container).toBeInTheDocument()
  })

  test('shows proposal reason when present', () => {
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1', proposalReason: 'Reduce overnight lows' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByText(/Reduce overnight lows/)).toBeInTheDocument()
  })

  test('does not show reason section when proposalReason is absent', () => {
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1', proposalReason: undefined })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.queryByText('Clinical rationale:')).not.toBeInTheDocument()
  })

  test('shows Accept and Reject buttons', () => {
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByRole('button', { name: /accept/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument()
  })

  test('Accept button calls acceptProposedProfile and shows success toast', async () => {
    mockedAccept.mockResolvedValueOnce(undefined as never)
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1', status: 'PROPOSED' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    fireEvent.click(screen.getByRole('button', { name: /accept/i }))
    await waitFor(() => {
      expect(mockedAccept).toHaveBeenCalledWith('user-1', 'proposed-1')
    })
    await waitFor(() => {
      expect(mockedToastSuccess).toHaveBeenCalledWith(
        'Proposed profile accepted and is now active.',
      )
    })
  })

  test('Reject button calls rejectProposedProfile and shows success toast', async () => {
    mockedReject.mockResolvedValueOnce(undefined as never)
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1', status: 'PROPOSED' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    fireEvent.click(screen.getByRole('button', { name: /reject/i }))
    await waitFor(() => {
      expect(mockedReject).toHaveBeenCalledWith('user-1', 'proposed-1')
    })
    await waitFor(() => {
      expect(mockedToastSuccess).toHaveBeenCalledWith('Proposed profile rejected.')
    })
  })

  test('Accept error shows error toast and keeps diff view visible', async () => {
    mockedAccept.mockRejectedValueOnce(new Error('Network error'))
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1', status: 'PROPOSED' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    fireEvent.click(screen.getByRole('button', { name: /accept/i }))
    await waitFor(() => {
      expect(mockedToastError).toHaveBeenCalledWith('Network error')
    })
    expect(screen.getByText('Review Proposed Profile')).toBeInTheDocument()
  })

  test('Reject error shows error toast and keeps diff view visible', async () => {
    mockedReject.mockRejectedValueOnce({ response: { data: { message: 'Server error' } } })
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1', status: 'PROPOSED' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    fireEvent.click(screen.getByRole('button', { name: /reject/i }))
    await waitFor(() => {
      expect(mockedToastError).toHaveBeenCalledWith('Server error')
    })
    expect(screen.getByText('Review Proposed Profile')).toBeInTheDocument()
  })

  test('shows no-changes message when profiles are identical', () => {
    const active = makeProfile({
      id: 'active-1',
      name: 'Same',
      insulinType: 'Fiasp',
      durationOfAction: 180,
      basal: [{ startTime: '00:00', value: 0.8 }],
      icr: [{ startTime: '00:00', value: 10 }],
      isf: [{ startTime: '00:00', value: 50 }],
      targets: [],
    })
    const proposed = makeProfile({
      id: 'proposed-1',
      name: 'Same',
      insulinType: 'Fiasp',
      durationOfAction: 180,
      basal: [{ startTime: '00:00', value: 0.8 }],
      icr: [{ startTime: '00:00', value: 10 }],
      isf: [{ startTime: '00:00', value: 50 }],
      targets: [],
    })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByText('No differences detected')).toBeInTheDocument()
  })

  test('renders basal segment values from both profiles', () => {
    const active = makeProfile({
      id: 'active-1',
      basal: [{ startTime: '00:00', value: 0.8 }],
    })
    const proposed = makeProfile({
      id: 'proposed-1',
      basal: [{ startTime: '00:00', value: 1.0 }],
    })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.getByText(/0.8 U\/hr/)).toBeInTheDocument()
    expect(screen.getByText(/1 U\/hr/)).toBeInTheDocument()
  })

  test('section has accessible aria-label', () => {
    const active = makeProfile({ id: 'active-1' })
    const proposed = makeProfile({ id: 'proposed-1' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(
      screen.getByRole('region', { name: 'Review proposed profile changes' }),
    ).toBeInTheDocument()
  })

  test('does not show no-changes message when only insulinType differs', () => {
    const active = makeProfile({ id: 'active-1', insulinType: 'Fiasp' })
    const proposed = makeProfile({ id: 'proposed-1', insulinType: 'NovoRapid' })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.queryByText('No differences detected')).not.toBeInTheDocument()
    expect(screen.getByText('Fiasp')).toBeInTheDocument()
    expect(screen.getByText('NovoRapid')).toBeInTheDocument()
  })

  test('does not show no-changes message when only durationOfAction differs', () => {
    const active = makeProfile({ id: 'active-1', durationOfAction: 180 })
    const proposed = makeProfile({ id: 'proposed-1', durationOfAction: 240 })
    renderWithQuery(
      <ProfileDiffView userId="user-1" activeProfile={active} proposedProfile={proposed} />,
    )
    expect(screen.queryByText('No differences detected')).not.toBeInTheDocument()
    expect(screen.getByText('180m')).toBeInTheDocument()
    expect(screen.getByText('240m')).toBeInTheDocument()
  })
})
