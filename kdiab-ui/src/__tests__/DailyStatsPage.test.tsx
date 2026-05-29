import { render, screen, within } from '@testing-library/react'
import { describe, test, expect } from 'vitest'
import React from 'react'
import '../i18n'

import { DailyStatsPage } from '../features/report/DailyStatsPage'
import type { DailyStatRow } from '../api/analyzeApi'

// ---- helpers ----

function makeRow(overrides: Partial<DailyStatRow> = {}): DailyStatRow {
  return {
    date: '2024-01-01',
    cgmCount: 288,
    veryLowPercent: 0.0,
    lowPercent: 2.5,
    inRangePercent: 70.0,
    highPercent: 20.0,
    veryHighPercent: 7.5,
    p25: 90,
    median: 120,
    p75: 160,
    sd: 30,
    eHbA1c: 7.0,
    ...overrides,
  }
}

function makeSummaryRow(): DailyStatRow {
  return makeRow({ date: 'summary', cgmCount: 2016 })
}

// ---- tests ----

describe('DailyStatsPage', () => {
  test('renders without crashing with minimal props', () => {
    render(
      <DailyStatsPage
        rows={[]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
      />,
    )
    // Table should be in the document
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  test('renders all 12 column headers', () => {
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
      />,
    )
    // Verify expected column headers are present
    expect(screen.getByText('Date')).toBeInTheDocument()
    expect(screen.getByText('Readings')).toBeInTheDocument()
    expect(screen.getByText('Very Low')).toBeInTheDocument()
    expect(screen.getByText('Low')).toBeInTheDocument()
    expect(screen.getByText('In Range')).toBeInTheDocument()
    expect(screen.getByText('High')).toBeInTheDocument()
    expect(screen.getByText('Very High')).toBeInTheDocument()
    expect(screen.getByText('eHbA1c')).toBeInTheDocument()

    // P25, Median, P75, SD have the unit in parens — text is split across nodes;
    // use a regex or function matcher to find the containing <th>.
    const headers = screen.getAllByRole('columnheader')
    const headerTexts = headers.map(h => h.textContent ?? '')
    expect(headerTexts.some(t => t.includes('P25') && t.includes('mg/dL'))).toBe(true)
    expect(headerTexts.some(t => t.includes('Median') && t.includes('mg/dL'))).toBe(true)
    expect(headerTexts.some(t => t.includes('P75') && t.includes('mg/dL'))).toBe(true)
    expect(headerTexts.some(t => t.includes('SD') && t.includes('mg/dL'))).toBe(true)
  })

  test('renders a data row for each input row', () => {
    const rows = [
      makeRow({ date: '2024-01-01', cgmCount: 288 }),
      makeRow({ date: '2024-01-02', cgmCount: 276 }),
      makeRow({ date: '2024-01-03', cgmCount: 250 }),
    ]
    render(
      <DailyStatsPage rows={rows} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    expect(screen.getByText('2024-01-01')).toBeInTheDocument()
    expect(screen.getByText('2024-01-02')).toBeInTheDocument()
    expect(screen.getByText('2024-01-03')).toBeInTheDocument()
  })

  test('renders the summary row in tfoot with bold label', () => {
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
      />,
    )
    const tfoot = document.querySelector('tfoot')
    expect(tfoot).toBeInTheDocument()
    // "Average" is the i18n key value for report.dailyStats.summary in en.json
    expect(within(tfoot!).getByText('Average')).toBeInTheDocument()
  })

  test('formats null values as an em dash', () => {
    const rowWithNulls = makeRow({
      veryLowPercent: null,
      lowPercent: null,
      inRangePercent: null,
      highPercent: null,
      veryHighPercent: null,
      p25: null,
      median: null,
      p75: null,
      sd: null,
      eHbA1c: null,
    })
    render(
      <DailyStatsPage
        rows={[rowWithNulls]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
      />,
    )
    // Several em dashes should be visible
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBeGreaterThan(0)
  })

  test('formats mg/dL glucose values as rounded integers', () => {
    // Use unique values that won't collide with cgmCount (288, 2016)
    const row = makeRow({ median: 122.8, p25: 91.1, p75: 163.9, cgmCount: 271 })
    render(
      <DailyStatsPage rows={[row]} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    // 122.8 → 123, 91.1 → 91, 163.9 → 164
    expect(screen.getByText('123')).toBeInTheDocument()
    expect(screen.getByText('91')).toBeInTheDocument()
    expect(screen.getByText('164')).toBeInTheDocument()
  })

  test('formats mmol/L glucose values to 1 decimal by dividing by 18', () => {
    // median = 126, 126 / 18 = 7.0
    const row = makeRow({ median: 126, p25: 72, p75: 144 })
    render(
      <DailyStatsPage rows={[row]} summary={makeSummaryRow()} glucoseUnit="mmol/L" />,
    )
    // 126/18 = 7.0, 72/18 = 4.0, 144/18 = 8.0
    expect(screen.getByText('7.0')).toBeInTheDocument()
    expect(screen.getByText('4.0')).toBeInTheDocument()
    expect(screen.getByText('8.0')).toBeInTheDocument()
  })

  test('shows unit in column headers when mmol/L', () => {
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mmol/L"
      />,
    )
    // Text is split across sibling nodes — use textContent of each th element
    const headers = screen.getAllByRole('columnheader')
    const headerTexts = headers.map(h => h.textContent ?? '')
    expect(headerTexts.some(t => t.includes('Median') && t.includes('mmol/L'))).toBe(true)
    expect(headerTexts.some(t => t.includes('P25') && t.includes('mmol/L'))).toBe(true)
    expect(headerTexts.some(t => t.includes('P75') && t.includes('mmol/L'))).toBe(true)
  })

  test('formats eHbA1c as a percentage string', () => {
    const row = makeRow({ eHbA1c: 7.3 })
    render(
      <DailyStatsPage rows={[row]} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    // eHbA1c is rendered as '7.3%'
    const pctCells = screen.getAllByText('7.3%')
    // At least one match (possibly in the row, and possibly in the summary)
    expect(pctCells.length).toBeGreaterThanOrEqual(1)
  })

  test('renders warnings list when warnings are provided', () => {
    const warnings = ['Insufficient data for reliable statistics', 'Sensor wear below 70%']
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
        warnings={warnings}
      />,
    )
    expect(screen.getByText('Insufficient data for reliable statistics')).toBeInTheDocument()
    expect(screen.getByText('Sensor wear below 70%')).toBeInTheDocument()
  })

  test('does not render warnings section when warnings is undefined', () => {
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
      />,
    )
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  test('does not render warnings section when warnings array is empty', () => {
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
        warnings={[]}
      />,
    )
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  test('caps display at 90 rows and shows advisory text', () => {
    const rows = Array.from({ length: 100 }, (_, i) =>
      makeRow({ date: `2024-${String(Math.floor(i / 30) + 1).padStart(2, '0')}-${String((i % 30) + 1).padStart(2, '0')}` }),
    )
    render(
      <DailyStatsPage rows={rows} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    // Advisory text should appear (showingFirst)
    expect(screen.getByText(/90.*100/)).toBeInTheDocument()
    // Only 90 dates should appear in tbody (not 100)
    const tbody = document.querySelector('tbody')
    const dataRows = within(tbody!).getAllByRole('row')
    expect(dataRows).toHaveLength(90)
  })

  test('does not show advisory text when rows <= 90', () => {
    const rows = Array.from({ length: 14 }, (_, i) =>
      makeRow({ date: `2024-01-${String(i + 1).padStart(2, '0')}` }),
    )
    render(
      <DailyStatsPage rows={rows} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    expect(screen.queryByText(/Showing first/)).not.toBeInTheDocument()
  })

  test('applies color-coded styling to veryLow percentage when > 0.1', () => {
    const row = makeRow({ veryLowPercent: 5.0 })
    render(
      <DailyStatsPage rows={[row]} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    // The cell with "5.0%" should have non-default background (color-coded)
    const cell = screen.getByText('5.0%').closest('td')
    expect(cell).toHaveStyle({ backgroundColor: '#fce4e4' })
  })

  test('does not apply color-coded styling when veryLow percentage is 0', () => {
    const row = makeRow({ veryLowPercent: 0.0 })
    render(
      <DailyStatsPage rows={[row]} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    // The 0.0% cell — should not have a colored background
    // Note: there may be multiple 0.0% cells (summary etc) so find in tbody
    const tbody = document.querySelector('tbody')
    const zeroCell = within(tbody!).getByText('0.0%').closest('td')
    expect(zeroCell).not.toHaveStyle({ backgroundColor: '#fce4e4' })
  })

  test('renders TIR zone percentages with one decimal place', () => {
    const row = makeRow({ inRangePercent: 68.75, highPercent: 21.5 })
    render(
      <DailyStatsPage rows={[row]} summary={makeSummaryRow()} glucoseUnit="mg/dL" />,
    )
    expect(screen.getByText('68.8%')).toBeInTheDocument()
    expect(screen.getByText('21.5%')).toBeInTheDocument()
  })

  test('renders the table wrapper with an accessible aria-label', () => {
    render(
      <DailyStatsPage
        rows={[makeRow()]}
        summary={makeSummaryRow()}
        glucoseUnit="mg/dL"
      />,
    )
    // The table wrapper div has role="region" and an aria-label
    const region = document.querySelector('[role="region"]')
    expect(region).not.toBeNull()
    expect(region?.getAttribute('aria-label')).toBeTruthy()
  })
})
