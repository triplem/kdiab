import {
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import type { TimelineResponse } from '../../api/analyzeApi'
import { groupByDay, toDisplayValue, MAX_DAYS } from './dailyChartUtils'
import type { DayData, TreatmentMarker } from './dailyChartUtils'

/** TIR target zone thresholds in mg/dL */
const TIR_LOW_MGDL = 70
const TIR_HIGH_MGDL = 180

// ---- Tooltip ------------------------------------------------------------------

interface TooltipPayloadEntry {
  dataKey?: string | number | ((obj: unknown) => unknown)
  value?: unknown
  payload?: Record<string, unknown>
}

function DailyTooltip({
  active,
  payload,
  label,
  glucoseUnit,
}: {
  active?: boolean
  payload?: readonly TooltipPayloadEntry[]
  label?: number | string
  glucoseUnit: string
}) {
  if (!active || !payload?.length || label == null) return null

  const ts = Number(label)
  const time = new Date(ts).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  })
  const unit = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

  return (
    <div
      style={{
        background: 'var(--bg-primary, #fff)',
        border: '1px solid var(--border, #ccc)',
        borderRadius: 4,
        padding: '0.35rem 0.6rem',
        fontSize: '0.8rem',
      }}
    >
      <p style={{ margin: 0, fontWeight: 600 }}>{time}</p>
      {payload.map((p, i) => {
        const numVal = typeof p.value === 'number' ? p.value : undefined
        if (p.dataKey === 'value' && numVal !== undefined) {
          return (
            <p key={i} style={{ margin: 0 }}>
              {numVal} {unit}
            </p>
          )
        }
        return null
      })}
    </div>
  )
}

// ---- Single day chart ---------------------------------------------------------

/** X-axis domain for a full day: 00:00–23:59 */
function dayDomain(date: string): [number, number] {
  const start = new Date(`${date}T00:00:00`).getTime()
  const end = new Date(`${date}T23:59:59`).getTime()
  return [start, end]
}

function formatTimeOfDay(ts: number): string {
  return new Date(ts).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

interface DailyChartProps {
  day: DayData
  glucoseUnit: string
  /** Formatted date string for display (e.g. "15. Nov 2024") */
  dateLabel: string
}

/** Renders the chart for a single day */
function SingleDayChart({ day, glucoseUnit, dateLabel }: DailyChartProps) {
  const { t } = useTranslation()
  const domain = dayDomain(day.date)

  const tirLow = toDisplayValue(TIR_LOW_MGDL, glucoseUnit)
  const tirHigh = toDisplayValue(TIR_HIGH_MGDL, glucoseUnit)
  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

  // Y-axis domain: 40–300 mg/dL (or mmol/L equivalent), with 10% padding
  const yMin = toDisplayValue(40, glucoseUnit)
  const yMax = toDisplayValue(300, glucoseUnit)

  return (
    <div
      className="daily-chart-day"
      style={{ breakAfter: 'page', pageBreakAfter: 'always' }}
    >
      {/* Date header */}
      <h3
        className="daily-chart-date-header"
        style={{
          margin: '0 0 0.5rem',
          fontSize: '1rem',
          fontWeight: 700,
          color: 'var(--text-primary, #111)',
          breakAfter: 'avoid',
          pageBreakAfter: 'avoid',
        }}
      >
        {dateLabel}
      </h3>

      {/* Chart */}
      <figure
        role="img"
        aria-label={t('report.dailyChart.chartAriaLabel', { date: dateLabel })}
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
          {t('report.dailyChart.chartCaption', { date: dateLabel })}
        </figcaption>
        <ResponsiveContainer width="100%" height={280}>
          <ComposedChart margin={{ top: 8, right: 16, left: 8, bottom: 24 }}>
            <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.4} />

            {/* TIR target zone background band */}
            <ReferenceArea
              y1={tirLow}
              y2={tirHigh}
              fill="rgba(39, 174, 96, 0.08)"
              xAxisId={0}
            />

            {/* TIR boundary lines */}
            <ReferenceLine
              y={tirLow}
              stroke="var(--accent-danger, #c0392b)"
              strokeDasharray="4 4"
              strokeWidth={1}
              label={{
                value: String(tirLow),
                fill: 'var(--accent-danger, #c0392b)',
                fontSize: 10,
                position: 'right',
              }}
            />
            <ReferenceLine
              y={tirHigh}
              stroke="var(--accent-warning, #f39c12)"
              strokeDasharray="4 4"
              strokeWidth={1}
              label={{
                value: String(tirHigh),
                fill: 'var(--accent-warning, #f39c12)',
                fontSize: 10,
                position: 'right',
              }}
            />

            <XAxis
              dataKey="ts"
              type="number"
              domain={domain}
              scale="time"
              tickFormatter={formatTimeOfDay}
              tick={{ fontSize: 10 }}
              ticks={[0, 3, 6, 9, 12, 15, 18, 21].map(
                (h) => new Date(`${day.date}T${String(h).padStart(2, '0')}:00:00`).getTime(),
              )}
            />
            <YAxis
              domain={[yMin, yMax]}
              label={{
                value: yLabel,
                angle: -90,
                position: 'insideLeft',
                offset: 10,
                fontSize: 10,
              }}
              tick={{ fontSize: 10 }}
              width={42}
            />
            <Tooltip
              content={(props) => (
                <DailyTooltip {...props} glucoseUnit={glucoseUnit} />
              )}
            />

            {/* CGM glucose trace — nulls create line breaks at gaps */}
            <Line
              data={day.cgmPoints}
              dataKey="value"
              name={t('report.dailyChart.glucoseLabel')}
              stroke="var(--chart-cgm, #2563eb)"
              dot={false}
              activeDot={{ r: 3 }}
              strokeWidth={1.5}
              connectNulls={false}
              isAnimationActive={false}
            />

            {/* Treatment markers — rendered as reference lines on the x-axis */}
            {day.markers.map((m) => (
              <ReferenceLine
                key={`${m.type}-${m.ts}`}
                x={m.ts}
                stroke={markerStroke(m.type)}
                strokeWidth={1}
                strokeDasharray="2 2"
                label={{
                  value: m.label,
                  position: 'bottom',
                  fontSize: 8,
                  fill: markerStroke(m.type),
                  offset: 2,
                }}
              />
            ))}
          </ComposedChart>
        </ResponsiveContainer>
      </figure>

      {/* Treatment legend below chart */}
      {day.markers.length > 0 && (
        <TreatmentSummary markers={day.markers} />
      )}
    </div>
  )
}

