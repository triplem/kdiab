import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { analyzeApi } from '../../api/analyzeApi'
import { profilesApi } from '../../api/profilesApi'
import { DeviceStatusWidget } from '../treatments/DeviceStatusWidget'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'

interface Props {
  userId: string
  glucoseUnit: string
}

const MGDL_TO_MMOL = 1 / 18.0

function toDisplay(mgdl: number, unit: string): number {
  if (unit === 'mmol/L') return Math.round(mgdl * MGDL_TO_MMOL * 10) / 10
  return Math.round(mgdl)
}

function glucoseColor(mgdl: number): string {
  if (mgdl < 70) return 'var(--accent-danger)'
  if (mgdl > 180) return 'var(--accent-warning)'
  return 'var(--accent-success)'
}

const TREND_ARROWS: Record<string, string> = {
  DoubleUp: '↑↑',
  SingleUp: '↑',
  FortyFiveUp: '↗',
  Flat: '→',
  FortyFiveDown: '↘',
  SingleDown: '↓',
  DoubleDown: '↓↓',
}

function trendArrow(trend: unknown): string {
  if (typeof trend !== 'string') return ''
  return TREND_ARROWS[trend] ?? ''
}

// Linear IOB decay: iob += insulin * (1 - minutesSinceDose / diaMinutes)
function calcIOB(
  treatments: Array<{ treatedAt: string; type: string; data: Record<string, unknown> }>,
  diaMinutes: number,
): number {
  const now = Date.now()
  let iob = 0
  for (const t of treatments) {
    if (t.type !== 'BOLUS' && t.type !== 'CORRECTION_BOLUS' && t.type !== 'MEAL') continue
    const minutesSince = (now - new Date(t.treatedAt).getTime()) / 60000
    if (minutesSince < 0 || minutesSince > diaMinutes) continue
    const insulin = typeof t.data['insulin'] === 'number' ? t.data['insulin'] : 0
    iob += insulin * (1 - minutesSince / diaMinutes)
  }
  return Math.max(0, iob)
}

// Linear COB decay: carbs absorbed linearly over absorptionMinutes (default 180)
function calcCOB(
  treatments: Array<{ treatedAt: string; type: string; data: Record<string, unknown> }>,
): number {
  const now = Date.now()
  const absorptionMin = 180
  let cob = 0
  for (const t of treatments) {
    if (t.type !== 'CARBS' && t.type !== 'MEAL' && t.type !== 'HYPO_TREATMENT') continue
    const minutesSince = (now - new Date(t.treatedAt).getTime()) / 60000
    if (minutesSince < 0 || minutesSince > absorptionMin) continue
    const carbs = typeof t.data['carbs'] === 'number' ? t.data['carbs'] : 0
    cob += carbs * (1 - minutesSince / absorptionMin)
  }
  return Math.max(0, cob)
}

// Find current basal rate from active profile basal segments (sorted by startTime HH:MM)
function currentBasalRate(
  basal: Array<{ startTime: string; value: number }> | undefined,
): number | null {
  if (!basal || basal.length === 0) return null
  const now = new Date()
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const sorted = [...basal].sort((a, b) => {
    const toMin = (t: string) => {
      const [h, m] = t.split(':').map(Number)
      return (h ?? 0) * 60 + (m ?? 0)
    }
    return toMin(a.startTime) - toMin(b.startTime)
  })
  let rate: number | null = null
  for (const seg of sorted) {
    const [h, m] = seg.startTime.split(':').map(Number)
    const segMinutes = (h ?? 0) * 60 + (m ?? 0)
    if (segMinutes <= nowMinutes) rate = seg.value
  }
  return rate ?? sorted[sorted.length - 1]?.value ?? null
}

interface StatTileProps {
  label: string
  value: string
  sub?: string
  color?: string
}

function StatTile({ label, value, sub, color }: StatTileProps) {
  return (
    <div
      className="card"
      style={{ padding: '1rem', minWidth: '120px', textAlign: 'center' }}
    >
      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.25rem' }}>
        {label}
      </div>
      <div style={{ fontSize: '1.75rem', fontWeight: 700, color: color ?? 'var(--text-primary)' }}>
        {value}
      </div>
      {sub && (
        <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.15rem' }}>
          {sub}
        </div>
      )}
    </div>
  )
}

