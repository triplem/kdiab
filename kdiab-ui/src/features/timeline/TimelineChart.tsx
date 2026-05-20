import {
  ComposedChart,
  Line,
  Scatter,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceArea,
  ResponsiveContainer,
  Legend,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'

interface Measure {
  id: string
  measuredAt: string
  type: string
  data: Record<string, unknown>
}

interface Treatment {
  id: string
  treatedAt: string
  type: string
  notes?: string
  data: Record<string, unknown>
}

interface Props {
  measures: Measure[]
  treatments: Treatment[]
  glucoseUnit: string
  profileChangeDates?: number[]
}

const MGDL_TO_MMOL = 1 / 18.0

interface TtPayload {
  // dataKey can be a function in recharts 3.x
  dataKey?: string | number | ((obj: unknown) => unknown)
  value?: unknown
  payload?: Record<string, unknown>
}

function TimelineTooltip({
  active,
  payload,
  label,
  glucoseUnit,
}: {
  active?: boolean
  payload?: readonly TtPayload[]
  label?: number | string
  glucoseUnit: string
}) {
  if (!active || !payload?.length || label == null) return null
  const yUnit = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'
  return (
    <div className="timeline-tooltip">
      <p className="timeline-tooltip-time">{new Date(Number(label)).toLocaleString()}</p>
      {payload.map((p, i) => {
        const numVal = typeof p.value === 'number' ? p.value : undefined
        if (p.dataKey === 'value') {
          return <p key={i}><strong>CGM</strong> {numVal} {yUnit}</p>
        }
        if (p.dataKey === 'bgmValue') {
          return <p key={i}><strong>BGM</strong> {numVal} {yUnit}</p>
        }
        if (p.dataKey === 'y') {
          const type = (p.payload?.['type'] as string) ?? ''
          const notes = p.payload?.['notes'] as string | undefined
          return <p key={i}><strong>{type}</strong>{notes ? ` — ${notes}` : ''}</p>
        }
        return null
      })}
    </div>
  )
}

function toMgDl(value: number, unit: string): number {
  return unit === 'mmol/L' ? value * 18.0 : value
}

function displayValue(mgDl: number, unit: string): number {
  return unit === 'mmol/L' ? Math.round(mgDl * MGDL_TO_MMOL * 10) / 10 : Math.round(mgDl)
}

interface TreatmentEntry {
  type: string
  notes?: string
  treatmentData: Record<string, unknown>
}

interface TreatmentMarker {
  ts: number
  y: number
  entries: TreatmentEntry[]
}

// Linear interpolation of the CGM value at a given timestamp
function interpolateCgm(ts: number, cgmData: { ts: number; value?: number }[]): number | undefined {
  if (cgmData.length === 0) return undefined
  let lo = -1
  let hi = -1
  for (let i = 0; i < cgmData.length; i++) {
    if (cgmData[i].ts <= ts) lo = i
    if (cgmData[i].ts >= ts && hi === -1) hi = i
  }
  if (lo === -1 && hi === -1) return undefined
  if (lo === -1) return cgmData[hi].value
  if (hi === -1) return cgmData[lo].value
  if (lo === hi) return cgmData[lo].value
  const t0 = cgmData[lo].ts, t1 = cgmData[hi].ts
  const v0 = cgmData[lo].value!, v1 = cgmData[hi].value!
  return v0 + ((ts - t0) / (t1 - t0)) * (v1 - v0)
}

export function TimelineChart({ measures, treatments, glucoseUnit, profileChangeDates }: Props) {
  const { t } = useTranslation()
  const { locale } = useTimeFormat()

  const tirLow = displayValue(70, glucoseUnit)
  const tirHigh = displayValue(180, glucoseUnit)
  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

  const cgmData = measures
    .filter(m => m.type === 'CGM')
    .map(m => ({
      ts: new Date(m.measuredAt).getTime(),
      value: (() => {
        const raw = m.data['value']
        const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN
        if (isNaN(val)) return undefined
        const storageUnit = typeof m.data['unit'] === 'string' ? m.data['unit'] as string : 'mg/dL'
        return displayValue(toMgDl(val, storageUnit), glucoseUnit)
      })(),
    }))
    .filter(d => d.value !== undefined)
    .sort((a, b) => a.ts - b.ts)

  const bgmData = measures
    .filter(m => m.type === 'BGM')
    .map(m => ({
      ts: new Date(m.measuredAt).getTime(),
      bgmValue: (() => {
        const raw = m.data['value']
        const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN
        if (isNaN(val)) return undefined
        const storageUnit = typeof m.data['unit'] === 'string' ? m.data['unit'] as string : 'mg/dL'
        return displayValue(toMgDl(val, storageUnit), glucoseUnit)
      })(),
    }))
    .filter(d => d.bgmValue !== undefined)

  const byTs = new Map<number, TreatmentEntry[]>()
  for (const tr of treatments) {
    const ts = new Date(tr.treatedAt).getTime()
    const bucket = byTs.get(ts) ?? []
    bucket.push({ type: tr.type, notes: tr.notes, treatmentData: tr.data })
    byTs.set(ts, bucket)
  }

  const treatmentMarkers: TreatmentMarker[] = Array.from(byTs.entries()).map(([ts, entries]) => ({
    ts,
    y: interpolateCgm(ts, cgmData) ?? tirLow,
    entries,
  }))

  const formatTs = (ts: number) =>
    new Date(ts).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })

  return (
    <figure role="img" aria-label={t('timeline.chartAriaLabel')} style={{ margin: 0 }}>
      <figcaption style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap' }}>
        {t('timeline.chartCaption')}
      </figcaption>
      <ResponsiveContainer width="100%" height={350}>
        <ComposedChart margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis
          dataKey="ts"
          type="number"
          domain={['auto', 'auto']}
          tickFormatter={formatTs}
          scale="time"
          label={{ value: '', position: 'insideBottom' }}
        />
        <YAxis
          label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 10 }}
        />
        <Tooltip content={(props) => <TimelineTooltip {...props} glucoseUnit={glucoseUnit} />} />
        <Legend />

        <ReferenceArea y1={tirLow} y2={tirHigh} fill="rgba(16, 185, 129, 0.07)" />
        <ReferenceLine y={tirLow} stroke="var(--accent-danger)" strokeDasharray="4 4" label={{ value: String(tirLow), fill: 'var(--accent-danger)', fontSize: 11 }} />
        <ReferenceLine y={tirHigh} stroke="var(--accent-warning)" strokeDasharray="4 4" label={{ value: String(tirHigh), fill: 'var(--accent-warning)', fontSize: 11 }} />

        {(profileChangeDates ?? []).map(ms => (
          <ReferenceLine
            key={ms}
            x={ms}
            stroke="var(--color-text-muted)"
            strokeDasharray="4 2"
            label={{ value: t('timeline.profileChanged'), position: 'top', fontSize: 10 }}
          />
        ))}

        <Line
          data={cgmData}
          dataKey="value"
          name={t('timeline.glucose') + ' (CGM)'}
          stroke="var(--chart-cgm)"
          dot={false}
          activeDot={false}
          strokeWidth={2}
          connectNulls={false}
        />

        {bgmData.length > 0 && (
          <Scatter
            data={bgmData}
            dataKey="bgmValue"
            name={t('timeline.glucose') + ' (BGM)'}
            fill="var(--chart-bgm)"
            shape={(props: unknown) => {
              const p = props as { cx?: number; cy?: number; fill?: string }
              const cx = p.cx ?? 0
              const cy = p.cy ?? 0
              return (
                <g>
                  <circle cx={cx} cy={cy} r={16} fill="transparent" />
                  <circle cx={cx} cy={cy} r={7} fill={p.fill ?? 'var(--chart-bgm)'} stroke="var(--bg-primary)" strokeWidth={1.5} />
                </g>
              )
            }}
          />
        )}

        {treatmentMarkers.length > 0 && (
          <Scatter
            data={treatmentMarkers}
            dataKey="y"
            name={t('timeline.treatment')}
            fill="var(--accent-primary)"
            isAnimationActive={false}
            shape={(props: unknown) => {
              const p = props as { cx?: number; cy?: number }
              const cx = p.cx ?? 0
              const cy = p.cy ?? 0
              const s = 7
              return (
                <g>
                  <circle cx={cx} cy={cy} r={16} fill="transparent" />
                  <polygon
                    points={`${cx},${cy - s} ${cx + s},${cy} ${cx},${cy + s} ${cx - s},${cy}`}
                    fill="var(--accent-primary)"
                    stroke="var(--bg-primary)"
                    strokeWidth={1.5}
                  />
                </g>
              )
            }}
          />
        )}
      </ComposedChart>
      </ResponsiveContainer>
    </figure>
  )
}
