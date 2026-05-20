import { render, screen } from '@testing-library/react'
import { describe, test, expect } from 'vitest'
import { TimeInRangeBar } from '../features/analytics/TimeInRangeBar'
import '../i18n'
import type { TirBreakdown } from '../api/analyzeApi'

const baseTir: TirBreakdown = {
  veryLowCount: 0,
  belowCount: 5,
  inRangeCount: 70,
  aboveCount: 15,
  highCount: 10,
  totalCount: 100,
}

describe('TimeInRangeBar', () => {
  test('shows correct percentage for in-range zone', () => {
    render(<TimeInRangeBar tir={baseTir} glucoseUnit="mg/dL" />)
    // Multiple elements may show 70.0% (hidden aria span + legend strong + dl dd)
    const matches = screen.getAllByText(/70\.0%/)
    expect(matches.length).toBeGreaterThan(0)
  })

  test('shows zero values correctly without NaN text', () => {
    const zeroTir: TirBreakdown = {
      veryLowCount: 0,
      belowCount: 0,
      inRangeCount: 0,
      aboveCount: 0,
      highCount: 0,
      totalCount: 0,
    }
    render(<TimeInRangeBar tir={zeroTir} glucoseUnit="mg/dL" />)
    const allText = document.body.textContent ?? ''
    expect(allText).not.toContain('NaN')
  })

  test('renders all four zone percentages', () => {
    render(<TimeInRangeBar tir={baseTir} glucoseUnit="mg/dL" />)
    // Each percentage appears at least once (legend shows x.x% in <strong> and dl shows it too)
    expect(screen.getAllByText(/5\.0%/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/70\.0%/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/15\.0%/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/10\.0%/).length).toBeGreaterThan(0)
  })

  test('works in mmol/L mode without crashing', () => {
    render(<TimeInRangeBar tir={baseTir} glucoseUnit="mmol/L" />)
    const matches = screen.getAllByText(/70\.0%/)
    expect(matches.length).toBeGreaterThan(0)
  })

  test('shows goal-met indicator when in-range >= 70%', () => {
    render(<TimeInRangeBar tir={baseTir} glucoseUnit="mg/dL" />)
    // baseTir has inRangeCount=70, totalCount=100 → 70% → meets goal
    const allText = document.body.textContent ?? ''
    expect(allText).not.toBe('')
    // The goal indicator element should have "meets-goal" class
    const goalEl = document.querySelector('.meets-goal')
    expect(goalEl).not.toBeNull()
  })

  test('shows goal-not-met indicator when in-range < 70%', () => {
    const lowTir: TirBreakdown = {
      veryLowCount: 0,
      belowCount: 20,
      inRangeCount: 50,
      aboveCount: 20,
      highCount: 10,
      totalCount: 100,
    }
    render(<TimeInRangeBar tir={lowTir} glucoseUnit="mg/dL" />)
    const goalEl = document.querySelector('.below-goal')
    expect(goalEl).not.toBeNull()
  })

  test('renders very low band when veryLowCount is non-zero', () => {
    const tirWithVeryLow: TirBreakdown = {
      veryLowCount: 3,
      belowCount: 5,
      inRangeCount: 70,
      aboveCount: 12,
      highCount: 10,
      totalCount: 100,
    }
    render(<TimeInRangeBar tir={tirWithVeryLow} glucoseUnit="mg/dL" />)
    expect(screen.getAllByText(/3\.0%/).length).toBeGreaterThan(0)
  })

  test('omits very low band from bar when veryLowCount is zero', () => {
    render(<TimeInRangeBar tir={baseTir} glucoseUnit="mg/dL" />)
    // veryLowCount=0 → segment has pct=0 and is not rendered in the bar
    const bar = document.querySelector('[aria-describedby="tir-desc-verylow"]')
    expect(bar).toBeNull()
  })
})
