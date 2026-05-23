import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import '../i18n'
import React from 'react'

vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => <div data-testid="composed-chart">{children}</div>,
  Bar: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

import { BolusAvgChart } from '../features/analytics/BolusAvgChart'

const emptyAvg: (number | null)[] = Array(24).fill(null)
const populatedAvg: (number | null)[] = Array.from({ length: 24 }, (_, i) => (i % 4 === 0 ? 2.5 : null))

describe('BolusAvgChart', () => {
  test('renders without crashing when all buckets are null', () => {
    render(<BolusAvgChart hourlyAvg={emptyAvg} />)
  })

  test('shows no-data message when all buckets are null', () => {
    render(<BolusAvgChart hourlyAvg={emptyAvg} />)
    expect(screen.queryByRole('img')).toBeNull()
  })

  test('renders chart figure with role=img when data is present', () => {
    render(<BolusAvgChart hourlyAvg={populatedAvg} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('chart figure has a non-empty aria-label', () => {
    render(<BolusAvgChart hourlyAvg={populatedAvg} />)
    const figure = screen.getByRole('img')
    expect(figure.getAttribute('aria-label')).toBeTruthy()
  })

  test('renders when only one bucket has a value', () => {
    const single = Array(24).fill(null)
    single[8] = 3.0
    render(<BolusAvgChart hourlyAvg={single} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })
})
