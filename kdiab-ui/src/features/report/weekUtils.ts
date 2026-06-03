import type { DailyTrendDay } from '../../api/analyzeApi'

// ---------------------------------------------------------------------------
// Okabe-Ito colour-blind-safe 8-colour palette
// Assigned by day-of-week index: 0=Monday … 6=Sunday
// ---------------------------------------------------------------------------
export const OKABE_ITO = [
  '#E69F00', // Monday    — orange
  '#56B4E9', // Tuesday   — sky blue
  '#009E73', // Wednesday — bluish green
  '#F0E442', // Thursday  — yellow
  '#0072B2', // Friday    — blue
  '#D55E00', // Saturday  — vermillion
  '#CC79A7', // Sunday    — reddish purple
] as const

/**
 * Return the ISO day-of-week index for a YYYY-MM-DD date string.
 * Monday = 0 … Sunday = 6  (matches the Okabe-Ito palette assignment)
 */
export function isoWeekdayIndex(dateStr: string): number {
  const d = new Date(dateStr + 'T00:00:00')
  // getDay(): 0=Sunday … 6=Saturday  → remap to Mon=0…Sun=6
  return (d.getDay() + 6) % 7
}

/**
 * Return the ISO calendar week number (1–53) for a YYYY-MM-DD date string.
 * Uses the standard algorithm: the week containing Thursday belongs to the year.
 */
export function isoWeekNumber(dateStr: string): number {
  const d = new Date(dateStr + 'T00:00:00')
  // Set to nearest Thursday: current date + 4 − current ISO weekday
  const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() + 4 - dayOfWeek)
  const yearStart = new Date(d.getFullYear(), 0, 1)
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
}

/**
 * Return the ISO year for the week (may differ from calendar year for the
 * first/last days of January/December).
 */
export function isoWeekYear(dateStr: string): number {
  const d = new Date(dateStr + 'T00:00:00')
  const dayOfWeek = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() + 4 - dayOfWeek)
  return d.getFullYear()
}

/**
 * Group an array of DailyTrendDay entries by ISO calendar week.
 * Returns a map keyed by "YYYY-Www" (e.g. "2024-W03").
 */
export function groupByIsoWeek(days: DailyTrendDay[]): Map<string, DailyTrendDay[]> {
  const map = new Map<string, DailyTrendDay[]>()
  for (const day of days) {
    const week = isoWeekYear(day.date)
    const num = isoWeekNumber(day.date)
    const key = `${week}-W${String(num).padStart(2, '0')}`
    const existing = map.get(key)
    if (existing !== undefined) {
      existing.push(day)
    } else {
      map.set(key, [day])
    }
  }
  return map
}
