import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'
import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  ReferenceArea,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'
import { type BasalBlock, BASAL_COLORS } from './basalUtils'

interface BasalProfilePoint {
  time: number
  sched: number
}

interface BasalHoverPoint {
  time: number
  delivered: number
  scheduled: number
  state: string
  startMs: number
  endMs: number
}

interface BasalRateChartProps {
  activeProfile: { name: string } | null | undefined
  basalBlocks: BasalBlock[]
  basalProfileLine: BasalProfilePoint[]
  windowFrom: string
  windowTo: string
}

export function BasalRateChart({
  activeProfile,
  basalBlocks,
  basalProfileLine,
  windowFrom,
  windowTo,
}: BasalRateChartProps) {
  const { t } = useTranslation()
  const { formatTime } = useTimeFormat()

  const basalHoverPoints: BasalHoverPoint[] = basalBlocks.map((b) => ({
    time: Math.round((b.startMs + b.endMs) / 2),
    delivered: b.deliveredRate,
    scheduled: b.scheduledRate,
    state: b.state,
    startMs: b.startMs,
    endMs: b.endMs,
  }))

  return (
    <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
      <h3 style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1rem' }}>
        {t('dashboard.basalChart')}
      </h3>
      {!activeProfile && (
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
          {t('dashboard.noProfile')}
        </p>
      )}
      {activeProfile && (
        <>
          <figure role="img" aria-label={t('dashboard.basalChartAriaLabel')} style={{ margin: 0 }}>
            <figcaption style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap' }}>
              {t('dashboard.basalChartCaption')}
            </figcaption>
          <ResponsiveContainer width="100%" height={120}>
            <ComposedChart data={basalProfileLine} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <Tooltip
                labelFormatter={(ms: unknown) => {
                  if (typeof ms !== 'number') return ''
                  const pt = basalHoverPoints.find(
                    (p) => ms >= p.startMs && ms <= p.endMs
                  ) ?? basalHoverPoints.find((p) => p.time === ms)
                  if (!pt) return formatTime(new Date(ms).toISOString())
                  return `${formatTime(new Date(pt.startMs).toISOString())} – ${formatTime(new Date(pt.endMs).toISOString())}`
                }}
                formatter={(v: unknown, name: unknown, entry: { payload?: BasalHoverPoint }) => {
                  if (name !== 'delivered' || typeof v !== 'number') return null
                  const pt = entry.payload
                  if (!pt) return [`${v.toFixed(2)} U/h`, '']
                  const state = pt.state === 'ABOVE' ? t('dashboard.basalAbove', { defaultValue: 'Temp above' })
                             : pt.state === 'BELOW' ? t('dashboard.basalBelow', { defaultValue: 'Temp below' })
                             : pt.state === 'SUSPENDED' ? t('dashboard.basalSuspended', { defaultValue: 'Suspended' })
                             : t('dashboard.basalScheduled', { defaultValue: 'Scheduled' })
                  const diff = pt.delivered !== pt.scheduled
                    ? ` (sched ${pt.scheduled.toFixed(2)})`
                    : ''
                  return [`${v.toFixed(2)} U/h${diff}`, state]
                }}
                contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
                wrapperStyle={{ outline: 'none' }}
              />
              <XAxis
                dataKey="time"
                type="number"
                domain={[new Date(windowFrom).getTime(), new Date(windowTo).getTime()]}
                tickFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
                tick={{ fontSize: 11 }}
                scale="time"
              />
              <YAxis
                tick={{ fontSize: 11 }}
                label={{ value: 'U/h', angle: -90, position: 'insideLeft', offset: 15, style: { fontSize: 11 }, fill: 'var(--text-secondary)' }}
                domain={[0, 'auto']}
              />
              {basalBlocks.map((block, i) => (
                <ReferenceArea
                  key={i}
                  x1={block.startMs}
                  x2={block.endMs}
                  y1={0}
                  y2={block.deliveredRate}
                  fill={BASAL_COLORS[block.state] ?? BASAL_COLORS['SCHEDULED']!}
                  fillOpacity={0.75}
                  stroke="none"
                  ifOverflow="extendDomain"
                />
              ))}
              <Line
                data={basalHoverPoints}
                dataKey="delivered"
                name="delivered"
                stroke="none"
                strokeWidth={0}
                dot={false}
                activeDot={false}
                isAnimationActive={false}
              />
              <Line
                dataKey="sched"
                stroke="var(--text-secondary)"
                strokeDasharray="6 3"
                strokeWidth={1.5}
                dot={false}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
          </figure>
          {/* Legend */}
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['SCHEDULED'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendScheduled')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['ABOVE'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendAbove')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['BELOW'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendBelow')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['SUSPENDED'], border: '1px solid var(--border)', borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendSuspended')}</span>
          </div>
        </>
      )}
    </div>
  )
}
