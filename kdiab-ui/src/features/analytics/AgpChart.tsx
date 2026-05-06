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

interface Props {
  hourlyData: AgpHourlyData[]
  glucoseUnit: string
}

const MGDL_TO_MMOL = 1 / 18.0

function convert(val: number, unit: string): number {
  if (unit === 'mmol/L') return Math.round(val * MGDL_TO_MMOL * 10) / 10
  return Math.round(val)
}

export function AgpChart({ hourlyData, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'
  const tirLow = convert(70, glucoseUnit)
  const tirHigh = convert(180, glucoseUnit)

  const chartData = hourlyData
    .filter(d => d.median !== null)
    .map(d => ({
      hour: d.hour,
      p10_p90: [convert(d.p10!, glucoseUnit), convert(d.p90!, glucoseUnit)] as [number, number],
      p25_p75: [convert(d.p25!, glucoseUnit), convert(d.p75!, glucoseUnit)] as [number, number],
      median: convert(d.median!, glucoseUnit),
      count: d.count,
    }))

  const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`

  return (
    <div className="card">
      <h3>
        {t('analytics.agp')}
        <span
          title={t('analytics.agpHelp')}
          style={{ marginLeft: '0.5rem', fontSize: '0.85rem', color: 'var(--text-secondary)', cursor: 'help' }}
        >
          ⓘ
        </span>
      </h3>
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
            labelFormatter={(h: number) => formatHour(h)}
            formatter={(val: number | [number, number], name: string) => {
              if (Array.isArray(val)) return [`${val[0]}–${val[1]} ${yLabel}`, name]
              return [`${val} ${yLabel}`, name]
            }}
            contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
            wrapperStyle={{ outline: 'none' }}
          />
          <Legend />

          <ReferenceLine y={tirLow} stroke="#ef4444" strokeDasharray="4 4" />
          <ReferenceLine y={tirHigh} stroke="#f59e0b" strokeDasharray="4 4" />

          <Area
            type="monotone"
            dataKey="p10_p90"
            name="p10–p90"
            stroke="transparent"
            fill="var(--chart-p10-p90)"
          />

          <Area
            type="monotone"
            dataKey="p25_p75"
            name="p25–p75"
            stroke="transparent"
            fill="var(--chart-p25-p75)"
          />

          <Area
            type="monotone"
            dataKey="median"
            name="Median"
            stroke="var(--chart-median)"
            fill="transparent"
            strokeWidth={2}
            dot={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}
