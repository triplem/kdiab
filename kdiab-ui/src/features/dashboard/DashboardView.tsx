import { useTranslation } from 'react-i18next'
import { useMemo } from 'react'
import { DeviceStatusWidget } from '../treatments/DeviceStatusWidget'
import { DeviceUsageCard } from './DeviceUsageCard'
import { GlucoseHeroTile } from './GlucoseHeroTile'
import { GlucoseTrendChart } from './GlucoseTrendChart'
import { BasalRateChart } from './BasalRateChart'
import { useDashboardData } from './useDashboardData'
import {
  toDisplay,
  calcIOB,
  calcCOB,
  currentBasalRate,
  sensorExpiryLabel,
  daysSince,
  reconstructBasalBlocks,
  buildBasalProfileLine,
  STALE_WARN_MS,
  STALE_ERROR_MS,
  WINDOWS,
} from './basalUtils'

interface Props {
  userId: string
  glucoseUnit: string
}

interface StatTileProps {
  label: string
  value: string
  sub?: string
  color?: string
}

type ChartPoint = {
  time: number
  sgv: number | null
  bgm: number | null
  marker: number | null
  treatmentType: string | null
  label: string | null
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

export function DashboardView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()

  const {
    windowKey,
    setWindowKey,
    setWindowEndOffset,
    windowMs,
    windowEnd,
    windowFrom,
    windowTo,
    atNow,
    recentTimeline,
    windowTimeline,
    isLoading,
    activeProfile,
    deviceAge,
    deviceStatus,
    userMe,
  } = useDashboardData(userId)

  const diaMinutes = activeProfile?.durationOfAction ?? 240

  // -- CGM derived values (from recent 6h) ------------------------------------

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

  // -- CGM staleness ----------------------------------------------------------

  const staleMs = latestCgm ? Date.now() - new Date(latestCgm.measuredAt).getTime() : 0
  const isStale = staleMs > STALE_WARN_MS
  const isVeryStale = staleMs > STALE_ERROR_MS

  // -- IOB / COB (from recent treatments) -------------------------------------

  const iob = calcIOB(recentTimeline?.treatments ?? [], diaMinutes)
  const cob = calcCOB(recentTimeline?.treatments ?? [])
  const basalRate = currentBasalRate(activeProfile?.basal ?? undefined)

  // -- Device ages ------------------------------------------------------------

  const catheterDate = deviceAge?.catheterChangedAt ?? undefined
  const reservoirDate = deviceAge?.reservoirChangedAt ?? undefined
  const sensorDate = deviceAge?.sensorInsertedAt ?? undefined
  const sensorDurationHours = userMe?.settings?.sensorDurationHours ?? 240
  const batteryLevel = deviceStatus?.batteryLevel ?? null

  // -- Chart data --------------------------------------------------------------

  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'
  const tirLow = toDisplay(70, glucoseUnit)
  const tirHigh = toDisplay(180, glucoseUnit)

  const cgmPoints = useMemo(() =>
    (windowTimeline?.measures ?? [])
      .filter(m => m.type === 'CGM' && typeof m.data['value'] === 'number')
      .map(m => ({
        time: new Date(m.measuredAt).getTime(),
        sgv: toDisplay(m.data['value'] as number, glucoseUnit),
        bgm: null as number | null,
        marker: null as number | null,
        treatmentType: null as string | null,
        label: null as string | null,
      })),
    [windowTimeline?.measures, glucoseUnit]
  )

  // BGM readings shown as distinct dots above the treatment marker row
  const bgmPoints = useMemo(() =>
    (windowTimeline?.measures ?? [])
      .filter(m => m.type === 'BGM' && typeof m.data['value'] === 'number')
      .map(m => ({
        time: new Date(m.measuredAt).getTime(),
        sgv: null as number | null,
        bgm: toDisplay(m.data['value'] as number, glucoseUnit),
        marker: null as number | null,
        treatmentType: null as string | null,
        label: null as string | null,
      })),
    [windowTimeline?.measures, glucoseUnit]
  )

  // Treatment markers: merge into same point shape as cgmPoints, at bottom of TIR range.
  // Basal types (BASAL, TEMP_BASAL, COMBO_BOLUS, PUMP_SUSPEND) are intentionally excluded:
  // they are continuous-rate events that appear as coloured blocks in the basal overlay,
  // so showing individual per-event dots would add visual noise without new information.
  const treatmentMarkers = useMemo(() =>
    (windowTimeline?.treatments ?? [])
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
      }),
    [windowTimeline?.treatments, tirLow]
  )

  // Combined dataset for ComposedChart root -- enables tooltip cursor to find points.
  // BGM and treatment entries are snapped to the nearest actual CGM timestamp so the
  // Recharts bisect cursor always lands on a merged object. Ownership is determined by
  // midpoint windows between adjacent CGM readings (dynamic, handles gaps correctly).
  const chartData = useMemo(() => {
    const sortedCgmTimes = cgmPoints.map(p => p.time).sort((a, b) => a - b)

    // Returns the actual CGM timestamp that "owns" the given ms value, or null if
    // no CGM reading is close enough (point lies outside all ownership windows).
    const snapToCgm = (ms: number): number | null => {
      if (sortedCgmTimes.length === 0) return null

      // Binary search: first index where sortedCgmTimes[i] >= ms
      let lo = 0
      let hi = sortedCgmTimes.length
      while (lo < hi) {
        const mid = (lo + hi) >> 1
        if ((sortedCgmTimes[mid] as number) < ms) lo = mid + 1
        else hi = mid
      }

      // Nearest neighbour among the two candidates bracketing ms
      let nearestIdx = lo
      if (lo > 0 && lo < sortedCgmTimes.length) {
        nearestIdx =
          Math.abs((sortedCgmTimes[lo] as number) - ms) <=
          Math.abs((sortedCgmTimes[lo - 1] as number) - ms)
            ? lo
            : lo - 1
      } else if (lo === sortedCgmTimes.length) {
        nearestIdx = lo - 1
      }

      const nearest = sortedCgmTimes[nearestIdx] as number
      const prev = nearestIdx > 0 ? (sortedCgmTimes[nearestIdx - 1] as number) : null
      const next =
        nearestIdx < sortedCgmTimes.length - 1
          ? (sortedCgmTimes[nearestIdx + 1] as number)
          : null

      // Ownership window: extends halfway to adjacent CGM readings (default 5 min at edges)
      const DEFAULT_HALF = 5 * 60_000
      const halfBefore = prev !== null ? (nearest - prev) / 2 : DEFAULT_HALF
      const halfAfter = next !== null ? (next - nearest) / 2 : DEFAULT_HALF

      return ms >= nearest - halfBefore && ms <= nearest + halfAfter ? nearest : null
    }

    const byTime = new Map<number, ChartPoint>()

    for (const p of cgmPoints) {
      byTime.set(p.time, { ...p })
    }

    for (const p of bgmPoints) {
      const cgmTime = snapToCgm(p.time)
      if (cgmTime !== null) {
        const existing = byTime.get(cgmTime)
        if (existing) {
          byTime.set(cgmTime, { ...existing, bgm: p.bgm })
        } else {
          byTime.set(cgmTime, { ...p, time: cgmTime })
        }
      } else {
        byTime.set(p.time, { ...p })
      }
    }

    for (const p of treatmentMarkers) {
      const cgmTime = snapToCgm(p.time)
      if (cgmTime !== null) {
        const existing = byTime.get(cgmTime)
        if (existing) {
          // Accumulate labels so concurrent treatments (e.g. BOLUS + CARBS at same time) both appear
          const combinedLabel = [existing.label, p.label].filter(Boolean).join(' · ')
          byTime.set(cgmTime, { ...existing, marker: p.marker, treatmentType: p.treatmentType, label: combinedLabel })
        } else {
          byTime.set(cgmTime, { ...p, time: cgmTime })
        }
      } else {
        byTime.set(p.time, { ...p })
      }
    }

    return Array.from(byTime.values()).sort((a, b) => a.time - b.time)
  }, [cgmPoints, bgmPoints, treatmentMarkers])

  // Basal block reconstruction
  const { basalBlocks, basalProfileLine } = useMemo(() => {
    const basal = activeProfile?.basal
    if (!basal?.length) return { basalBlocks: [], basalProfileLine: [] }
    const fromMs = new Date(windowFrom).getTime()
    const toMs = new Date(windowTo).getTime()
    const blocks = reconstructBasalBlocks(fromMs, toMs, basal, windowTimeline?.treatments ?? [])

    return { basalBlocks: blocks, basalProfileLine: buildBasalProfileLine(fromMs, toMs, basal) }
  }, [activeProfile?.basal, windowFrom, windowTo, windowTimeline?.treatments])

  // -- Render ------------------------------------------------------------------

  return (
    <div>
      <DeviceStatusWidget userId={userId} />

      {/* -- Glucose hero --------------------------------------------------- */}
      {latestSgv !== null && (
        <GlucoseHeroTile
          latestSgv={latestSgv}
          latestTrend={latestTrend}
          delta={delta}
          delta15={delta15}
          minutesAgo={minutesAgo}
          isStale={isStale}
          isVeryStale={isVeryStale}
          glucoseUnit={glucoseUnit}
          yLabel={yLabel}
        />
      )}

      {/* -- Stat tiles row 1: IOB / COB / Basal / DIA ---------------------- */}
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
        <StatTile label={t('dashboard.iob', { defaultValue: 'IOB' })} value={`${iob.toFixed(1)} U`} sub={t('dashboard.insulinOnBoard', { defaultValue: 'Insulin on Board' })} />
        <StatTile label={t('dashboard.cob', { defaultValue: 'COB' })} value={`${Math.round(cob)} g`} sub={t('dashboard.carbsOnBoard', { defaultValue: 'Carbs on Board' })} />
        {basalRate !== null && <StatTile label={t('dashboard.basal', { defaultValue: 'Basal' })} value={`${basalRate.toFixed(2)} U/h`} sub={t('dashboard.currentBasal', { defaultValue: 'Current Rate' })} />}
        {activeProfile && <StatTile label={t('dashboard.dia', { defaultValue: 'DIA' })} value={`${Math.round(diaMinutes / 60)} h`} sub={activeProfile.insulinType ?? ''} />}
        {activeProfile && <StatTile label={t('dashboard.profile', { defaultValue: 'Profile' })} value={activeProfile.name} />}
      </div>

      {/* -- Stat tiles row 2: device ages + battery ------------------------- */}
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

      {/* -- Time window selector -------------------------------------------- */}
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

      {/* -- Combined glucose + treatment markers chart ---------------------- */}
      <GlucoseTrendChart
        chartData={chartData}
        cgmPoints={cgmPoints}
        bgmPoints={bgmPoints}
        treatmentMarkers={treatmentMarkers}
        windowFrom={windowFrom}
        windowTo={windowTo}
        glucoseUnit={glucoseUnit}
        yLabel={yLabel}
        tirLow={tirLow}
        tirHigh={tirHigh}
        isLoading={isLoading}
        basalBlocks={basalBlocks}
        basalProfileLine={basalProfileLine}
      />

      {/* -- Basal block chart ----------------------------------------------- */}
      <BasalRateChart
        activeProfile={activeProfile}
        basalBlocks={basalBlocks}
        basalProfileLine={basalProfileLine}
        windowFrom={windowFrom}
        windowTo={windowTo}
      />

      {/* -- Device usage averages ------------------------------------------- */}
      <DeviceUsageCard userId={userId} />
    </div>
  )
}
