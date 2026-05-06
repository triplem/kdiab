import { render, screen, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReactNode } from 'react'
import { ProfileList } from '../features/profiles/ProfileList'
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

import { profilesApi } from '../api/profilesApi'
import type { Profile } from '../api/profilesApi'

const mockedListProfiles = vi.mocked(profilesApi.listProfiles)

function makeProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 'profile-1',
    userId: 'user-1',
    name: 'Test Profile',
    status: 'ACTIVE',
    insulinType: 'Fiasp',
    durationOfAction: 180,
    basal: [],
    icr: [],
    isf: [],
    targets: [],
    ...overrides,
  }
}

function renderWithQuery(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>{ui}</TimeFormatProvider>
    </QueryClientProvider>
  )
}

describe('ProfileList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('shows loading state while fetching', () => {
    // never resolves — keeps component in loading state
    mockedListProfiles.mockImplementation(() => new Promise(() => {}))
    renderWithQuery(<ProfileList userId="user-1" />)
    expect(screen.getByText('Loading profiles...')).toBeInTheDocument()
  })

  test('shows active profile section when ACTIVE profile exists', async () => {
    const activeProfile = makeProfile({ id: 'p-active', name: 'My Active Plan', status: 'ACTIVE' })
    mockedListProfiles.mockResolvedValueOnce({ data: [activeProfile] } as never)

    renderWithQuery(<ProfileList userId="user-1" />)

    await waitFor(() => {
      expect(screen.getByText('My Active Plan')).toBeInTheDocument()
    })
    // Section heading from profileList.yourConfigurations
    expect(screen.getByText('Your Configurations')).toBeInTheDocument()
  })

  test('shows proposed profiles section with accept and reject buttons when PROPOSED profile exists', async () => {
    const proposedProfile = makeProfile({
      id: 'p-proposed',
      name: 'Doctor Recommendation',
      status: 'PROPOSED',
    })
    mockedListProfiles.mockResolvedValueOnce({ data: [proposedProfile] } as never)

    renderWithQuery(<ProfileList userId="user-1" />)

    await waitFor(() => {
      expect(screen.getByText('Doctor Recommendation')).toBeInTheDocument()
    })
    // Proposed section heading
    expect(screen.getByText(/Pending Doctor Recommendations/)).toBeInTheDocument()
    // Accept and reject buttons
    expect(screen.getByText('Accept')).toBeInTheDocument()
    expect(screen.getByText('Reject')).toBeInTheDocument()
  })

  test('shows no profiles message when list is empty', async () => {
    mockedListProfiles.mockResolvedValueOnce({ data: [] } as never)

    renderWithQuery(<ProfileList userId="user-1" />)

    await waitFor(() => {
      expect(screen.getByText('No profiles found.')).toBeInTheDocument()
    })
  })
})
