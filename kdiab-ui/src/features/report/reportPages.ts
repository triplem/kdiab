/**
 * Report page identifiers. These strings are persisted in UserSettings.reportPageSelection.
 *
 * SUMMARY and AUSWERTUNG are always included and cannot be deselected by the user.
 * AUSWERTUNG is the comprehensive Auswertung page (report-summary endpoint).
 */
export const REPORT_PAGE_IDS = [
  'AUSWERTUNG',
  'SUMMARY',
  'AGP',
  'DAILY_STATS',
  'DAILY_TREND',
  'WOCHENGRAPHIK',
  'DAILY_CHARTS',
  'GLUCOSE_DISTRIBUTION',
  'PROFILE',
  'CGP',
  'BASAL_RATE',
] as const

export type ReportPageId = typeof REPORT_PAGE_IDS[number]

/** Pages the user can toggle (SUMMARY and AUSWERTUNG are always on) */
export const TOGGLEABLE_PAGE_IDS = REPORT_PAGE_IDS.filter(
  (id): id is Exclude<ReportPageId, 'SUMMARY' | 'AUSWERTUNG'> =>
    id !== 'SUMMARY' && id !== 'AUSWERTUNG',
)

export const ALL_PAGES_SELECTED: readonly ReportPageId[] = REPORT_PAGE_IDS

/** i18n key prefix — page labels are at `report.page.<id>` */
export function pageI18nKey(id: ReportPageId): string {
  return `report.page.${id}`
}