export function DashboardView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const { formatTime } = useTimeFormat()

  const sixHoursAgo = new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString()
  const now = new Date().toISOString()

  const { data: timeline, isLoading: timelineLoading } = useQuery({
    queryKey: ['dashboard-timeline', userId],
    queryFn: () => analyzeApi.getTimeline(userId, sixHoursAgo, now).then(r => r.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
  })

  const { data: profiles } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => profilesApi.listProfiles(userId).then(r => r.data.items),
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  const activeProfile = profiles?.find(p => p.status === 'ACTIVE')
  const diaMinutes = activeProfile?.durationOfAction ?? 240

  const cgmReadings = (timeline?.measures ?? [])
    .filter(m => m.type === 'CGM')
    .sort((a, b) => new Date(a.measuredAt).getTime() - new Date(b.measuredAt).getTime())

  const latestCgm = cgmReadings[cgmReadings.length - 1]
  const latestSgv = typeof latestCgm?.data['sgv'] === 'number' ? latestCgm.data['sgv'] : null
  const latestTrend = latestCgm?.data['trend']

  const iob = calcIOB(timeline?.treatments ?? [], diaMinutes)
  const cob = calcCOB(timeline?.treatments ?? [])
  const basalRate = currentBasalRate(activeProfile?.basal)

  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'
  const tirLow = glucoseUnit === 'mmol/L' ? toDisplay(70, glucoseUnit) : 70
  const tirHigh = glucoseUnit === 'mmol/L' ? toDisplay(180, glucoseUnit) : 180

  const chartData = cgmReadings
    .filter(m => typeof m.data['sgv'] === 'number')
    .map(m => ({
      time: new Date(m.measuredAt).getTime(),
      sgv: toDisplay(m.data['sgv'] as number, glucoseUnit),
    }))

  return (
    <div>
      <DeviceStatusWidget userId={userId} />

      {/* Current glucose hero */}
      {timelineLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
      {latestSgv !== null && latestSgv !== undefined && (
        <div
          className="card"
          style={{ padding: '1.25rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}
        >
          <div style={{ fontSize: '3.5rem', fontWeight: 800, lineHeight: 1, color: glucoseColor(latestSgv) }}>
            {toDisplay(latestSgv, glucoseUnit)}
            <span style={{ fontSize: '1.25rem', marginLeft: '0.25rem', fontWeight: 400 }}>{yLabel}</span>
            {' '}
            <span style={{ fontSize: '2rem' }}>{trendArrow(latestTrend)}</span>
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            {t('dashboard.lastReading', { defaultValue: 'Last reading' })}: {latestCgm?.measuredAt ? formatTime(latestCgm.measuredAt) : '—'}
          </div>
        </div>
      )}

      {/* Stat tiles */}
      <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
        <StatTile
          label={t('dashboard.iob', { defaultValue: 'IOB' })}
          value={`${iob.toFixed(1)} U`}
          sub={t('dashboard.insulinOnBoard', { defaultValue: 'Insulin on Board' })}
        />
        <StatTile
          label={t('dashboard.cob', { defaultValue: 'COB' })}
          value={`${Math.round(cob)} g`}
          sub={t('dashboard.carbsOnBoard', { defaultValue: 'Carbs on Board' })}
        />
        {basalRate !== null && (
          <StatTile
            label={t('dashboard.basal', { defaultValue: 'Basal' })}
            value={`${basalRate.toFixed(2)} U/h`}
            sub={t('dashboard.currentBasal', { defaultValue: 'Current Rate' })}
          />
        )}
        {activeProfile && (
          <StatTile
            label={t('dashboard.dia', { defaultValue: 'DIA' })}
            value={`${Math.round(diaMinutes / 60)} h`}
            sub={activeProfile.insulinType ?? ''}
          />
        )}
      </div>

      {/* Mini CGM chart */}
      {chartData.length > 1 && (
        <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
          <h3 style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1rem' }}>
            {t('dashboard.cgmChart', { defaultValue: 'Glucose (6 h)' })}
          </h3>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="time"
                type="number"
                domain={['dataMin', 'dataMax']}
                tickFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
                tick={{ fontSize: 11 }}
              />
              <YAxis
                domain={[
                  glucoseUnit === 'mmol/L' ? 2 : 40,
                  glucoseUnit === 'mmol/L' ? 18 : 330,
                ]}
                tickFormatter={(v: number) => String(v)}
                tick={{ fontSize: 11 }}
                label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 15, style: { fontSize: 11 }, fill: 'var(--text-secondary)' }}
              />
              <Tooltip
                labelFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
                formatter={(v: number) => [`${v} ${yLabel}`, 'CGM']}
                contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
                wrapperStyle={{ outline: 'none' }}
              />
              <ReferenceLine y={tirLow} stroke="#ef4444" strokeDasharray="4 4" />
              <ReferenceLine y={tirHigh} stroke="#f59e0b" strokeDasharray="4 4" />
              <Line
                type="monotone"
                dataKey="sgv"
                name="CGM"
                stroke="var(--chart-median)"
                dot={false}
                strokeWidth={2}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
