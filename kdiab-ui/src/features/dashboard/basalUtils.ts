// -- Constants ------------------------------------------------------------------

const MGDL_TO_MMOL = 1 / 18.0

const TREND_ARROWS: Record<string, string> = {
  DoubleUp: '↑↑', SingleUp: '↑', FortyFiveUp: '↗',
  Flat: '→', FortyFiveDown: '↘', SingleDown: '↓', DoubleDown: '↓↓',
}

export const BASAL_COLORS: Record<string, string> = {
  SCHEDULED: '#64748b',
  ABOVE:     '#22c55e',
  BELOW:     '#f59e0b',
  SUSPENDED: '#e2e8f0',
}

export const STALE_WARN_MS = 15 * 60 * 1000  // 15 minutes
export const STALE_ERROR_MS = 30 * 60 * 1000 // 30 minutes

export const WINDOWS: { key: string; hours: number; label: string }[] = [
  { key: '2h', hours: 2, label: '2h' },
  { key: '4h', hours: 4, label: '4h' },
  { key: '6h', hours: 6, label: '6h' },
  { key: '12h', hours: 12, label: '12h' },
  { key: '24h', hours: 24, label: '24h' },
]

export interface BasalBlock {
  startMs: number
  endMs: number
  deliveredRate: number
  scheduledRate: number
  state: 'SCHEDULED' | 'ABOVE' | 'BELOW' | 'SUSPENDED'
}

export interface BasalProfilePoint {
  time: number
  sched: number
}

// -- Pure helper functions ------------------------------------------------------

export function toDisplay(mgdl: number, unit: string): number {
  return unit === 'mmol/L'
    ? Math.round(mgdl * MGDL_TO_MMOL * 10) / 10
    : Math.round(mgdl)
}

export function glucoseColor(mgdl: number): string {
  if (mgdl < 70) return 'var(--accent-danger)'
  if (mgdl > 180) return 'var(--accent-warning)'
  return 'var(--accent-success)'
}

export function trendArrow(trend: unknown): string {
  return typeof trend === 'string' ? (TREND_ARROWS[trend] ?? '') : ''
}

export function daysSince(iso: string | undefined): string {
  if (!iso) return '—'
  const d = (Date.now() - new Date(iso).getTime()) / 86400000
  return `${d.toFixed(1)} d`
}

export function sensorExpiryLabel(insertedAt: string | undefined, durationHours: number): string {
  if (!insertedAt) return '—'
  const remainingMs = new Date(insertedAt).getTime() + durationHours * 3600000 - Date.now()
  const remainingHours = Math.round(remainingMs / 3600000)
  return remainingHours <= 0 ? 'expired' : `exp ${remainingHours}h`
}

// Parabolic (Scheiner) IOB decay over DIA window
export function calcIOB(
  treatments: Array<{ treatedAt: string; type: string; data: Record<string, unknown> }>,
  diaMinutes: number,
): number {
  const now = Date.now()
  let iob = 0
  for (const t of treatments) {
    if (t.type !== 'BOLUS' && t.type !== 'CORRECTION_BOLUS' && t.type !== 'COMBO_BOLUS' && t.type !== 'MEAL') continue
    const min = (now - new Date(t.treatedAt).getTime()) / 60000
    if (min < 0 || min > diaMinutes) continue
    const insulin = typeof t.data['insulin'] === 'number' ? t.data['insulin'] : 0
    iob += insulin * (1 - Math.pow(min / diaMinutes, 2))
  }
  return Math.max(0, iob)
}

// Linear COB decay over absorption window (absorptionTime field, default 180 min)
export function calcCOB(
  treatments: Array<{ treatedAt: string; type: string; data: Record<string, unknown> }>,
): number {
  const now = Date.now()
  let cob = 0
  for (const t of treatments) {
    if (t.type !== 'CARBS' && t.type !== 'MEAL' && t.type !== 'HYPO_TREATMENT') continue
    const min = (now - new Date(t.treatedAt).getTime()) / 60000
    const absorb = typeof t.data['absorptionTime'] === 'number' ? t.data['absorptionTime'] * 60 : 180
    if (min < 0 || min > absorb) continue
    const carbs = typeof t.data['carbs'] === 'number' ? t.data['carbs'] : 0
    cob += carbs * (1 - min / absorb)
  }
  return Math.max(0, cob)
}

