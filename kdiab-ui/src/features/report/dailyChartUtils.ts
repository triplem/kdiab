import type { TimelineResponse } from '../../api/analyzeApi'

// ---- Constants ----------------------------------------------------------------

/** Maximum days to show in the daily chart section (limits report length). */
export const MAX_DAYS = 14

/** Gap threshold in milliseconds: breaks > this create a null separator in the CGM line. */
const GAP_THRESHOLD_MS = 20 * 60 * 1000

const MGDL_TO_MMOL = 1 / 18.0

// ---- Types --------------------------------------------------------------------

export interface CgmPoint {
  /** ms since epoch */
  ts: number
  /** Display value (already converted to the requested unit) */
  value: number | null
}

export interface TreatmentMarker {
  ts: number
  type: string
  /** Insulin units (bolus) */
  units?: number
  /** Carbohydrates in grams */
  carbsG?: number
  /** Display label shown on the chart */
  label: string
}

export interface DayData {
  /** ISO date string, e.g. "2024-11-15" */
  date: string
  cgmPoints: CgmPoint[]
  markers: TreatmentMarker[]
}

// ---- Utility helpers ----------------------------------------------------------

export function toDisplayValue(mgDl: number, glucoseUnit: string): number {
  return glucoseUnit === 'mmol/L'
    ? Math.round(mgDl * MGDL_TO_MMOL * 10) / 10
    : Math.round(mgDl)
}

/** Parse a raw measure value from the timeline response. Returns mg/dL or NaN. */
function parseMgDl(data: Record<string, unknown>): number {
  const raw = data['value']
  const val =
    typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN
  if (isNaN(val)) return NaN
  const unit = typeof data['unit'] === 'string' ? (data['unit'] as string) : 'mg/dL'
  return unit === 'mmol/L' ? val * 18.0 : val
}

/**
 * Build a CGM point array for one day that includes null separators at gaps
 * larger than GAP_THRESHOLD_MS. Recharts treats null y-values as a line break.
 */
function buildCgmPoints(
  rawPoints: { ts: number; mgDl: number }[],
  glucoseUnit: string,
): CgmPoint[] {
  if (rawPoints.length === 0) return []

  const sorted = [...rawPoints].sort((a, b) => a.ts - b.ts)
  const result: CgmPoint[] = []

  for (let i = 0; i < sorted.length; i++) {
    const cur = sorted[i]
    if (!cur) continue
    if (i > 0) {
      const prev = sorted[i - 1]
      if (prev && cur.ts - prev.ts > GAP_THRESHOLD_MS) {
        // Insert a null-value point at the midpoint to break the line
        result.push({ ts: Math.floor((prev.ts + cur.ts) / 2), value: null })
      }
    }
    result.push({ ts: cur.ts, value: toDisplayValue(cur.mgDl, glucoseUnit) })
  }

  return result
}

/** Build a human-readable label for a treatment marker. */
function buildTreatmentLabel(type: string, data: Record<string, unknown>): string {
  switch (type) {
    case 'BOLUS':
    case 'CORRECTION_BOLUS': {
      const units = typeof data['units'] === 'number' ? data['units'] : null
      return units !== null ? `↓${units.toFixed(1)}u` : '↓'
    }
    case 'CARBS': {
      const g = typeof data['carbsG'] === 'number' ? data['carbsG'] : null
      return g !== null ? `C${Math.round(g)}g` : 'C'
    }
    case 'SITE_CHANGE':
      return '⚙'
    case 'SENSOR_INSERT':
      return '◎'
    case 'INSULIN_CHANGE':
      return '💉'
    default:
      return '•'
  }
}

/**
 * Groups timeline measures and treatments by calendar day (local date of
 * treatedAt/measuredAt). Returns sorted array, newest first, capped at MAX_DAYS.
 */
export function groupByDay(
  timeline: TimelineResponse,
  glucoseUnit: string,
): DayData[] {
  // Collect CGM readings by local date
  const cgmByDate = new Map<string, { ts: number; mgDl: number }[]>()
  for (const m of timeline.measures) {
    if (m.type !== 'CGM') continue
    const mgDl = parseMgDl(m.data)
    if (isNaN(mgDl)) continue
    const ts = new Date(m.measuredAt).getTime()
    const date = m.measuredAt.slice(0, 10) // "YYYY-MM-DD" from ISO string
    const arr = cgmByDate.get(date) ?? []
    arr.push({ ts, mgDl })
    cgmByDate.set(date, arr)
  }

  // Collect treatment markers by local date
  const treatmentsByDate = new Map<string, TreatmentMarker[]>()
  for (const tr of timeline.treatments) {
    const ts = new Date(tr.treatedAt).getTime()
    const date = tr.treatedAt.slice(0, 10)
    const arr = treatmentsByDate.get(date) ?? []
    arr.push({
      ts,
      type: tr.type,
      ...(typeof tr.data['units'] === 'number' && { units: tr.data['units'] as number }),
      ...(typeof tr.data['carbsG'] === 'number' && { carbsG: tr.data['carbsG'] as number }),
      label: buildTreatmentLabel(tr.type, tr.data),
    })
    treatmentsByDate.set(date, arr)
  }

  // Build sorted day list (all dates that have CGM data)
  const allDates = new Set<string>([...cgmByDate.keys(), ...treatmentsByDate.keys()])
  const sortedDates = [...allDates].sort().reverse() // newest first
  const cappedDates = sortedDates.slice(0, MAX_DAYS) // limit to MAX_DAYS

  return cappedDates.map((date) => ({
    date,
    cgmPoints: buildCgmPoints(cgmByDate.get(date) ?? [], glucoseUnit),
    markers: (treatmentsByDate.get(date) ?? []).sort((a, b) => a.ts - b.ts),
  }))
}
