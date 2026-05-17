import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useState, useMemo } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { profilesApi } from '../../api/profilesApi'
import { treatmentsApi } from '../../api/treatmentsApi'
import { usersApi } from '../../api/usersApi'
import { DeviceStatusWidget } from '../treatments/DeviceStatusWidget'
import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceArea,
  ResponsiveContainer,
} from 'recharts'

interface Props {
  userId: string
  glucoseUnit: string
}

// ── Constants ──────────────────────────────────────────────────────────────────

const MGDL_TO_MMOL = 1 / 18.0

const WINDOWS: { key: string; hours: number; label: string }[] = [
  { key: '2h', hours: 2, label: '2h' },
  { key: '4h', hours: 4, label: '4h' },
  { key: '6h', hours: 6, label: '6h' },
  { key: '12h', hours: 12, label: '12h' },
  { key: '24h', hours: 24, label: '24h' },
]

const TREND_ARROWS: Record<string, string> = {
  DoubleUp: '↑↑', SingleUp: '↑', FortyFiveUp: '↗',
  Flat: '→', FortyFiveDown: '↘', SingleDown: '↓', DoubleDown: '↓↓',
}

const BASAL_COLORS: Record<string, string> = {
  SCHEDULED: '#64748b',
  ABOVE:     '#22c55e',
  BELOW:     '#f59e0b',
  SUSPENDED: '#e2e8f0',
}

interface BasalBlock {
  startMs: number
  endMs: number
  deliveredRate: number
  scheduledRate: number
  state: 'SCHEDULED' | 'ABOVE' | 'BELOW' | 'SUSPENDED'
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function toDisplay(mgdl: number, unit: string): number {
  return unit === 'mmol/L'
    ? Math.round(mgdl * MGDL_TO_MMOL * 10) / 10
    : Math.round(mgdl)
}

function glucoseColor(mgdl: number): string {
  if (mgdl < 70) return 'var(--accent-danger)'
  if (mgdl > 180) return 'var(--accent-warning)'
  return 'var(--accent-success)'
}

function trendArrow(trend: unknown): string {
  return typeof trend === 'string' ? (TREND_ARROWS[trend] ?? '') : ''
}

function daysSince(iso: string | undefined): string {
  if (!iso) return '—'
  const d = (Date.now() - new Date(iso).getTime()) / 86400000
  return `${d.toFixed(1)} d`
}

function sensorExpiryLabel(insertedAt: string | undefined, durationHours: number): string {
  if (!insertedAt) return '—'
  const remainingMs = new Date(insertedAt).getTime() + durationHours * 3600000 - Date.now()
  const remainingHours = Math.round(remainingMs / 3600000)
  return remainingHours <= 0 ? 'expired' : `exp ${remainingHours}h`
}

// Linear IOB decay over DIA window
function calcIOB(
  treatments: Array<{ treatedAt: string; type: string; data: Record<string, unknown> }>,
  diaMinutes: number,
): number {
  const now = Date.now()
  let iob = 0
  for (const t of treatments) {
    if (t.type !== 'BOLUS' && t.type !== 'CORRECTION_BOLUS' && t.type !== 'MEAL') continue
    const min = (now - new Date(t.treatedAt).getTime()) / 60000
    if (min < 0 || min > diaMinutes) continue
    const insulin = typeof t.data['insulin'] === 'number' ? t.data['insulin'] : 0
    iob += insulin * (1 - min / diaMinutes)
  }
  return Math.max(0, iob)
}

// Linear COB decay over absorption window (absorptionTime field, default 180 min)
function calcCOB(
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

function segToMin(t: string): number {
  const [h = 0, m = 0] = t.split(':').map(Number)
  return h * 60 + m
}

// Scheduled basal rate from profile at a given timestamp
function scheduledRateAt(
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
function currentBasalRate(basal: Array<{ startTime: string; value: number }> | undefined): number | null {
  if (!basal?.length) return null
  const rate = scheduledRateAt(basal, Date.now())
  return rate > 0 ? rate : null
}

// ── Sub-components ─────────────────────────────────────────────────────────────

interface StatTileProps {
  label: string
  value: string
  sub?: string
  color?: string
}

function StatTile({ label, value, sub, color }: StatTileProps) {
  return (
    <div className="card" style={{ padding: '0.75rem 1rem', minWidth: '110px', textAlign: 'center', flex: '1 1 110px' }}>
      <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.2rem' }}>
        {label}
      </div>
      <div style={{ fontSize: '1.5rem', fontWeight: 700, color: color ?? 'var(--text-primary)', lineHeight: 1.1 }}>
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)', marginTop: '0.1rem' }}>
          {sub}
        </div>
      )}
    </div>
  )
}

