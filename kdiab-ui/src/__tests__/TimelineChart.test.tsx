import { render } from '@testing-library/react'
import { describe, test, vi } from 'vitest'
import '../i18n'
import React from 'react'

vi.mock('recharts', () => ({
  ComposedChart: ({ children }: { children: React.ReactNode }) => <div data-testid="composed-chart">{children}</div>,
  Line: () => null,
  Scatter: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div data-testid="responsive-container">{children}</div>,
  ReferenceLine: () => null,
  ReferenceArea: () => null,
  Legend: () => null,
  Cell: () => null,
}))

import { TimelineChart } from '../features/timeline/TimelineChart'

const makeCgmMeasures = (count: number) =>
  Array.from({ length: count }, (_, i) => ({
    id: `cgm-${i}`,
    measuredAt: new Date(Date.now() - i * 5 * 60 * 1000).toISOString(),
    type: 'CGM',
    data: { value: 120 + i, unit: 'mg/dL' },
  }))

const makeTreatments = (count: number) =>
  Array.from({ length: count }, (_, i) => ({
    id: `tr-${i}`,
    treatedAt: new Date(Date.now() - i * 30 * 60 * 1000).toISOString(),
    type: 'BOLUS',
    data: { units: 2 },
  }))

describe('TimelineChart', () => {
  test('renders without crashing with empty data', () => {
    render(<TimelineChart measures={[]} treatments={[]} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing with CGM data', () => {
    render(<TimelineChart measures={makeCgmMeasures(5)} treatments={[]} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing with treatments', () => {
    render(<TimelineChart measures={[]} treatments={makeTreatments(3)} glucoseUnit="mg/dL" />)
  })

  test('renders without crashing with both measures and treatments', () => {
    render(
      <TimelineChart
        measures={makeCgmMeasures(5)}
        treatments={makeTreatments(3)}
        glucoseUnit="mg/dL"
      />,
    )
  })

  test('renders without crashing in mmol/L mode', () => {
    render(
      <TimelineChart
        measures={makeCgmMeasures(5)}
        treatments={[]}
        glucoseUnit="mmol/L"
      />,
    )
  })

  test('renders without crashing with profile change dates', () => {
    const profileDates = [Date.now() - 86400000, Date.now() - 172800000]
    render(
      <TimelineChart
        measures={makeCgmMeasures(5)}
        treatments={[]}
        glucoseUnit="mg/dL"
        profileChangeDates={profileDates}
      />,
    )
  })

  test('handles BGM measures without crashing', () => {
    const bgmMeasures = Array.from({ length: 3 }, (_, i) => ({
      id: `bgm-${i}`,
      measuredAt: new Date(Date.now() - i * 60 * 60 * 1000).toISOString(),
      type: 'BGM',
      data: { value: 115 + i, unit: 'mg/dL' },
    }))
    render(<TimelineChart measures={bgmMeasures} treatments={[]} glucoseUnit="mg/dL" />)
  })
})
