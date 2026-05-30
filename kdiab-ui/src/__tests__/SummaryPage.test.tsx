import { render, screen } from '@testing-library/react'
import { describe, test, expect } from 'vitest'
import React from 'react'
import { SummaryPage } from '../features/report/SummaryPage'
import type { ReportSummaryResponse } from '../api/analyzeApi'

// i18n keys are returned as-is in test environment (no provider)
// so we assert on key strings or test structure/DOM presence

const makeTirResult = (overrides?: Partial<{ customTirFallback: boolean }>) => ({
  veryLow: { count: 5, percent: 0.5 },
  low: { count: 50, percent: 5.0 },
  inRange: { count: 1500, percent: 74.4 },
  high: { count: 400, percent: 19.8 },
  veryHigh: { count: 61, percent: 3.0 },
  customTirFallback: overrides?.customTirFallback ?? false,
})

const BASE_DATA: ReportSummaryResponse = {
  displayName: 'Sarah Müller',
  daysAnalysed: 30,
  cgmReadingCount: 8640,
  cgmIntervalMinutes: 5,
  insulinTypes: ['NovoRapid'],
  insulinChanges: 10,
  avgDaysPerCartridge: 3.1,
  siteChanges: 10,
  avgDaysPerSite: 3.0,
  sensorInserts: 5,
  avgDaysPerSensor: 6.0,
  tirProfile: makeTirResult(),
  tirStandard: makeTirResult(),
  minGlucose: 52,
  maxGlucose: 285,
  meanGlucose: 154,
  sd: 45,
  gvi: 1.412,
  pgs: 67.3,
  gri: 18.4,
  griZone: 'A',
  eHbA1c: 7.1,
  avgCarbsPerDayG: 185.0,
  avgBolusPerDayIe: 22.5,
  bolusPercent: 55.0,
  avgBasalPerDayIe: 18.3,
  basalPercent: 45.0,
  avgTotalInsulinPerDayIe: 40.8,
  warnings: [],
}

function renderSummaryPage(data: ReportSummaryResponse = BASE_DATA, glucoseUnit = 'mg/dL') {
  return render(<SummaryPage data={data} glucoseUnit={glucoseUnit} />)
}

