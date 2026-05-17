import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'
import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'

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
}: GlucoseTrendChartProps) {
  const { t } = useTranslation()
  const { formatTime } = useTimeFormat()

  return (
    <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
      <h3 style={{ marginTop: 0, marginBottom: '0.5rem', fontSize: '1rem' }}>
        {t('dashboard.cgmChart')}
      </h3>
      {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
      {cgmPoints.length > 0 && (
        <div aria-label={t('dashboard.cgmChartAriaLabel', { defaultValue: 'Blood glucose over time chart' })}>
        <ResponsiveContainer width="100%" height={220}>
          <ComposedChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
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
              domain={[glucoseUnit === 'mmol/L' ? 2 : 40, glucoseUnit === 'mmol/L' ? 18 : 330]}
              tick={{ fontSize: 11 }}
              label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 15, style: { fontSize: 11 }, fill: 'var(--text-secondary)' }}
            />
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
                return [`${String(v)}`, String(name ?? '')]
              }}
              contentStyle={{ backgroundColor: 'var(--tooltip-bg)', border: '1px solid var(--tooltip-border)', borderRadius: '8px', color: 'var(--tooltip-text)' }}
              wrapperStyle={{ outline: 'none' }}
            />
            <ReferenceLine y={tirLow} stroke="#ef4444" strokeDasharray="4 4" />
            <ReferenceLine y={tirHigh} stroke="#f59e0b" strokeDasharray="4 4" />
            <Line
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
                dataKey="bgm"
                name="bgm"
                stroke="none"
                strokeWidth={0}
                dot={{ fill: '#ef4444', stroke: '#fff', strokeWidth: 1.5, r: 5 }}
                activeDot={{ fill: '#ef4444', stroke: '#fff', strokeWidth: 1.5, r: 7 }}
                isAnimationActive={false}
                connectNulls={false}
              />
            )}
            {treatmentMarkers.length > 0 && (
              <Line
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
        </div>
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
      </div>
    </div>
  )
}
