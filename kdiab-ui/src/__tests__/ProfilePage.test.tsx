import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import React from 'react'
import '../i18n'

// Mock Recharts to avoid canvas/SVG rendering in jsdom
vi.mock('recharts', () => ({
  BarChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="bar-chart">{children}</div>
  ),
  Bar: () => null,
  Cell: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
}))

import { ProfilePage } from '../features/report/ProfilePage'
import type { ProfileSummary } from '../api/analyzeApi'

// ---- Test data ----

function makeProfile(overrides: Partial<ProfileSummary> = {}): ProfileSummary {
  return {
    id: 'profile-1',
    name: 'Test Profile',
    status: 'ACTIVE',
    insulinType: 'Fiasp',
    durationOfAction: 180,
    validFrom: '2024-01-01T00:00:00Z',
    basal: [
      { startTime: '00:00:00', value: 0.75 },
      { startTime: '08:00:00', value: 1.0 },
    ],
    icr: [
      { startTime: '00:00:00', value: 10.0 },
    ],
    isf: [
      { startTime: '00:00:00', value: 50.0 },
    ],
    targets: [
      { startTime: '00:00:00', low: 80, high: 130 },
    ],
    ...overrides,
  }
}

// ---- Tests ----

describe('ProfilePage', () => {
  test('renders "no active profile" message when profiles list is empty', () => {
    render(<ProfilePage profiles={[]} glucoseUnit="mg/dL" />)
    expect(screen.getByText(/No active insulin profile found/i)).toBeInTheDocument()
  })

  test('renders "no active profile" message when only ARCHIVED profiles are present', () => {
    const archived = makeProfile({ status: 'ARCHIVED' })
    render(<ProfilePage profiles={[archived]} glucoseUnit="mg/dL" />)
    expect(screen.getByText(/No active insulin profile found/i)).toBeInTheDocument()
  })

  test('renders the profile name when an ACTIVE profile is present', () => {
    const active = makeProfile({ name: 'My Active Profile' })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('My Active Profile')).toBeInTheDocument()
  })

  test('renders the insulin type from the profile header', () => {
    const active = makeProfile({ insulinType: 'Humalog' })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('Humalog')).toBeInTheDocument()
  })

  test('renders the duration of action', () => {
    const active = makeProfile({ durationOfAction: 240 })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('240 min')).toBeInTheDocument()
  })

  test('renders the validFrom date (first 10 chars of ISO string)', () => {
    const active = makeProfile({ validFrom: '2024-03-15T00:00:00Z' })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('2024-03-15')).toBeInTheDocument()
  })

  test('renders the basal rate table with segment start times', () => {
    const active = makeProfile({
      basal: [
        { startTime: '06:00:00', value: 0.75 },
        { startTime: '18:00:00', value: 1.25 },
      ],
      icr: null,
      isf: null,
      targets: null,
    })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('06:00')).toBeInTheDocument()
    expect(screen.getByText('18:00')).toBeInTheDocument()
  })

  test('renders the basal rate bar chart', () => {
    const active = makeProfile()
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByTestId('responsive-container')).toBeInTheDocument()
    expect(screen.getByTestId('bar-chart')).toBeInTheDocument()
  })

  test('renders ICR section with segment values', () => {
    const active = makeProfile({
      icr: [{ startTime: '06:00:00', value: 12.0 }],
    })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    // Section header key — i18n resolves to "Insulin-to-Carb Ratio (ICR)"
    expect(screen.getByText(/ICR/i)).toBeInTheDocument()
    expect(screen.getByText('06:00')).toBeInTheDocument()
  })

  test('renders ISF section with mg/dL unit label when glucoseUnit is mg/dL', () => {
    const active = makeProfile()
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText(/mg\/dL per IE/i)).toBeInTheDocument()
  })

  test('renders ISF section with mmol/L unit label when glucoseUnit is mmol/L', () => {
    const active = makeProfile()
    render(<ProfilePage profiles={[active]} glucoseUnit="mmol/L" />)
    expect(screen.getByText(/mmol\/L per IE/i)).toBeInTheDocument()
  })

  test('renders glucose targets table', () => {
    const active = makeProfile({
      targets: [{ startTime: '00:00:00', low: 80, high: 130 }],
    })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    // Low and high headers
    expect(screen.getByText(/Low/i)).toBeInTheDocument()
    expect(screen.getByText(/High/i)).toBeInTheDocument()
  })

  test('renders targets with mmol/L conversion when glucoseUnit is mmol/L', () => {
    const active = makeProfile({
      basal: null,
      icr: null,
      isf: null,
      targets: [{ startTime: '00:00:00', low: 72, high: 198 }],
    })
    render(<ProfilePage profiles={[active]} glucoseUnit="mmol/L" />)
    // 72 mg/dL → 4.0 mmol/L, 198 mg/dL → 11.0 mmol/L
    expect(screen.getByText('4.0')).toBeInTheDocument()
    expect(screen.getByText('11.0')).toBeInTheDocument()
  })

  test('does not render basal section when basal is null', () => {
    const active = makeProfile({ basal: null })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.queryByTestId('bar-chart')).not.toBeInTheDocument()
  })

  test('does not render basal section when basal is empty array', () => {
    const active = makeProfile({ basal: [] })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.queryByTestId('bar-chart')).not.toBeInTheDocument()
  })

  test('does not render ICR section when icr is null', () => {
    const active = makeProfile({ icr: null })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    // The ICR label should not appear
    expect(screen.queryByText('Insulin-to-Carb Ratio (ICR)')).not.toBeInTheDocument()
  })

  test('selects ACTIVE profile when both ACTIVE and ARCHIVED profiles are in the list', () => {
    const archived = makeProfile({ status: 'ARCHIVED', name: 'Old Profile' })
    const active = makeProfile({ status: 'ACTIVE', name: 'Current Profile' })
    render(<ProfilePage profiles={[archived, active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('Current Profile')).toBeInTheDocument()
    expect(screen.queryByText('Old Profile')).not.toBeInTheDocument()
  })

  test('renders ACTIVE status badge', () => {
    const active = makeProfile({ status: 'ACTIVE' })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  test('renders validFrom from createdAt when validFrom is null', () => {
    const active = makeProfile({ validFrom: null, createdAt: '2024-05-10T08:00:00Z' })
    render(<ProfilePage profiles={[active]} glucoseUnit="mg/dL" />)
    expect(screen.getByText('2024-05-10')).toBeInTheDocument()
  })
})
