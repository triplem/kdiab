import { useTranslation } from 'react-i18next'
import {
  ComposedChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LabelList,
} from 'recharts'
import type { ProfileSegment } from '../../api/analyzeApi'
import { buildBasalChartData, fmtMinute, fmtDuration } from './basalRateUtils'

const X_TICKS = [0, 120, 240, 360, 480, 600, 720, 840, 960, 1080, 1200, 1320, 1440]

interface Props {
  /** Basal segments from the active profile */
  segments: ProfileSegment[]
}

/**
 * BasalRatePage renders the basal rate schedule as a step chart
 * and a detailed segment table.
 *
 * This is a pure presentation component — all data is received via props.
 * Business logic (duration, IE, sorting) lives in basalRateUtils.ts.
 */
export function BasalRatePage({ segments }: Props) {
  const { t } = useTranslation()

  const { points, rows, totalDailyIE } = buildBasalChartData(segments)

  const hasData = segments.length > 0

  const yMax = hasData
    ? Math.max(1, ...segments.map(s => s.value)) * 1.2
    : 2

  return (
    <div className="basal-rate-page">
      {/* Summary stat */}
      <dl className="basal-rate-summary" aria-label={t('report.basalRate.summaryAriaLabel')}>
        <dt>{t('report.basalRate.totalDailyBasal')}</dt>
        <dd>
          <strong>{totalDailyIE.toFixed(2)}</strong>
          {' IE'}
        </dd>
      </dl>

      {!hasData && (
        <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
      )}

      {hasData && (
        <>
          {/* Step chart */}
          <figure
            role="img"
            aria-label={t('report.basalRate.chartAriaLabel')}
            style={{ margin: '1rem 0' }}
          >
            <figcaption
              style={{
                position: 'absolute',
                width: '1px',
                height: '1px',
                overflow: 'hidden',
                clip: 'rect(0,0,0,0)',
                whiteSpace: 'nowrap',
              }}
            >
              {t('report.basalRate.chartCaption')}
            </figcaption>
            <ResponsiveContainer width="100%" height={260}>
              <ComposedChart
                data={points}
                margin={{ top: 24, right: 24, left: 10, bottom: 20 }}
              >
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="minute"
                  type="number"
                  domain={[0, 1440]}
                  ticks={X_TICKS}
                  tickFormatter={fmtMinute}
                  label={{
                    value: t('report.basalRate.xAxisLabel'),
                    position: 'insideBottom',
                    offset: -12,
                    fill: 'var(--text-secondary)',
                    fontSize: 12,
                  }}
                />
                <YAxis
                  domain={[0, yMax]}
                  label={{
                    value: t('report.basalRate.yAxisLabel'),
                    angle: -90,
                    position: 'insideLeft',
                    offset: 14,
                    fill: 'var(--text-secondary)',
                    fontSize: 12,
                  }}
                  tickFormatter={(v: number) => v.toFixed(2)}
                />
                <Tooltip
                  labelFormatter={(m: unknown) =>
                    typeof m === 'number' ? fmtMinute(m) : String(m)
                  }
                  formatter={(val: unknown) => [
                    `${typeof val === 'number' ? val.toFixed(3) : String(val)} IE/h`,
                    t('report.basalRate.rate'),
                  ]}
                  contentStyle={{
                    backgroundColor: 'var(--tooltip-bg)',
                    border: '1px solid var(--tooltip-border)',
                    borderRadius: '8px',
                    color: 'var(--tooltip-text)',
                  }}
                  wrapperStyle={{ outline: 'none' }}
                />
                <Area
                  type="stepAfter"
                  dataKey="rate"
                  name={t('report.basalRate.rate')}
                  fill="#3b82f6"
                  fillOpacity={0.25}
                  stroke="#3b82f6"
                  strokeWidth={2}
                  dot={{ fill: '#3b82f6', r: 4 }}
                  isAnimationActive={false}
                  connectNulls={false}
                >
                  <LabelList
                    dataKey="rate"
                    position="top"
                    formatter={(v: number) => v.toFixed(2)}
                    style={{ fontSize: '0.7rem', fill: '#1d4ed8' }}
                  />
                </Area>
              </ComposedChart>
            </ResponsiveContainer>
          </figure>

          {/* Segment table */}
          <div style={{ overflowX: 'auto', marginTop: '1rem' }}>
            <table
              aria-label={t('report.basalRate.tableAriaLabel')}
              style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}
            >
              <thead>
                <tr
                  style={{
                    background: 'var(--surface)',
                    borderBottom: '2px solid var(--border)',
                  }}
                >
                  <th style={{ padding: '0.4rem 0.6rem', textAlign: 'left' }}>
                    {t('report.basalRate.colStartTime')}
                  </th>
                  <th style={{ padding: '0.4rem 0.6rem', textAlign: 'right' }}>
                    {t('report.basalRate.colRate')}
                  </th>
                  <th style={{ padding: '0.4rem 0.6rem', textAlign: 'right' }}>
                    {t('report.basalRate.colDuration')}
                  </th>
                  <th style={{ padding: '0.4rem 0.6rem', textAlign: 'right' }}>
                    {t('report.basalRate.colIE')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={row.startTime}
                    style={{ borderBottom: '1px solid var(--border)' }}
                  >
                    <td style={{ padding: '0.35rem 0.6rem' }}>{row.startTime}</td>
                    <td style={{ padding: '0.35rem 0.6rem', textAlign: 'right' }}>
                      {row.rate.toFixed(3)} IE/h
                    </td>
                    <td style={{ padding: '0.35rem 0.6rem', textAlign: 'right' }}>
                      {fmtDuration(row.durationMinutes)}
                    </td>
                    <td style={{ padding: '0.35rem 0.6rem', textAlign: 'right' }}>
                      {row.ie.toFixed(3)} IE
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr
                  style={{
                    background: 'var(--surface)',
                    fontWeight: 600,
                    borderTop: '2px solid var(--border)',
                  }}
                >
                  <td style={{ padding: '0.35rem 0.6rem' }} colSpan={3}>
                    {t('report.basalRate.totalDaily')}
                  </td>
                  <td style={{ padding: '0.35rem 0.6rem', textAlign: 'right' }}>
                    {totalDailyIE.toFixed(3)} IE
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
