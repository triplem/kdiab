import { useTranslation } from 'react-i18next'
import type { DailyTrendResponse, HourlyTrendRow } from '../../api/analyzeApi'

// Zone colour map
const ZONE_COLORS: Record<NonNullable<HourlyTrendRow['zone']>, string> = {
  veryHypo: '#7b0a0a',  // dark red
  hypo: '#c0392b',       // red
  inRange: '#27ae60',    // green
  hyper: '#f39c12',      // yellow/amber
  veryHyper: '#e67e22',  // orange
  noData: '#bdc3c7',     // gray
}

// Trend arrow labels
const TREND_ARROWS: Record<NonNullable<HourlyTrendRow['trendZone']>, string> = {
  risingFast: '↑↑',
  rising: '↑',
  stable: '→',
  falling: '↓',
  fallingFast: '↓↓',
}

interface CellProps {
  row: HourlyTrendRow | undefined
  glucoseUnit: string
}

/** Single table cell: glucose value coloured by zone, optional trend arrow, optional carbs dot. */
function TrendCell({ row, glucoseUnit }: CellProps) {
  if (!row || row.zone === null || row.zone === 'noData' || row.meanGlucose === null) {
    return (
      <td
        style={{
          padding: '0.2rem 0.3rem',
          textAlign: 'center',
          background: '#f4f4f4',
          color: '#aaa',
          fontSize: '0.72rem',
          minWidth: '3.5rem',
          border: '1px solid var(--border, #e0e0e0)',
        }}
        aria-label="no data"
      >
        —
      </td>
    )
  }

  const bgColor = ZONE_COLORS[row.zone]
  const isLight = row.zone === 'inRange' || row.zone === 'hyper'
  const textColor = isLight ? '#1a1a1a' : '#fff'

  const glucoseDisplay =
    glucoseUnit === 'mmol/L'
      ? (row.meanGlucose / 18.0).toFixed(1)
      : Math.round(row.meanGlucose).toString()

  const trendArrow = row.trendZone !== null ? TREND_ARROWS[row.trendZone] : ''

  return (
    <td
      style={{
        padding: '0.2rem 0.3rem',
        textAlign: 'center',
        background: bgColor,
        color: textColor,
        fontSize: '0.72rem',
        minWidth: '3.5rem',
        border: '1px solid var(--border, #e0e0e0)',
        position: 'relative',
        whiteSpace: 'nowrap',
      }}
    >
      <span style={{ fontWeight: 600 }}>{glucoseDisplay}</span>
      {trendArrow && (
        <span style={{ fontSize: '0.65rem', marginLeft: '0.15rem', opacity: 0.9 }}>
          {trendArrow}
        </span>
      )}
      {row.carbsG > 0 && (
        <span
          aria-label="carbs"
          style={{
            display: 'inline-block',
            width: 5,
            height: 5,
            borderRadius: '50%',
            background: '#3498db',
            marginLeft: '0.2rem',
            verticalAlign: 'middle',
          }}
        />
      )}
    </td>
  )
}

interface DailyTrendTableProps {
  data: DailyTrendResponse
  glucoseUnit: string
}

/**
 * DailyTrendTable renders a grid of hours (rows) × days (columns).
 * Each cell shows the mean glucose for that hour on that day, coloured by glucose zone.
 */
export function DailyTrendTable({ data, glucoseUnit }: DailyTrendTableProps) {
  const { t } = useTranslation()

  // Cap at 14 days as per spec
  const days = data.days.slice(0, 14)

  // Build a lookup: dayIndex → hour → HourlyTrendRow
  const hourMap: Map<number, Map<number, HourlyTrendRow>> = new Map(
    days.map((day, dayIdx) => [
      dayIdx,
      new Map(day.hours.map(h => [h.hour, h])),
    ]),
  )

  const hours = Array.from({ length: 24 }, (_, i) => i)

  return (
    <div>
      {data.warnings && data.warnings.length > 0 && (
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

      {/* Legend */}
      <div
        aria-label={t('report.dailyTrend.legendAriaLabel')}
        style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.5rem', fontSize: '0.75rem' }}
      >
        {(
          [
            ['veryHypo', 'report.dailyTrend.zone.veryHypo'],
            ['hypo', 'report.dailyTrend.zone.hypo'],
            ['inRange', 'report.dailyTrend.zone.inRange'],
            ['hyper', 'report.dailyTrend.zone.hyper'],
            ['veryHyper', 'report.dailyTrend.zone.veryHyper'],
          ] as Array<[keyof typeof ZONE_COLORS, string]>
        ).map(([zone, labelKey]) => (
          <span key={zone} style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
            <span
              style={{
                display: 'inline-block',
                width: 12,
                height: 12,
                borderRadius: 2,
                background: ZONE_COLORS[zone],
              }}
            />
            {t(labelKey)}
          </span>
        ))}
        <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
          <span
            style={{
              display: 'inline-block',
              width: 6,
              height: 6,
              borderRadius: '50%',
              background: '#3498db',
            }}
          />
          {t('report.dailyTrend.carbsIndicator')}
        </span>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table
          role="grid"
          aria-label={t('report.dailyTrend.tableAriaLabel')}
          style={{ borderCollapse: 'collapse', fontSize: '0.8rem', tableLayout: 'auto' }}
        >
          <thead>
            <tr>
              <th
                scope="col"
                style={{
                  padding: '0.3rem 0.5rem',
                  textAlign: 'left',
                  background: 'var(--surface, #f9f9f9)',
                  border: '1px solid var(--border, #e0e0e0)',
                  whiteSpace: 'nowrap',
                }}
              >
                {t('report.dailyTrend.hourHeader')}
              </th>
              {days.map((day, idx) => (
                <th
                  key={idx}
                  scope="col"
                  style={{
                    padding: '0.3rem 0.4rem',
                    textAlign: 'center',
                    background: 'var(--surface, #f9f9f9)',
                    border: '1px solid var(--border, #e0e0e0)',
                    minWidth: '3.5rem',
                    fontSize: '0.72rem',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {day.date.slice(5)} {/* Show MM-DD */}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {hours.map(hour => (
              <tr key={hour}>
                <th
                  scope="row"
                  style={{
                    padding: '0.2rem 0.5rem',
                    textAlign: 'right',
                    background: 'var(--surface, #f9f9f9)',
                    border: '1px solid var(--border, #e0e0e0)',
                    fontWeight: 500,
                    fontSize: '0.72rem',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {String(hour).padStart(2, '0')}:00
                </th>
                {days.map((_, dayIdx) => (
                  <TrendCell
                    key={dayIdx}
                    row={hourMap.get(dayIdx)?.get(hour)}
                    glucoseUnit={glucoseUnit}
                  />
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data.days.length > 14 && (
        <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.4rem' }}>
          {t('report.dailyTrend.showingFirst', { shown: 14, total: data.days.length })}
        </p>
      )}
    </div>
  )
}