function treatmentAppearance(type: string): { color: string; shape: string } {
  if (type === 'BOLUS' || type === 'CORRECTION_BOLUS') return { color: '#3b82f6', shape: '▲' }
  if (type === 'CARBS' || type === 'MEAL')             return { color: '#f59e0b', shape: '●' }
  if (type === 'SITE_CHANGE')                          return { color: '#10b981', shape: '⊕' }
  if (type === 'SENSOR_INSERT')                        return { color: '#8b5cf6', shape: '◆' }
  if (type === 'INSULIN_CHANGE')                       return { color: '#ec4899', shape: '◈' }
  return { color: '#6366f1', shape: '▼' }
}

// Treatment marker shape — `unknown` satisfies Recharts' contravariant dot prop type; cast internally.
function TreatmentDot(props: unknown) {
  const p = props as Record<string, unknown>
  const cx = (p['cx'] as number) ?? 0
  const cy = (p['cy'] as number) ?? 0
  const payload = p['payload'] as { treatmentType?: string; label?: string } | undefined
  const { color, shape } = treatmentAppearance(payload?.treatmentType ?? '')
  const label = payload?.label ?? ''

  // Spread Recharts-injected event handlers so the shared Tooltip fires on hover
  const eventProps: Record<string, unknown> = {}
  for (const key of Object.keys(p)) {
    if (key.startsWith('on')) eventProps[key] = p[key]
  }

  return (
    <g {...eventProps} style={{ cursor: 'pointer' }}>
      <circle cx={cx} cy={cy} r={14} fill="transparent" pointerEvents="all" />
      <text x={cx} y={cy - 4} textAnchor="middle" fill={color} fontSize={12}>{shape}</text>
      {label && <text x={cx} y={cy + 14} textAnchor="middle" fill={color} fontSize={9}>{label}</text>}
    </g>
  )
}

function TreatmentActiveDot(props: unknown) {
  const p = props as Record<string, unknown>
  const cx = (p['cx'] as number) ?? 0
  const cy = (p['cy'] as number) ?? 0
  const payload = p['payload'] as { treatmentType?: string; label?: string } | undefined
  const { color, shape } = treatmentAppearance(payload?.treatmentType ?? '')
  const label = payload?.label ?? ''

  return (
    <g style={{ cursor: 'pointer' }}>
      <circle cx={cx} cy={cy} r={16} fill={color} fillOpacity={0.2} stroke={color} strokeWidth={1} />
      <text x={cx} y={cy - 4} textAnchor="middle" fill={color} fontSize={15}>{shape}</text>
      {label && <text x={cx} y={cy + 16} textAnchor="middle" fill={color} fontSize={9}>{label}</text>}
    </g>
  )
}

// ── Main Component ─────────────────────────────────────────────────────────────

