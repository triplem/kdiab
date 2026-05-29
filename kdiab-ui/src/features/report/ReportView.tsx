import { useTranslation } from 'react-i18next'
import type { ReportDataState } from './useReportData'
import type { ReportPageId } from './reportPages'
import { HbA1cCard } from '../analytics/HbA1cCard'
import { TimeInRangeBar } from '../analytics/TimeInRangeBar'
import { AgpChart } from '../analytics/AgpChart'
import { GlucoseDistributionPage } from './GlucoseDistributionPage'
import { WochengraphikPage } from './WochengraphikPage'
import { ProfilePage } from './ProfilePage'

interface Props {
  userId: string
  from: string
  to: string
  selectedPages: readonly ReportPageId[]
  data: ReportDataState
  glucoseUnit: string
  patientName?: string
}

interface ReportSectionProps {
  title: string
  isLoading: boolean
  isError: boolean
  errorMessage: string
  children: React.ReactNode
}

function ReportSection({ title, isLoading, isError, errorMessage, children }: ReportSectionProps) {
  const { t } = useTranslation()
  return (
    <section className="report-section">
      <h2 className="report-section-title">{title}</h2>
      {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('common.loading')}</p>}
      {isError && (
        <div className="report-section-error" role="alert">
          {errorMessage}
        </div>
      )}
      {!isLoading && !isError && children}
    </section>
  )
}

/**
 * ReportView renders the printable patient report.
 * Only selected pages are rendered (SUMMARY is always shown).
 * API calls for unselected pages are not made (managed by useReportData).
 */
