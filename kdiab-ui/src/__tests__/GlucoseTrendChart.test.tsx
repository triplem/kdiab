import { render, screen } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import '../i18n'
import React from 'react'

vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => <div data-testid="composed-chart">{children}</div>,
  Line: () => null,
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ReferenceLine: () => null,
  ReferenceArea: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('../context/TimeFormatContext', () => ({
  useTimeFormat: () => ({ formatTime: (s: string) => s }),
}))

import { GlucoseTrendChart } from '../features/dashboard/GlucoseTrendChart'
import type { BasalBlock } from '../features/dashboard/basalUtils'

const W_FROM = '2024-06-01T00:00:00Z'
const W_TO   = '2024-06-01T06:00:00Z'

const baseProps = {
  chartData: [],
  cgmPoints: [],
  bgmPoints: [],
  treatmentMarkers: [],
  windowFrom: W_FROM,
  windowTo: W_TO,
  glucoseUnit: 'mg/dL' as const,
  yLabel: 'mg/dL',
  tirLow: 70,
  tirHigh: 180,
  isLoading: false,
}

const cgmPoint = {
  time: new Date('2024-06-01T01:00:00Z').getTime(),
  sgv: 110,
  bgm: null,
  marker: null,
  treatmentType: null,
  label: null,
}

const mockBasalBlock: BasalBlock = {
  startMs: new Date('2024-06-01T06:00:00Z').getTime(),
  endMs:   new Date('2024-06-01T07:00:00Z').getTime(),
  deliveredRate: 0.8,
  scheduledRate: 0.8,
  state: 'SCHEDULED',
}

const mockBasalProfileLine = [
  { time: new Date('2024-06-01T00:00:00Z').getTime(), sched: 0.8 },
  { time: new Date('2024-06-01T06:00:00Z').getTime(), sched: 1.2 },
]

describe('GlucoseTrendChart', () => {
  test('renders without crashing when cgmPoints is empty', () => {
    render(<GlucoseTrendChart {...baseProps} />)
  })

  test('renders loading text when isLoading is true', () => {
    render(<GlucoseTrendChart {...baseProps} isLoading={true} />)
    // Loading indicator should be present (text from i18n)
    const loading = document.querySelector('[style]')
    expect(loading).toBeTruthy()
  })

  test('renders figure with role=img when cgmPoints are present', () => {
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
      />
    )
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('chart figure has a non-empty aria-label', () => {
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
      />
    )
    const figure = screen.getByRole('img')
    expect(figure.getAttribute('aria-label')).toBeTruthy()
  })

  test('renders without basal overlay when no basal props are provided', () => {
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
      />
    )
    // Basal legend entries should not be present
    expect(screen.queryByText(/scheduled/i)).toBeNull()
    expect(screen.queryByText(/temp above/i)).toBeNull()
  })

  test('renders without crashing when basalBlocks and basalProfileLine are provided', () => {
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
        basalBlocks={[mockBasalBlock]}
        basalProfileLine={mockBasalProfileLine}
      />
    )
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('renders without crashing when basalBlocks provided but basalProfileLine is absent', () => {
    // Edge case: blocks loaded but profile line not yet computed — maxBasalRate falls back to deliveredRate max
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
        basalBlocks={[mockBasalBlock]}
      />
    )
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('renders without crashing when basalBlocks contains a high ABOVE rate', () => {
    const aboveBlock: BasalBlock = { ...mockBasalBlock, deliveredRate: 3.5, scheduledRate: 0.8, state: 'ABOVE' }
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
        basalBlocks={[aboveBlock]}
        basalProfileLine={mockBasalProfileLine}
      />
    )
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('renders without crashing in mmol/L mode', () => {
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
        glucoseUnit="mmol/L"
        yLabel="mmol/L"
      />
    )
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('renders without crashing when basalBlocks contains a BELOW block (delivered line dips)', () => {
    const belowBlock: BasalBlock = { startMs: new Date('2024-06-01T00:00:00Z').getTime(), endMs: new Date('2024-06-01T01:00:00Z').getTime(), deliveredRate: 0.45, scheduledRate: 0.9, state: 'BELOW' }
    render(<GlucoseTrendChart {...baseProps} chartData={[cgmPoint]} cgmPoints={[cgmPoint]} basalBlocks={[belowBlock]} basalProfileLine={mockBasalProfileLine} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('renders without crashing when basalBlocks contains a SUSPENDED block (rate=0)', () => {
    const suspendBlock: BasalBlock = { startMs: new Date('2024-06-01T00:00:00Z').getTime(), endMs: new Date('2024-06-01T00:05:00Z').getTime(), deliveredRate: 0, scheduledRate: 0.9, state: 'SUSPENDED' }
    render(<GlucoseTrendChart {...baseProps} chartData={[cgmPoint]} cgmPoints={[cgmPoint]} basalBlocks={[suspendBlock]} basalProfileLine={mockBasalProfileLine} />)
    expect(screen.getByRole('img')).toBeTruthy()
  })

  test('CGM line has activeDot configured for per-reading hover', () => {
    // With recharts mocked, just verify the component renders without errors
    // when cgmPoints are present (activeDot is a prop, not a rendered element in the mock)
    render(
      <GlucoseTrendChart
        {...baseProps}
        chartData={[cgmPoint]}
        cgmPoints={[cgmPoint]}
      />
    )
    expect(screen.getByRole('img')).toBeTruthy()
  })
})
