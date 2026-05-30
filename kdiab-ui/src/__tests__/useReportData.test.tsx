import { renderHook, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'

vi.mock('../api/analyzeApi', () => ({
  analyzeApi: {
    getReportSummary: vi.fn(),
    getHba1c: vi.fn(),
    getAgp: vi.fn(),
    getDailyStats: vi.fn(),
    getDailyTrend: vi.fn(),
    getTimeline: vi.fn(),
    getGlucoseDistribution: vi.fn(),
    getActiveProfiles: vi.fn(),
    getCgp: vi.fn(),
  },
}))

import { analyzeApi } from '../api/analyzeApi'
import { useReportData } from '../features/report/useReportData'
import type { ReportPageId } from '../features/report/reportPages'

const mockedGetReportSummary = vi.mocked(analyzeApi.getReportSummary)
const mockedGetHba1c = vi.mocked(analyzeApi.getHba1c)
const mockedGetAgp = vi.mocked(analyzeApi.getAgp)
const mockedGetDailyStats = vi.mocked(analyzeApi.getDailyStats)
const mockedGetDailyTrend = vi.mocked(analyzeApi.getDailyTrend)
const mockedGetGlucoseDistribution = vi.mocked(analyzeApi.getGlucoseDistribution)
const mockedGetTimeline = vi.mocked(analyzeApi.getTimeline)
const mockedGetActiveProfiles = vi.mocked(analyzeApi.getActiveProfiles)
const mockedGetCgp = vi.mocked(analyzeApi.getCgp)

const FAKE_TIR_ZONE = { count: 100, percent: 100, zone: 'inRange' }
const FAKE_TIR_RESULT = {
  veryLow: { count: 0, percent: 0 },
  low: { count: 0, percent: 0 },
  inRange: FAKE_TIR_ZONE,
  high: { count: 0, percent: 0 },
  veryHigh: { count: 0, percent: 0 },
  customTirFallback: false,
}

const FAKE_REPORT_SUMMARY = {
  displayName: 'Test Patient',
  daysAnalysed: 14,
  cgmReadingCount: 4032,
  cgmIntervalMinutes: 5,
  insulinTypes: ['Humalog'],
  insulinChanges: 2,
  avgDaysPerCartridge: 7,
  siteChanges: 4,
  avgDaysPerSite: 3.5,
  sensorInserts: 2,
  avgDaysPerSensor: 7,
  tirProfile: FAKE_TIR_RESULT,
  tirStandard: FAKE_TIR_RESULT,
  minGlucose: 65,
  maxGlucose: 240,
  meanGlucose: 130,
  sd: 35,
  gvi: 1.2,
  pgs: 45,
  gri: 20,
  griZone: 'A',
  eHbA1c: 6.8,
  avgCarbsPerDayG: 180,
  avgBolusPerDayIe: 24,
}

const FAKE_HBA1C = {
  hba1c: 7.0,
  meanGlucose: 154,
  readingCount: 2016,
  tir: { veryLowCount: 0, belowCount: 10, inRangeCount: 1800, aboveCount: 200, highCount: 6, totalCount: 2016 },
}

const FAKE_AGP = {
  bucketData: [],
  totalReadingCount: 2016,
  sensorWearDays: 14,
}

function wrapper({ children }: { children: React.ReactNode }) {
  // retry: false at QueryClient level — per-query retry config can still override this
  // but for tests we need fast failure, so override globally
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

const ALL_PAGES: ReportPageId[] = ['SUMMARY', 'AGP', 'DAILY_STATS', 'DAILY_TREND', 'GLUCOSE_DISTRIBUTION', 'PROFILE']
const SUMMARY_ONLY: ReportPageId[] = ['SUMMARY']

beforeEach(() => {
  vi.clearAllMocks()
  mockedGetReportSummary.mockResolvedValue({ data: FAKE_REPORT_SUMMARY } as never)
  mockedGetHba1c.mockResolvedValue({ data: FAKE_HBA1C } as never)
  mockedGetAgp.mockResolvedValue({ data: FAKE_AGP } as never)
  mockedGetDailyStats.mockResolvedValue({ data: { rows: [], summary: { date: 'summary', cgmCount: 0, veryLowPercent: null, lowPercent: null, inRangePercent: null, highPercent: null, veryHighPercent: null, p25: null, median: null, p75: null, sd: null, eHbA1c: null } } } as never)
  mockedGetDailyTrend.mockResolvedValue({ data: { days: [] } } as never)
  mockedGetTimeline.mockResolvedValue({ data: { measures: [], treatments: [] } } as never)
  mockedGetGlucoseDistribution.mockResolvedValue({ data: { buckets: [], zonePercents: { veryLow: 0, low: 0, inRange: 100, high: 0, veryHigh: 0 }, unit: 'mg/dL', totalCount: 2016 } } as never)
  mockedGetActiveProfiles.mockResolvedValue({ data: { profiles: [] } } as never)
  mockedGetCgp.mockResolvedValue({ data: { hourlyData: [], totalReadingCount: 0, sensorWearDays: 14 } } as never)
})

describe('useReportData', () => {
  test('always calls getHba1c (summary endpoint) regardless of page selection', async () => {
    renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', SUMMARY_ONLY, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => expect(mockedGetHba1c).toHaveBeenCalledOnce())
  })

  test('does NOT call AGP endpoint when AGP is not in selectedPages', async () => {
    renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', SUMMARY_ONLY, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => expect(mockedGetHba1c).toHaveBeenCalled())
    expect(mockedGetAgp).not.toHaveBeenCalled()
  })

  test('calls AGP endpoint when AGP is in selectedPages', async () => {
    renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', ALL_PAGES, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => expect(mockedGetAgp).toHaveBeenCalledOnce())
  })

  test('does NOT call any secondary endpoint when only SUMMARY is selected', async () => {
    renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', SUMMARY_ONLY, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => expect(mockedGetHba1c).toHaveBeenCalled())
    expect(mockedGetDailyStats).not.toHaveBeenCalled()
    expect(mockedGetDailyTrend).not.toHaveBeenCalled()
    expect(mockedGetGlucoseDistribution).not.toHaveBeenCalled()
    expect(mockedGetActiveProfiles).not.toHaveBeenCalled()
  })

  test('calls all endpoints when all pages are selected', async () => {
    renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', ALL_PAGES, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => {
      expect(mockedGetHba1c).toHaveBeenCalled()
      expect(mockedGetAgp).toHaveBeenCalled()
      expect(mockedGetDailyStats).toHaveBeenCalled()
      expect(mockedGetDailyTrend).toHaveBeenCalled()
      expect(mockedGetGlucoseDistribution).toHaveBeenCalled()
      expect(mockedGetActiveProfiles).toHaveBeenCalled()
    })
  })

  test('returns summary data when fetched successfully', async () => {
    const { result } = renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', SUMMARY_ONLY, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => expect(result.current.summary.data).not.toBeNull())
    expect(result.current.summary.data?.hba1c).toBe(7.0)
    expect(result.current.summary.isError).toBe(false)
  })

  test('marks summary as error when getHba1c fails, other sections unaffected', async () => {
    mockedGetHba1c.mockRejectedValue(new Error('network error'))
    const selectedWithAgp: ReportPageId[] = ['SUMMARY', 'AGP']

    const { result } = renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', selectedWithAgp, 'mg/dL'),
      { wrapper },
    )
    await waitFor(() => expect(result.current.summary.isError).toBe(true))
    await waitFor(() => expect(result.current.agp.data).not.toBeNull())
    expect(result.current.summary.data).toBeNull()
  })

  test('isAnyLoading reflects loading state of active queries', () => {
    // On first render before promises resolve, isAnyLoading should be true
    // (SUMMARY is always enabled)
    let resolve!: () => void
    mockedGetHba1c.mockImplementation(() =>
      new Promise(r => { resolve = () => r({ data: FAKE_HBA1C } as never) }),
    )

    const { result } = renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', SUMMARY_ONLY, 'mg/dL'),
      { wrapper },
    )
    expect(result.current.isAnyLoading).toBe(true)
    resolve()
  })

  test('passes glucoseUnit to all fetchers that accept it', async () => {
    renderHook(
      () => useReportData('user-1', '2024-01-01T00:00:00Z', '2024-01-14T23:59:59Z', ALL_PAGES, 'mmol/L'),
      { wrapper },
    )
    // Wave 2 (AGP etc.) fires only after wave 1 (HbA1c) has settled — wait for both.
    await waitFor(() => {
      expect(mockedGetHba1c).toHaveBeenCalled()
      expect(mockedGetAgp).toHaveBeenCalled()
    })
    expect(mockedGetHba1c).toHaveBeenCalledWith(
      'user-1',
      '2024-01-01T00:00:00Z',
      '2024-01-14T23:59:59Z',
      'mmol/L',
    )
    expect(mockedGetAgp).toHaveBeenCalledWith(
      'user-1',
      '2024-01-01T00:00:00Z',
      '2024-01-14T23:59:59Z',
      'mmol/L',
    )
  })

  test('should not call any API when enabled is false', async () => {
    renderHook(
      () =>
        useReportData(
          'user-1',
          '2024-01-01T00:00:00Z',
          '2024-01-14T23:59:59Z',
          ALL_PAGES,
          'mg/dL',
          undefined,
          false,
        ),
      { wrapper },
    )
    // Let microtasks and any pending promises flush before asserting
    await new Promise(r => setTimeout(r, 50))
    expect(mockedGetReportSummary).not.toHaveBeenCalled()
    expect(mockedGetHba1c).not.toHaveBeenCalled()
    expect(mockedGetAgp).not.toHaveBeenCalled()
    expect(mockedGetDailyStats).not.toHaveBeenCalled()
    expect(mockedGetDailyTrend).not.toHaveBeenCalled()
    expect(mockedGetTimeline).not.toHaveBeenCalled()
    expect(mockedGetGlucoseDistribution).not.toHaveBeenCalled()
    expect(mockedGetActiveProfiles).not.toHaveBeenCalled()
    expect(mockedGetCgp).not.toHaveBeenCalled()
  })
})
