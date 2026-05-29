import { render } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import '../i18n'
import type { AgpHourlyData, AgpResponse, TirBreakdown } from '../api/analyzeApi'
import React from 'react'

// Stub out recharts — heavy SVG components break jsdom
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

import { AgpChartPage } from '../features/report/AgpChartPage'

// ---- helpers ----

function makeEmptyHourlyData(): AgpHourlyData[] {
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

function makePopulatedHourlyData(): AgpHourlyData[] {
  return Array.from({ length: 24 }, (_, i) => ({
    hour: i,
    p10: 80 + i,
    p25: 90 + i,
    median: 120 + i,
    p75: 150 + i,
    p90: 180 + i,
    count: 12,
  }))
}

function makeMinimalAgpResponse(): AgpResponse {
  return {
    hourlyData: makeEmptyHourlyData(),
  }
}

function makeFullAgpResponse(): AgpResponse {
  return {
    hourlyData: makePopulatedHourlyData(),
    totalReadingCount: 4032,
    sensorWearDays: 14,
  }
}

const SAMPLE_TIR: TirBreakdown = {
  veryLowCount: 5,
  belowCount: 20,
  inRangeCount: 200,
  aboveCount: 50,
  highCount: 10,
  totalCount: 285,
}

// ---- tests ----

describe('AgpChartPage', () => {
  test('renders without crashing with minimal AGP data (no optional fields)', () => {
    render(<AgpChartPage agp={makeMinimalAgpResponse()} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing with fully populated AGP data', () => {
    render(<AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />)
  })

  test('renders stats summary container', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />,
    )
    // The AgpStatsSummary renders with class agp-stats-summary
    expect(container.querySelector('.agp-stats-summary')).toBeTruthy()
  })

  test('stats summary has the correct aria-label', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />,
    )
    const statsEl = container.querySelector('[aria-label]')
    expect(statsEl).toBeTruthy()
  })

  test('renders sensor wear days when provided', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />,
    )
    expect(container.textContent).toContain('14')
  })

  test('renders total reading count when provided', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />,
    )
    expect(container.textContent).toContain('4032')
  })

  test('does not render stats dl when neither sensorWearDays nor totalReadingCount is present', () => {
    const { container } = render(
      <AgpChartPage agp={makeMinimalAgpResponse()} glucoseUnit="mg/dL" />,
    )
    expect(container.querySelector('dl')).toBeNull()
  })

  test('renders warning banner when warnings are present', () => {
    const agp: AgpResponse = {
      ...makeMinimalAgpResponse(),
      warnings: ['Not enough data for reliable AGP'],
    }
    const { getAllByRole } = render(<AgpChartPage agp={agp} glucoseUnit="mg/dL" />)
    // Both AgpStatsSummary and the forwarded AgpChart emit role="alert" for the same warnings.
    // We confirm at least one alert contains the expected text.
    const alerts = getAllByRole('alert')
    expect(alerts.length).toBeGreaterThanOrEqual(1)
    const combinedText = alerts.map((a) => a.textContent ?? '').join(' ')
    expect(combinedText).toContain('Not enough data for reliable AGP')
  })

  test('does not render warning banner when warnings array is empty', () => {
    const agp: AgpResponse = {
      ...makeMinimalAgpResponse(),
      warnings: [],
    }
    const { queryByRole } = render(<AgpChartPage agp={agp} glucoseUnit="mg/dL" />)
    expect(queryByRole('alert')).toBeNull()
  })

  test('does not render TIR section when tir prop is absent', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />,
    )
    expect(container.querySelector('.agp-tir-section')).toBeNull()
  })

  test('renders TIR section when tir prop is provided', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" tir={SAMPLE_TIR} />,
    )
    expect(container.querySelector('.agp-tir-section')).toBeTruthy()
  })

  test('renders in mmol/L mode without crashing', () => {
    render(<AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mmol/L" tir={SAMPLE_TIR} />)
  })

  test('renders with multiple warnings', () => {
    const agp: AgpResponse = {
      ...makeMinimalAgpResponse(),
      warnings: ['First warning', 'Second warning'],
    }
    const { getAllByRole } = render(<AgpChartPage agp={agp} glucoseUnit="mg/dL" />)
    const alerts = getAllByRole('alert')
    expect(alerts.length).toBeGreaterThanOrEqual(1)
    const combinedText = alerts.map((a) => a.textContent ?? '').join(' ')
    expect(combinedText).toContain('First warning')
    expect(combinedText).toContain('Second warning')
  })

  test('renders AgpChart inside the page', () => {
    const { container } = render(
      <AgpChartPage agp={makeFullAgpResponse()} glucoseUnit="mg/dL" />,
    )
    // The mocked recharts ResponsiveContainer should appear
    expect(container.querySelector('[data-testid="responsive-container"]')).toBeTruthy()
  })
})