export function segToMin(t: string): number {
  const [h = 0, m = 0] = t.split(':').map(Number)
  return h * 60 + m
}

// Scheduled basal rate from profile at a given timestamp
export function scheduledRateAt(
  basal: Array<{ startTime: string; value: number }>,
  ms: number,
): number {
  const sorted = [...basal].sort((a, b) => segToMin(a.startTime) - segToMin(b.startTime))
  const d = new Date(ms)
  const nowMin = d.getHours() * 60 + d.getMinutes()
  let rate = sorted[sorted.length - 1]?.value ?? 0
  for (const seg of sorted) {
    if (segToMin(seg.startTime) <= nowMin) rate = seg.value
  }
  return rate
}

// Reconstruct delivered basal blocks from profile + TEMP_BASAL/PUMP_SUSPEND treatments
export function reconstructBasalBlocks(
  fromMs: number,
  toMs: number,
  basal: Array<{ startTime: string; value: number }>,
  treatments: Array<{ treatedAt: string; type: string; data: Record<string, unknown> }>,
): BasalBlock[] {
  if (!basal.length) return []

  const bounds = new Set<number>([fromMs, toMs])

  // Profile segment change timestamps (per calendar day)
  for (let d = Math.floor(fromMs / 86400000) * 86400000; d <= toMs; d += 86400000) {
    for (const seg of basal) {
      const ms = d + segToMin(seg.startTime) * 60000
      if (ms > fromMs && ms < toMs) bounds.add(ms)
    }
  }

  // TEMP_BASAL and PUMP_SUSPEND periods
  type TempPeriod = { startMs: number; endMs: number; rate: number; absolute: boolean; isSuspend: boolean }
  const tempPeriods: TempPeriod[] = []
  for (const t of treatments) {
    if (t.type !== 'TEMP_BASAL' && t.type !== 'PUMP_SUSPEND') continue
    const startMs = new Date(t.treatedAt).getTime()
    const durMin = typeof t.data['duration'] === 'number' ? (t.data['duration'] as number) : 0
    const endMs = startMs + durMin * 60000
    if (endMs <= fromMs || startMs >= toMs) continue
    const cs = Math.max(startMs, fromMs)
    const ce = Math.min(endMs, toMs)
    bounds.add(cs)
    bounds.add(ce)
    tempPeriods.push({
      startMs: cs, endMs: ce,
      rate: typeof t.data['rate'] === 'number' ? (t.data['rate'] as number) : 0,
      absolute: t.data['absolute'] === true,
      isSuspend: t.type === 'PUMP_SUSPEND',
    })
  }

  const sortedBounds = [...bounds].sort((a, b) => a - b)
  const raw: BasalBlock[] = []

  for (let i = 0; i < sortedBounds.length - 1; i++) {
    const startMs = sortedBounds[i]!
    const endMs = sortedBounds[i + 1]!
    const sched = scheduledRateAt(basal, (startMs + endMs) / 2)

    // Latest-started active period wins
    const active = tempPeriods
      .filter(p => p.startMs <= startMs && p.endMs >= endMs)
      .sort((a, b) => b.startMs - a.startMs)[0]

    let delivered: number
    let state: BasalBlock['state']
    if (!active) {
      delivered = sched; state = 'SCHEDULED'
    } else if (active.isSuspend) {
      delivered = 0; state = 'SUSPENDED'
    } else {
      delivered = active.absolute ? active.rate : sched * (active.rate / 100)
      state = Math.abs(delivered - sched) < 0.001 ? 'SCHEDULED'
           : delivered > sched ? 'ABOVE' : 'BELOW'
    }
    raw.push({ startMs, endMs, deliveredRate: delivered, scheduledRate: sched, state })
  }

  // Merge consecutive same-state blocks
  const merged: BasalBlock[] = []
  for (const b of raw) {
    const last = merged[merged.length - 1]
    if (last && last.state === b.state && Math.abs(last.deliveredRate - b.deliveredRate) < 0.001) {
      last.endMs = b.endMs
    } else {
      merged.push({ ...b })
    }
  }
  return merged
}

// Current basal rate from active profile
export function currentBasalRate(basal: Array<{ startTime: string; value: number }> | undefined): number | null {
  if (!basal?.length) return null
  return scheduledRateAt(basal, Date.now())
}
