import { render } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import '../i18n'
import React from 'react'

// Mock Recharts — same pattern used across all chart tests in this codebase.
vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="composed-chart">{children}</div>
  ),
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  ReferenceLine: () => null,
  ReferenceArea: () => null,
}))

import { DailyChartPage } from '../features/report/DailyChartPage'
import { groupByDay } from '../features/report/dailyChartUtils'
import type { TimelineResponse } from '../api/analyzeApi'

// ---- Factories ----------------------------------------------------------------

function makeCgmMeasure(date: string, timeOfDay: string, valuesMgDl: number) {
  return {
    id: `cgm-${date}-${timeOfDay}`,
    measuredAt: `${date}T${timeOfDay}:00Z`,
    type: 'CGM',
    data: { value: valuesMgDl, unit: 'mg/dL' } as Record<string, unknown>,
  }
}

function makeTreatment(
  date: string,
  timeOfDay: string,
  type: string,
  data: Record<string, unknown> = {},
) {
  return {
    id: `tr-${date}-${timeOfDay}-${type}`,
    treatedAt: `${date}T${timeOfDay}:00Z`,
    type,
    data,
  }
}

function makeTimeline(
  measures: TimelineResponse['measures'],
  treatments: TimelineResponse['treatments'],
): TimelineResponse {
  return { measures, treatments }
}

const EMPTY_TIMELINE: TimelineResponse = { measures: [], treatments: [] }

// ---- Tests for groupByDay (pure function) ------------------------------------

describe('groupByDay', () => {
  test('returns empty array for empty timeline', () => {
    expect(groupByDay(EMPTY_TIMELINE, 'mg/dL')).toHaveLength(0)
  })

  test('groups CGM readings by day', () => {
    const timeline = makeTimeline(
      [
        makeCgmMeasure('2024-11-01', '08:00', 120),
        makeCgmMeasure('2024-11-01', '08:05', 122),
        makeCgmMeasure('2024-11-02', '10:00', 95),
      ],
      [],
    )
    const days = groupByDay(timeline, 'mg/dL')
    expect(days).toHaveLength(2)
    // newest first
    expect(days[0]?.date).toBe('2024-11-02')
    expect(days[1]?.date).toBe('2024-11-01')
  })

  test('limits to 14 days when more are present', () => {
    const measures = Array.from({ length: 20 }, (_, i) => {
      const date = new Date(2024, 0, i + 1).toISOString().slice(0, 10)
      return makeCgmMeasure(date, '08:00', 120)
    })
    const days = groupByDay(makeTimeline(measures, []), 'mg/dL')
    expect(days).toHaveLength(14)
  })

  test('inserts null gap separator for CGM readings > 20 min apart', () => {
    const timeline = makeTimeline(
      [
        makeCgmMeasure('2024-11-01', '08:00', 120),
        // 30-minute gap — should produce a null-value point between them
        makeCgmMeasure('2024-11-01', '08:30', 130),
      ],
      [],
    )
    const days = groupByDay(timeline, 'mg/dL')
    const day = days[0]
    // Should have 3 points: first reading, null gap, second reading
    expect(day?.cgmPoints).toHaveLength(3)
    expect(day?.cgmPoints[1]?.value).toBeNull()
  })

  test('does NOT insert null gap for consecutive readings <= 20 min apart', () => {
    const timeline = makeTimeline(
      [
        makeCgmMeasure('2024-11-01', '08:00', 120),
        makeCgmMeasure('2024-11-01', '08:05', 125),
      ],
      [],
    )
    const days = groupByDay(timeline, 'mg/dL')
    const day = days[0]
    expect(day?.cgmPoints).toHaveLength(2)
    expect(day?.cgmPoints.every((p) => p.value !== null)).toBe(true)
  })

  test('converts mg/dL to mmol/L when glucoseUnit is mmol/L', () => {
    const timeline = makeTimeline([makeCgmMeasure('2024-11-01', '08:00', 180)], [])
    const days = groupByDay(timeline, 'mmol/L')
    const day = days[0]
    const pt = day?.cgmPoints[0]
    // 180 mg/dL / 18 ≈ 10.0 mmol/L
    expect(pt?.value).toBeCloseTo(10.0, 1)
  })

  test('keeps values in mg/dL when glucoseUnit is mg/dL', () => {
    const timeline = makeTimeline([makeCgmMeasure('2024-11-01', '08:00', 150)], [])
    const days = groupByDay(timeline, 'mg/dL')
    expect(days[0]?.cgmPoints[0]?.value).toBe(150)
  })

  test('includes treatment markers on the correct day', () => {
    const timeline = makeTimeline(
      [makeCgmMeasure('2024-11-01', '08:00', 120)],
      [
        makeTreatment('2024-11-01', '08:30', 'BOLUS', { units: 3 }),
        makeTreatment('2024-11-01', '08:30', 'CARBS', { carbsG: 40 }),
      ],
    )
    const days = groupByDay(timeline, 'mg/dL')
    const day = days[0]
    expect(day?.markers).toHaveLength(2)
  })

  test('assigns bolus label with units', () => {
    const timeline = makeTimeline(
      [],
      [makeTreatment('2024-11-01', '12:00', 'BOLUS', { units: 2.5 })],
    )
    const days = groupByDay(timeline, 'mg/dL')
    const marker = days[0]?.markers[0]
    expect(marker?.label).toContain('2.5')
  })

  test('assigns carbs label with grams', () => {
    const timeline = makeTimeline(
      [],
      [makeTreatment('2024-11-01', '12:00', 'CARBS', { carbsG: 45 })],
    )
    const days = groupByDay(timeline, 'mg/dL')
    const marker = days[0]?.markers[0]
    expect(marker?.label).toContain('C')
    expect(marker?.label).toContain('45')
  })

  test('skips non-CGM measures (e.g. BGM)', () => {
    const timeline = makeTimeline(
      [
        {
          id: 'bgm-1',
          measuredAt: '2024-11-01T08:00:00Z',
          type: 'BGM',
          data: { value: 110, unit: 'mg/dL' } as Record<string, unknown>,
        },
      ],
      [],
    )
    const days = groupByDay(timeline, 'mg/dL')
    // BGM readings produce no CGM points so the day only appears via treatments.
    // Since there are no treatments either, the day should NOT appear.
    expect(days).toHaveLength(0)
  })

  test('returns days sorted newest-first', () => {
    const timeline = makeTimeline(
      [
        makeCgmMeasure('2024-11-01', '08:00', 100),
        makeCgmMeasure('2024-11-03', '08:00', 110),
        makeCgmMeasure('2024-11-02', '08:00', 105),
      ],
      [],
    )
    const days = groupByDay(timeline, 'mg/dL')
    expect(days.map((d) => d.date)).toEqual(['2024-11-03', '2024-11-02', '2024-11-01'])
  })
})

