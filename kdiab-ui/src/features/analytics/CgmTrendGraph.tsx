import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  ComposedChart,
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'
import { analyzeApi } from '../../api/analyzeApi'

const MGDL_TO_MMOL = 1 / 18.0

type Window = '1W' | '2W' | '1M' | '90D'

const WINDOWS: { key: Window; days: number }[] = [
  { key: '1W', days: 7 },
  { key: '2W', days: 14 },
  { key: '1M', days: 30 },
  { key: '90D', days: 90 },
]

function windowDates(days: number): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000)
  return {
    from: from.toISOString().slice(0, 10) + 'T00:00:00Z',
    to: to.toISOString().slice(0, 10) + 'T23:59:59Z',
  }
}

function toDisplayUnit(mgDl: number, unit: string): number {
  return unit === 'mmol/L' ? Math.round(mgDl * MGDL_TO_MMOL * 10) / 10 : Math.round(mgDl)
}

function toMgDl(value: number, unit: string): number {
  return unit === 'mmol/L' ? value * 18.0 : value
}

function median(sorted: number[]): number {
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 !== 0 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

interface TrendPoint {
  ts: number
  label: string
  med: number
  min: number
  max: number
  range: [number, number]
}

interface CgmMeasure {
  measuredAt: string
  type: string
  data: Record<string, unknown>
}

function buildTrendPoints(measures: CgmMeasure[], glucoseUnit: string): TrendPoint[] {
  const byDay = new Map<string, number[]>()

  for (const m of measures) {
    if (m.type !== 'CGM') continue
    const raw = m.data['value']
    const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN
    if (isNaN(val)) continue
    const storageUnit = typeof m.data['unit'] === 'string' ? (m.data['unit'] as string) : 'mg/dL'
    const mgDl = toMgDl(val, storageUnit)
    const displayVal = toDisplayUnit(mgDl, glucoseUnit)
    const day = m.measuredAt.slice(0, 10)
    const bucket = byDay.get(day) ?? []
    bucket.push(displayVal)
    byDay.set(day, bucket)
  }

  return Array.from(byDay.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([day, values]) => {
      const sorted = [...values].sort((a, b) => a - b)
      const med = median(sorted)
      const min = sorted[0]
      const max = sorted[sorted.length - 1]
      return {
        ts: new Date(day + 'T12:00:00Z').getTime(),
        label: day,
        med,
        min,
        max,
        range: [min, max] as [number, number],
      }
    })
}

interface TrendTooltipProps {
  active?: boolean
  payload?: Array<{ name?: string; value?: unknown; payload?: TrendPoint }>
  label?: number
  glucoseUnit: string
}

function TrendTooltip({ active, payload, label, glucoseUnit }: TrendTooltipProps) {
  if (!active || !payload?.length || label == null) return null
  const point = payload[0]?.payload
  if (!point) return null
  const unit = glucoseUnit
  return (
    <div className="timeline-tooltip">
      <p className="timeline-tooltip-time">{point.label}</p>
      <p><strong>Median:</strong> {point.med} {unit}</p>
      <p><strong>Range:</strong> {point.min} – {point.max} {unit}</p>
    </div>
  )
}

interface Props {
  userId: string
  glucoseUnit: string
}

export function CgmTrendGraph({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const [activeWindow, setActiveWindow] = useState<Window>('2W')

  const { from, to } = useMemo(() => windowDates(WINDOWS.find((w) => w.key === activeWindow)!.days), [activeWindow])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cgmTrend', userId, activeWindow],
    queryFn: () => analyzeApi.getTimeline(userId, from, to).then((r) => r.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })

  const trendPoints = useMemo(
    () => buildTrendPoints(data?.measures ?? [], glucoseUnit),
    [data, glucoseUnit],
  )

  const tirLow = toDisplayUnit(70, glucoseUnit)
  const tirHigh = toDisplayUnit(180, glucoseUnit)
  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

  const formatLabel = (ts: number) =>
    new Date(ts).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })

  return (
    <div className="card" style={{ marginTop: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
        <h3 style={{ margin: 0 }}>{t('analytics.cgmTrend', { defaultValue: 'CGM Daily Trend' })}</h3>
        <div style={{ display: 'flex', gap: '0.25rem' }}>
          {WINDOWS.map((w) => (
            <button
              key={w.key}
              onClick={() => setActiveWindow(w.key)}
              className={activeWindow === w.key ? 'primary' : 'btn outline'}
              style={{ padding: '0.25rem 0.6rem', fontSize: '0.8rem' }}
            >
              {w.key}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
      {isError && <p style={{ color: 'var(--accent-danger)' }}>{t('analytics.cgmTrendError', { defaultValue: 'Failed to load trend data.' })}</p>}

      {!isLoading && !isError && trendPoints.length === 0 && (
        <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData', { defaultValue: 'No CGM data in this window.' })}</p>
      )}

      {trendPoints.length > 0 && (
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={trendPoints} margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              dataKey="ts"
              type="number"
              scale="time"
              domain={['auto', 'auto']}
              tickFormatter={formatLabel}
            />
            <YAxis label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 10 }} />
            <Tooltip content={(props) => <TrendTooltip {...props} glucoseUnit={glucoseUnit} />} />

            <ReferenceArea y1={tirLow} y2={tirHigh} fill="rgba(16, 185, 129, 0.07)" />
            <ReferenceLine y={tirLow} stroke="var(--accent-danger)" strokeDasharray="4 4" label={{ value: String(tirLow), fill: 'var(--accent-danger)', fontSize: 11 }} />
            <ReferenceLine y={tirHigh} stroke="var(--accent-warning)" strokeDasharray="4 4" label={{ value: String(tirHigh), fill: 'var(--accent-warning)', fontSize: 11 }} />

            <Area
              dataKey="range"
              name={t('analytics.cgmRange', { defaultValue: 'Daily range (min–max)' })}
              stroke="none"
              fill="var(--chart-cgm)"
              fillOpacity={0.15}
              connectNulls={false}
            />
            <Line
              dataKey="med"
              name={t('analytics.cgmMedian', { defaultValue: 'Daily median' })}
              stroke="var(--chart-cgm)"
              dot={{ r: 3 }}
              activeDot={{ r: 5 }}
              strokeWidth={2}
              connectNulls={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
