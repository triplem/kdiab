import { useTranslation } from 'react-i18next'
import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
  ResponsiveContainer,
  Legend,
} from 'recharts'
import type { DailyTrendResponse, DailyTrendDay } from '../../api/analyzeApi'
import { OKABE_ITO, isoWeekdayIndex, groupByIsoWeek } from './weekUtils'

// ---------------------------------------------------------------------------
// Single-week chart
// ---------------------------------------------------------------------------

interface WeekChartProps {
  weekKey: string
  days: DailyTrendDay[]
  glucoseUnit: string
  targetLow: number
  targetHigh: number
  locale: string
}

/**
 * Renders one 24-hour overlay chart for a single ISO calendar week.
 * Up to 7 Lines are rendered — one per day — using the Okabe-Ito palette.
 */
function WeekChart({ weekKey, days, glucoseUnit, targetLow, targetHigh, locale }: WeekChartProps) {
  const { t } = useTranslation()

  // Build 24 x-axis points and attach each day's glucose as a separate key
  const chartData: Array<Record<string, number | null>> = Array.from({ length: 24 }, (_, h) => {
    const point: Record<string, number | null> = { hour: h }
    for (const day of days) {
      const hourRow = day.hours.find(r => r.hour === h)
      let glucose: number | null = null
      if (hourRow !== undefined && hourRow.meanGlucose !== null) {
        glucose =
          glucoseUnit === 'mmol/L'
            ? Math.round((hourRow.meanGlucose / 18.0) * 10) / 10
            : Math.round(hourRow.meanGlucose)
      }
      point[day.date] = glucose
    }
    return point
  })

  // Y-axis domain
  const yLow = glucoseUnit === 'mmol/L' ? 2 : 40
  const yHigh = glucoseUnit === 'mmol/L' ? 22 : 400

  // Convert target zone to display unit
  const zoneLow =
    glucoseUnit === 'mmol/L' ? Math.round((targetLow / 18.0) * 10) / 10 : Math.round(targetLow)
  const zoneHigh =
    glucoseUnit === 'mmol/L' ? Math.round((targetHigh / 18.0) * 10) / 10 : Math.round(targetHigh)

  // Hour x-axis ticks: every 2 hours
  const xTicks = Array.from({ length: 13 }, (_, i) => i * 2)

  // Format week label (e.g. "KW 03 / 2024")
  const [yearStr, wStr] = weekKey.split('-')
  const weekLabel = `${t('report.wochengraphik.weekLabel')} ${wStr?.replace('W', '')} / ${yearStr ?? ''}`

  // Build per-day legend entries
  const dayFormatter = new Intl.DateTimeFormat(locale, { weekday: 'short' })
  const fullFormatter = new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })

  return (
    <div
      className="wochengraphik-week"
      data-testid={`week-chart-${weekKey}`}
      style={{ marginBottom: '2rem' }}
    >
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.5rem' }}>{weekLabel}</h3>

      <figure
        role="img"
        aria-label={`${t('report.wochengraphik.chartAriaLabel')} ${weekLabel}`}
        style={{ margin: 0 }}
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
          {t('report.wochengraphik.chartCaption')} {weekLabel}
        </figcaption>

        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart data={chartData} margin={{ top: 16, right: 40, left: 10, bottom: 20 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />

            {/* Green target zone */}
            <ReferenceArea
              y1={zoneLow}
              y2={zoneHigh}
              fill="#27ae60"
              fillOpacity={0.12}
              ifOverflow="hidden"
            />

            <XAxis
              dataKey="hour"
              type="number"
              domain={[0, 23]}
              ticks={xTicks}
              tickFormatter={(h: unknown) => typeof h === 'number' ? `${String(h).padStart(2, '0')}:00` : String(h)}
              label={{
                value: t('report.wochengraphik.xAxisLabel'),
                position: 'insideBottom',
                offset: -12,
                fill: 'var(--text-secondary)',
                fontSize: 11,
              }}
            />

            {/* Left Y-axis */}
            <YAxis
              yAxisId="left"
              domain={[yLow, yHigh]}
              label={{
                value: glucoseUnit,
                angle: -90,
                position: 'insideLeft',
                offset: 14,
                fill: 'var(--text-secondary)',
                fontSize: 11,
              }}
              tickFormatter={(v: unknown) =>
                typeof v === 'number'
                  ? (glucoseUnit === 'mmol/L' ? v.toFixed(1) : String(Math.round(v)))
                  : String(v)
              }
              width={50}
            />

            {/* Right Y-axis (mirror label) */}
            <YAxis
              yAxisId="right"
              orientation="right"
              domain={[yLow, yHigh]}
              label={{
                value: glucoseUnit,
                angle: 90,
                position: 'insideRight',
                offset: -14,
                fill: 'var(--text-secondary)',
                fontSize: 11,
              }}
              tickFormatter={(v: unknown) =>
                typeof v === 'number'
                  ? (glucoseUnit === 'mmol/L' ? v.toFixed(1) : String(Math.round(v)))
                  : String(v)
              }
              width={50}
            />

            <Tooltip
              labelFormatter={(h: unknown) =>
                typeof h === 'number' ? `${String(h).padStart(2, '0')}:00` : String(h)
              }
              formatter={(value: unknown, name: unknown) => {
                const label =
                  typeof name === 'string'
                    ? (() => {
                        try {
                          return dayFormatter.format(new Date(name + 'T00:00:00'))
                        } catch {
                          return name
                        }
                      })()
                    : String(name)
                const display =
                  value === null
                    ? '—'
                    : glucoseUnit === 'mmol/L'
                      ? `${typeof value === 'number' ? value.toFixed(1) : String(value)} mmol/L`
                      : `${typeof value === 'number' ? Math.round(value) : String(value)} mg/dL`
                return [display, label]
              }}
              contentStyle={{
                backgroundColor: 'var(--tooltip-bg)',
                border: '1px solid var(--tooltip-border)',
                borderRadius: '8px',
                color: 'var(--tooltip-text)',
                fontSize: '0.8rem',
              }}
              wrapperStyle={{ outline: 'none' }}
            />

            {/* One Line per day */}
            {days.map(day => {
              const dowIdx = isoWeekdayIndex(day.date)
              const colour = OKABE_ITO[dowIdx] ?? '#000000'
              return (
                <Line
                  key={day.date}
                  yAxisId="left"
                  type="monotone"
                  dataKey={day.date}
                  stroke={colour}
                  strokeWidth={1.5}
                  dot={false}
                  connectNulls={false}
                  isAnimationActive={false}
                />
              )
            })}

            {/* Recharts Legend is not used here — we render a custom legend below */}
            <Legend content={() => null} />
          </ComposedChart>
        </ResponsiveContainer>
      </figure>

      {/* Custom legend: colour swatch + full localised date */}
      <div
        aria-label={t('report.wochengraphik.legendAriaLabel')}
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '0.6rem 1.2rem',
          marginTop: '0.5rem',
          fontSize: '0.8rem',
        }}
      >
        {days.map(day => {
          const dowIdx = isoWeekdayIndex(day.date)
          const colour = OKABE_ITO[dowIdx] ?? '#000000'
          let fullLabel = day.date
          try {
            fullLabel = fullFormatter.format(new Date(day.date + 'T00:00:00'))
          } catch {
            // fallback to date string
          }
          return (
            <span
              key={day.date}
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
              data-testid={`legend-${day.date}`}
            >
              <span
                aria-hidden="true"
                style={{
                  display: 'inline-block',
                  width: 16,
                  height: 4,
                  borderRadius: 2,
                  background: colour,
                  flexShrink: 0,
                }}
              />
              <span>{fullLabel}</span>
            </span>
          )
        })}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Public component
// ---------------------------------------------------------------------------

interface Props {
  data: DailyTrendResponse
  glucoseUnit: string
  /** Target zone low in mg/dL (from active profile, falls back to 70) */
  targetLow?: number
  /** Target zone high in mg/dL (from active profile, falls back to 180) */
  targetHigh?: number
  /** BCP 47 locale string for day abbreviations (e.g. "de-DE") */
  locale?: string
}

/**
 * WochengraphikPage renders the weekly overlay chart section of the patient report.
 *
 * The daily-trend data is grouped into ISO calendar weeks.
 * One chart per week shows up to 7 glucose curves overlaid on a 24-hour x-axis,
 * each line coloured with the Okabe-Ito colour-blind-safe palette assigned by
 * day-of-week index (Monday=0 → #E69F00, …, Sunday=6 → #CC79A7).
 *
 * A green ReferenceArea marks the target glucose zone.
 */
export function WochengraphikPage({
  data,
  glucoseUnit,
  targetLow = 70,
  targetHigh = 180,
  locale = 'en',
}: Props) {
  const { t } = useTranslation()

  const weekMap = groupByIsoWeek(data.days)

  if (weekMap.size === 0) {
    return <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
  }

  return (
    <div className="wochengraphik-page" data-testid="wochengraphik-page">
      {data.warnings !== undefined && data.warnings.length > 0 && (
        <div
          role="alert"
          style={{
            background: '#fff3cd',
            border: '1px solid #ffc107',
            borderRadius: 4,
            padding: '0.5rem 0.75rem',
            marginBottom: '0.75rem',
            fontSize: '0.85rem',
          }}
        >
          {data.warnings.map((w, i) => (
            <p key={i} style={{ margin: 0 }}>
              {w}
            </p>
          ))}
        </div>
      )}

      {Array.from(weekMap.entries()).map(([weekKey, days]) => (
        <WeekChart
          key={weekKey}
          weekKey={weekKey}
          days={days}
          glucoseUnit={glucoseUnit}
          targetLow={targetLow}
          targetHigh={targetHigh}
          locale={locale}
        />
      ))}
    </div>
  )
}