export function ReportView({ from, to, selectedPages, data, glucoseUnit, patientName }: Props) {
  const { t } = useTranslation()

  const isSelected = (page: ReportPageId) => selectedPages.includes(page)

  // Format display date range
  const fromDisplay = from.slice(0, 10)
  const toDisplay = to.slice(0, 10)

  return (
    <div className="report-view" aria-label={t('report.viewAriaLabel')}>
      {/* Print-only header */}
      <div className="report-print-header" aria-hidden="true">
        <h2>{t('report.printTitle')}</h2>
        {patientName && <p>{t('report.patient')}: {patientName}</p>}
        <p>{t('report.dateRange')}: {fromDisplay} – {toDisplay}</p>
        <p style={{ fontSize: '0.75rem', color: '#666' }}>
          {t('report.generatedAt')}: {new Date().toLocaleString()}
        </p>
      </div>

      {/* SUMMARY — always rendered */}
      <ReportSection
        title={t('report.page.SUMMARY')}
        isLoading={data.summary.isLoading}
        isError={data.summary.isError}
        errorMessage={t('report.error.summary')}
      >
        {data.summary.data && (
          <>
            <HbA1cCard
              hba1c={data.summary.data.hba1c}
              meanGlucose={data.summary.data.meanGlucose ?? 0}
              tir={data.summary.data.tir}
              glucoseUnit={glucoseUnit}
              {...(data.summary.data.warnings !== undefined && { warnings: data.summary.data.warnings })}
            />
            <TimeInRangeBar tir={data.summary.data.tir} glucoseUnit={glucoseUnit} />
          </>
        )}
        {!data.summary.isLoading && !data.summary.isError && !data.summary.data && (
          <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
        )}
      </ReportSection>

      {/* AGP */}
      {isSelected('AGP') && (
        <ReportSection
          title={t('report.page.AGP')}
          isLoading={data.agp.isLoading}
          isError={data.agp.isError}
          errorMessage={t('report.error.agp')}
        >
          {data.agp.data && (
            <div className="chart-section">
              <AgpChart
                hourlyData={data.agp.data.hourlyData}
                glucoseUnit={glucoseUnit}
                {...(data.agp.data.warnings !== undefined && { warnings: data.agp.data.warnings })}
                {...(data.agp.data.totalReadingCount !== undefined && { totalReadingCount: data.agp.data.totalReadingCount })}
                {...(data.agp.data.sensorWearDays !== undefined && { sensorWearDays: data.agp.data.sensorWearDays })}
              />
            </div>
          )}
          {!data.agp.isLoading && !data.agp.isError && !data.agp.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* Daily Stats */}
      {isSelected('DAILY_STATS') && (
        <ReportSection
          title={t('report.page.DAILY_STATS')}
          isLoading={data.dailyStats.isLoading}
          isError={data.dailyStats.isError}
          errorMessage={t('report.error.dailyStats')}
        >
          {data.dailyStats.data && (
            <DailyStatsTable rows={data.dailyStats.data.rows} summary={data.dailyStats.data.summary} glucoseUnit={glucoseUnit} />
          )}
          {!data.dailyStats.isLoading && !data.dailyStats.isError && !data.dailyStats.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* Daily Trend */}
      {isSelected('DAILY_TREND') && (
        <ReportSection
          title={t('report.page.DAILY_TREND')}
          isLoading={data.dailyTrend.isLoading}
          isError={data.dailyTrend.isError}
          errorMessage={t('report.error.dailyTrend')}
        >
          {data.dailyTrend.data && (
            <p style={{ color: 'var(--text-secondary)', fontStyle: 'italic' }}>
              {t('report.dailyTrendDays', { count: data.dailyTrend.data.days.length })}
            </p>
          )}
          {!data.dailyTrend.isLoading && !data.dailyTrend.isError && !data.dailyTrend.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* Wochengraphik (Weekly Overlay Chart) */}
      {isSelected('WOCHENGRAPHIK') && (
        <ReportSection
          title={t('report.page.WOCHENGRAPHIK')}
          isLoading={data.dailyTrend.isLoading}
          isError={data.dailyTrend.isError}
          errorMessage={t('report.error.wochengraphik')}
        >
          {data.dailyTrend.data && (
            <WochengraphikPage
              data={data.dailyTrend.data}
              glucoseUnit={glucoseUnit}
            />
          )}
          {!data.dailyTrend.isLoading && !data.dailyTrend.isError && !data.dailyTrend.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* Glucose Distribution */}
      {isSelected('GLUCOSE_DISTRIBUTION') && (
        <ReportSection
          title={t('report.page.GLUCOSE_DISTRIBUTION')}
          isLoading={data.glucoseDistribution.isLoading}
          isError={data.glucoseDistribution.isError}
          errorMessage={t('report.error.glucoseDistribution')}
        >
          {data.glucoseDistribution.data && (
            <GlucoseDistributionPage
              buckets={data.glucoseDistribution.data.buckets}
              zonePercents={data.glucoseDistribution.data.zonePercents}
              unit={data.glucoseDistribution.data.unit}
              totalCount={data.glucoseDistribution.data.totalCount}
              {...(data.glucoseDistribution.data.warnings !== undefined && { warnings: data.glucoseDistribution.data.warnings })}
            />
          )}
          {!data.glucoseDistribution.isLoading && !data.glucoseDistribution.isError && !data.glucoseDistribution.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* Profile */}
      {isSelected('PROFILE') && (
        <ReportSection
          title={t('report.page.PROFILE')}
          isLoading={data.profile.isLoading}
          isError={data.profile.isError}
          errorMessage={t('report.error.profile')}
        >
          {data.profile.data && (
            <ProfilePage profiles={data.profile.data.profiles} glucoseUnit={glucoseUnit} />
          )}
          {!data.profile.isLoading && !data.profile.isError && !data.profile.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}
    </div>
  )
}

// ---- Inline sub-components ----

interface DailyStatRow {
  date: string
  cgmCount: number
  veryLowPercent: number | null
  lowPercent: number | null
  inRangePercent: number | null
  highPercent: number | null
  veryHighPercent: number | null
  median: number | null
  sd: number | null
  eHbA1c: number | null
}

interface DailyStatsTableProps {
  rows: DailyStatRow[]
  summary: DailyStatRow
  glucoseUnit: string
}

function DailyStatsTable({ rows, summary, glucoseUnit }: DailyStatsTableProps) {
  const { t } = useTranslation()
  const unit = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL'

  const fmt = (v: number | null): string => {
    if (v === null) return '—'
    return unit === 'mmol/L' ? (v / 18.0).toFixed(1) : Math.round(v).toString()
  }

  const fmtPct = (v: number | null): string => (v === null ? '—' : v.toFixed(1) + '%')

  const displayRows = rows.slice(0, 14) // Show max 14 rows for readability

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        <thead>
          <tr style={{ background: 'var(--surface)', borderBottom: '2px solid var(--border)' }}>
            <th style={{ padding: '0.4rem', textAlign: 'left' }}>{t('report.dailyStats.date')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>{t('report.dailyStats.readings')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>{t('report.dailyStats.veryLow')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>{t('report.dailyStats.low')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>{t('report.dailyStats.inRange')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>{t('report.dailyStats.high')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>{t('report.dailyStats.veryHigh')}</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>
              {t('report.dailyStats.median')} ({unit})
            </th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>SD</th>
            <th style={{ padding: '0.4rem', textAlign: 'right' }}>eHbA1c</th>
          </tr>
        </thead>
        <tbody>
          {displayRows.map((row) => (
            <tr key={row.date} style={{ borderBottom: '1px solid var(--border)' }}>
              <td style={{ padding: '0.35rem' }}>{row.date}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{row.cgmCount}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right', color: row.veryLowPercent !== null && row.veryLowPercent > 0 ? 'var(--color-error, #c0392b)' : 'inherit' }}>
                {fmtPct(row.veryLowPercent)}
              </td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(row.lowPercent)}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(row.inRangePercent)}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(row.highPercent)}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(row.veryHighPercent)}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmt(row.median)}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmt(row.sd)}</td>
              <td style={{ padding: '0.35rem', textAlign: 'right' }}>
                {row.eHbA1c !== null ? row.eHbA1c.toFixed(1) + '%' : '—'}
              </td>
            </tr>
          ))}
        </tbody>
        {/* Summary row */}
        <tfoot>
          <tr style={{ background: 'var(--surface)', fontWeight: 600, borderTop: '2px solid var(--border)' }}>
            <td style={{ padding: '0.35rem' }}>{t('report.dailyStats.summary')}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{summary.cgmCount}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(summary.veryLowPercent)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(summary.lowPercent)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(summary.inRangePercent)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(summary.highPercent)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmtPct(summary.veryHighPercent)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmt(summary.median)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>{fmt(summary.sd)}</td>
            <td style={{ padding: '0.35rem', textAlign: 'right' }}>
              {summary.eHbA1c !== null ? summary.eHbA1c.toFixed(1) + '%' : '—'}
            </td>
          </tr>
        </tfoot>
      </table>
      {rows.length > 14 && (
        <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.4rem' }}>
          {t('report.dailyStats.showingFirst', { shown: 14, total: rows.length })}
        </p>
      )}
    </div>
  )
}

