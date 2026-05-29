import { useTranslation } from 'react-i18next'
import {
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  ResponsiveContainer,
  Legend,
} from 'recharts'
import type { ReportDataState } from './useReportData'
import type { ReportPageId } from './reportPages'
import type { CgpResponse } from '../../api/analyzeApi'
import { HbA1cCard } from '../analytics/HbA1cCard'
import { TimeInRangeBar } from '../analytics/TimeInRangeBar'
import { DailyStatsPage } from './DailyStatsPage'
import { AgpChartPage } from './AgpChartPage'
import { WochengraphikPage } from './WochengraphikPage'
import { BasalRatePage } from './BasalRatePage'
import { ProfilePage } from './ProfilePage'
import { DailyChartPage } from './DailyChartPage'
import { DailyTrendTable } from './DailyTrendPage'

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
            <AgpChartPage
              agp={data.agp.data}
              glucoseUnit={glucoseUnit}
              {...(data.summary.data !== null && data.summary.data !== undefined && { tir: data.summary.data.tir })}
            />
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
            <DailyStatsPage
              rows={data.dailyStats.data.rows}
              summary={data.dailyStats.data.summary}
              glucoseUnit={glucoseUnit}
              {...(data.dailyStats.data.warnings !== undefined && { warnings: data.dailyStats.data.warnings })}
            />
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
            <DailyTrendTable
              data={data.dailyTrend.data}
              glucoseUnit={glucoseUnit}
            />
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

      {/* Daily Charts */}
      {isSelected('DAILY_CHARTS') && (
        <ReportSection
          title={t('report.page.DAILY_CHARTS')}
          isLoading={data.dailyCharts.isLoading}
          isError={data.dailyCharts.isError}
          errorMessage={t('report.error.dailyCharts')}
        >
          {data.dailyCharts.data && (
            <DailyChartPage timeline={data.dailyCharts.data} glucoseUnit={glucoseUnit} />
          )}
          {!data.dailyCharts.isLoading && !data.dailyCharts.isError && !data.dailyCharts.data && (
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
            <GlucoseDistributionSummary
              zonePercents={data.glucoseDistribution.data.zonePercents}
              totalCount={data.glucoseDistribution.data.totalCount}
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
            <ProfilePage
              profiles={data.profile.data.profiles}
              glucoseUnit={glucoseUnit}
            />
          )}
          {!data.profile.isLoading && !data.profile.isError && !data.profile.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* CGP */}
      {isSelected('CGP') && (
        <ReportSection
          title={t('report.page.CGP')}
          isLoading={data.cgp.isLoading}
          isError={data.cgp.isError}
          errorMessage={t('report.error.cgp')}
        >
          {data.cgp.data && (
            <CgpPentagonChart cgp={data.cgp.data} />
          )}
          {!data.cgp.isLoading && !data.cgp.isError && !data.cgp.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}

      {/* Basal Rate Chart */}
      {isSelected('BASAL_RATE') && (
        <ReportSection
          title={t('report.page.BASAL_RATE')}
          isLoading={data.profile.isLoading}
          isError={data.profile.isError}
          errorMessage={t('report.error.basalRate')}
        >
          {data.profile.data && (
            <BasalRatePage
              segments={data.profile.data.profiles[0]?.basal ?? []}
            />
          )}
          {!data.profile.isLoading && !data.profile.isError && !data.profile.data && (
            <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
          )}
        </ReportSection>
      )}
    </div>
  )
}

// ---- CGP pentagon chart ----

interface CgpPentagonChartProps {
  cgp: CgpResponse
}

/**
 * Renders the Comprehensive Glucose Pentagon (CGP) with two Radar layers:
 * - Green reference pentagon (optimal values)
 * - Yellow/orange patient pentagon (normalised 0–1 per axis)
 *
 * Based on: Vigersky et al. (2018). Journal of Diabetes Science and Technology, 12(1), 114–123.
 */
function CgpPentagonChart({ cgp }: CgpPentagonChartProps) {
  const { t } = useTranslation()

  const axes = [
    { axis: t('report.cgp.axisTor'),      patient: cgp.normTor,         ref: 1 - cgp.refTor / 1440 },
    { axis: t('report.cgp.axisVarK'),     patient: cgp.normVarK,        ref: 1 - cgp.refVarK / 50 },
    { axis: t('report.cgp.axisHypo'),     patient: cgp.normHypo,        ref: 1 },
    { axis: t('report.cgp.axisHyper'),    patient: cgp.normHyper,       ref: 1 },
    { axis: t('report.cgp.axisMeanGluc'), patient: cgp.normMeanGlucose, ref: 1 },
  ]

  const pgrRiskKey = `report.cgp.risk.${cgp.pgrRisk}`
  const pgrRiskLabel = t(pgrRiskKey)

  const pgrColor = cgp.pgr >= 4.5
    ? '#27ae60'
    : cgp.pgr >= 4.0
    ? '#2ecc71'
    : cgp.pgr >= 3.0
    ? '#f39c12'
    : cgp.pgr >= 2.0
    ? '#e67e22'
    : '#c0392b'

  return (
    <div>
      {/* Warnings */}
      {cgp.warnings && cgp.warnings.length > 0 && (
        <div
          role="alert"
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: 4,
            padding: '0.5rem 0.75rem',
            marginBottom: '1rem',
            fontSize: '0.85rem',
            color: 'var(--text-secondary)',
          }}
        >
          {cgp.warnings.map((w, i) => (
            <div key={i}>{w}</div>
          ))}
        </div>
      )}

      {/* PGR score badge */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
        <div
          style={{
            background: pgrColor,
            color: '#fff',
            borderRadius: '50%',
            width: 64,
            height: 64,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            fontSize: '1.4rem',
            flexShrink: 0,
          }}
          aria-label={t('report.cgp.pgrScore')}
        >
          {cgp.pgr.toFixed(1)}
        </div>
        <div>
          <div style={{ fontWeight: 600 }}>{t('report.cgp.pgrScore')}</div>
          <div style={{ fontSize: '0.9rem', color: pgrColor, fontWeight: 600 }}>
            {pgrRiskLabel}
          </div>
        </div>
      </div>

      {/* Pentagon chart */}
      <div className="chart-section" style={{ height: 320 }}>
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={axes} margin={{ top: 10, right: 30, bottom: 10, left: 30 }}>
            <PolarGrid />
            <PolarAngleAxis dataKey="axis" tick={{ fontSize: 12 }} />
            {/* Reference pentagon */}
            <Radar
              name={t('report.cgp.legendRef')}
              dataKey="ref"
              stroke="#27ae60"
              fill="#27ae60"
              fillOpacity={0.15}
              strokeWidth={2}
            />
            {/* Patient pentagon */}
            <Radar
              name={t('report.cgp.legendPatient')}
              dataKey="patient"
              stroke="#e67e22"
              fill="#e67e22"
              fillOpacity={0.35}
              strokeWidth={2}
            />
            <Legend />
          </RadarChart>
        </ResponsiveContainer>
      </div>

      {/* Raw values legend table */}
      <table
        style={{
          width: '100%',
          borderCollapse: 'collapse',
          fontSize: '0.8rem',
          marginTop: '0.75rem',
        }}
      >
        <thead>
          <tr style={{ borderBottom: '2px solid var(--border)', background: 'var(--surface)' }}>
            <th style={{ padding: '0.3rem 0.4rem', textAlign: 'left' }}>{t('report.cgp.axisLabel')}</th>
            <th style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{t('report.cgp.patientValue')}</th>
            <th style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{t('report.cgp.referenceValue')}</th>
          </tr>
        </thead>
        <tbody>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '0.3rem 0.4rem' }}>{t('report.cgp.axisTor')}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.tor.toFixed(0)} min/d</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.refTor.toFixed(0)} min/d</td>
          </tr>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '0.3rem 0.4rem' }}>{t('report.cgp.axisVarK')}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.varK.toFixed(1)} %</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.refVarK.toFixed(1)} %</td>
          </tr>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '0.3rem 0.4rem' }}>{t('report.cgp.axisHypo')}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.hypoIntensity.toFixed(0)}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.refHypo.toFixed(0)}</td>
          </tr>
          <tr style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: '0.3rem 0.4rem' }}>{t('report.cgp.axisHyper')}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.hyperIntensity.toFixed(0)}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.refHyper.toFixed(0)}</td>
          </tr>
          <tr>
            <td style={{ padding: '0.3rem 0.4rem' }}>{t('report.cgp.axisMeanGluc')}</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.meanGlucose.toFixed(0)} mg/dL</td>
            <td style={{ padding: '0.3rem 0.4rem', textAlign: 'right' }}>{cgp.refMeanGlucose.toFixed(0)} mg/dL</td>
          </tr>
        </tbody>
      </table>

      {/* Citation */}
      <p
        style={{
          marginTop: '0.75rem',
          fontSize: '0.75rem',
          color: 'var(--text-secondary)',
          fontStyle: 'italic',
        }}
      >
        {t('report.cgp.citation')}
      </p>
    </div>
  )
}

