import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import React from 'react'
import '../i18n'
import type { DailyTrendDay } from '../api/analyzeApi'
import {
  groupByIsoWeek,
  isoWeekdayIndex,
  isoWeekNumber,
  OKABE_ITO,
  WochengraphikPage,
} from '../features/report/WochengraphikPage'

// ---------------------------------------------------------------------------
// Mock recharts — avoid SVG rendering issues in jsdom
// ---------------------------------------------------------------------------
vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="composed-chart">{children}</div>
  ),
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ReferenceArea: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Legend: () => null,
}))

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeDay(date: string): DailyTrendDay {
  return {
    date,
    hours: Array.from({ length: 24 }, (_, h) => ({
      hour: h,
      meanGlucose: 120,
      trendPercent: 0,
      trendZone: 'stable' as const,
      zone: 'inRange' as const,
      basalRateIePerH: 0.8,
      carbsG: 0,
    })),
  }
}

// ---------------------------------------------------------------------------
// Unit tests: isoWeekNumber / isoWeekdayIndex / groupByIsoWeek
// ---------------------------------------------------------------------------

describe('isoWeekNumber', () => {
  test('returns correct ISO week for a mid-January date', () => {
    // 2024-01-15 is a Monday in week 3
    expect(isoWeekNumber('2024-01-15')).toBe(3)
  })

  test('returns correct ISO week for New Year edge case', () => {
    // 2023-01-01 is a Sunday in week 52 of 2022
    expect(isoWeekNumber('2023-01-01')).toBe(52)
  })

  test('returns correct ISO week for last ISO week of year', () => {
    // 2024-12-30 is a Monday in week 1 of 2025 (ISO)
    expect(isoWeekNumber('2024-12-30')).toBe(1)
  })
})

describe('isoWeekdayIndex', () => {
  test('Monday maps to index 0', () => {
    // 2024-01-15 is a Monday
    expect(isoWeekdayIndex('2024-01-15')).toBe(0)
  })

  test('Tuesday maps to index 1', () => {
    expect(isoWeekdayIndex('2024-01-16')).toBe(1)
  })

  test('Wednesday maps to index 2', () => {
    expect(isoWeekdayIndex('2024-01-17')).toBe(2)
  })

  test('Thursday maps to index 3', () => {
    expect(isoWeekdayIndex('2024-01-18')).toBe(3)
  })

  test('Friday maps to index 4', () => {
    expect(isoWeekdayIndex('2024-01-19')).toBe(4)
  })

  test('Saturday maps to index 5', () => {
    expect(isoWeekdayIndex('2024-01-20')).toBe(5)
  })

  test('Sunday maps to index 6', () => {
    expect(isoWeekdayIndex('2024-01-21')).toBe(6)
  })
})

describe('OKABE_ITO palette', () => {
  test('has exactly 7 entries (Mon–Sun)', () => {
    expect(OKABE_ITO).toHaveLength(7)
  })

  test('Monday (index 0) gets the orange colour #E69F00', () => {
    expect(OKABE_ITO[0]).toBe('#E69F00')
  })

  test('Sunday (index 6) gets the reddish-purple colour #CC79A7', () => {
    expect(OKABE_ITO[6]).toBe('#CC79A7')
  })

  test('each colour is assigned consistently by day-of-week regardless of which days are in the data', () => {
    // A Wednesday (index 2) always gets the same colour whether it is the only day or one of seven
    const wednesdayColour = OKABE_ITO[isoWeekdayIndex('2024-01-17')]
    // In a different week, a Wednesday should get the same colour
    const wednesdayColour2 = OKABE_ITO[isoWeekdayIndex('2024-01-24')]
    expect(wednesdayColour).toBe(wednesdayColour2)
    expect(wednesdayColour).toBe('#009E73')
  })
})

