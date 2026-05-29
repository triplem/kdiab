import { render, screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import React from 'react'
import '../i18n'

// Mock Recharts — avoid canvas/SVG rendering in jsdom
vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="composed-chart">{children}</div>
  ),
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  LabelList: () => null,
}))

import { BasalRatePage } from '../features/report/BasalRatePage'
import { buildBasalChartData } from '../features/report/basalRateUtils'
import type { ProfileSegment } from '../api/analyzeApi'

// ---- Test data ----

const TWO_SEGMENTS: ProfileSegment[] = [
  { startTime: '00:00:00', value: 0.75 },
  { startTime: '08:00:00', value: 1.0 },
]

const THREE_SEGMENTS: ProfileSegment[] = [
  { startTime: '00:00:00', value: 0.6 },
  { startTime: '06:00:00', value: 0.8 },
  { startTime: '22:00:00', value: 0.5 },
]

// ---- Unit tests for buildBasalChartData ----

describe('buildBasalChartData', () => {
  test('returns empty results for empty segments', () => {
    const { points, rows, totalDailyIE } = buildBasalChartData([])
    expect(points).toHaveLength(0)
    expect(rows).toHaveLength(0)
    expect(totalDailyIE).toBe(0)
  })

  test('calculates correct duration and IE for two segments', () => {
    const { rows, totalDailyIE } = buildBasalChartData(TWO_SEGMENTS)
    expect(rows).toHaveLength(2)

    // First segment: 00:00–08:00 = 480 min, rate 0.75 IE/h → 6 IE
    expect(rows[0]?.startTime).toBe('00:00')
    expect(rows[0]?.durationMinutes).toBe(480)
    expect(rows[0]?.ie).toBeCloseTo(6.0, 5)

    // Second segment: 08:00–24:00 = 960 min, rate 1.0 IE/h → 16 IE
    expect(rows[1]?.startTime).toBe('08:00')
    expect(rows[1]?.durationMinutes).toBe(960)
    expect(rows[1]?.ie).toBeCloseTo(16.0, 5)

    expect(totalDailyIE).toBeCloseTo(22.0, 5)
  })

  test('builds correct number of chart points (segments + 1 closing point)', () => {
    const { points } = buildBasalChartData(TWO_SEGMENTS)
    // 2 segments + 1 closing point at minute 1440
    expect(points).toHaveLength(3)
    expect(points[0]?.minute).toBe(0)
    expect(points[1]?.minute).toBe(480)
    expect(points[2]?.minute).toBe(1440)
  })

  test('closing point has same rate as last segment', () => {
    const { points } = buildBasalChartData(TWO_SEGMENTS)
    expect(points[2]?.rate).toBe(1.0)
  })

  test('handles three segments and sorts by startTime', () => {
    // Supply out-of-order to verify sorting
    const outOfOrder: ProfileSegment[] = [
      { startTime: '22:00:00', value: 0.5 },
      { startTime: '00:00:00', value: 0.6 },
      { startTime: '06:00:00', value: 0.8 },
    ]
    const { rows } = buildBasalChartData(outOfOrder)
    expect(rows[0]?.startTime).toBe('00:00')
    expect(rows[1]?.startTime).toBe('06:00')
    expect(rows[2]?.startTime).toBe('22:00')
  })

  test('totalDailyIE matches sum of segment IEs', () => {
    const { rows, totalDailyIE } = buildBasalChartData(THREE_SEGMENTS)
    const manualSum = rows.reduce((s, r) => s + r.ie, 0)
    expect(totalDailyIE).toBeCloseTo(manualSum, 10)
  })

  test('all 24 hours are accounted for (durations sum to 1440 min)', () => {
    const { rows } = buildBasalChartData(THREE_SEGMENTS)
    const totalMin = rows.reduce((s, r) => s + r.durationMinutes, 0)
    expect(totalMin).toBe(1440)
  })
})

// ---- Rendering tests for BasalRatePage ----

describe('BasalRatePage', () => {
  test('shows no-data message when segments array is empty', () => {
    render(<BasalRatePage segments={[]} />)
    // The component renders a p element for the no-data state
    expect(document.querySelector('p')).not.toBeNull()
  })

  test('renders the recharts chart when segments are provided', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    expect(screen.getByTestId('responsive-container')).toBeTruthy()
    expect(screen.getByTestId('composed-chart')).toBeTruthy()
  })

  test('renders the segments table with correct number of rows', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    // tbody rows — one per segment
    const rows = document.querySelectorAll('tbody tr')
    expect(rows).toHaveLength(2)
  })

  test('renders the footer total row', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    const footerRows = document.querySelectorAll('tfoot tr')
    expect(footerRows).toHaveLength(1)
  })

  test('renders start times in HH:MM format in the table', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    expect(screen.getByText('00:00')).toBeTruthy()
    expect(screen.getByText('08:00')).toBeTruthy()
  })

  test('renders a dl element with the summary stat', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    const dl = document.querySelector('dl.basal-rate-summary')
    expect(dl).not.toBeNull()
  })

  test('renders total IE correctly (22.000 IE for TWO_SEGMENTS)', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    // "22.000 IE" should appear at least once (in the tfoot)
    const ieText = screen.getAllByText(/22\.000 IE/)
    expect(ieText.length).toBeGreaterThanOrEqual(1)
  })

  test('renders figure with accessible aria-label', () => {
    render(<BasalRatePage segments={TWO_SEGMENTS} />)
    const figure = document.querySelector('figure[role="img"]')
    expect(figure).not.toBeNull()
    expect(figure?.getAttribute('aria-label')).toBeTruthy()
  })
})