export function DashboardView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const { formatTime } = useTimeFormat()

  const [windowKey, setWindowKey] = useState('6h')
  const [windowEndOffset, setWindowEndOffset] = useState(0) // ms shift from "now" (0 = current)

  const windowHours = WINDOWS.find(w => w.key === windowKey)?.hours ?? 6
  const windowMs = windowHours * 60 * 60 * 1000

  const { windowEnd, windowFrom, windowTo } = useMemo(() => {
    const end = new Date(Date.now() - windowEndOffset)
    const start = new Date(end.getTime() - windowMs)
    return {
      windowEnd: end,
      windowFrom: start.toISOString(),
      windowTo: end.toISOString(),
    }
  }, [windowMs, windowEndOffset])

  const atNow = windowEndOffset === 0

  // Fetch 6h of data for IOB/COB (always back from now)
  const sixHoursAgo = useMemo(() => new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString(), [])
  const nowIso = useMemo(() => new Date().toISOString(), [])

  const { data: recentTimeline } = useQuery({
    queryKey: ['dashboard-recent', userId],
    queryFn: () => analyzeApi.getTimeline(userId, sixHoursAgo, nowIso).then(r => r.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
  })

  // Fetch windowed data for chart
  const { data: windowTimeline, isLoading } = useQuery({
    queryKey: ['dashboard-window', userId, windowFrom, windowTo],
    queryFn: () => analyzeApi.getTimeline(userId, windowFrom, windowTo).then(r => r.data),
    enabled: !!userId,
    staleTime: 2 * 60 * 1000,
  })

  const { data: profiles } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => profilesApi.listProfiles(userId).then(r => r.data.items),
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  const activeProfile = profiles?.find(p => p.status === 'ACTIVE')
  const diaMinutes = activeProfile?.durationOfAction ?? 240

  // ── CGM derived values (from recent 6h) ────────────────────────────────────

  const recentCgm = (recentTimeline?.measures ?? [])
    .filter(m => m.type === 'CGM')
    .sort((a, b) => new Date(b.measuredAt).getTime() - new Date(a.measuredAt).getTime())

  const latestCgm = recentCgm[0]
  const latestSgv = typeof latestCgm?.data['value'] === 'number' ? latestCgm.data['value'] : null
  const latestTrend = latestCgm?.data['trend']

  // Δ: difference from previous reading
  const prevSgv = typeof recentCgm[1]?.data['value'] === 'number' ? recentCgm[1].data['value'] as number : null
  const delta = latestSgv !== null && prevSgv !== null ? toDisplay(latestSgv - prevSgv, glucoseUnit) : null

  // Δ15: difference from reading closest to 15 min ago (±10 min tolerance)
  const target15ms = latestCgm ? new Date(latestCgm.measuredAt).getTime() - 15 * 60 * 1000 : 0
  const reading15 = recentCgm.slice(1).reduce<(typeof recentCgm)[0] | null>((best, r) => {
    const diff = Math.abs(new Date(r.measuredAt).getTime() - target15ms)
    const bestDiff = best ? Math.abs(new Date(best.measuredAt).getTime() - target15ms) : Infinity
    return diff < bestDiff ? r : best
  }, null)
  const within10min = reading15 && Math.abs(new Date(reading15.measuredAt).getTime() - target15ms) <= 10 * 60 * 1000
  const sgv15 = within10min && typeof reading15?.data['value'] === 'number' ? reading15.data['value'] as number : null
  const delta15 = latestSgv !== null && sgv15 !== null ? toDisplay(latestSgv - sgv15, glucoseUnit) : null

  const minutesAgo = latestCgm
    ? Math.round((Date.now() - new Date(latestCgm.measuredAt).getTime()) / 60000)
    : null

  // ── IOB / COB (from recent treatments) ─────────────────────────────────────

  const iob = calcIOB(recentTimeline?.treatments ?? [], diaMinutes)
  const cob = calcCOB(recentTimeline?.treatments ?? [])
  const basalRate = currentBasalRate(activeProfile?.basal)

  // ── Device ages (fetched from server — no time-window limit) ───────────────

  const { data: deviceAge } = useQuery({
    queryKey: ['device-age', userId],
    queryFn: () => treatmentsApi.getDeviceAge(userId).then(r => r.data),
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  const { data: deviceStatus } = useQuery({
    queryKey: ['device-status', userId],
    queryFn: () => treatmentsApi.getLatestDeviceStatus(userId).then(r => r.data).catch(() => null),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })

  const { data: userMe } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: () => usersApi.getMe().then(r => r.data),
    staleTime: 10 * 60 * 1000,
  })

  const catheterDate = deviceAge?.catheterChangedAt ?? undefined
  const reservoirDate = deviceAge?.reservoirChangedAt ?? undefined
  const sensorDate = deviceAge?.sensorInsertedAt ?? undefined
  const sensorDurationHours = userMe?.settings?.sensorDurationHours ?? 240
  const batteryLevel = deviceStatus?.batteryLevel ?? null

  // ── Chart data ──────────────────────────────────────────────────────────────

  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'
  const tirLow = toDisplay(70, glucoseUnit)
  const tirHigh = toDisplay(180, glucoseUnit)

  const cgmPoints = (windowTimeline?.measures ?? [])
    .filter(m => m.type === 'CGM' && typeof m.data['value'] === 'number')
    .map(m => ({
      time: new Date(m.measuredAt).getTime(),
      sgv: toDisplay(m.data['value'] as number, glucoseUnit),
      bgm: null as number | null,
      marker: null as number | null,
      treatmentType: null as string | null,
      label: null as string | null,
    }))

  // BGM readings shown as distinct dots above the treatment marker row
  const bgmPoints = (windowTimeline?.measures ?? [])
    .filter(m => m.type === 'BGM' && typeof m.data['value'] === 'number')
    .map(m => ({
      time: new Date(m.measuredAt).getTime(),
      sgv: null as number | null,
      bgm: toDisplay(m.data['value'] as number, glucoseUnit),
      marker: null as number | null,
      treatmentType: null as string | null,
      label: null as string | null,
    }))

  // Treatment markers: merge into same point shape as cgmPoints, at bottom of TIR range
  const treatmentMarkers = (windowTimeline?.treatments ?? [])
    .filter(t => ['BOLUS', 'CORRECTION_BOLUS', 'CARBS', 'MEAL', 'SITE_CHANGE', 'SENSOR_INSERT', 'INSULIN_CHANGE'].includes(t.type))
    .map(t => {
      let label = ''
      if ((t.type === 'BOLUS' || t.type === 'CORRECTION_BOLUS') && typeof t.data['insulin'] === 'number')
        label = `${(t.data['insulin'] as number).toFixed(1)}U`
      else if ((t.type === 'CARBS' || t.type === 'MEAL') && typeof t.data['carbs'] === 'number')
        label = `${Math.round(t.data['carbs'] as number)}g`
      return {
        time: new Date(t.treatedAt).getTime(),
        sgv: null as number | null,
        bgm: null as number | null,
        marker: tirLow * 0.85,
        treatmentType: t.type,
        label,
      }
    })

  // Combined dataset for ComposedChart root — enables tooltip cursor to find points
  const chartData = [...cgmPoints, ...bgmPoints, ...treatmentMarkers].sort((a, b) => a.time - b.time)

  // Basal block reconstruction
  const { basalBlocks, basalProfileLine } = useMemo(() => {
    const basal = activeProfile?.basal
    if (!basal?.length) return { basalBlocks: [], basalProfileLine: [] }
    const fromMs = new Date(windowFrom).getTime()
    const toMs = new Date(windowTo).getTime()
    const blocks = reconstructBasalBlocks(fromMs, toMs, basal, windowTimeline?.treatments ?? [])

    // Stepped profile rate line for dashed overlay
    const sorted = [...basal].sort((a, b) => segToMin(a.startTime) - segToMin(b.startTime))
    const line: Array<{ time: number; sched: number }> = [
      { time: fromMs, sched: scheduledRateAt(sorted, fromMs) },
    ]
    for (let d = Math.floor(fromMs / 86400000) * 86400000; d <= toMs; d += 86400000) {
      for (const seg of sorted) {
        const ms = d + segToMin(seg.startTime) * 60000
        if (ms > fromMs && ms < toMs) {
          line.push({ time: ms - 1, sched: line[line.length - 1]!.sched })
          line.push({ time: ms, sched: seg.value })
        }
      }
    }
    line.push({ time: toMs, sched: line[line.length - 1]!.sched })
    return { basalBlocks: blocks, basalProfileLine: line }
  }, [activeProfile?.basal, windowFrom, windowTo, windowTimeline?.treatments])

  // ── Render ──────────────────────────────────────────────────────────────────

  const deltaColor = (d: number | null) => {
    if (d === null) return 'var(--text-secondary)'
    const abs = Math.abs(d)
    if (abs < (glucoseUnit === 'mmol/L' ? 0.5 : 10)) return 'var(--accent-success)'
    if (abs < (glucoseUnit === 'mmol/L' ? 1.5 : 25)) return 'var(--accent-warning)'
    return 'var(--accent-danger)'
  }

  const formatDelta = (d: number | null): string => {
    if (d === null) return '—'
    return `${d > 0 ? '+' : ''}${d} ${yLabel}`
  }

  return (
    <div>
      <DeviceStatusWidget userId={userId} />

      {/* ── Glucose hero ─────────────────────────────────────────────────── */}
      {latestSgv !== null && (
        <div className="card" style={{ padding: '1rem 1.25rem', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.75rem', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '3rem', fontWeight: 800, lineHeight: 1, color: glucoseColor(latestSgv) }}>
              {toDisplay(latestSgv, glucoseUnit)}
            </span>
            <span style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>{yLabel}</span>
            <span style={{ fontSize: '2rem' }}>{trendArrow(latestTrend)}</span>
            <span style={{ fontSize: '0.95rem', color: deltaColor(delta) }}>
              {t('dashboard.delta', { defaultValue: 'Δ' })}: {formatDelta(delta)}
            </span>
            <span style={{ fontSize: '0.95rem', color: deltaColor(delta15) }}>
              {t('dashboard.delta15', { defaultValue: 'Δ15' })}: {formatDelta(delta15)}
            </span>
            {minutesAgo !== null && (
              <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginLeft: 'auto' }}>
                {minutesAgo} {t('dashboard.minAgo', { defaultValue: 'min ago' })}
              </span>
            )}
          </div>
        </div>
      )}

      {/* ── Stat tiles row 1: IOB / COB / Basal / DIA ────────────────────── */}
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
        <StatTile label={t('dashboard.iob', { defaultValue: 'IOB' })} value={`${iob.toFixed(1)} U`} sub={t('dashboard.insulinOnBoard', { defaultValue: 'Insulin on Board' })} />
        <StatTile label={t('dashboard.cob', { defaultValue: 'COB' })} value={`${Math.round(cob)} g`} sub={t('dashboard.carbsOnBoard', { defaultValue: 'Carbs on Board' })} />
        {basalRate !== null && <StatTile label={t('dashboard.basal', { defaultValue: 'Basal' })} value={`${basalRate.toFixed(2)} U/h`} sub={t('dashboard.currentBasal', { defaultValue: 'Current Rate' })} />}
        {activeProfile && <StatTile label={t('dashboard.dia', { defaultValue: 'DIA' })} value={`${Math.round(diaMinutes / 60)} h`} sub={activeProfile.insulinType ?? ''} />}
        {activeProfile && <StatTile label={t('dashboard.profile', { defaultValue: 'Profile' })} value={activeProfile.name} />}
      </div>

      {/* ── Stat tiles row 2: device ages + battery ───────────────────────── */}
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
        <StatTile label={t('dashboard.catheter', { defaultValue: 'Catheter' })} value={daysSince(catheterDate)} sub={t('dashboard.age', { defaultValue: 'age' })} />
        <StatTile label={t('dashboard.reservoir', { defaultValue: 'Reservoir' })} value={daysSince(reservoirDate)} sub={t('dashboard.age', { defaultValue: 'age' })} />
        <StatTile
          label={t('dashboard.sensor', { defaultValue: 'Sensor' })}
          value={daysSince(sensorDate)}
          sub={sensorExpiryLabel(sensorDate, sensorDurationHours)}
        />
        {batteryLevel !== null && (
          <StatTile label={t('dashboard.battery', { defaultValue: 'Battery' })} value={`${batteryLevel} %`} sub={t('dashboard.pumpBattery', { defaultValue: 'pump' })} />
        )}
      </div>

      {/* ── Time window selector ──────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
        <button
          onClick={() => setWindowEndOffset(o => o + windowMs / 2)}
          style={{ padding: '0.3em 0.7em', fontSize: '0.9rem' }}
          title={t('dashboard.prev', { defaultValue: 'Earlier' })}
        >
          ←
        </button>
        {WINDOWS.map(w => (
          <button
            key={w.key}
            onClick={() => { setWindowKey(w.key); setWindowEndOffset(0) }}
            className={windowKey === w.key ? 'primary' : 'btn outline'}
            style={{ padding: '0.3em 0.8em', fontSize: '0.9rem' }}
          >
            {w.label}
          </button>
        ))}
        <button
          onClick={() => setWindowEndOffset(o => Math.max(0, o - windowMs / 2))}
          disabled={atNow}
          style={{ padding: '0.3em 0.7em', fontSize: '0.9rem' }}
          title={t('dashboard.next', { defaultValue: 'Later' })}
        >
          →
        </button>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginLeft: '0.25rem' }}>
          {windowEnd.toLocaleDateString(navigator.language, { weekday: 'short', day: 'numeric', month: 'short' })}
        </span>
      </div>

      {/* ── Combined glucose + treatment markers chart ────────────────────── */}
      <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
        <h3 style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1rem' }}>
          {t('dashboard.cgmChart')}
        </h3>
        {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
        {cgmPoints.length > 0 && (
          <ResponsiveContainer width="100%" height={220}>
            <ComposedChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="time"
                type="number"
                domain={[new Date(windowFrom).getTime(), new Date(windowTo).getTime()]}
                tickFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
                tick={{ fontSize: 11 }}
                scale="time"
              />
              <YAxis
                domain={[glucoseUnit === 'mmol/L' ? 2 : 40, glucoseUnit === 'mmol/L' ? 18 : 330]}
                tick={{ fontSize: 11 }}
                label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 15, style: { fontSize: 11 }, fill: 'var(--text-secondary)' }}
              />
              <Tooltip
                labelFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
                formatter={(v: unknown, name: string, entry: { payload?: { treatmentType?: string; label?: string } }) => {
                  if (name === 'sgv' && typeof v === 'number') return [`${v} ${yLabel}`, 'CGM']
                  if (name === 'bgm' && typeof v === 'number') return [`${v} ${yLabel}`, 'BGM']
                  if (name === 'marker') {
                    const ttype = entry.payload?.treatmentType ?? ''
                    const lbl = entry.payload?.label ?? ''
                    return [lbl || ttype, ttype]
                  }
                  return [`${String(v)}`, name]
                }}
                contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
                wrapperStyle={{ outline: 'none' }}
              />
              <ReferenceLine y={tirLow} stroke="#ef4444" strokeDasharray="4 4" />
              <ReferenceLine y={tirHigh} stroke="#f59e0b" strokeDasharray="4 4" />
              <Line
                type="monotone"
                dataKey="sgv"
                stroke="var(--chart-median)"
                dot={false}
                strokeWidth={2}
                isAnimationActive={false}
                connectNulls={true}
              />
              {bgmPoints.length > 0 && (
                <Line
                  dataKey="bgm"
                  name="bgm"
                  stroke="none"
                  strokeWidth={0}
                  dot={{ fill: '#ef4444', stroke: '#fff', strokeWidth: 1.5, r: 5 }}
                  activeDot={{ fill: '#ef4444', stroke: '#fff', strokeWidth: 1.5, r: 7 }}
                  isAnimationActive={false}
                  connectNulls={false}
                />
              )}
              {treatmentMarkers.length > 0 && (
                <Line
                  dataKey="marker"
                  name="marker"
                  stroke="none"
                  strokeWidth={0}
                  dot={(props: object) => <TreatmentDot {...props} />}
                  activeDot={(props: object) => <TreatmentActiveDot {...props} />}
                  isAnimationActive={false}
                  connectNulls={false}
                />
              )}
            </ComposedChart>
          </ResponsiveContainer>
        )}
        {!isLoading && cgmPoints.length === 0 && (
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            {t('dashboard.noData')}
          </p>
        )}
        {/* Legend */}
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
          <span><span style={{ color: '#ef4444' }}>●</span> {t('dashboard.legendBgm')}</span>
          <span><span style={{ color: '#3b82f6' }}>▲</span> {t('dashboard.legendBolus')}</span>
          <span><span style={{ color: '#f59e0b' }}>●</span> {t('dashboard.legendCarbs')}</span>
          <span><span style={{ color: '#10b981' }}>⊕</span> {t('dashboard.legendSiteChange')}</span>
          <span><span style={{ color: '#8b5cf6' }}>◆</span> {t('dashboard.legendSensorInsert')}</span>
          <span><span style={{ color: '#ec4899' }}>◈</span> {t('dashboard.legendInsulinChange')}</span>
        </div>
      </div>

      {/* ── Basal block chart ─────────────────────────────────────────────── */}
      <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
        <h3 style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1rem' }}>
          {t('dashboard.basalChart')}
        </h3>
        {!activeProfile && (
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            {t('dashboard.noProfile')}
          </p>
        )}
        {activeProfile && (
          <>
            <ResponsiveContainer width="100%" height={120}>
              <ComposedChart data={basalProfileLine} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="time"
                  type="number"
                  domain={[new Date(windowFrom).getTime(), new Date(windowTo).getTime()]}
                  tickFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
                  tick={{ fontSize: 11 }}
                  scale="time"
                />
                <YAxis
                  tick={{ fontSize: 11 }}
                  label={{ value: 'U/h', angle: -90, position: 'insideLeft', offset: 15, style: { fontSize: 11 }, fill: 'var(--text-secondary)' }}
                  domain={[0, 'auto']}
                />
                {basalBlocks.map((block, i) => (
                  <ReferenceArea
                    key={i}
                    x1={block.startMs}
                    x2={block.endMs}
                    y1={0}
                    y2={block.deliveredRate}
                    fill={BASAL_COLORS[block.state] ?? BASAL_COLORS['SCHEDULED']!}
                    fillOpacity={0.75}
                    stroke="none"
                    ifOverflow="extendDomain"
                  />
                ))}
                <Line
                  dataKey="sched"
                  stroke="var(--text-secondary)"
                  strokeDasharray="6 3"
                  strokeWidth={1.5}
                  dot={false}
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
            {/* Legend */}
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
              <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['SCHEDULED'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendScheduled')}</span>
              <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['ABOVE'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendAbove')}</span>
              <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['BELOW'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendBelow')}</span>
              <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['SUSPENDED'], border: '1px solid var(--border)', borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendSuspended')}</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
