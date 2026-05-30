import { render, screen } from '@testing-library/react'
import { describe, test, expect } from 'vitest'
import React from 'react'
import '../i18n'
import type { DailyTrendResponse, HourlyTrendRow } from '../api/analyzeApi'
import { DailyTrendTable } from '../features/report/DailyTrendPage'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeHourlyRow(hour: number, overrides: Partial<HourlyTrendRow> = {}): HourlyTrendRow {
  return {
    hour,
    meanGlucose: 120,
    trendPercent: 0,
    trendZone: 'stable',
    zone: 'inRange',
    basalRateIePerH: 0.8,
    carbsG: 0,
    ...overrides,
  }
}

function makeDay(date: string, hourOverrides: Partial<HourlyTrendRow>[] = []): { date: string; hours: HourlyTrendRow[] } {
  const hours = Array.from({ length: 24 }, (_, i) =>
    makeHourlyRow(i, hourOverrides[i] ?? {}),
  )
  return { date, hours }
}

function makeResponse(overrides: Partial<DailyTrendResponse> = {}): DailyTrendResponse {
  return {
    days: [makeDay('2026-05-01'), makeDay('2026-05-02')],
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('DailyTrendTable', () => {
  test('renders without crashing with minimal data', () => {
    render(<DailyTrendTable data={makeResponse()} glucoseUnit="mg/dL" />)
  })

  test('renders a grid table with aria-label', () => {
    render(<DailyTrendTable data={makeResponse()} glucoseUnit="mg/dL" />)
    const table = screen.getByRole('grid')
    expect(table.getAttribute('aria-label')).toBeTruthy()
  })

  test('renders a column header per day', () => {
    const data = makeResponse()
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    // There are 2 days; column headers appear as MM-DD slices in the thead
    // Plus one row-header column for "Hour"
    const columnHeaders = screen.getAllByRole('columnheader')
    // Hour header + 2 day columns = 3
    expect(columnHeaders).toHaveLength(3)
  })

  test('renders 24 hour row headers (00:00 – 23:00)', () => {
    render(<DailyTrendTable data={makeResponse()} glucoseUnit="mg/dL" />)
    const rowHeaders = screen.getAllByRole('rowheader')
    expect(rowHeaders).toHaveLength(24)
    expect(rowHeaders[0].textContent).toBe('00:00')
    expect(rowHeaders[23].textContent).toBe('23:00')
  })

  test('displays glucose value in mg/dL', () => {
    const data = makeResponse()
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    // meanGlucose=120, displayed as "120" in mg/dL (rounded)
    const cells = screen.getAllByText('120')
    expect(cells.length).toBeGreaterThan(0)
  })

  test('converts glucose to mmol/L correctly', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ meanGlucose: 180, zone: 'hyper', trendZone: null, trendPercent: null, basalRateIePerH: null, carbsG: 0, hour: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mmol/L" />)
    // 180 mg/dL → 10.0 mmol/L
    expect(screen.getAllByText('10.0').length).toBeGreaterThan(0)
  })

  test('renders trend arrow for rising trend', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ trendZone: 'rising', hour: 0, meanGlucose: 130, zone: 'inRange', trendPercent: 10, basalRateIePerH: null, carbsG: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    expect(screen.getAllByText(/↑/).length).toBeGreaterThan(0)
  })

  test('renders rising fast arrow ↑↑', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ trendZone: 'risingFast', hour: 0, meanGlucose: 200, zone: 'hyper', trendPercent: 25, basalRateIePerH: null, carbsG: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    expect(screen.getAllByText(/↑↑/).length).toBeGreaterThan(0)
  })

  test('renders falling arrow ↓', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ trendZone: 'falling', hour: 0, meanGlucose: 90, zone: 'inRange', trendPercent: -10, basalRateIePerH: null, carbsG: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    expect(screen.getAllByText(/↓/).length).toBeGreaterThan(0)
  })

  test('renders carbs indicator dot when carbsG > 0', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ carbsG: 45, hour: 0, meanGlucose: 130, zone: 'inRange', trendZone: 'stable', trendPercent: 0, basalRateIePerH: null }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    const carbsDots = screen.getAllByLabelText('carbs')
    expect(carbsDots.length).toBeGreaterThan(0)
  })

  test('does not render carbs dot when carbsG is 0', () => {
    render(<DailyTrendTable data={makeResponse()} glucoseUnit="mg/dL" />)
    expect(screen.queryAllByLabelText('carbs')).toHaveLength(0)
  })

  test('renders em-dash for noData cells', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ zone: 'noData', meanGlucose: null, trendZone: null, trendPercent: null, carbsG: 0, basalRateIePerH: null, hour: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    const noCells = screen.getAllByLabelText('no data')
    expect(noCells.length).toBeGreaterThan(0)
  })

  test('renders em-dash for null zone cells', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ zone: null, meanGlucose: null, trendZone: null, trendPercent: null, carbsG: 0, basalRateIePerH: null, hour: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    const noCells = screen.getAllByLabelText('no data')
    expect(noCells.length).toBeGreaterThan(0)
  })

  test('shows warning banner when warnings are present', () => {
    const data = makeResponse({ warnings: ['Insufficient sensor data'] })
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toContain('Insufficient sensor data')
  })

  test('does not render warning banner when warnings is empty', () => {
    const data = makeResponse({ warnings: [] })
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('does not render warning banner when warnings is undefined', () => {
    const data = makeResponse()
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('renders a zone colour legend', () => {
    render(<DailyTrendTable data={makeResponse()} glucoseUnit="mg/dL" />)
    const legend = screen.getByLabelText('Zone colour legend')
    expect(legend).toBeTruthy()
  })

  test('caps display to 14 days and shows overflow notice', () => {
    const fifteenDays = Array.from({ length: 15 }, (_, i) =>
      makeDay(`2026-05-${String(i + 1).padStart(2, '0')}`),
    )
    const data: DailyTrendResponse = { days: fifteenDays }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    // 14 day column headers + 1 hour header column
    const columnHeaders = screen.getAllByRole('columnheader')
    expect(columnHeaders).toHaveLength(15) // 14 days + 1 hour column
    // overflow notice should be visible
    expect(screen.getByText(/Showing first/)).toBeTruthy()
  })

  test('renders empty state correctly with no days', () => {
    const data: DailyTrendResponse = { days: [] }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    // Only the hour header column should exist (no day columns)
    const columnHeaders = screen.getAllByRole('columnheader')
    expect(columnHeaders).toHaveLength(1)
  })

  test('renders stable arrow →', () => {
    const data: DailyTrendResponse = {
      days: [makeDay('2026-05-01', [{ trendZone: 'stable', hour: 0, meanGlucose: 100, zone: 'inRange', trendPercent: 0, basalRateIePerH: null, carbsG: 0 }])],
    }
    render(<DailyTrendTable data={data} glucoseUnit="mg/dL" />)
    expect(screen.getAllByText(/→/).length).toBeGreaterThan(0)
  })
})
