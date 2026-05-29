import { useTranslation } from 'react-i18next'
import type { ReportSummaryResponse, TirResult, TirZone } from '../../api/analyzeApi'

interface Props {
  data: ReportSummaryResponse
  glucoseUnit: string
}

/**
 * SummaryPage (Auswertung) — renders the comprehensive patient report summary.
 *
 * Sections:
 * 1. Patient header (name, days analysed, CGM info, device event counts)
 * 2. TIR profile thresholds (5-zone colour bar)
 * 3. TIR standard thresholds 54/70/180/250
 * 4. Glucose statistics (min, max, mean, SD, GVI, PGS, GRI, eHbA1c)
 * 5. Daily averages (carbs, bolus, basal, total insulin)
 *
 * This component is purely presentational — all data is passed as props.
 */
export function SummaryPage({ data, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const isMmol = glucoseUnit === 'mmol/L'

  const fmtGlucose = (mg: number | null): string => {
    if (mg === null) return '—'
    if (isMmol) return (mg / 18.0).toFixed(1)
    return Math.round(mg).toString()
  }

  const fmtNum = (v: number | null, decimals = 1): string =>
    v === null ? '—' : v.toFixed(decimals)

  const unit = isMmol ? 'mmol/L' : 'mg/dL'

  return (
    <div className="summary-page">
      {/* ---- 1. Patient header ---- */}
      <PatientHeader data={data} t={t} />

      {/* ---- 2. TIR profile thresholds ---- */}
      <TirSection
        title={t('report.summary.tirProfile')}
        {...(data.tirProfile.customTirFallback ? { subtitle: t('report.summary.tirFallback') } : {})}
        tir={data.tirProfile}
        glucoseUnit={unit}
        t={t}
      />

      {/* ---- 3. TIR standard thresholds ---- */}
      <TirSection
        title={t('report.summary.tirStandard')}
        tir={data.tirStandard}
        glucoseUnit={unit}
        standardLabels
        t={t}
      />

      {/* ---- 4. Glucose statistics ---- */}
      <section className="summary-section" aria-label={t('report.summary.glucoseStats')}>
        <h3 className="summary-section-title">{t('report.summary.glucoseStats')}</h3>
        <div className="summary-stats-grid">
          <StatItem label={t('report.summary.minGlucose')} value={`${fmtGlucose(data.minGlucose)} ${unit}`} />
          <StatItem label={t('report.summary.maxGlucose')} value={`${fmtGlucose(data.maxGlucose)} ${unit}`} />
          <StatItem label={t('report.summary.meanGlucose')} value={`${fmtGlucose(data.meanGlucose)} ${unit}`} />
          <StatItem label={t('report.summary.sd')} value={`${fmtGlucose(data.sd)} ${unit}`} />
          <StatItem label="GVI" value={fmtNum(data.gvi, 3)} />
          <StatItem label="PGS" value={fmtNum(data.pgs, 1)} />
          <StatItem
            label="GRI"
            value={
              data.gri !== null
                ? `${data.gri.toFixed(1)}${data.griZone !== null ? ` (${data.griZone})` : ''}`
                : '—'
            }
          />
          <StatItem label="eHbA1c" value={data.eHbA1c !== null ? `${data.eHbA1c.toFixed(1)}%` : '—'} />
        </div>
      </section>

      {/* ---- 5. Daily averages ---- */}
      <section className="summary-section" aria-label={t('report.summary.dailyAverages')}>
        <h3 className="summary-section-title">{t('report.summary.dailyAverages')}</h3>
        <div className="summary-stats-grid">
          <StatItem
            label={t('report.summary.avgCarbs')}
            value={
              data.avgCarbsPerDayG !== null
                ? `${data.avgCarbsPerDayG.toFixed(1)} g (${(data.avgCarbsPerDayG / 10).toFixed(1)} BE)`
                : '—'
            }
          />
          <StatItem
            label={t('report.summary.avgBolus')}
            value={
              data.avgBolusPerDayIe !== null
                ? `${data.avgBolusPerDayIe.toFixed(1)} IE${data.bolusPercent !== null ? ` (${data.bolusPercent.toFixed(1)}%)` : ''}`
                : '—'
            }
          />
          <StatItem
            label={t('report.summary.avgBasal')}
            value={
              data.avgBasalPerDayIe !== null
                ? `${data.avgBasalPerDayIe.toFixed(1)} IE${data.basalPercent !== null ? ` (${data.basalPercent.toFixed(1)}%)` : ''}`
                : '—'
            }
          />
          <StatItem
            label={t('report.summary.avgTotalInsulin')}
            value={data.avgTotalInsulinPerDayIe !== null ? `${data.avgTotalInsulinPerDayIe.toFixed(1)} IE` : '—'}
          />
        </div>
      </section>

      {/* Warnings */}
      {data.warnings.length > 0 && (
        <div className="summary-warnings" role="alert">
          {data.warnings.includes('lessThan14Days') && (
            <p style={{ fontSize: '0.8rem', color: 'var(--color-warn, #856404)', margin: 0 }}>
              {t('report.advisoryShortRange')}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

// ---- Sub-components ----

interface TranslateFn {
  (key: string): string
  (key: string, opts: Record<string, unknown>): string
}

interface PatientHeaderProps {
  data: ReportSummaryResponse
  t: TranslateFn
}

function PatientHeader({ data, t }: PatientHeaderProps) {
  return (
    <section className="summary-section summary-header" aria-label={t('report.summary.patientInfo')}>
      <div className="summary-patient-name">{data.displayName}</div>
      <div className="summary-header-grid">
        <HeaderItem label={t('report.summary.daysAnalysed')} value={String(data.daysAnalysed)} />
        <HeaderItem
          label={t('report.summary.cgmReadings')}
          value={`${data.cgmReadingCount.toLocaleString()} (${data.cgmIntervalMinutes} min)`}
        />
        <HeaderItem
          label={t('report.summary.insulinType')}
          value={data.insulinTypes.length > 0 ? data.insulinTypes.join(', ') : '—'}
        />
        <HeaderItem
          label={t('report.summary.insulinChanges')}
          value={formatEventCount(data.insulinChanges, data.avgDaysPerCartridge)}
        />
        <HeaderItem
          label={t('report.summary.siteChanges')}
          value={formatEventCount(data.siteChanges, data.avgDaysPerSite)}
        />
        <HeaderItem
          label={t('report.summary.sensorInserts')}
          value={formatEventCount(data.sensorInserts, data.avgDaysPerSensor)}
        />
      </div>
    </section>
  )
}

function formatEventCount(count: number, avgDays: number | null): string {
  if (avgDays !== null) {
    return `${count} (ø ${avgDays.toFixed(1)} d)`
  }
  return String(count)
}

interface HeaderItemProps {
  label: string
  value: string
}

function HeaderItem({ label, value }: HeaderItemProps) {
  return (
    <div className="summary-header-item">
      <span className="summary-header-label">{label}</span>
      <span className="summary-header-value">{value}</span>
    </div>
  )
}

// ---- TIR section ----

const ZONE_COLORS = {
  veryLow: '#c0392b',
  low: '#e67e22',
  inRange: '#27ae60',
  high: '#f39c12',
  veryHigh: '#8e44ad',
} as const

// Standard threshold labels for TirStandard section
const STANDARD_THRESHOLDS = {
  veryLow: '< 54',
  low: '54–70',
  inRange: '70–180',
  high: '180–250',
  veryHigh: '> 250',
} as const

type ZoneKey = keyof typeof ZONE_COLORS

interface TirSectionProps {
  title: string
  subtitle?: string
  tir: TirResult
  glucoseUnit: string
  standardLabels?: boolean
  t: TranslateFn
}

function TirSection({ title, subtitle, tir, glucoseUnit, standardLabels = false, t }: TirSectionProps) {
  const zones: ZoneKey[] = ['veryLow', 'low', 'inRange', 'high', 'veryHigh']
  const totalPercent = zones.reduce((sum, z) => sum + tir[z].percent, 0)
  // Avoid division by zero; use 100 as fallback
  const barTotal = totalPercent > 0 ? totalPercent : 100

  const labelKey = (zone: ZoneKey): string => {
    switch (zone) {
      case 'veryLow': return t('report.summary.tir.veryLow')
      case 'low': return t('report.summary.tir.low')
      case 'inRange': return t('report.summary.tir.inRange')
      case 'high': return t('report.summary.tir.high')
      case 'veryHigh': return t('report.summary.tir.veryHigh')
    }
  }

  const thresholdLabel = (zone: ZoneKey): string => {
    if (standardLabels) {
      return `${STANDARD_THRESHOLDS[zone]} ${glucoseUnit}`
    }
    // For profile TIR use the zone label only (thresholds come from profile)
    return labelKey(zone)
  }

  return (
    <section className="summary-section" aria-label={title}>
      <h3 className="summary-section-title">
        {title}
        {subtitle && (
          <span style={{ fontSize: '0.75rem', fontWeight: 400, color: 'var(--text-secondary)', marginLeft: '0.5rem' }}>
            ({subtitle})
          </span>
        )}
      </h3>

      {/* 5-zone color bar */}
      <TirColorBar zones={zones.map(z => ({ key: z, zone: tir[z], barTotal }))} />

      {/* Zone rows */}
      <div className="tir-zone-rows">
        {zones.map(zone => (
          <TirZoneRow
            key={zone}
            zone={tir[zone]}
            color={ZONE_COLORS[zone]}
            label={standardLabels ? STANDARD_THRESHOLDS[zone] + ' ' + glucoseUnit : labelKey(zone)}
            thresholdLabel={thresholdLabel(zone)}
            isInRange={zone === 'inRange'}
          />
        ))}
      </div>
    </section>
  )
}

interface TirColorBarProps {
  zones: Array<{ key: ZoneKey; zone: TirZone; barTotal: number }>
}

function TirColorBar({ zones }: TirColorBarProps) {
  return (
    <div
      className="tir-color-bar"
      role="img"
      aria-label="Time in range colour bar"
      style={{
        display: 'flex',
        height: 24,
        borderRadius: 4,
        overflow: 'hidden',
        marginBottom: '0.75rem',
      }}
    >
      {zones.map(({ key, zone, barTotal }) => {
        const width = (zone.percent / barTotal) * 100
        if (width < 0.1) return null
        return (
          <div
            key={key}
            style={{
              width: `${width}%`,
              background: ZONE_COLORS[key],
              flexShrink: 0,
            }}
            title={`${key}: ${zone.percent.toFixed(1)}%`}
          />
        )
      })}
    </div>
  )
}

interface TirZoneRowProps {
  zone: TirZone
  color: string
  label: string
  thresholdLabel: string
  isInRange: boolean
}

function TirZoneRow({ zone, color, label, isInRange }: TirZoneRowProps) {
  return (
    <div
      className="tir-zone-row"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '0.6rem',
        padding: '0.2rem 0',
        fontWeight: isInRange ? 600 : 400,
      }}
    >
      <div
        style={{
          width: 14,
          height: 14,
          borderRadius: 2,
          background: color,
          flexShrink: 0,
        }}
      />
      <span style={{ flex: 1, fontSize: '0.875rem' }}>{label}</span>
      <span style={{ minWidth: 48, textAlign: 'right', fontSize: '0.875rem' }}>
        {zone.count.toLocaleString()}
      </span>
      <span style={{ minWidth: 54, textAlign: 'right', fontSize: '0.875rem', color: isInRange ? 'var(--color-success, #27ae60)' : 'inherit' }}>
        {zone.percent.toFixed(1)}%
      </span>
    </div>
  )
}

interface StatItemProps {
  label: string
  value: string
}

function StatItem({ label, value }: StatItemProps) {
  return (
    <div className="summary-stat-item">
      <span className="summary-stat-label">{label}</span>
      <span className="summary-stat-value">{value}</span>
    </div>
  )
}