// ---- Inline sub-components ----

interface GlucoseDistributionSummaryProps {
  zonePercents: {
    veryLow: number
    low: number
    inRange: number
    high: number
    veryHigh: number
  }
  totalCount: number
}

function GlucoseDistributionSummary({ zonePercents, totalCount }: GlucoseDistributionSummaryProps) {
  const { t } = useTranslation()

  const zones: Array<{ key: keyof typeof zonePercents; labelKey: string; color: string }> = [
    { key: 'veryLow', labelKey: 'analytics.tirVeryLow', color: '#c0392b' },
    { key: 'low', labelKey: 'analytics.tirLow', color: '#e67e22' },
    { key: 'inRange', labelKey: 'analytics.tirInRange', color: '#27ae60' },
    { key: 'high', labelKey: 'analytics.tirHigh2', color: '#f39c12' },
    { key: 'veryHigh', labelKey: 'analytics.tirHighLabel', color: '#8e44ad' },
  ]

  return (
    <div>
      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
        {t('analytics.basedOnReadings', { count: totalCount })}
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
        {zones.map(({ key, labelKey, color }) => (
          <div key={key} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: `${Math.max(zonePercents[key], 0.5)}%`,
                minWidth: 4,
                maxWidth: '60%',
                height: 18,
                background: color,
                borderRadius: 2,
                flexShrink: 0,
              }}
            />
            <span style={{ fontSize: '0.85rem', minWidth: 60 }}>{t(labelKey)}</span>
            <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>
              {zonePercents[key].toFixed(1)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
