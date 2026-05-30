import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { usersApi } from '../../api/usersApi'
import { DateRangePicker } from './DateRangePicker'
import { buildInitialRange } from './dateRangeUtils'
import type { DateRange } from './dateRangeUtils'
import { ReportPageSelector } from './ReportPageSelector'
import { ReportView } from './ReportView'
import { useReportData } from './useReportData'
import { useReportPageSelection } from './useReportPageSelection'
import './report.css'

interface Props {
  userId: string
  glucoseUnit: string
  patientName?: string
}

/**
 * ReportPage — entry point for the PDF patient report feature.
 *
 * Responsibilities:
 * - Date range selection (presets + custom)
 * - Page selection (persisted in UserSettings + localStorage)
 * - Fan-out data fetching via useReportData
 * - Print / Download PDF trigger
 */
export function ReportPage({ userId, glucoseUnit, patientName }: Props) {
  const { t } = useTranslation()

  const [dateRange, setDateRange] = useState<DateRange>(buildInitialRange(14))
  const [showReport, setShowReport] = useState(false)

  // Fetch persisted page selection from UserSettings
  // TODO(#1118): Once kdiab-users backend adds reportPageSelection field,
  //              pass it directly: data?.settings?.reportPageSelection
  const { data: userData } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: () => usersApi.getMe().then(r => r.data),
    staleTime: 60 * 1000,
  })

  // reportPageSelection from UserSettings — gracefully undefined if field not yet in backend
  // TODO(#1118): Remove cast once kdiab-users backend adds reportPageSelection to UserSettings type
  const serverPageSelection =
    (userData?.settings as Record<string, unknown> | undefined)?.['reportPageSelection'] as string[] | undefined

  const { selectedPages, togglePage, selectAll, deselectAll } = useReportPageSelection(
    serverPageSelection,
  )

  const reportData = useReportData(
    userId,
    dateRange.from,
    dateRange.to,
    selectedPages,
    glucoseUnit,
    patientName,
  )

  const handleDateChange = useCallback((range: DateRange) => {
    setDateRange(range)
    setShowReport(false) // reset report view when date changes
  }, [])

  const handleGenerate = useCallback(() => {
    setShowReport(true)
  }, [])

  const handleDownloadPdf = useCallback(() => {
    window.print()
  }, [])

  return (
    <div className="report-page">
      <h2 style={{ marginBottom: '1.25rem' }}>{t('report.title')}</h2>

      {/* Controls — hidden in print */}
      <div className="report-controls" aria-label={t('report.controlsAriaLabel')}>
        {/* Date range selector */}
        <div>
          <h3 style={{ margin: '0 0 0.75rem', fontSize: '1rem' }}>{t('report.dateRangeTitle')}</h3>
          <DateRangePicker value={dateRange} onChange={handleDateChange} />
        </div>

        {/* Page selection */}
        <ReportPageSelector
          selectedPages={selectedPages}
          onToggle={togglePage}
          onSelectAll={selectAll}
          onDeselectAll={deselectAll}
        />

        {/* Action buttons */}
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="primary"
            onClick={handleGenerate}
            style={{ padding: '0.5rem 1.25rem' }}
          >
            {t('report.generate')}
          </button>
          {showReport && (
            <button
              type="button"
              className="btn outline"
              onClick={handleDownloadPdf}
              style={{ padding: '0.5rem 1.25rem' }}
            >
              {t('report.downloadPdf')}
            </button>
          )}
        </div>
      </div>

      {/* Loading state */}
      {showReport && reportData.isAnyLoading && (
        <div
          role="status"
          aria-live="polite"
          style={{ padding: '1rem 0', color: 'var(--text-secondary)' }}
        >
          {t('report.loading')}
        </div>
      )}

      {/* Report output */}
      {showReport && (
        <ReportView
          userId={userId}
          from={dateRange.from}
          to={dateRange.to}
          selectedPages={selectedPages}
          data={reportData}
          glucoseUnit={glucoseUnit}
          {...(patientName !== undefined && { patientName })}
        />
      )}
    </div>
  )
}
