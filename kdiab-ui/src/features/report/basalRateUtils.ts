import type { ProfileSegment } from '../../api/analyzeApi'

/** One chart data point: minute-of-day + basal rate */
export interface BasalChartPoint {
  minute: number
  rate: number
}

/** One row in the segments table */
export interface BasalSegmentRow {
  startTime: string
  rate: number
  durationMinutes: number
  ie: number
}

/** Result of buildBasalChartData */
export interface BasalChartData {
  points: BasalChartPoint[]
  rows: BasalSegmentRow[]
  totalDailyIE: number
}

const TOTAL_MINUTES = 24 * 60

function toMinutes(time: string): number {
  const parts = time.split(':')
  const h = parseInt(parts[0] ?? '0', 10)
  const m = parseInt(parts[1] ?? '0', 10)
  return h * 60 + m
}

/**
 * Build the 24-hour step-chart data and segment table rows from profile segments.
 *
 * Pure function — no side effects. Exported separately from the component so it
 * can be unit-tested in isolation without rendering overhead.
 */
export function buildBasalChartData(segments: ProfileSegment[]): BasalChartData {
  if (segments.length === 0) {
    return { points: [], rows: [], totalDailyIE: 0 }
  }

  // Sort segments by startTime ascending
  const sorted = [...segments].sort((a, b) => a.startTime.localeCompare(b.startTime))

  const rows: BasalSegmentRow[] = sorted.map((seg, i) => {
    const start = toMinutes(seg.startTime)
    const nextSeg = sorted[i + 1]
    const end = nextSeg !== undefined ? toMinutes(nextSeg.startTime) : TOTAL_MINUTES
    const durationMinutes = end - start
    const ie = (seg.value * durationMinutes) / 60
    return {
      startTime: seg.startTime.slice(0, 5), // "HH:MM"
      rate: seg.value,
      durationMinutes,
      ie,
    }
  })

  const totalDailyIE = rows.reduce((sum, r) => sum + r.ie, 0)

  // Build step-chart points: one point per segment start, plus a closing point at 24:00
  const points: BasalChartPoint[] = sorted.map(seg => ({
    minute: toMinutes(seg.startTime),
    rate: seg.value,
  }))
  const lastSeg = sorted[sorted.length - 1]
  if (lastSeg !== undefined) {
    points.push({ minute: TOTAL_MINUTES, rate: lastSeg.value })
  }

  return { points, rows, totalDailyIE }
}

/** Format minute-of-day as HH:MM */
export function fmtMinute(minute: number): string {
  const h = Math.floor(minute / 60)
  const m = minute % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** Format duration in minutes as "Xh Ym" */
export function fmtDuration(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}min`
  if (m === 0) return `${h}h`
  return `${h}h ${m}min`
}