// ---- Tests for DailyChartPage (rendering) ------------------------------------

describe('DailyChartPage', () => {
  test('renders without crashing with empty timeline', () => {
    render(<DailyChartPage timeline={EMPTY_TIMELINE} glucoseUnit="mg/dL" />)
  })

  test('renders no-data message when timeline has no CGM data', () => {
    const { getByText } = render(<DailyChartPage timeline={EMPTY_TIMELINE} glucoseUnit="mg/dL" />)
    // i18n key: report.dailyChart.noData
    expect(getByText(/no cgm data/i)).toBeTruthy()
  })

  test('renders a chart for each day in the timeline', () => {
    const timeline = makeTimeline(
      [
        makeCgmMeasure('2024-11-01', '08:00', 120),
        makeCgmMeasure('2024-11-02', '08:00', 130),
      ],
      [],
    )
    const { getAllByTestId } = render(<DailyChartPage timeline={timeline} glucoseUnit="mg/dL" />)
    // Each day gets one ResponsiveContainer
    expect(getAllByTestId('responsive-container')).toHaveLength(2)
  })

  test('renders without crashing with treatments and no CGM data', () => {
    const timeline = makeTimeline(
      [],
      [makeTreatment('2024-11-01', '08:00', 'BOLUS', { units: 2 })],
    )
    render(<DailyChartPage timeline={timeline} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing in mmol/L mode', () => {
    const timeline = makeTimeline([makeCgmMeasure('2024-11-01', '08:00', 150)], [])
    render(<DailyChartPage timeline={timeline} glucoseUnit="mmol/L" />)
  })

  test('section has a non-empty aria-label when data is present', () => {
    const timeline = makeTimeline([makeCgmMeasure('2024-11-01', '08:00', 120)], [])
    const { getByLabelText } = render(<DailyChartPage timeline={timeline} glucoseUnit="mg/dL" />)
    expect(getByLabelText(/daily charts/i)).toBeTruthy()
  })

  test('each day chart has role=img with aria-label', () => {
    const timeline = makeTimeline([makeCgmMeasure('2024-11-01', '08:00', 120)], [])
    const { getAllByRole } = render(<DailyChartPage timeline={timeline} glucoseUnit="mg/dL" />)
    const figures = getAllByRole('img')
    expect(figures.length).toBeGreaterThanOrEqual(1)
    figures.forEach((fig) => {
      expect(fig.getAttribute('aria-label')).toBeTruthy()
    })
  })

  test('limits rendered days to 14 even when timeline has more', () => {
    const measures = Array.from({ length: 20 }, (_, i) => {
      const date = new Date(2024, 0, i + 1).toISOString().slice(0, 10)
      return makeCgmMeasure(date, '08:00', 120)
    })
    const { getAllByTestId } = render(
      <DailyChartPage timeline={makeTimeline(measures, [])} glucoseUnit="mg/dL" />,
    )
    expect(getAllByTestId('responsive-container')).toHaveLength(14)
  })

  test('renders treatment summary text when bolus is present', () => {
    const timeline = makeTimeline(
      [makeCgmMeasure('2024-11-01', '08:00', 120)],
      [makeTreatment('2024-11-01', '12:00', 'BOLUS', { units: 3.5 })],
    )
    const { getByText } = render(<DailyChartPage timeline={timeline} glucoseUnit="mg/dL" />)
    // i18n: report.dailyChart.totalBolus → "Bolus: 3.5 IE"
    expect(getByText(/3\.5/)).toBeTruthy()
  })

  test('renders treatment summary text when carbs are present', () => {
    const timeline = makeTimeline(
      [makeCgmMeasure('2024-11-01', '08:00', 120)],
      [makeTreatment('2024-11-01', '12:00', 'CARBS', { carbsG: 45 })],
    )
    const { getByText } = render(<DailyChartPage timeline={timeline} glucoseUnit="mg/dL" />)
    // i18n: report.dailyChart.totalCarbs → "Carbs: 45 g"
    expect(getByText(/45/)).toBeTruthy()
  })
})
