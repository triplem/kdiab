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
  ReportSummaryResponse,
} from '../../api/analyzeApi'
import type { ReportPageId } from './reportPages'

export interface ReportSection<T> {
  data: T | null
  isLoading: boolean
  isError: boolean
}

export interface ReportDataState {
  /** Comprehensive report summary (Auswertung) — always fetched */
  reportSummary: ReportSection<ReportSummaryResponse>
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
 * Queries are issued in two waves to avoid saturating the nginx rate-limit burst budget:
 *   Wave 1 — REPORT_SUMMARY and SUMMARY (HbA1c) fire immediately on enable.
 *   Wave 2 — all selected-page queries fire only after wave 1 has settled (success or error).
 *
 * If an individual endpoint fails the others continue loading independently,
 * so the report renders partial results with per-section error banners.
 *
 * @param enabled - When false, all queries are suspended (default: true).
 *   Pass the "Generate" button state here to prevent eager fetching on mount.
 */
export function useReportData(
  userId: string,
  from: string,
  to: string,
  selectedPages: readonly ReportPageId[],
  glucoseUnit: string,
  displayName?: string,
  enabled?: boolean,
): ReportDataState {
  const baseEnabled = !!userId && !!from && !!to
  const queryEnabled = baseEnabled && (enabled ?? true)
  const isSelected = (page: ReportPageId) => selectedPages.includes(page)

  // ── Wave 1: always-on queries (fire immediately) ──────────────────────────
  const wave1 = useQueries({
    queries: [
      // AUSWERTUNG (report-summary) — always fetched
      {
        queryKey: ['report-summary-page', userId, from, to, glucoseUnit, displayName] as const,
        queryFn: () =>
          analyzeApi.getReportSummary(userId, from, to, glucoseUnit, displayName).then(r => r.data),
        enabled: queryEnabled,
        staleTime: 5 * 60 * 1000,
      },
      // SUMMARY (HbA1c) — always fetched
      {
        queryKey: ['report-summary', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getHba1c(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: queryEnabled,
        staleTime: 5 * 60 * 1000,
      },
    ],
  })

  // Wave 2 fires only after wave 1 has settled (either success or error on both queries).
  // This keeps the total concurrent burst within the nginx burst=20 budget.
  const wave1Done = (wave1[0].isSuccess || wave1[0].isError) && (wave1[1].isSuccess || wave1[1].isError)
  const wave2Enabled = queryEnabled && wave1Done

  // ── Wave 2: selected-page queries (fire after wave 1 settles) ─────────────
  const wave2 = useQueries({
    queries: [
      // AGP — only if selected
      {
        queryKey: ['report-agp', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getAgp(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: wave2Enabled && isSelected('AGP'),
        staleTime: 5 * 60 * 1000,
      },
      // Daily Stats — only if selected
      {
        queryKey: ['report-daily-stats', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getDailyStats(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: wave2Enabled && isSelected('DAILY_STATS'),
        staleTime: 5 * 60 * 1000,
      },
      // Daily Trend — only if selected
      {
        queryKey: ['report-daily-trend', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getDailyTrend(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: wave2Enabled && isSelected('DAILY_TREND'),
        staleTime: 5 * 60 * 1000,
      },
      // Daily Charts (timeline) — only if selected
      {
        queryKey: ['report-daily-charts', userId, from, to] as const,
        queryFn: () => analyzeApi.getTimeline(userId, from, to).then(r => r.data),
        enabled: wave2Enabled && isSelected('DAILY_CHARTS'),
        staleTime: 5 * 60 * 1000,
      },
      // Glucose Distribution — only if selected
      {
        queryKey: ['report-glucose-distribution', userId, from, to, glucoseUnit] as const,
        queryFn: () =>
          analyzeApi.getGlucoseDistribution(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: wave2Enabled && isSelected('GLUCOSE_DISTRIBUTION'),
        staleTime: 5 * 60 * 1000,
      },
      // Profile — only if selected
      {
        queryKey: ['report-profile', userId, from, to] as const,
        queryFn: () => analyzeApi.getActiveProfiles(userId, from, to).then(r => r.data),
        enabled: wave2Enabled && isSelected('PROFILE'),
        staleTime: 5 * 60 * 1000,
      },
      // CGP — only if selected
      {
        queryKey: ['report-cgp', userId, from, to, glucoseUnit] as const,
        queryFn: () => analyzeApi.getCgp(userId, from, to, glucoseUnit).then(r => r.data),
        enabled: wave2Enabled && isSelected('CGP'),
        staleTime: 5 * 60 * 1000,
      },
    ],
  })

  const [reportSummaryQ, summaryQ] = wave1
  const [agpQ, dailyStatsQ, dailyTrendQ, dailyChartsQ, glucoseDistQ, profileQ, cgpQ] = wave2

  return {
    reportSummary: {
      data: reportSummaryQ.data ?? null,
      isLoading: reportSummaryQ.isLoading,
      isError: reportSummaryQ.isError,
    },
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
    isAnyLoading: [...wave1, ...wave2].some(r => r.isLoading),
  }
}
