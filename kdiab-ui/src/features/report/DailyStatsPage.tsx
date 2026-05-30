import { useTranslation } from 'react-i18next'
import type { DailyStatRow } from '../../api/analyzeApi'

/**
 * Color-coding helpers for TIR zone cells.
 * Matches the standard clinical colour palette used across the app.
 */
const ZONE_COLORS = {
  veryLow: { bg: '#fce4e4', text: '#9b1c1c' },
  low: { bg: '#fef3cd', text: '#7d4a00' },
  inRange: { bg: '#dcfce7', text: '#14532d' },
  high: { bg: '#fff3cd', text: '#7c5700' },
  veryHigh: { bg: '#ede9fe', text: '#4c1d95' },
} as const

const ZONE_THRESHOLD = 0.1 // only colour-code if > 0.1 %

interface ZoneCellProps {
  value: number | null
  zone: keyof typeof ZONE_COLORS
}

function ZoneCell({ value, zone }: ZoneCellProps) {
  if (value === null) {
    return (
      <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>—</td>
    )
  }
  const shouldColor = value > ZONE_THRESHOLD
  const colors = ZONE_COLORS[zone]
  return (
    <td
      style={{
        padding: '0.3rem 0.4rem',
        textAlign: 'right',
        whiteSpace: 'nowrap',
        backgroundColor: shouldColor ? colors.bg : undefined,
        color: shouldColor ? colors.text : undefined,
        fontWeight: shouldColor ? 600 : undefined,
      }}
    >
      {value.toFixed(1)}%
    </td>
  )
}

interface Props {
  rows: DailyStatRow[]
  summary: DailyStatRow
  warnings?: string[]
  glucoseUnit: string
}

/**
 * DailyStatsPage renders the Tagesstatistik (daily statistics) table.
 *
 * - All values come from the parent — no data fetching here.
 * - Summary row is always rendered last in bold (tfoot).
 * - Warnings are listed above the table.
 * - Up to 90 rows; table overflows-x on screen and page-breaks in print.
 */
export function DailyStatsPage({ rows, summary, warnings, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const unit = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

  const fmtGlucose = (v: number | null): string => {
    if (v === null) return '—'
    if (unit === 'mmol/L') return (v / 18.0).toFixed(1)
    return Math.round(v).toString()
  }

  const fmtSd = (v: number | null): string => {
    if (v === null) return '—'
    if (unit === 'mmol/L') return (v / 18.0).toFixed(1)
    return Math.round(v).toString()
  }

  const fmtEHbA1c = (v: number | null): string =>
    v === null ? '—' : v.toFixed(1) + '%'

  // Limit to 90 days as per story spec
  const displayRows = rows.slice(0, 90)

  return (
    <div>
      {/* Warnings */}
      {warnings && warnings.length > 0 && (
        <ul
          role="list"
          aria-label={t('report.dailyStats.warningsLabel')}
          style={{
            margin: '0 0 0.75rem',
            padding: '0.5rem 0.75rem 0.5rem 1.5rem',
            background: '#fffbeb',
            border: '1px solid #f59e0b',
            borderRadius: '0.375rem',
            color: '#92400e',
            fontSize: '0.85rem',
            listStyle: 'disc',
          }}
        >
          {warnings.map((w, i) => (
            <li key={i}>{w}</li>
          ))}
        </ul>
      )}

      {/* Table — overflows horizontally on narrow screens */}
      <div
        role="region"
        style={{ overflowX: 'auto' }}
        aria-label={t('report.dailyStats.tableAriaLabel')}
      >
        <table
          style={{
            width: '100%',
            borderCollapse: 'collapse',
            fontSize: '0.82rem',
            tableLayout: 'auto',
          }}
        >
          <thead>
            <tr
              style={{
                background: 'var(--surface-color)',
                borderBottom: '2px solid var(--border-color)',
              }}
            >
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'left', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.date')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.readings')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.veryLow')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.low')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.inRange')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.high')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.veryHigh')}
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.p25')} ({unit})
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.median')} ({unit})
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.p75')} ({unit})
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                SD ({unit})
              </th>
              <th style={{ padding: '0.35rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.eHbA1c')}
              </th>
            </tr>
          </thead>
          <tbody>
            {displayRows.map((row) => (
              <tr
                key={row.date}
                style={{ borderBottom: '1px solid var(--border-color)' }}
              >
                <td style={{ padding: '0.3rem 0.4rem', whiteSpace: 'nowrap' }}>{row.date}</td>
                <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{row.cgmCount}</td>
                <ZoneCell value={row.veryLowPercent} zone="veryLow" />
                <ZoneCell value={row.lowPercent} zone="low" />
                <ZoneCell value={row.inRangePercent} zone="inRange" />
                <ZoneCell value={row.highPercent} zone="high" />
                <ZoneCell value={row.veryHighPercent} zone="veryHigh" />
                <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {fmtGlucose(row.p25)}
                </td>
                <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {fmtGlucose(row.median)}
                </td>
                <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {fmtGlucose(row.p75)}
                </td>
                <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {fmtSd(row.sd)}
                </td>
                <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {fmtEHbA1c(row.eHbA1c)}
                </td>
              </tr>
            ))}
          </tbody>
          {/* Summary row — bold, separated by double border */}
          <tfoot>
            <tr
              style={{
                background: 'var(--surface-color)',
                fontWeight: 700,
                borderTop: '2px solid var(--border-color)',
              }}
            >
              <td style={{ padding: '0.3rem 0.4rem', whiteSpace: 'nowrap' }}>
                {t('report.dailyStats.summary')}
              </td>
              <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{summary.cgmCount}</td>
              <ZoneCell value={summary.veryLowPercent} zone="veryLow" />
              <ZoneCell value={summary.lowPercent} zone="low" />
              <ZoneCell value={summary.inRangePercent} zone="inRange" />
              <ZoneCell value={summary.highPercent} zone="high" />
              <ZoneCell value={summary.veryHighPercent} zone="veryHigh" />
              <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap' }}>
                {fmtGlucose(summary.p25)}
              </td>
              <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap' }}>
                {fmtGlucose(summary.median)}
              </td>
              <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap' }}>
                {fmtGlucose(summary.p75)}
              </td>
              <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap' }}>
                {fmtSd(summary.sd)}
              </td>
              <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap' }}>
                {fmtEHbA1c(summary.eHbA1c)}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

      {/* Row count advisory when data was capped */}
      {rows.length > 90 && (
        <p
          style={{
            fontSize: '0.78rem',
            color: 'var(--text-secondary)',
            marginTop: '0.5rem',
          }}
        >
          {t('report.dailyStats.showingFirst', { shown: 90, total: rows.length })}
        </p>
      )}
    </div>
  )
}
