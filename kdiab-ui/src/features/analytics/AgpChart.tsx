import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
  Legend,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { AgpHourlyData } from '../../api/analyzeApi'

// Inline SVG pattern defs are rendered outside the Recharts tree so they are
// accessible from the `fill="url(#...)"` refs inside the AreaChart SVG.
// They must be in a zero-size element sibling to the ResponsiveContainer.
function AgpPatternDefs() {
  return (
    <svg
      aria-hidden="true"
      style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden' }}
    >
      <defs>
        {/* Diagonal-line hatch for the P10–P90 outer band */}
        <pattern
          id="agp-p10p90-pattern"
          patternUnits="userSpaceOnUse"
          width="8"
          height="8"
        >
          <path
            d="M-1,1 l2,-2 M0,8 l8,-8 M7,9 l2,-2"
            stroke="var(--chart-p10-p90, #b3d9ff)"
            strokeWidth="2"
            opacity="0.6"
          />
        </pattern>
        {/* Dot pattern for the P25–P75 inner band */}
        <pattern
          id="agp-p25p75-pattern"
          patternUnits="userSpaceOnUse"
          width="6"
          height="6"
        >
          <circle
            cx="3"
            cy="3"
            r="1.5"
            fill="var(--chart-p25-p75, #5b9bd5)"
            opacity="0.7"
          />
        </pattern>
      </defs>
    </svg>
  )
}

interface Props {
  hourlyData: AgpHourlyData[]
  glucoseUnit: string
  warnings?: string[]
  totalReadingCount?: number
  sensorWearDays?: number
}

const MGDL_TO_MMOL = 1 / 18.0

function convert(val: number, unit: string): number {
  if (unit === 'mmol/L') return Math.round(val * MGDL_TO_MMOL * 10) / 10
  return Math.round(val)
}

export function AgpChart({ hourlyData, glucoseUnit, warnings, totalReadingCount, sensorWearDays }: Props) {
  const { t } = useTranslation()
  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'
  const tirLow = convert(70, glucoseUnit)
  const tirHigh = convert(180, glucoseUnit)

  const chartData = (hourlyData ?? [])
    .filter(
      (d): d is typeof d & { p10: number; p25: number; median: number; p75: number; p90: number } =>
        d.median !== null &&
        d.p10 !== null &&
        d.p25 !== null &&
        d.p75 !== null &&
        d.p90 !== null,
    )
    .map(d => ({
      hour: d.hour,
      p10_p90: [convert(d.p10, glucoseUnit), convert(d.p90, glucoseUnit)] as [number, number],
      p25_p75: [convert(d.p25, glucoseUnit), convert(d.p75, glucoseUnit)] as [number, number],
      median: convert(d.median, glucoseUnit),
      count: d.count,
    }))

  const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`

  return (
    <div className="card">
      {warnings && warnings.length > 0 && (
        <div className="warning-banner" role="alert">
          {warnings.map((w, i) => <p key={i}>{w}</p>)}
        </div>
      )}
      <h3>
        {t('analytics.agp')}
        <span
          title={t('analytics.agpHelp')}
          style={{ marginLeft: '0.5rem', fontSize: '0.85rem', color: 'var(--text-secondary)', cursor: 'help' }}
        >
          ⓘ
        </span>
      </h3>
      {(sensorWearDays !== undefined || totalReadingCount !== undefined) && (
        <p
          aria-label={t('analytics.agpDataQuality')}
          style={{ margin: '0 0 0.5rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}
        >
          {sensorWearDays !== undefined && (
            <span>{t('analytics.agpSensorWearDays', { count: sensorWearDays })}</span>
          )}
          {sensorWearDays !== undefined && totalReadingCount !== undefined && (
            <span style={{ margin: '0 0.4rem' }}>·</span>
          )}
          {totalReadingCount !== undefined && (
            <span>{t('analytics.agpTotalReadings', { count: totalReadingCount })}</span>
          )}
        </p>
      )}
      <figure role="img" aria-label={t('analytics.agpChartAriaLabel')} style={{ margin: 0, position: 'relative' }}>
        <figcaption style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap' }}>
          {t('analytics.agpChartCaption')}
        </figcaption>
        {/* Zero-size SVG hosts pattern defs referenced by fill="url(#...)" inside Recharts */}
        <AgpPatternDefs />
        <ResponsiveContainer width="100%" height={320}>
          <AreaChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              dataKey="hour"
              tickFormatter={formatHour}
              label={{ value: t('analytics.agpHour'), position: 'insideBottom', offset: -5, fill: 'var(--text-secondary)' }}
            />
            <YAxis
              label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 10, fill: 'var(--text-secondary)' }}
            />
            <Tooltip
              labelFormatter={(h: unknown) => formatHour(typeof h === 'number' ? h : 0)}
              formatter={(val: unknown, name: unknown) => {
                if (Array.isArray(val)) return [`${String(val[0])}–${String(val[1])} ${yLabel}`, String(name ?? '')]
                return [`${String(val)} ${yLabel}`, String(name ?? '')]
              }}
              contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
              wrapperStyle={{ outline: 'none' }}
            />
            {/*
              Legend names include shape descriptors so colorblind users and
              screen-reader users can distinguish bands without relying on color.
            */}
            <Legend
              formatter={(value: string) => (
                <span aria-label={value}>{value}</span>
              )}
            />

            <ReferenceLine y={tirLow} stroke="#ef4444" strokeDasharray="4 4" />
            <ReferenceLine y={tirHigh} stroke="#f59e0b" strokeDasharray="4 4" />

            {/*
              Outer band (P10–P90): diagonal-line SVG pattern fill + dashed stroke.
              The strokeDasharray "5 5" gives line-style variation even in
              environments where SVG pattern fill is flattened (e.g. some PDF exports).
            */}
            <Area
              type="monotone"
              dataKey="p10_p90"
              name="P10–P90 (diagonal lines)"
              stroke="var(--chart-p10-p90, #b3d9ff)"
              strokeDasharray="5 5"
              strokeWidth={1}
              fill="url(#agp-p10p90-pattern)"
            />

            {/*
              Inner band (P25–P75): dot SVG pattern fill + short-dash stroke.
              Distinct from P10–P90 by both fill texture and dash spacing.
            */}
            <Area
              type="monotone"
              dataKey="p25_p75"
              name="P25–P75 (dots)"
              stroke="var(--chart-p25-p75, #5b9bd5)"
              strokeDasharray="2 2"
              strokeWidth={1}
              fill="url(#agp-p25p75-pattern)"
            />

            {/* Median line: solid, no fill — always distinct from the bands */}
            <Area
              type="monotone"
              dataKey="median"
              name="Median (solid line)"
              stroke="var(--chart-median)"
              fill="transparent"
              strokeWidth={2}
              dot={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </figure>
    </div>
  )
}
