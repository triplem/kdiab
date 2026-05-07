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
  Cell,
} from 'recharts'
import { useTranslation } from 'react-i18next'

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

export function TimelineChart({ measures, treatments, glucoseUnit, profileChangeDates }: Props) {
  const { t } = useTranslation()

  const tirLow = displayValue(70, glucoseUnit)
  const tirHigh = displayValue(180, glucoseUnit)

  // Fixed absolute positions above the glucose range — 40 mg/dL (2.2 mmol/L) apart
  // so paired bolus+carbs events at the same timestamp are clearly separated.
  const bolusY = displayValue(210, glucoseUnit)
  const carbsY = displayValue(250, glucoseUnit)
  const otherY = displayValue(290, glucoseUnit)

  const cgmData = measures
    .filter(m => m.type === 'CGM')
    .map(m => ({
      ts: new Date(m.measuredAt).getTime(),
      value: (() => {
        const raw = m.data['value']
        const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN
        if (isNaN(val)) return undefined
        const storageUnit = typeof m.data['unit'] === 'string' ? m.data['unit'] as string : 'mg/dL'
        const mgDl = toMgDl(val, storageUnit)
        return displayValue(mgDl, glucoseUnit)
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
        const mgDl = toMgDl(val, storageUnit)
        return displayValue(mgDl, glucoseUnit)
      })(),
    }))
    .filter(d => d.bgmValue !== undefined)

  const bolusData = treatments
    .filter(tr => BOLUS_TYPES.has(tr.type))
    .map(tr => ({ ts: new Date(tr.treatedAt).getTime(), y: bolusY, type: tr.type, notes: tr.notes }))

  const carbsData = treatments
    .filter(tr => CARBS_TYPES.has(tr.type))
    .map(tr => ({ ts: new Date(tr.treatedAt).getTime(), y: carbsY, type: tr.type, notes: tr.notes }))

  const otherTreatmentData = treatments
    .filter(tr => !BOLUS_TYPES.has(tr.type) && !CARBS_TYPES.has(tr.type))
    .map(tr => ({ ts: new Date(tr.treatedAt).getTime(), y: otherY, type: tr.type, notes: tr.notes }))

  const formatTs = (ts: number) =>
    new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })

  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

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
        <Tooltip
          labelFormatter={(val: number) => new Date(val).toLocaleString()}
          formatter={(val: number, name: string) => [`${val} ${yLabel}`, name]}
          contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
          wrapperStyle={{ outline: 'none' }}
        />
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

        {bolusData.length > 0 && (
          <Scatter
            data={bolusData}
            dataKey="y"
            name={t('timeline.bolus')}
            fill="var(--color-bolus)"
            shape="triangle"
            isAnimationActive={false}
          >
            {bolusData.map((entry, index) => (
              <Cell key={index} fill={treatmentColor(entry.type)} />
            ))}
          </Scatter>
        )}

        {carbsData.length > 0 && (
          <Scatter
            data={carbsData}
            dataKey="y"
            name={t('timeline.carbs')}
            fill="var(--color-carbs)"
            shape="triangle"
            isAnimationActive={false}
          >
            {carbsData.map((entry, index) => (
              <Cell key={index} fill={treatmentColor(entry.type)} />
            ))}
          </Scatter>
        )}

        {otherTreatmentData.length > 0 && (
          <Scatter
            data={otherTreatmentData}
            dataKey="y"
            name={t('timeline.other')}
            fill="var(--color-other)"
            shape="triangle"
            isAnimationActive={false}
          >
            {otherTreatmentData.map((entry, index) => (
              <Cell key={index} fill={treatmentColor(entry.type)} />
            ))}
          </Scatter>
        )}
      </ComposedChart>
    </ResponsiveContainer>
  )
}
