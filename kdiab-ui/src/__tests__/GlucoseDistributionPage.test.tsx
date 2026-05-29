import { render } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import '../i18n'
import React from 'react'
import type { GlucoseBucket, ZonePercents } from '../api/analyzeApi'

vi.mock('recharts', () => ({
  BarChart: ({ children }: { children: React.ReactNode }) => <div data-testid="bar-chart">{children}</div>,
  Bar: () => null,
  Cell: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
}))

import { GlucoseDistributionPage } from '../features/report/GlucoseDistributionPage'

const ZONE_PERCENTS: ZonePercents = {
  veryLow: 1.5,
  low: 3.2,
  inRange: 72.1,
  high: 18.4,
  veryHigh: 4.8,
}

function makeBuckets(count = 5): GlucoseBucket[] {
  return Array.from({ length: count }, (_, i) => ({
    lowerBound: i * 5,
    upperBound: i * 5 + 5,
    count: 10 + i,
    percent: 2 + i * 0.5,
    zone: ['veryLow', 'low', 'inRange', 'high', 'veryHigh'][i % 5],
  }))
}

describe('GlucoseDistributionPage', () => {
  test('renders without crashing with normal data', () => {
    render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
      />,
    )
  })

  test('renders BarChart when there is data', () => {
    const { getByTestId } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
      />,
    )
    expect(getByTestId('bar-chart')).toBeDefined()
  })

  test('renders no chart when all buckets have zero count', () => {
    const emptyBuckets: GlucoseBucket[] = [
      { lowerBound: 0, upperBound: 5, count: 0, percent: 0, zone: 'veryLow' },
      { lowerBound: 5, upperBound: 10, count: 0, percent: 0, zone: 'low' },
    ]
    const { queryByTestId } = render(
      <GlucoseDistributionPage
        buckets={emptyBuckets}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={0}
      />,
    )
    expect(queryByTestId('bar-chart')).toBeNull()
  })

  test('renders no chart when buckets array is empty', () => {
    const { queryByTestId } = render(
      <GlucoseDistributionPage
        buckets={[]}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={0}
      />,
    )
    expect(queryByTestId('bar-chart')).toBeNull()
  })

  test('renders chart figure with role=img', () => {
    const { getByRole } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
      />,
    )
    const figure = getByRole('img')
    expect(figure).toBeDefined()
  })

  test('renders in mmol/L mode without crashing', () => {
    const { getByTestId } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mmol/L"
        totalCount={2016}
      />,
    )
    expect(getByTestId('bar-chart')).toBeDefined()
  })

  test('renders zone summary section', () => {
    const { container } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
      />,
    )
    // Zone summary should display percentage values
    expect(container.textContent).toContain('72.1%')
    expect(container.textContent).toContain('1.5%')
  })

  test('renders zone summary with aria-label', () => {
    const { getByLabelText } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
      />,
    )
    // The ZoneSummary div has an aria-label
    const summary = getByLabelText(/zone percentage summary/i)
    expect(summary).toBeDefined()
  })

  test('renders warning banner when warnings are provided', () => {
    const { getByRole } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
        warnings={['Insufficient data — results may be unreliable']}
      />,
    )
    getByRole('alert')
  })

  test('does not render warning banner when warnings is empty', () => {
    const { queryByRole } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
        warnings={[]}
      />,
    )
    expect(queryByRole('alert')).toBeNull()
  })

  test('does not render warning banner when warnings prop is absent', () => {
    const { queryByRole } = render(
      <GlucoseDistributionPage
        buckets={makeBuckets()}
        zonePercents={ZONE_PERCENTS}
        unit="mg/dL"
        totalCount={2016}
      />,
    )
    expect(queryByRole('alert')).toBeNull()
  })
})