describe('groupByIsoWeek', () => {
  test('groups a single-week range into exactly one week', () => {
    // 2024-01-15 to 2024-01-19 are all week 3 of 2024
    const days = [
      makeDay('2024-01-15'),
      makeDay('2024-01-16'),
      makeDay('2024-01-17'),
      makeDay('2024-01-18'),
      makeDay('2024-01-19'),
    ]
    const map = groupByIsoWeek(days)
    expect(map.size).toBe(1)
  })

  test('14-day range spanning 2 full ISO weeks splits into exactly 2 weeks', () => {
    // Week 3: 2024-01-15 (Mon) – 2024-01-21 (Sun) = 7 days
    // Week 4: 2024-01-22 (Mon) – 2024-01-28 (Sun) = 7 days
    const days = Array.from({ length: 14 }, (_, i) => {
      const d = new Date('2024-01-15')
      d.setDate(d.getDate() + i)
      return makeDay(d.toISOString().slice(0, 10))
    })
    const map = groupByIsoWeek(days)
    expect(map.size).toBe(2)
    // Each week should contain 7 days
    for (const [, weekDays] of map) {
      expect(weekDays).toHaveLength(7)
    }
  })

  test('returns an empty map for an empty days array', () => {
    const map = groupByIsoWeek([])
    expect(map.size).toBe(0)
  })

  test('preserves day order within each week', () => {
    const days = [
      makeDay('2024-01-15'),
      makeDay('2024-01-17'),
      makeDay('2024-01-19'),
    ]
    const map = groupByIsoWeek(days)
    const weekDays = Array.from(map.values())[0]
    expect(weekDays).toBeDefined()
    expect(weekDays![0]!.date).toBe('2024-01-15')
    expect(weekDays![1]!.date).toBe('2024-01-17')
    expect(weekDays![2]!.date).toBe('2024-01-19')
  })

  test('spans a week boundary correctly (Sun/Mon split)', () => {
    // 2024-01-21 (Sun) is still week 3; 2024-01-22 (Mon) starts week 4
    const days = [makeDay('2024-01-21'), makeDay('2024-01-22')]
    const map = groupByIsoWeek(days)
    expect(map.size).toBe(2)
  })
})

// ---------------------------------------------------------------------------
// Component rendering tests
// ---------------------------------------------------------------------------

describe('WochengraphikPage', () => {
  test('renders without crashing with minimal data (single week)', () => {
    const data = {
      days: [makeDay('2024-01-15'), makeDay('2024-01-16')],
    }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    expect(screen.getByTestId('wochengraphik-page')).toBeTruthy()
  })

  test('renders no-data message when days array is empty', () => {
    const data = { days: [] }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    // Should not render the page container
    expect(screen.queryByTestId('wochengraphik-page')).toBeNull()
  })

  test('renders one chart per ISO week for a 14-day range', () => {
    const days = Array.from({ length: 14 }, (_, i) => {
      const d = new Date('2024-01-15')
      d.setDate(d.getDate() + i)
      return makeDay(d.toISOString().slice(0, 10))
    })
    const data = { days }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    // Two weeks → two week-chart containers
    expect(screen.getByTestId('week-chart-2024-W03')).toBeTruthy()
    expect(screen.getByTestId('week-chart-2024-W04')).toBeTruthy()
  })

  test('renders one chart for a single-week range', () => {
    const data = {
      days: [makeDay('2024-01-15'), makeDay('2024-01-17'), makeDay('2024-01-19')],
    }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    expect(screen.getByTestId('week-chart-2024-W03')).toBeTruthy()
    // Week 4 should not be rendered
    expect(screen.queryByTestId('week-chart-2024-W04')).toBeNull()
  })

  test('renders legend items for each day', () => {
    const data = {
      days: [makeDay('2024-01-15'), makeDay('2024-01-16')],
    }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    expect(screen.getByTestId('legend-2024-01-15')).toBeTruthy()
    expect(screen.getByTestId('legend-2024-01-16')).toBeTruthy()
  })

  test('renders warning banner when warnings are present', () => {
    const data = {
      days: [makeDay('2024-01-15')],
      warnings: ['Insufficient data for reliable analysis'],
    }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toContain('Insufficient data')
  })

  test('does not render warning banner when warnings is empty', () => {
    const data = { days: [makeDay('2024-01-15')], warnings: [] }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('does not render warning banner when warnings is undefined', () => {
    const data = { days: [makeDay('2024-01-15')] }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  test('renders chart figures with role=img', () => {
    const data = { days: [makeDay('2024-01-15'), makeDay('2024-01-16')] }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    const figures = screen.getAllByRole('img')
    expect(figures.length).toBeGreaterThan(0)
  })

  test('each chart figure has a non-empty aria-label', () => {
    const data = { days: [makeDay('2024-01-15')] }
    render(<WochengraphikPage data={data} glucoseUnit="mg/dL" />)
    const figure = screen.getByRole('img')
    expect(figure.getAttribute('aria-label')).toBeTruthy()
  })
})
