import { useTranslation } from 'react-i18next'
import { AgpChart } from '../analytics/AgpChart'
import { TimeInRangeBar } from '../analytics/TimeInRangeBar'
import type { AgpResponse, TirBreakdown } from '../../api/analyzeApi'

interface Props {
  agp: AgpResponse
  glucoseUnit: string
  /** TIR breakdown from the summary section — rendered as a color bar below the chart */
  tir?: TirBreakdown
}

/**
 * AgpChartPage renders the AGP (Ambulatory Glucose Profile) section for the
 * patient report. It is a pure presentation component — all data is received
 * via props from the parent report orchestrator.
 *
 * Layout:
 *   1. Stats summary (reading count, sensor wear days, warnings)
 *   2. AGP percentile chart (via AgpChart)
 *   3. TIR color bar (optional — only shown when tir prop is provided)
 */
export function AgpChartPage({ agp, glucoseUnit, tir }: Props) {
  const { t } = useTranslation()

  return (
    <div className="agp-chart-page">
      {/* Stats summary row */}
      <AgpStatsSummary
        {...(agp.totalReadingCount !== undefined && { totalReadingCount: agp.totalReadingCount })}
        {...(agp.sensorWearDays !== undefined && { sensorWearDays: agp.sensorWearDays })}
        {...(agp.warnings !== undefined && { warnings: agp.warnings })}
      />

      {/* AGP percentile chart */}
      <AgpChart
        hourlyData={agp.hourlyData}
        glucoseUnit={glucoseUnit}
        {...(agp.warnings !== undefined && { warnings: agp.warnings })}
        {...(agp.totalReadingCount !== undefined && { totalReadingCount: agp.totalReadingCount })}
        {...(agp.sensorWearDays !== undefined && { sensorWearDays: agp.sensorWearDays })}
      />

      {/* TIR color bar — optional */}
      {tir !== undefined && (
        <div className="agp-tir-section">
          <h4 className="agp-tir-title">{t('analytics.tir')}</h4>
          <TimeInRangeBar tir={tir} glucoseUnit={glucoseUnit} />
        </div>
      )}
    </div>
  )
}

// ---- Inline sub-component ----

interface AgpStatsSummaryProps {
  totalReadingCount?: number
  sensorWearDays?: number
  warnings?: string[]
}

function AgpStatsSummary({ totalReadingCount, sensorWearDays, warnings }: AgpStatsSummaryProps) {
  const { t } = useTranslation()

  const hasStats = totalReadingCount !== undefined || sensorWearDays !== undefined

  return (
    <div className="agp-stats-summary" aria-label={t('report.agp.statsSummaryAriaLabel')}>
      {hasStats && (
        <dl className="agp-stats-list">
          {sensorWearDays !== undefined && (
            <>
              <dt>{t('report.agp.sensorWearDays')}</dt>
              <dd>{t('analytics.agpSensorWearDays', { count: sensorWearDays })}</dd>
            </>
          )}
          {totalReadingCount !== undefined && (
            <>
              <dt>{t('report.agp.totalReadings')}</dt>
              <dd>{t('analytics.agpTotalReadings', { count: totalReadingCount })}</dd>
            </>
          )}
        </dl>
      )}

      {warnings && warnings.length > 0 && (
        <div className="warning-banner" role="alert">
          {warnings.map((w, i) => (
            <p key={i}>{w}</p>
          ))}
        </div>
      )}
    </div>
  )
}
