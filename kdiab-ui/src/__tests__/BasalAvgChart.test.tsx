import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import '../i18n'
import React from 'react'

vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => <div data-testid="composed-chart">{children}</div>,
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

import { BasalAvgChart } from '../features/analytics/BasalAvgChart'

const emptyAvg: (number | null)[] = Array(24).fill(null)
const populatedAvg: (number | null)[] = Array.from({ length: 24 }, (_, i) => 0.5 + i * 0.02)

describe('BasalAvgChart', () => {
  test('renders without crashing when all buckets are null', () => {
    render(<BasalAvgChart hourlyAvg={emptyAvg} />)
  })

  test('shows no-data message when all buckets are null', () => {
    render(<BasalAvgChart hourlyAvg={emptyAvg} />)
    expect(screen.queryByRole('img')).toBeNull()
  })

  test('renders chart figure with role=img when data is present', () => {
    render(<BasalAvgChart hourlyAvg={populatedAvg} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('chart figure has a non-empty aria-label', () => {
    render(<BasalAvgChart hourlyAvg={populatedAvg} />)
    const figure = screen.getByRole('img')
    expect(figure.getAttribute('aria-label')).toBeTruthy()
  })

  test('renders when only some buckets have values (partial data)', () => {
    const partial = Array(24).fill(null)
    partial[6] = 0.8
    partial[12] = 1.0
    render(<BasalAvgChart hourlyAvg={partial} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('renders without crashing with a single populated bucket', () => {
    const single = Array(24).fill(null)
    single[0] = 1.2
    render(<BasalAvgChart hourlyAvg={single} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })
})
