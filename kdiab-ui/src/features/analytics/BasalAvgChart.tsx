import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { useTranslation } from 'react-i18next'

interface Props {
  /** Hourly buckets (index = UTC hour 0–23), each value is the avg rate in U/hr or null */
  hourlyAvg: (number | null)[]
}

export function BasalAvgChart({ hourlyAvg }: Props) {
  const { t } = useTranslation()

  const chartData = hourlyAvg.map((avg, hour) => ({
    hour,
    avg: avg !== null ? Math.round(avg * 100) / 100 : null,
  }))

  const hasData = hourlyAvg.some(v => v !== null)

  const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`

  return (
    <div className="card">
      <h3>
        {t('analytics.basalAvg')}
        <span
          title={t('analytics.basalAvgHelp')}
          style={{ marginLeft: '0.5rem', fontSize: '0.85rem', color: 'var(--text-secondary)', cursor: 'help' }}
        >
          ⓘ
        </span>
      </h3>
      {!hasData && (
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>{t('analytics.noData')}</p>
      )}
      {hasData && (
        <ResponsiveContainer width="100%" height={240}>
          <BarChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              dataKey="hour"
              tickFormatter={formatHour}
              label={{ value: t('analytics.agpHour'), position: 'insideBottom', offset: -5, fill: 'var(--text-secondary)' }}
            />
            <YAxis
              label={{ value: 'U/hr', angle: -90, position: 'insideLeft', offset: 10, fill: 'var(--text-secondary)' }}
            />
            <Tooltip
              labelFormatter={(h: unknown) => formatHour(typeof h === 'number' ? h : 0)}
              formatter={(val: unknown) => [`${String(val)} U/hr`, t('analytics.basalAvgRate')]}
              contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
              wrapperStyle={{ outline: 'none' }}
            />
            <Bar
              dataKey="avg"
              name={t('analytics.basalAvgRate')}
              fill="var(--chart-basal)"
              radius={[2, 2, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
