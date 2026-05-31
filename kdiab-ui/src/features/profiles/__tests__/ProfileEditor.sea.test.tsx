import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import '../../../i18n'

vi.mock('../../../api/profilesApi', () => ({
  profilesApi: {
    getInsulins: vi.fn().mockResolvedValue({ data: [{ id: 'ins-1', name: 'Humalog' }] }),
    listProfiles: vi.fn().mockResolvedValue({ data: { items: [] } }),
    createProfile: vi.fn(),
    updateProfile: vi.fn(),
    createInsulin: vi.fn(),
  },
}))

vi.mock('../../../context/TimeFormatContext', () => ({
  useTimeFormat: () => ({
    formatDate: (d: string) => d,
    formatTime: (t: string) => t,
  }),
}))

import { ProfileEditor } from '../ProfileEditor'
import type { Profile } from '../../../api/profilesApi'

function makeProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 'p-1',
    userId: 'user-1',
    name: 'My Profile',
    status: 'ACTIVE',
    insulinType: 'Humalog',
    durationOfAction: 300,
    basal: [{ startTime: '00:00', value: 0.5 }],
    icr: [],
    isf: [],
    targets: [],
    insulinToMealInterval: [],
    ...overrides,
  }
}

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

function renderEditor(profileOverrides: Partial<Profile> = {}) {
  return render(
    <ProfileEditor userId="user-1" initialProfile={makeProfile(profileOverrides)} />,
    { wrapper },
  )
}

describe('ProfileEditor SEA tab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders SEA tab and shows minutes input when profile has a SEA segment', async () => {
    renderEditor({
      insulinToMealInterval: [{ startTime: '00:00', minutes: 15 }],
    })

    const seaTab = await screen.findByRole('button', { name: 'SEA' })
    fireEvent.click(seaTab)

    await waitFor(() => {
      const minutesInputs = screen.getAllByRole('spinbutton').filter((el) => {
        const label = el.getAttribute('aria-label') ?? ''
        return label.startsWith('SEA minutes')
      })
      expect(minutesInputs.length).toBeGreaterThan(0)
    })
  })

  test('appends a SEA segment when clicking Add SEA Segment', async () => {
    renderEditor({ insulinToMealInterval: [] })

    const seaTab = await screen.findByRole('button', { name: 'SEA' })
    fireEvent.click(seaTab)

    await waitFor(() => {
      expect(screen.getByText('Add SEA Segment')).toBeInTheDocument()
    })

    const before = screen
      .queryAllByRole('spinbutton')
      .filter((el) => (el.getAttribute('aria-label') ?? '').startsWith('SEA minutes'))
    expect(before).toHaveLength(0)

    fireEvent.click(screen.getByText('Add SEA Segment'))

    await waitFor(() => {
      const after = screen
        .queryAllByRole('spinbutton')
        .filter((el) => (el.getAttribute('aria-label') ?? '').startsWith('SEA minutes'))
      expect(after).toHaveLength(1)
    })
  })

  test('removes a SEA segment when clicking Remove', async () => {
    renderEditor({
      insulinToMealInterval: [{ startTime: '00:00', minutes: 20 }],
    })

    const seaTab = await screen.findByRole('button', { name: 'SEA' })
    fireEvent.click(seaTab)

    await waitFor(() => {
      const minutesInputs = screen
        .queryAllByRole('spinbutton')
        .filter((el) => (el.getAttribute('aria-label') ?? '').startsWith('SEA minutes'))
      expect(minutesInputs).toHaveLength(1)
    })

    // The Remove button inside the SEA tab content
    const removeButtons = screen.getAllByRole('button', { name: 'Remove' })
    fireEvent.click(removeButtons[removeButtons.length - 1]!)

    await waitFor(() => {
      const minutesInputs = screen
        .queryAllByRole('spinbutton')
        .filter((el) => (el.getAttribute('aria-label') ?? '').startsWith('SEA minutes'))
      expect(minutesInputs).toHaveLength(0)
    })
  })

  test('minutes input enforces max of 120 via html attribute', async () => {
    renderEditor({ insulinToMealInterval: [{ startTime: '00:00', minutes: 15 }] })

    const seaTab = await screen.findByRole('button', { name: 'SEA' })
    fireEvent.click(seaTab)

    await waitFor(() => {
      const minutesInputs = screen
        .queryAllByRole('spinbutton')
        .filter((el) => (el.getAttribute('aria-label') ?? '').startsWith('SEA minutes'))
      expect(minutesInputs).toHaveLength(1)
    })

    const minutesInput = screen
      .getAllByRole('spinbutton')
      .find((el) => (el.getAttribute('aria-label') ?? '').startsWith('SEA minutes'))!

    // The input has max="120" enforced by the HTML attribute and Zod schema
    expect(minutesInput).toHaveAttribute('max', '120')
    expect(minutesInput).toHaveAttribute('min', '0')
  })
})
