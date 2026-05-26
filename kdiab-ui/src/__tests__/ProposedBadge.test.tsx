import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ProposedBadge } from '../features/profiles/ProposedBadge'
import { useProposedProfileCount } from '../features/profiles/useProposedProfileCount'

vi.mock('../api/profilesApi', () => ({
  profilesApi: {
    listProfiles: vi.fn(),
  },
}))

import { profilesApi } from '../api/profilesApi'
import type { Profile } from '../api/profilesApi'

const mockedListProfiles = vi.mocked(profilesApi.listProfiles)

function makeProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 'p-1',
    userId: 'user-1',
    name: 'Test Profile',
    status: 'ACTIVE',
    ...overrides,
  }
}

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('ProposedBadge', () => {
  test('renders count when count is 1', () => {
    render(<ProposedBadge count={1} />)
    const badge = screen.getByText('1')
    expect(badge).toBeInTheDocument()
  })

  test('renders correct count when multiple proposed', () => {
    render(<ProposedBadge count={3} />)
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  test('renders nothing when count is 0', () => {
    const { container } = render(<ProposedBadge count={0} />)
    expect(container.firstChild).toBeNull()
  })

  test('has correct aria-label for count 1', () => {
    render(<ProposedBadge count={1} />)
    expect(screen.getByLabelText('1 proposed profiles awaiting decision')).toBeInTheDocument()
  })

  test('has correct aria-label for count 2', () => {
    render(<ProposedBadge count={2} />)
    expect(screen.getByLabelText('2 proposed profiles awaiting decision')).toBeInTheDocument()
  })
})

describe('useProposedProfileCount', () => {
  function CountConsumer({ userId }: { userId: string }) {
    const count = useProposedProfileCount(userId)
    return <span data-testid="count">{count}</span>
  }

  test('returns 0 when there are no proposed profiles', async () => {
    mockedListProfiles.mockResolvedValueOnce({
      data: { items: [makeProfile({ status: 'ACTIVE' })], page: 0, size: 20, totalCount: 1 },
    } as never)

    render(<CountConsumer userId="user-1" />, { wrapper })

    await screen.findByText('0')
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  test('returns count of proposed profiles', async () => {
    mockedListProfiles.mockResolvedValueOnce({
      data: {
        items: [
          makeProfile({ id: 'p-1', status: 'PROPOSED' }),
          makeProfile({ id: 'p-2', status: 'PROPOSED' }),
          makeProfile({ id: 'p-3', status: 'ACTIVE' }),
        ],
        page: 0,
        size: 20,
        totalCount: 3,
      },
    } as never)

    render(<CountConsumer userId="user-1" />, { wrapper })

    await screen.findByText('2')
    expect(screen.getByTestId('count').textContent).toBe('2')
  })

  test('returns 0 on API error (degrades silently)', async () => {
    mockedListProfiles.mockRejectedValueOnce(new Error('Network error'))

    render(<CountConsumer userId="user-1" />, { wrapper })

    await new Promise((r) => setTimeout(r, 50))
    expect(screen.getByTestId('count').textContent).toBe('0')
  })

  test('returns 0 when userId is empty (query disabled)', () => {
    render(<CountConsumer userId="" />, { wrapper })
    expect(screen.getByTestId('count').textContent).toBe('0')
  })
})
