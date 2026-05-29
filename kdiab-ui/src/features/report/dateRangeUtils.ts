/** Maximum date range accepted by the kdiab-analyze backend (> 365 days → 400 Bad Request) */
export const MAX_DATE_RANGE_DAYS = 365

export interface DateRange {
  from: string  // ISO-8601 datetime string, e.g. "2024-01-01T00:00:00Z"
  to: string    // ISO-8601 datetime string
  days: number  // difference in days
}

function diffDays(from: Date, to: Date): number {
  return Math.ceil((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24))
}

export function buildRange(fromDate: Date, toDate: Date): DateRange {
  return {
    from: fromDate.toISOString().slice(0, 10) + 'T00:00:00Z',
    to: toDate.toISOString().slice(0, 10) + 'T23:59:59Z',
    days: diffDays(fromDate, toDate),
  }
}

export function buildInitialRange(days = 14): DateRange {
  const to = new Date()
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000)
  return buildRange(from, to)
}
