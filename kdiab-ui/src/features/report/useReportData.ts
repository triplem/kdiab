import { useQueries } from '@tanstack/react-query'
import { analyzeApi } from '../../api/analyzeApi'
import type {
  Hba1cResponse,
  AgpResponse,
  DailyStatsResponse,
  DailyTrendResponse,
  GlucoseDistributionResponse,
  ProfilesResponse,
  CgpResponse,
  TimelineResponse,
} from '../../api/analyzeApi'
import type { ReportPageId } from './reportPages'

export interface ReportSection<T> {
  data: T | null
  isLoading: boolean
  isError: boolean
}

export interface ReportDataState {
  /** Summary (HbA1c + TIR) — always fetched regardless of page selection */
  summary: ReportSection<Hba1cResponse>
  agp: ReportSection<AgpResponse>
  dailyStats: ReportSection<DailyStatsResponse>
  dailyTrend: ReportSection<DailyTrendResponse>
  dailyCharts: ReportSection<TimelineResponse>
  glucoseDistribution: ReportSection<GlucoseDistributionResponse>
  profile: ReportSection<ProfilesResponse>
  cgp: ReportSection<CgpResponse>
  isAnyLoading: boolean
}

/**
 * Fans out parallel data fetches to all kdiab-analyze report endpoints.
 *
 * Only fetches data for selected pages — SUMMARY (HbA1c) is always fetched.
 * If an individual endpoint fails the others continue loading independently,
 * so the report renders partial results with per-section error banners.
 */
export function useReportData(
  userId: string,
  from: string,
  to: string,
  selectedPages: readonly ReportPageId[],
  glucoseUnit: string,
): ReportDataState {
  const enabled = !!userId && !!from && !!to
  const isSelected = (page: ReportPageId) => selectedPages.includes(page)

  const results = useQueries({
    queries: [
      // SUMMARY (HbA1c) — always fetched
      {
        queryKey: ['report-summary', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getHba1c(userId, from, to, glucoseUnit).then(r => r.data),
        enabled,
        staleTime: 5 * 60 * 1000,
      },
      // AGP — only if selected
      {
        queryKey: ['report-agp', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getAgp(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: enabled && isSelected('AGP'),
        staleTime: 5 * 60 * 1000,
      },
      // Daily Stats — only if selected
      {
        queryKey: ['report-daily-stats', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getDailyStats(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: enabled && isSelected('DAILY_STATS'),
        staleTime: 5 * 60 * 1000,
      },
      // Daily Trend — only if selected
      {
        queryKey: ['report-daily-trend', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getDailyTrend(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: enabled && isSelected('DAILY_TREND'),
        staleTime: 5 * 60 * 1000,
      },
      // Daily Charts (timeline) — only if selected
      {
        queryKey: ['report-daily-charts', userId, from, to] as const,
        queryFn: () => analyzeApi.getTimeline(userId, from, to).then(r => r.data),
        enabled: enabled && isSelected('DAILY_CHARTS'),
        staleTime: 5 * 60 * 1000,
      },
      // Glucose Distribution — only if selected
      {
        queryKey: ['report-glucose-distribution', userId, from, to, glucoseUnit] as const,
        queryFn: () =>
          analyzeApi.getGlucoseDistribution(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: enabled && isSelected('GLUCOSE_DISTRIBUTION'),
        staleTime: 5 * 60 * 1000,
      },
      // Profile — only if selected
      {
        queryKey: ['report-profile', userId, from, to] as const,
        queryFn: () => analyzeApi.getActiveProfiles(userId, from, to).then(r => r.data),
        enabled: enabled && isSelected('PROFILE'),
        staleTime: 5 * 60 * 1000,
      },
      // CGP — only if selected
      {
        queryKey: ['report-cgp', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getCgp(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: enabled && isSelected('CGP'),
        staleTime: 5 * 60 * 1000,
      },
    ],
  })

  const [summaryQ, agpQ, dailyStatsQ, dailyTrendQ, dailyChartsQ, glucoseDistQ, profileQ, cgpQ] = results

  return {
    summary: {
      data: summaryQ.data ?? null,
      isLoading: summaryQ.isLoading,
      isError: summaryQ.isError,
    },
    agp: {
      data: agpQ.data ?? null,
      isLoading: agpQ.isLoading,
      isError: agpQ.isError,
    },
    dailyStats: {
      data: dailyStatsQ.data ?? null,
      isLoading: dailyStatsQ.isLoading,
      isError: dailyStatsQ.isError,
    },
    dailyTrend: {
      data: dailyTrendQ.data ?? null,
      isLoading: dailyTrendQ.isLoading,
      isError: dailyTrendQ.isError,
    },
    dailyCharts: {
      data: dailyChartsQ.data ?? null,
      isLoading: dailyChartsQ.isLoading,
      isError: dailyChartsQ.isError,
    },
    glucoseDistribution: {
      data: glucoseDistQ.data ?? null,
      isLoading: glucoseDistQ.isLoading,
      isError: glucoseDistQ.isError,
    },
    profile: {
      data: profileQ.data ?? null,
      isLoading: profileQ.isLoading,
      isError: profileQ.isError,
    },
    cgp: {
      data: cgpQ.data ?? null,
      isLoading: cgpQ.isLoading,
      isError: cgpQ.isError,
    },
    isAnyLoading: results.some(r => r.isLoading),
  }
}
