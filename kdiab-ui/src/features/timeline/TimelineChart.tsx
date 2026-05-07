import type { ReactNode } from 'react'
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
import type { TooltipProps } from 'recharts'
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

const TREATMENT_COLORS: Record<string, string> = {
  BOLUS: 'var(--color-bolus)',
  CORRECTION_BOLUS: 'var(--color-bolus)',
  COMBO_BOLUS: 'var(--color-bolus)',
  CARBS: 'var(--color-carbs)',
  HYPO_TREATMENT: 'var(--color-carbs)',
  EXERCISE: 'var(--color-activity)',
  MEAL: 'var(--color-carbs)',
  ACTIVITY: 'var(--color-activity)',
  BASAL: 'var(--color-basal)',
  TEMP_BASAL: 'var(--color-basal)',
  SITE_CHANGE: 'var(--color-device)',
  SENSOR_INSERT: 'var(--color-device)',
  INSULIN_CHANGE: 'var(--color-device)',
  PUMP_SUSPEND: 'var(--color-device)',
  NOTE: 'var(--color-other)',
  BG_CHECK: 'var(--color-other)',
}

const treatmentColor = (type: string): string =>
  TREATMENT_COLORS[type] ?? 'var(--color-other)'

const MGDL_TO_MMOL = 1 / 18.0

function toMgDl(value: number, unit: string): number {
  return unit === 'mmol/L' ? value * 18.0 : value
}

function displayValue(mgDl: number, unit: string): number {
  return unit === 'mmol/L' ? Math.round(mgDl * MGDL_TO_MMOL * 10) / 10 : Math.round(mgDl)
}

const BOLUS_TYPES = new Set(['BOLUS', 'CORRECTION_BOLUS', 'COMBO_BOLUS'])
const CARBS_TYPES = new Set(['CARBS', 'MEAL', 'HYPO_TREATMENT'])

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

interface GlucosePoint {
  ts: number
  value?: number
  bgmValue?: number
}

type TooltipPayload = {
  name: string
  value: number
  color: string
  payload: (TreatmentMarker | GlucosePoint) & Record<string, unknown>
}

function formatTreatmentLine(type: string, data: Record<string, unknown>): string {
  if (BOLUS_TYPES.has(type)) {
    const units = data['insulin'] ?? data['units']
    const insulinType = data['insulinType']
    return insulinType ? `${units} U (${insulinType})` : `${units} U`
  }
  if (CARBS_TYPES.has(type)) {
    return `${data['carbs'] ?? '?'} g`
  }
  if (type === 'EXERCISE' || type === 'ACTIVITY') {
    const duration = data['duration']
    const intensity = data['intensity']
    return [duration ? `${duration} min` : null, intensity ?? null].filter(Boolean).join(', ')
  }
  return ''
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
  const { formatDate, locale } = useTimeFormat()

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

  // Group all treatments by timestamp → one diamond marker per unique ts on the CGM line
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

  const renderTooltip = ({ active, payload, label }: TooltipProps<number, string>) => {
    if (!active || !payload?.length || label == null) return null
    const entries = payload as unknown as TooltipPayload[]

    const dateLabel = formatDate(new Date(label as number).toISOString())
    const nodes: ReactNode[] = []

    for (let i = 0; i < entries.length; i++) {
      const entry = entries[i]
      const p = entry.payload

      if ('value' in p && p.value !== undefined) {
        const gp = p as GlucosePoint
        nodes.push(
          <p key={`cgm-${i}`} style={{ margin: 0, color: entry.color }}>
            {entry.name}: {gp.value} {yLabel}
          </p>
        )
      } else if ('bgmValue' in p && p.bgmValue !== undefined) {
        const gp = p as GlucosePoint
        nodes.push(
          <p key={`bgm-${i}`} style={{ margin: 0, color: entry.color }}>
            {entry.name}: {gp.bgmValue} {yLabel}
          </p>
        )
      } else if ('entries' in p && Array.isArray(p.entries)) {
        const seen = new Set<string>()
        for (const te of p.entries as TreatmentEntry[]) {
          const key = `${te.type}-${te.notes ?? ''}`
          if (seen.has(key)) continue
          seen.add(key)
          const typeName = t(`treatmentModal.types.${te.type}`, { defaultValue: te.type })
          const entryLabel = te.notes ? `${typeName} (${te.notes})` : typeName
          const valueStr = formatTreatmentLine(te.type, te.treatmentData)
          nodes.push(
            <p key={`tr-${key}-${i}`} style={{ margin: 0, color: treatmentColor(te.type) }}>
              {entryLabel}{valueStr ? `: ${valueStr}` : ''}
            </p>
          )
        }
      }
    }

    return (
      <div style={{
        backgroundColor: 'var(--tooltip-bg)',
        border: '1px solid var(--tooltip-border)',
        borderRadius: '8px',
        color: 'var(--tooltip-text)',
        padding: '8px 12px',
        fontSize: '0.85rem',
        lineHeight: 1.6,
      }}>
        <p style={{ margin: '0 0 4px', fontWeight: 600 }}>{dateLabel}</p>
        {nodes}
      </div>
    )
  }

  return (
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
        <Tooltip content={renderTooltip} wrapperStyle={{ outline: 'none' }} />
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
              return (
                <circle
                  cx={p.cx ?? 0}
                  cy={p.cy ?? 0}
                  r={7}
                  fill={p.fill ?? 'var(--chart-bgm)'}
                  stroke="var(--bg-primary)"
                  strokeWidth={1.5}
                />
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
                <polygon
                  points={`${cx},${cy - s} ${cx + s},${cy} ${cx},${cy + s} ${cx - s},${cy}`}
                  fill="var(--accent-primary)"
                  stroke="var(--bg-primary)"
                  strokeWidth={1.5}
                />
              )
            }}
          />
        )}
      </ComposedChart>
    </ResponsiveContainer>
  )
}
