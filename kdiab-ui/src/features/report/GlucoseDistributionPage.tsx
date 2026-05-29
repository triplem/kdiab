import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { GlucoseBucket, ZonePercents } from '../../api/analyzeApi'

// Zone colour palette — matches clinical convention used across the app
const ZONE_COLORS: Record<string, string> = {
  veryLow: '#8b0000',  // dark red
  low: '#c0392b',      // red
  inRange: '#27ae60',  // green
  high: '#f1c40f',     // yellow
  veryHigh: '#e67e22', // orange
}

function getBarColor(zone: string): string {
  return ZONE_COLORS[zone] ?? '#95a5a6'
}

interface ZoneSummaryProps {
  zonePercents: ZonePercents
  totalCount: number
}

function ZoneSummary({ zonePercents, totalCount }: ZoneSummaryProps) {
  const { t } = useTranslation()

  const zones: Array<{ key: keyof ZonePercents; labelKey: string; color: string }> = [
    { key: 'veryLow', labelKey: 'report.glucoseDist.zoneVeryLow', color: ZONE_COLORS['veryLow'] ?? '#8b0000' },
    { key: 'low', labelKey: 'report.glucoseDist.zoneLow', color: ZONE_COLORS['low'] ?? '#c0392b' },
    { key: 'inRange', labelKey: 'report.glucoseDist.zoneInRange', color: ZONE_COLORS['inRange'] ?? '#27ae60' },
    { key: 'high', labelKey: 'report.glucoseDist.zoneHigh', color: ZONE_COLORS['high'] ?? '#f1c40f' },
    { key: 'veryHigh', labelKey: 'report.glucoseDist.zoneVeryHigh', color: ZONE_COLORS['veryHigh'] ?? '#e67e22' },
  ]

  return (
    <div className="glucose-dist-zone-summary" aria-label={t('report.glucoseDist.zoneSummaryAriaLabel')}>
      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
        {t('analytics.basedOnReadings', { count: totalCount })}
      </p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem' }}>
        {zones.map(({ key, labelKey, color }) => (
          <div key={key} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <div
              aria-hidden="true"
              style={{
                width: 14,
                height: 14,
                background: color,
                borderRadius: 2,
                flexShrink: 0,
              }}
            />
            <span style={{ fontSize: '0.85rem' }}>{t(labelKey)}</span>
            <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>
              {zonePercents[key].toFixed(1)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

interface Props {
  buckets: GlucoseBucket[]
  zonePercents: ZonePercents
  unit: string
  totalCount: number
  warnings?: string[]
}

/**
 * GlucoseDistributionPage renders the glucose distribution histogram section
 * of the patient report.
 *
 * It is a pure presentation component — all data is received via props from
 * the parent report orchestrator (ReportView). No API calls are made here.
 *
 * Layout:
 *   1. Warning banners (if any)
 *   2. Recharts BarChart — glucose value range on X axis, percent on Y axis,
 *      bars coloured by clinical zone (veryLow/low/inRange/high/veryHigh)
 *   3. Zone summary row — compact percent display for each zone
 */
export function GlucoseDistributionPage({ buckets, zonePercents, unit, totalCount, warnings }: Props) {
  const { t } = useTranslation()

  // Prepare chart data — only include buckets that have readings to avoid clutter
  const chartData = buckets
    .filter(b => b.count > 0)
    .map(b => ({
      label: `${b.lowerBound}–${b.upperBound}`,
      lowerBound: b.lowerBound,
      percent: b.percent,
      count: b.count,
      zone: b.zone,
    }))

  const hasData = chartData.length > 0

  return (
    <div className="glucose-dist-page">
      {/* Warning banners */}
      {warnings !== undefined && warnings.length > 0 && (
        <div role="alert" className="report-section-error" style={{ marginBottom: '0.75rem' }}>
          {warnings.map(w => (
            <p key={w} style={{ margin: '0.2rem 0' }}>{w}</p>
          ))}
        </div>
      )}

      {!hasData && (
        <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
      )}

      {hasData && (
        <>
          {/* Histogram */}
          <figure
            role="img"
            aria-label={t('report.glucoseDist.chartAriaLabel', { unit })}
            style={{ margin: 0 }}
          >
            <ResponsiveContainer width="100%" height={260}>
              <BarChart
                data={chartData}
                margin={{ top: 8, right: 16, left: 0, bottom: 40 }}
                barCategoryGap="2%"
              >
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border, #e0e0e0)" />
                <XAxis
                  dataKey="lowerBound"
                  type="number"
                  domain={['dataMin', 'dataMax']}
                  label={{
                    value: unit,
                    position: 'insideBottom',
                    offset: -8,
                    style: { fontSize: '0.78rem', fill: 'var(--text-secondary, #666)' },
                  }}
                  tick={{ fontSize: 10 }}
                  tickFormatter={(v: number) => String(v)}
                />
                <YAxis
                  tickFormatter={(v: number) => `${v}%`}
                  domain={[0, 'auto']}
                  tick={{ fontSize: 10 }}
                  width={40}
                />
                <Tooltip
                  formatter={(value: number, _name: string, entry: { payload: { label: string; count: number; zone: string } }) => [
                    `${value.toFixed(2)}% (${entry.payload.count} ${t('report.glucoseDist.readings')})`,
                    entry.payload.label,
                  ]}
                  labelFormatter={() => ''}
                  contentStyle={{ fontSize: '0.82rem' }}
                />
                <Bar dataKey="percent" maxBarSize={12} isAnimationActive={false}>
                  {chartData.map((entry) => (
                    <Cell
                      key={`cell-${entry.lowerBound}`}
                      fill={getBarColor(entry.zone)}
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            <figcaption style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', textAlign: 'center', marginTop: '0.25rem' }}>
              {t('report.glucoseDist.chartCaption')}
            </figcaption>
          </figure>

          {/* Zone summary */}
          <ZoneSummary zonePercents={zonePercents} totalCount={totalCount} />
        </>
      )}
    </div>
  )
}