/** Returns a stroke color for a treatment type */
function markerStroke(type: string): string {
  switch (type) {
    case 'BOLUS':
    case 'CORRECTION_BOLUS':
      return 'var(--accent-primary, #6366f1)'
    case 'CARBS':
      return 'var(--accent-success, #16a34a)'
    case 'SITE_CHANGE':
      return 'var(--accent-warning, #f59e0b)'
    case 'SENSOR_INSERT':
      return 'var(--text-secondary, #64748b)'
    case 'INSULIN_CHANGE':
      return 'var(--accent-danger, #dc2626)'
    default:
      return 'var(--text-secondary, #64748b)'
  }
}

// ---- Treatment summary row below chart ----------------------------------------

interface TreatmentSummaryProps {
  markers: TreatmentMarker[]
}

function TreatmentSummary({ markers }: TreatmentSummaryProps) {
  const { t } = useTranslation()

  // Aggregate: total bolus, total carbs, counts of site/sensor/insulin changes
  let totalBolus = 0
  let totalCarbs = 0
  let siteChanges = 0
  let sensorInserts = 0
  let insulinChanges = 0

  for (const m of markers) {
    if (m.type === 'BOLUS' || m.type === 'CORRECTION_BOLUS') {
      totalBolus += m.units ?? 0
    } else if (m.type === 'CARBS') {
      totalCarbs += m.carbsG ?? 0
    } else if (m.type === 'SITE_CHANGE') {
      siteChanges += 1
    } else if (m.type === 'SENSOR_INSERT') {
      sensorInserts += 1
    } else if (m.type === 'INSULIN_CHANGE') {
      insulinChanges += 1
    }
  }

  const parts: string[] = []
  if (totalBolus > 0)
    parts.push(t('report.dailyChart.totalBolus', { units: totalBolus.toFixed(1) }))
  if (totalCarbs > 0)
    parts.push(t('report.dailyChart.totalCarbs', { grams: Math.round(totalCarbs) }))
  if (siteChanges > 0)
    parts.push(t('report.dailyChart.siteChanges', { count: siteChanges }))
  if (sensorInserts > 0)
    parts.push(t('report.dailyChart.sensorInserts', { count: sensorInserts }))
  if (insulinChanges > 0)
    parts.push(t('report.dailyChart.insulinChanges', { count: insulinChanges }))

  if (parts.length === 0) return null

  return (
    <p
      style={{
        margin: '0.25rem 0 0',
        fontSize: '0.78rem',
        color: 'var(--text-secondary, #555)',
      }}
    >
      {parts.join(' · ')}
    </p>
  )
}

// ---- Main exported component --------------------------------------------------

interface Props {
  timeline: TimelineResponse
  glucoseUnit: string
}

/**
 * DailyChartPage renders one print page per day showing the glucose trace
 * with treatment event markers.
 *
 * - X axis spans 00:00–23:59 for each day.
 * - CGM trace breaks at gaps > 20 min.
 * - Treatment events are shown as labelled vertical reference lines.
 * - TIR target zone (70–180 mg/dL) is shown as a background band.
 * - Limited to the most recent MAX_DAYS (14) days to keep the report manageable.
 * - Each day uses `break-after: page` so each day prints on its own page.
 */
export function DailyChartPage({ timeline, glucoseUnit }: Props) {
  const { t } = useTranslation()

  const days = groupByDay(timeline, glucoseUnit)

  if (days.length === 0) {
    return (
      <p
        style={{ color: 'var(--text-secondary, #888)', fontStyle: 'italic' }}
        aria-label={t('report.dailyChart.noData')}
      >
        {t('report.dailyChart.noData')}
      </p>
    )
  }

  return (
    <div className="daily-chart-page" aria-label={t('report.dailyChart.sectionAriaLabel')}>
      {days.length < (timeline.measures.length > 0 ? MAX_DAYS : 0) && (
        // Note: this will only show if there were more days than the cap —
        // evaluated via the derived `days` count vs the total distinct dates.
        null
      )}
      {days.map((day) => {
        const dateLabel = new Date(`${day.date}T12:00:00`).toLocaleDateString(undefined, {
          weekday: 'short',
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        })
        return (
          <SingleDayChart
            key={day.date}
            day={day}
            glucoseUnit={glucoseUnit}
            dateLabel={dateLabel}
          />
        )
      })}

      {/* Advisory when data was capped to MAX_DAYS */}
      {days.length === MAX_DAYS && (
        <p
          style={{
            fontSize: '0.78rem',
            color: 'var(--text-secondary, #888)',
            marginTop: '0.5rem',
          }}
        >
          {t('report.dailyChart.capped', { count: MAX_DAYS })}
        </p>
      )}
    </div>
  )
}
