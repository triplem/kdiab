/**
 * Report page identifiers. These strings are persisted in UserSettings.reportPageSelection.
 *
 * SUMMARY is always included and cannot be deselected by the user.
 */
export const REPORT_PAGE_IDS = [
  'SUMMARY',
  'AGP',
  'DAILY_STATS',
  'DAILY_TREND',
  'GLUCOSE_DISTRIBUTION',
  'PROFILE',
  'CGP',
] as const

export type ReportPageId = typeof REPORT_PAGE_IDS[number]

/** Pages the user can toggle (SUMMARY is always on) */
export const TOGGLEABLE_PAGE_IDS = REPORT_PAGE_IDS.filter(
  (id): id is Exclude<ReportPageId, 'SUMMARY'> => id !== 'SUMMARY',
)

export const ALL_PAGES_SELECTED: readonly ReportPageId[] = REPORT_PAGE_IDS

/** i18n key prefix — page labels are at `report.page.<id>` */
export function pageI18nKey(id: ReportPageId): string {
  return `report.page.${id}`
}