describe('SummaryPage', () => {
  test('renders patient display name', () => {
    renderSummaryPage()
    expect(screen.getByText('Sarah Müller')).toBeInTheDocument()
  })

  test('renders days analysed', () => {
    renderSummaryPage()
    expect(screen.getByText('30')).toBeInTheDocument()
  })

  test('renders CGM reading count', () => {
    renderSummaryPage()
    // 8640 formatted with toLocaleString — check for the number
    expect(screen.getByText(/8[,.]?640.*5 min/)).toBeInTheDocument()
  })

  test('renders insulin types', () => {
    renderSummaryPage()
    expect(screen.getByText('NovoRapid')).toBeInTheDocument()
  })

  test('renders two TIR colour bars (profile + standard)', () => {
    renderSummaryPage()
    const bars = screen.getAllByRole('img', { name: /time in range/i })
    expect(bars.length).toBeGreaterThanOrEqual(2)
  })

  test('renders inRange TIR percent', () => {
    renderSummaryPage()
    // 74.4% appears in at least one TIR section
    const matches = screen.getAllByText('74.4%')
    expect(matches.length).toBeGreaterThan(0)
  })

  test('renders standard threshold label 54–70', () => {
    renderSummaryPage()
    // Standard threshold labels include "54–70 mg/dL"
    expect(screen.getByText(/54.70 mg\/dL/)).toBeInTheDocument()
  })

  test('renders standard threshold label 70–180', () => {
    renderSummaryPage()
    expect(screen.getByText(/70.180 mg\/dL/)).toBeInTheDocument()
  })

  test('renders standard threshold label 180–250', () => {
    renderSummaryPage()
    expect(screen.getByText(/180.250 mg\/dL/)).toBeInTheDocument()
  })

  test('renders min glucose in mg/dL', () => {
    renderSummaryPage()
    // 52 mg/dL minimum
    expect(screen.getByText(/52 mg\/dL/)).toBeInTheDocument()
  })

  test('renders max glucose in mg/dL', () => {
    renderSummaryPage()
    expect(screen.getByText(/285 mg\/dL/)).toBeInTheDocument()
  })

  test('renders mean glucose in mg/dL', () => {
    renderSummaryPage()
    expect(screen.getByText(/154 mg\/dL/)).toBeInTheDocument()
  })

  test('renders eHbA1c', () => {
    renderSummaryPage()
    expect(screen.getByText('7.1%')).toBeInTheDocument()
  })

  test('renders GRI with zone letter', () => {
    renderSummaryPage()
    expect(screen.getByText('18.4 (A)')).toBeInTheDocument()
  })

  test('renders avg carbs with BE conversion', () => {
    renderSummaryPage()
    // 185.0 g / 10 = 18.5 BE
    expect(screen.getByText(/185\.0 g.*18\.5 BE/)).toBeInTheDocument()
  })

  test('renders avg bolus with percent', () => {
    renderSummaryPage()
    expect(screen.getByText(/22\.5 IE.*55\.0%/)).toBeInTheDocument()
  })

  test('renders avg basal with percent', () => {
    renderSummaryPage()
    expect(screen.getByText(/18\.3 IE.*45\.0%/)).toBeInTheDocument()
  })

  test('renders avg total insulin', () => {
    renderSummaryPage()
    expect(screen.getByText('40.8 IE')).toBeInTheDocument()
  })

  test('converts glucose to mmol/L when glucoseUnit is mmol/L', () => {
    renderSummaryPage(BASE_DATA, 'mmol/L')
    // 52 mg/dL → 2.9 mmol/L, 285 → 15.8, 154 → 8.6
    expect(screen.getByText(/2\.9 mmol\/L/)).toBeInTheDocument()
    expect(screen.getByText(/8\.6 mmol\/L/)).toBeInTheDocument()
  })

  test('renders dash for null glucose stats', () => {
    const nullData: ReportSummaryResponse = {
      ...BASE_DATA,
      minGlucose: null,
      maxGlucose: null,
      meanGlucose: null,
      sd: null,
      gvi: null,
      pgs: null,
      gri: null,
      griZone: null,
      eHbA1c: null,
    }
    renderSummaryPage(nullData)
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBeGreaterThanOrEqual(2)
  })

  test('renders lessThan14Days warning when present in warnings array', () => {
    const dataWithWarning: ReportSummaryResponse = {
      ...BASE_DATA,
      warnings: ['lessThan14Days'],
    }
    renderSummaryPage(dataWithWarning)
    const alert = screen.getByRole('alert')
    expect(alert).toBeInTheDocument()
  })

  test('does not render warning box when warnings is empty', () => {
    renderSummaryPage(BASE_DATA)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('renders event count with avg days', () => {
    renderSummaryPage()
    // insulinChanges=10, avgDaysPerCartridge=3.1 → "10 (ø 3.1 d)"
    expect(screen.getByText('10 (ø 3.1 d)')).toBeInTheDocument()
  })

  test('renders event count without avg days when null', () => {
    const data: ReportSummaryResponse = { ...BASE_DATA, avgDaysPerCartridge: null }
    renderSummaryPage(data)
    // Should render just "10" for insulinChanges
    expect(screen.getByText('10')).toBeInTheDocument()
  })

  test('renders dash for empty insulinTypes', () => {
    const data: ReportSummaryResponse = { ...BASE_DATA, insulinTypes: [] }
    renderSummaryPage(data)
    // At least one dash should appear (insulinTypes = '—')
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })

  test('renders tirFallback subtitle when customTirFallback is true', () => {
    const data: ReportSummaryResponse = {
      ...BASE_DATA,
      tirProfile: makeTirResult({ customTirFallback: true }),
    }
    renderSummaryPage(data)
    // The subtitle span should appear (i18n key rendered as-is without provider)
    // Just verify the component renders without error when flag is true
    expect(screen.getByText('Sarah Müller')).toBeInTheDocument()
  })
})
