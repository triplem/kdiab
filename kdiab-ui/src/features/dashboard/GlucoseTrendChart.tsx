import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'
import {
  ComposedChart,
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceArea,
  ResponsiveContainer,
} from 'recharts'
import { type BasalBlock, type BasalProfilePoint, BASAL_COLORS } from './basalUtils'

interface ChartPoint {
  time: number
  sgv: number | null
  bgm: number | null
  marker: number | null
  treatmentType: string | null
  label: string | null
}

function treatmentAppearance(type: string): { color: string; shape: string } {
  if (type === 'BOLUS' || type === 'CORRECTION_BOLUS') return { color: '#3b82f6', shape: '▲' }
  if (type === 'CARBS' || type === 'MEAL')             return { color: '#f59e0b', shape: '●' }
  if (type === 'SITE_CHANGE')                          return { color: '#10b981', shape: '⊕' }
  if (type === 'SENSOR_INSERT')                        return { color: '#8b5cf6', shape: '◆' }
  if (type === 'INSULIN_CHANGE')                       return { color: '#ec4899', shape: '◈' }
  return { color: '#6366f1', shape: '▼' }
}

// BGM dot — large transparent hit target ensures Tooltip fires reliably on hover.
function BgmDot(props: unknown) {
  const p = props as Record<string, unknown>
  const cx = (p['cx'] as number) ?? 0
  const cy = (p['cy'] as number) ?? 0
  const eventProps: Record<string, unknown> = {}
  for (const key of Object.keys(p)) {
    if (key.startsWith('on')) eventProps[key] = p[key]
  }
  return (
    <g {...eventProps} style={{ cursor: 'crosshair' }}>
      <circle cx={cx} cy={cy} r={12} fill="transparent" pointerEvents="all" />
      <circle cx={cx} cy={cy} r={5} fill="#ef4444" stroke="#fff" strokeWidth={1.5} />
    </g>
  )
}

function BgmActiveDot(props: unknown) {
  const p = props as Record<string, unknown>
  const cx = (p['cx'] as number) ?? 0
  const cy = (p['cy'] as number) ?? 0
  return (
    <g>
      <circle cx={cx} cy={cy} r={12} fill="#ef4444" fillOpacity={0.2} stroke="#ef4444" strokeWidth={1} />
      <circle cx={cx} cy={cy} r={6} fill="#ef4444" stroke="#fff" strokeWidth={1.5} />
    </g>
  )
}

// Treatment marker shape — `unknown` satisfies Recharts' contravariant dot prop type; cast internally.
function TreatmentDot(props: unknown) {
  const p = props as Record<string, unknown>
  const cx = (p['cx'] as number) ?? 0
  const cy = (p['cy'] as number) ?? 0
  const payload = p['payload'] as { treatmentType?: string; label?: string } | undefined
  const { color, shape } = treatmentAppearance(payload?.treatmentType ?? '')
  const label = payload?.label ?? ''

  // Spread Recharts-injected event handlers so the shared Tooltip fires on hover
  const eventProps: Record<string, unknown> = {}
  for (const key of Object.keys(p)) {
    if (key.startsWith('on')) eventProps[key] = p[key]
  }

  return (
    <g {...eventProps} style={{ cursor: 'pointer' }}>
      <circle cx={cx} cy={cy} r={14} fill="transparent" pointerEvents="all" />
      <text x={cx} y={cy - 4} textAnchor="middle" fill={color} fontSize={12}>{shape}</text>
      {label && <text x={cx} y={cy + 14} textAnchor="middle" fill={color} fontSize={9}>{label}</text>}
    </g>
  )
}

function TreatmentActiveDot(props: unknown) {
  const p = props as Record<string, unknown>
  const cx = (p['cx'] as number) ?? 0
  const cy = (p['cy'] as number) ?? 0
  const payload = p['payload'] as { treatmentType?: string; label?: string } | undefined
  const { color, shape } = treatmentAppearance(payload?.treatmentType ?? '')
  const label = payload?.label ?? ''

  return (
    <g style={{ cursor: 'pointer' }}>
      <circle cx={cx} cy={cy} r={16} fill={color} fillOpacity={0.2} stroke={color} strokeWidth={1} />
      <text x={cx} y={cy - 4} textAnchor="middle" fill={color} fontSize={15}>{shape}</text>
      {label && <text x={cx} y={cy + 16} textAnchor="middle" fill={color} fontSize={9}>{label}</text>}
    </g>
  )
}

