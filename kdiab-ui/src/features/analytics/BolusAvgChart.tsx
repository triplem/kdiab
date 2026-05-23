import {
  ComposedChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { useTranslation } from 'react-i18next'

// Bolus blue matching the ▲ treatment marker colour in GlucoseTrendChart
const BOLUS_COLOR = '#3b82f6'

interface Props {
  /** Hourly buckets (index = UTC hour 0–23), each value is the avg dose in U or null */
  hourlyAvg: (number | null)[]
}

export function BolusAvgChart({ hourlyAvg }: Props) {
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
        {t('analytics.bolusAvg')}
        <span
          title={t('analytics.bolusAvgHelp')}
          style={{ marginLeft: '0.5rem', fontSize: '0.85rem', color: 'var(--text-secondary)', cursor: 'help' }}
        >
          ⓘ
        </span>
      </h3>
      {!hasData && (
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>{t('analytics.noData')}</p>
      )}
      {hasData && (
        <figure role="img" aria-label={t('analytics.bolusAvgChartAriaLabel')} style={{ margin: 0 }}>
          <figcaption style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap' }}>
            {t('analytics.bolusAvgChartCaption')}
          </figcaption>
          <ResponsiveContainer width="100%" height={240}>
            <ComposedChart data={chartData} margin={{ top: 16, right: 20, left: 10, bottom: 10 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="hour"
                tickFormatter={formatHour}
                label={{ value: t('analytics.agpHour'), position: 'insideBottom', offset: -5, fill: 'var(--text-secondary)' }}
              />
              <YAxis
                domain={[0, 'auto']}
                label={{ value: 'U', angle: -90, position: 'insideLeft', offset: 10, fill: 'var(--text-secondary)' }}
              />
              <Tooltip
                labelFormatter={(h: unknown) => formatHour(typeof h === 'number' ? h : 0)}
                formatter={(val: unknown) => [`${String(val)} U`, t('analytics.bolusAvgDose')]}
                contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
                wrapperStyle={{ outline: 'none' }}
              />
              <Bar
                dataKey="avg"
                name={t('analytics.bolusAvgDose')}
                shape={(props: unknown) => {
                  const p = props as Record<string, unknown>
                  const x = (p['x'] as number) ?? 0
                  const y = (p['y'] as number) ?? 0
                  const width = (p['width'] as number) ?? 0
                  const height = (p['height'] as number) ?? 0
                  const value = p['value'] as number | null
                  if (!value || height <= 0) return <g />
                  const cx = x + width / 2
                  const tipSize = Math.min(width * 0.5, 8)
                  return (
                    <g>
                      <rect x={x} y={y} width={width} height={height} fill={BOLUS_COLOR} fillOpacity={0.7} />
                      <polygon
                        points={`${cx},${y - 2} ${cx - tipSize},${y + tipSize} ${cx + tipSize},${y + tipSize}`}
                        fill={BOLUS_COLOR}
                      />
                    </g>
                  )
                }}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </figure>
      )}
    </div>
  )
}
