import {
  ComposedChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import { BASAL_COLORS } from '../dashboard/basalUtils'

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

  // Always show at least 2.0 U/hr on the Y-axis so the chart has visual headroom
  // above the max rate and the step curve doesn't fill the full chart height.
  const yMax = Math.max(2, ...hourlyAvg.filter((v): v is number => v !== null))

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
        <figure role="img" aria-label={t('analytics.basalAvgChartAriaLabel')} style={{ margin: 0 }}>
          <figcaption style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap' }}>
            {t('analytics.basalAvgChartCaption')}
          </figcaption>
          <ResponsiveContainer width="100%" height={240}>
            <ComposedChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="hour"
                tickFormatter={formatHour}
                label={{ value: t('analytics.agpHour'), position: 'insideBottom', offset: -5, fill: 'var(--text-secondary)' }}
              />
              <YAxis
                domain={[0, yMax]}
                label={{ value: 'U/hr', angle: -90, position: 'insideLeft', offset: 10, fill: 'var(--text-secondary)' }}
              />
              <Tooltip
                labelFormatter={(h: unknown) => formatHour(typeof h === 'number' ? h : 0)}
                formatter={(val: unknown) => [`${String(val)} U/hr`, t('analytics.basalAvgRate')]}
                contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
                wrapperStyle={{ outline: 'none' }}
              />
              <Area
                type="stepAfter"
                dataKey="avg"
                name={t('analytics.basalAvgRate')}
                fill={BASAL_COLORS['SCHEDULED']!}
                fillOpacity={0.5}
                stroke={BASAL_COLORS['SCHEDULED']!}
                strokeWidth={1.5}
                dot={false}
                isAnimationActive={false}
                connectNulls={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </figure>
      )}
    </div>
  )
}