interface GlucoseTrendChartProps {
  chartData: ChartPoint[]
  cgmPoints: ChartPoint[]
  bgmPoints: ChartPoint[]
  treatmentMarkers: ChartPoint[]
  windowFrom: string
  windowTo: string
  glucoseUnit: string
  yLabel: string
  tirLow: number
  tirHigh: number
  isLoading: boolean
  basalBlocks?: BasalBlock[]
  basalProfileLine?: BasalProfilePoint[]
}

export function GlucoseTrendChart({
  chartData,
  cgmPoints,
  bgmPoints,
  treatmentMarkers,
  windowFrom,
  windowTo,
  glucoseUnit,
  yLabel,
  tirLow,
  tirHigh,
  isLoading,
  basalBlocks,
  basalProfileLine,
}: GlucoseTrendChartProps) {
  const { t } = useTranslation()
  const { formatTime } = useTimeFormat()

  // Basal overlay: domain is [-(maxRate * 4), 0] so max rate occupies top 25% of chart.
  // Values are negated so the area fills downward from the top edge.
  // maxBasalRate uses both scheduled (profile) and delivered (blocks) to handle the case
  // where blocks arrive before the profile line is populated.
  const maxBasalRate = useMemo(() => {
    const schedMax = basalProfileLine?.length ? Math.max(...basalProfileLine.map(p => p.sched)) : 0
    const deliveredMax = basalBlocks?.length ? Math.max(...basalBlocks.map(b => b.deliveredRate)) : 0
    return Math.max(schedMax, deliveredMax, 1)
  }, [basalProfileLine, basalBlocks])
  const basalDomain = useMemo(
    () => [-(maxBasalRate * 4), 0] as [number, number],
    [maxBasalRate]
  )
  const negatedBasalLine = useMemo(
    () => basalProfileLine?.map(p => ({ time: p.time, basalSched: -p.sched })) ?? [],
    [basalProfileLine]
  )
  const negatedDeliveredLine = useMemo(() => {
    if (!basalBlocks?.length) return []
    const pts: { time: number; basalDelivered: number }[] = []
    for (const block of basalBlocks) {
      pts.push({ time: block.startMs, basalDelivered: -block.deliveredRate })
      pts.push({ time: block.endMs - 1, basalDelivered: -block.deliveredRate })
    }
    return pts.sort((a, b) => a.time - b.time)
  }, [basalBlocks])
  const hasBasal = (basalBlocks?.length ?? 0) > 0 || negatedBasalLine.length > 0

  return (
    <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
      <h3 style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1rem' }}>
        {t('dashboard.cgmChart')}
      </h3>
      {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
      {cgmPoints.length > 0 && (
        <figure role="img" aria-label={t('dashboard.cgmChartAriaLabel')} style={{ margin: 0 }}>
          <figcaption style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)', whiteSpace: 'nowrap' }}>
            {t('dashboard.cgmChartCaption')}
          </figcaption>
        <ResponsiveContainer width="100%" height={220}>
          <ComposedChart data={chartData} margin={{ top: 5, right: hasBasal ? 0 : 10, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              dataKey="time"
              type="number"
              domain={[new Date(windowFrom).getTime(), new Date(windowTo).getTime()]}
              tickFormatter={(ms: number) => formatTime(new Date(ms).toISOString())}
              tick={{ fontSize: 11 }}
              scale="time"
            />
            <YAxis
              yAxisId="left"
              domain={[glucoseUnit === 'mmol/L' ? 2 : 40, glucoseUnit === 'mmol/L' ? 18 : 330]}
              tick={{ fontSize: 11 }}
              label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 15, style: { fontSize: 11 }, fill: 'var(--text-secondary)' }}
            />
            {hasBasal && (
              <YAxis
                yAxisId="basal"
                orientation="right"
                domain={basalDomain}
                hide={true}
                width={0}
              />
            )}
            <Tooltip
              labelFormatter={(ms: unknown) => formatTime(new Date(typeof ms === 'number' ? ms : 0).toISOString())}
              formatter={(v: unknown, name: unknown, entry: { payload?: { treatmentType?: string; label?: string } }) => {
                if (name === 'sgv' && typeof v === 'number') return [`${v} ${yLabel}`, 'CGM']
                if (name === 'bgm' && typeof v === 'number') return [`${v} ${yLabel}`, 'BGM']
                if (name === 'marker') {
                  const ttype = entry.payload?.treatmentType ?? ''
                  const lbl = entry.payload?.label ?? ''
                  return [lbl || ttype, ttype]
                }
                if (name === 'basalSched') return null
                if (name === 'basalDelivered') return null
                return [`${String(v)}`, String(name ?? '')]
              }}
              contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
              wrapperStyle={{ outline: 'none' }}
            />
            <ReferenceLine yAxisId="left" y={tirLow} stroke="#ef4444" strokeDasharray="4 4" />
            <ReferenceLine yAxisId="left" y={tirHigh} stroke="#f59e0b" strokeDasharray="4 4" />
            {/* Basal overlay: colored blocks at top of chart, then dashed scheduled rate line */}
            {hasBasal && basalBlocks?.map((block, i) => (
              <ReferenceArea
                key={i}
                yAxisId="basal"
                x1={block.startMs}
                x2={block.endMs}
                y1={-block.deliveredRate}
                y2={0}
                fill={BASAL_COLORS[block.state] ?? BASAL_COLORS['SCHEDULED']!}
                fillOpacity={0.65}
                stroke="none"
                ifOverflow="extendDomain"
              />
            ))}
            {hasBasal && negatedBasalLine.length > 0 && (
              <Line
                data={negatedBasalLine}
                dataKey="basalSched"
                name="basalSched"
                yAxisId="basal"
                stroke={BASAL_COLORS['SCHEDULED']!}
                strokeWidth={1}
                strokeDasharray="4 2"
                dot={false}
                legendType="none"
                isAnimationActive={false}
              />
            )}
            {hasBasal && negatedDeliveredLine.length > 0 && (
              <Area
                data={negatedDeliveredLine}
                dataKey="basalDelivered"
                name="basalDelivered"
                yAxisId="basal"
                type="stepAfter"
                stroke="#475569"
                strokeWidth={1.5}
                fill="#475569"
                fillOpacity={0.25}
                y2={0}
                dot={false}
                legendType="none"
                isAnimationActive={false}
              />
            )}
            <Line
              yAxisId="left"
              type="monotone"
              dataKey="sgv"
              stroke="var(--chart-median)"
              dot={false}
              strokeWidth={2}
              isAnimationActive={false}
              connectNulls={false}
            />
            {bgmPoints.length > 0 && (
              <Line
                yAxisId="left"
                dataKey="bgm"
                name="bgm"
                stroke="none"
                strokeWidth={0}
                dot={(props: object) => <BgmDot {...props} />}
                activeDot={(props: object) => <BgmActiveDot {...props} />}
                isAnimationActive={false}
                connectNulls={false}
              />
            )}
            {treatmentMarkers.length > 0 && (
              <Line
                yAxisId="left"
                dataKey="marker"
                name="marker"
                stroke="none"
                strokeWidth={0}
                dot={(props: object) => <TreatmentDot {...props} />}
                activeDot={(props: object) => <TreatmentActiveDot {...props} />}
                isAnimationActive={false}
                connectNulls={false}
              />
            )}
          </ComposedChart>
        </ResponsiveContainer>
        </figure>
      )}
      {!isLoading && cgmPoints.length === 0 && (
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
          {t('dashboard.noData')}
        </p>
      )}
      {/* Legend */}
      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
        <span><span style={{ color: '#ef4444' }}>●</span> {t('dashboard.legendBgm')}</span>
        <span><span style={{ color: '#3b82f6' }}>▲</span> {t('dashboard.legendBolus')}</span>
        <span><span style={{ color: '#f59e0b' }}>●</span> {t('dashboard.legendCarbs')}</span>
        <span><span style={{ color: '#10b981' }}>⊕</span> {t('dashboard.legendSiteChange')}</span>
        <span><span style={{ color: '#8b5cf6' }}>◆</span> {t('dashboard.legendSensorInsert')}</span>
        <span><span style={{ color: '#ec4899' }}>◈</span> {t('dashboard.legendInsulinChange')}</span>
        {hasBasal && (
          <>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['SCHEDULED'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendScheduled')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['ABOVE'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendAbove')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['BELOW'], borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendBelow')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, background: BASAL_COLORS['SUSPENDED'], border: '1px solid var(--border)', borderRadius: 2, marginRight: 3 }} />{t('dashboard.legendSuspended')}</span>
          </>
        )}
      </div>
    </div>
  )
}
