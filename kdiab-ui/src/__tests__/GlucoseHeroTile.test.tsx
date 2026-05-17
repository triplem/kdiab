import { describe, test, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { GlucoseHeroTile } from '../features/dashboard/GlucoseHeroTile'
import '../i18n'

const baseProps = {
  latestSgv: 110,
  latestTrend: 'Flat',
  delta: 2,
  delta15: -1,
  minutesAgo: 5,
  isStale: false,
  isVeryStale: false,
  glucoseUnit: 'mg/dL',
  yLabel: 'mg/dL',
}

describe('GlucoseHeroTile', () => {
  test('renders glucose value in-range with success color', () => {
    const { container } = render(<GlucoseHeroTile {...baseProps} latestSgv={110} />)
    // 110 mg/dL is in range (70-180) — should show success color
    const glucoseSpan = container.querySelector('[style*="font-size: 3rem"]') as HTMLElement
    expect(glucoseSpan).toBeDefined()
    expect(glucoseSpan.style.color).toBe('var(--accent-success)')
    expect(glucoseSpan.textContent).toBe('110')
  })

  test('renders hypo glucose with danger color (< 70 mg/dL)', () => {
    const { container } = render(<GlucoseHeroTile {...baseProps} latestSgv={55} />)
    const glucoseSpan = container.querySelector('[style*="font-size: 3rem"]') as HTMLElement
    expect(glucoseSpan.style.color).toBe('var(--accent-danger)')
    expect(glucoseSpan.textContent).toBe('55')
  })

  test('renders hyper glucose with warning color (> 180 mg/dL)', () => {
    const { container } = render(<GlucoseHeroTile {...baseProps} latestSgv={220} />)
    const glucoseSpan = container.querySelector('[style*="font-size: 3rem"]') as HTMLElement
    expect(glucoseSpan.style.color).toBe('var(--accent-warning)')
    expect(glucoseSpan.textContent).toBe('220')
  })

  test('renders boundary value 70 mg/dL with success color (in range)', () => {
    const { container } = render(<GlucoseHeroTile {...baseProps} latestSgv={70} />)
    const glucoseSpan = container.querySelector('[style*="font-size: 3rem"]') as HTMLElement
    expect(glucoseSpan.style.color).toBe('var(--accent-success)')
  })

  test('renders boundary value 180 mg/dL with success color (in range)', () => {
    const { container } = render(<GlucoseHeroTile {...baseProps} latestSgv={180} />)
    const glucoseSpan = container.querySelector('[style*="font-size: 3rem"]') as HTMLElement
    expect(glucoseSpan.style.color).toBe('var(--accent-success)')
  })

  test('displays trend arrow for Flat trend', () => {
    render(<GlucoseHeroTile {...baseProps} latestTrend="Flat" />)
    expect(screen.getByText('→')).toBeDefined()
  })

  test('displays trend arrow for SingleUp trend', () => {
    render(<GlucoseHeroTile {...baseProps} latestTrend="SingleUp" />)
    expect(screen.getByText('↑')).toBeDefined()
  })

  test('displays trend arrow for DoubleDown trend', () => {
    render(<GlucoseHeroTile {...baseProps} latestTrend="DoubleDown" />)
    expect(screen.getByText('↓↓')).toBeDefined()
  })

  test('displays empty string for unknown trend', () => {
    const { container } = render(<GlucoseHeroTile {...baseProps} latestTrend="UnknownTrend" />)
    // The trend span should render but contain empty string
    const trendSpan = container.querySelector('[style*="font-size: 2rem"]') as HTMLElement
    expect(trendSpan.textContent).toBe('')
  })

  test('displays minutesAgo when provided', () => {
    render(<GlucoseHeroTile {...baseProps} minutesAgo={7} />)
    expect(screen.getByText(/7/)).toBeDefined()
  })

  test('does not display minutesAgo section when null', () => {
    render(<GlucoseHeroTile {...baseProps} minutesAgo={null} />)
    expect(screen.queryByText(/min ago/i)).toBeNull()
  })

  test('shows very stale warning when isVeryStale is true', () => {
    render(<GlucoseHeroTile {...baseProps} isVeryStale={true} />)
    expect(screen.getByText(/more than 30 min old/i)).toBeDefined()
  })

  test('shows stale warning when isStale is true and not very stale', () => {
    render(<GlucoseHeroTile {...baseProps} isStale={true} isVeryStale={false} />)
    expect(screen.getByText(/may be outdated/i)).toBeDefined()
  })

  test('stale warning not shown when both flags are false', () => {
    render(<GlucoseHeroTile {...baseProps} isStale={false} isVeryStale={false} />)
    expect(screen.queryByText(/may be outdated/i)).toBeNull()
    expect(screen.queryByText(/more than 30 min old/i)).toBeNull()
  })

  test('very stale warning takes priority over stale warning', () => {
    render(<GlucoseHeroTile {...baseProps} isStale={true} isVeryStale={true} />)
    expect(screen.getByText(/more than 30 min old/i)).toBeDefined()
    expect(screen.queryByText(/may be outdated/i)).toBeNull()
  })

  test('displays delta value', () => {
    render(<GlucoseHeroTile {...baseProps} delta={5} />)
    expect(screen.getByText(/\+5 mg\/dL/)).toBeDefined()
  })

  test('displays negative delta value', () => {
    render(<GlucoseHeroTile {...baseProps} delta={-3} />)
    expect(screen.getByText(/-3 mg\/dL/)).toBeDefined()
  })

  test('displays dash when delta is null', () => {
    render(<GlucoseHeroTile {...baseProps} delta={null} />)
    // The formatted delta shows '—' when null
    const content = screen.getByText(/Δ15.*—|Δ.*—/s)
    expect(content).toBeDefined()
  })

  test('displays mmol/L glucose value correctly', () => {
    const { container } = render(
      <GlucoseHeroTile {...baseProps} latestSgv={126} glucoseUnit="mmol/L" yLabel="mmol/L" />,
    )
    // 126 mg/dL = 7.0 mmol/L
    const glucoseSpan = container.querySelector('[style*="font-size: 3rem"]') as HTMLElement
    expect(glucoseSpan.textContent).toBe('7')
  })

  test('displays y-label unit', () => {
    render(<GlucoseHeroTile {...baseProps} yLabel="mg/dL" />)
    expect(screen.getByText('mg/dL')).toBeDefined()
  })
})
