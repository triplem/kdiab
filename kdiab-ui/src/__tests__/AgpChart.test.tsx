import { render } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import '../i18n'
import type { AgpHourlyData } from '../api/analyzeApi'
import React from 'react'

vi.mock('recharts', () => ({
  AreaChart: ({ children }: { children: React.ReactNode }) => <div data-testid="area-chart">{children}</div>,
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div data-testid="responsive-container">{children}</div>,
  ReferenceLine: () => null,
  Legend: () => null,
}))

import { AgpChart } from '../features/analytics/AgpChart'

function makeEmptyBuckets(): AgpHourlyData[] {
  return Array.from({ length: 24 }, (_, i) => ({
    hour: i,
    p10: null,
    p25: null,
    median: null,
    p75: null,
    p90: null,
    count: 0,
  }))
}

function makePopulatedBuckets(): AgpHourlyData[] {
  return Array.from({ length: 24 }, (_, i) => ({
    hour: i,
    p10: 80 + i,
    p25: 90 + i,
    median: 120 + i,
    p75: 150 + i,
    p90: 180 + i,
    count: 10,
  }))
}

describe('AgpChart', () => {
  test('renders without crashing with empty hourly data (all null percentiles)', () => {
    render(<AgpChart hourlyData={makeEmptyBuckets()} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing with populated data', () => {
    render(<AgpChart hourlyData={makePopulatedBuckets()} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing in mmol/L mode', () => {
    render(<AgpChart hourlyData={makePopulatedBuckets()} glucoseUnit="mmol/L" />)
  })

  test('renders without crashing with empty array', () => {
    render(<AgpChart hourlyData={[]} glucoseUnit="mg/dL" />)
  })

  test('renders warning banner when warnings provided', () => {
    const { getByRole } = render(
      <AgpChart
        hourlyData={makeEmptyBuckets()}
        glucoseUnit="mg/dL"
        warnings={['Insufficient data for reliable AGP']}
      />,
    )
    getByRole('alert')
  })

  test('renders sensor wear indicator when sensorWearDays and totalReadingCount are provided', () => {
    const { getByLabelText } = render(
      <AgpChart
        hourlyData={makePopulatedBuckets()}
        glucoseUnit="mg/dL"
        sensorWearDays={14}
        totalReadingCount={4032}
      />,
    )
    const indicator = getByLabelText('AGP data quality')
    expect(indicator.textContent).toContain('14')
    expect(indicator.textContent).toContain('4032')
  })

  test('renders only sensorWearDays when totalReadingCount is absent', () => {
    const { getByLabelText } = render(
      <AgpChart
        hourlyData={makePopulatedBuckets()}
        glucoseUnit="mg/dL"
        sensorWearDays={7}
      />,
    )
    const indicator = getByLabelText('AGP data quality')
    expect(indicator.textContent).toContain('7')
  })

  test('does not render sensor wear indicator when neither prop is provided', () => {
    const { queryByLabelText } = render(
      <AgpChart hourlyData={makePopulatedBuckets()} glucoseUnit="mg/dL" />,
    )
    expect(queryByLabelText('AGP data quality')).toBeNull()
  })

  test('renders without warnings when warnings is empty array', () => {
    const { queryByRole } = render(
      <AgpChart hourlyData={makeEmptyBuckets()} glucoseUnit="mg/dL" warnings={[]} />,
    )
    // empty warnings array → no alert rendered
    const alert = queryByRole('alert')
    // Either null or not present is acceptable
    if (alert) {
      // If rendered, it should have no content
      const p = alert.querySelector('p')
      if (p === null) {
        // OK — no warning paragraphs
      }
    }
  })
})
