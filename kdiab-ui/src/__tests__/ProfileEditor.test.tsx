import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import { ProfileEditor } from '../features/profiles/ProfileEditor'
import '../i18n'

vi.mock('../api/profilesApi', () => ({
  profilesApi: {
    listProfiles: vi.fn().mockResolvedValue({ data: { items: [] } }),
    createProfile: vi.fn().mockResolvedValue({ data: {} }),
    updateProfile: vi.fn().mockResolvedValue({ data: {} }),
    getInsulins: vi.fn().mockResolvedValue({ data: [] }),
    createInsulin: vi.fn().mockResolvedValue({ data: { id: '1', name: 'NewInsulin' } }),
  },
}))

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <TimeFormatProvider>{children}</TimeFormatProvider>
    </QueryClientProvider>
  )
}

describe('ProfileEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders Create Profile heading when no initialProfile', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    // Both the heading h3 and the submit button say "Create Profile"
    const elements = screen.getAllByText('Create Profile')
    expect(elements.length).toBeGreaterThanOrEqual(1)
  })

  test('renders Name input field', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    expect(screen.getByLabelText('Name')).toBeDefined()
  })

  test('renders Duration of Action input', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    expect(screen.getByLabelText(/duration of action/i)).toBeDefined()
  })

  test('renders BASAL tab button by default', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    expect(screen.getByText('BASAL')).toBeDefined()
  })

  test('renders ICR, ISF, TARGETS tab buttons', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    expect(screen.getByText('ICR')).toBeDefined()
    expect(screen.getByText('ISF')).toBeDefined()
    expect(screen.getByText('TARGETS')).toBeDefined()
  })

  test('basal tab is active by default and shows Add Segment button', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    expect(screen.getByText('Add Segment')).toBeDefined()
  })

  test('clicking ICR tab shows Add ICR Segment button', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    fireEvent.click(screen.getByText('ICR'))
    expect(screen.getByText('Add ICR Segment')).toBeDefined()
  })

  test('clicking ISF tab shows Add ISF Segment button', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    fireEvent.click(screen.getByText('ISF'))
    expect(screen.getByText('Add ISF Segment')).toBeDefined()
  })

  test('clicking TARGETS tab shows Add Target Segment button', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    fireEvent.click(screen.getByText('TARGETS'))
    expect(screen.getByText('Add Target Segment')).toBeDefined()
  })

  test('renders default basal segment on initial render', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    // Default basal has one segment starting at 00:00
    const removeButtons = screen.getAllByText('Remove')
    expect(removeButtons.length).toBeGreaterThanOrEqual(1)
  })

  test('Add Segment button adds a new basal segment', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    const initialRemoveCount = screen.getAllByText('Remove').length
    fireEvent.click(screen.getByText('Add Segment'))
    const newRemoveCount = screen.getAllByText('Remove').length
    expect(newRemoveCount).toBe(initialRemoveCount + 1)
  })

  test('Remove button removes a basal segment', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    // Add a segment first
    fireEvent.click(screen.getByText('Add Segment'))
    const removeButtons = screen.getAllByText('Remove')
    const countBefore = removeButtons.length
    fireEvent.click(removeButtons[0])
    const countAfter = screen.getAllByText('Remove').length
    expect(countAfter).toBe(countBefore - 1)
  })

  test('ICR tab Add Segment adds ICR segment', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    fireEvent.click(screen.getByText('ICR'))
    const addBtn = screen.getByText('Add ICR Segment')
    fireEvent.click(addBtn)
    expect(screen.getAllByText('Remove').length).toBeGreaterThanOrEqual(1)
  })

  test('submitting empty name shows validation error', async () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    const nameInput = screen.getByLabelText('Name')
    fireEvent.change(nameInput, { target: { value: '' } })
    // Click submit button (button type="submit")
    const submitBtns = screen.getAllByText('Create Profile')
    const submitBtn = submitBtns.find(
      (el) => el.tagName === 'BUTTON' && (el as HTMLButtonElement).type === 'submit',
    )!
    fireEvent.click(submitBtn)
    await waitFor(() => {
      // Validation error for name should appear
      expect(screen.queryByRole('alert')).not.toBeNull()
    })
  })

  test('renders Edit Profile heading when initialProfile is provided', () => {
    const profile = {
      id: 'p1',
      userId: 'user1',
      name: 'My Profile',
      status: 'ACTIVE',
      insulinType: 'Humalog',
      durationOfAction: 300,
      basal: [{ startTime: '00:00', value: 0.8 }],
      icr: [],
      isf: [],
      targets: [],
    }
    render(<ProfileEditor userId="user1" initialProfile={profile} />, { wrapper })
    expect(screen.getByText('Edit Profile')).toBeDefined()
  })

  test('renders View Profile heading in readOnly mode', () => {
    const profile = {
      id: 'p1',
      userId: 'user1',
      name: 'My Profile',
      status: 'ACTIVE',
      basal: [{ startTime: '00:00', value: 0.8 }],
      icr: [],
      isf: [],
      targets: [],
    }
    render(<ProfileEditor userId="user1" initialProfile={profile} readOnly />, { wrapper })
    expect(screen.getByText('View Profile')).toBeDefined()
  })

  test('readOnly mode shows read-only notice', () => {
    const profile = {
      id: 'p1',
      userId: 'user1',
      name: 'My Profile',
      status: 'ACTIVE',
      basal: [{ startTime: '00:00', value: 0.8 }],
      icr: [],
      isf: [],
      targets: [],
    }
    render(<ProfileEditor userId="user1" initialProfile={profile} readOnly />, { wrapper })
    expect(screen.getByText(/read-only view/i)).toBeDefined()
  })

  test('readOnly mode does not show submit button', () => {
    const profile = {
      id: 'p1',
      userId: 'user1',
      name: 'My Profile',
      status: 'ACTIVE',
      basal: [{ startTime: '00:00', value: 0.8 }],
      icr: [],
      isf: [],
      targets: [],
    }
    render(<ProfileEditor userId="user1" initialProfile={profile} readOnly />, { wrapper })
    expect(screen.queryByText(/update profile/i)).toBeNull()
    expect(screen.queryByText(/create profile/i)).toBeNull()
  })

  test('shows proposal reason field for doctors', () => {
    render(<ProfileEditor userId="user1" isDoctor />, { wrapper })
    // Doctor sees proposalReason textarea (label translated as "Clinical rationale")
    const textarea = screen.getByRole('textbox', { name: /clinical rationale/i })
    expect(textarea).toBeDefined()
  })

  test('+ Add New button shows new insulin text input', () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    const addNewBtn = screen.getByText('+ Add New')
    fireEvent.click(addNewBtn)
    // Should show a text input for new insulin name
    const textInput = screen.getByPlaceholderText(/enter new insulin name/i)
    expect(textInput).toBeDefined()
  })

  test('Cancel in add new insulin mode switches back to select', async () => {
    render(<ProfileEditor userId="user1" />, { wrapper })
    fireEvent.click(screen.getByText('+ Add New'))
    fireEvent.click(screen.getByText('Cancel'))
    await waitFor(() => {
      expect(screen.getByText('+ Add New')).toBeDefined()
    })
  })
})
