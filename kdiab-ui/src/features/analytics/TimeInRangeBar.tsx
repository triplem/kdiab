import { useTranslation } from 'react-i18next'
import type { TirBreakdown } from '../../api/analyzeApi'

const MGDL_TO_MMOL = 1 / 18.0182
const TIR_GOAL = 70

function fmtThreshold(mgDl: number, unit: string): string {
  if (unit === 'mmol/L') return (mgDl * MGDL_TO_MMOL).toFixed(1)
  return String(mgDl)
}

interface Props {
  tir: TirBreakdown
  glucoseUnit: string
}

export function TimeInRangeBar({ tir, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const total = tir.totalCount || 1
  const u = glucoseUnit

  const inRangePct = (tir.inRangeCount / total) * 100
  const meetsGoal = inRangePct >= TIR_GOAL

  const segments = [
    { key: 'veryLow', count: tir.veryLowCount,  color: '#B00020',           label: t('analytics.tirVeryLowBand', { high: fmtThreshold(54, u), unit: u }),                                 descId: 'tir-desc-verylow' },
    { key: 'below',   count: tir.belowCount,    color: 'var(--tir-below)',  label: t('analytics.tirBelow',       { low: fmtThreshold(54, u), high: fmtThreshold(70, u), unit: u }),   descId: 'tir-desc-below' },
    { key: 'target',  count: tir.inRangeCount,  color: 'var(--tir-target)', label: t('analytics.tirTarget',      { low: fmtThreshold(70, u), high: fmtThreshold(180, u), unit: u }),      descId: 'tir-desc-target' },
    { key: 'above',   count: tir.aboveCount,    color: 'var(--tir-above)',  label: t('analytics.tirAbove',       { low: fmtThreshold(180, u), high: fmtThreshold(250, u), unit: u }),     descId: 'tir-desc-above' },
    { key: 'high',    count: tir.highCount,     color: 'var(--tir-high)',   label: t('analytics.tirHigh',        { high: fmtThreshold(250, u), unit: u }),                                descId: 'tir-desc-high' },
  ]

  const goalLabel = meetsGoal ? t('analytics.tirGoalMet') : t('analytics.tirGoalNotMet')

  return (
    <div className="card">
      <h3>{t('analytics.tir')}</h3>

      <div
        className={`tir-goal-indicator ${meetsGoal ? 'meets-goal' : 'below-goal'}`}
        aria-label={goalLabel}
      >
        {goalLabel}
      </div>

      {/* Hidden descriptions for aria-describedby */}
      {segments.map(seg => {
        const pct = (seg.count / total) * 100
        return (
          <span key={seg.descId} id={seg.descId} style={{ display: 'none' }}>
            {seg.label}: {pct.toFixed(1)}%
          </span>
        )
      })}

      <div style={{
        display: 'flex',
        height: '2.5rem',
        borderRadius: '8px',
        overflow: 'hidden',
        marginBottom: '1rem',
        border: '1px solid var(--border-color)',
      }}>
        {segments.map(seg => {
          const pct = (seg.count / total) * 100
          if (pct === 0) return null
          return (
            <div
              key={seg.key}
              aria-describedby={seg.descId}
              style={{
                width: `${pct}%`,
                backgroundColor: seg.color,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '0.8rem',
                fontWeight: 600,
                color: 'var(--text-primary)',
                overflow: 'hidden',
                whiteSpace: 'nowrap',
              }}
              title={`${seg.label}: ${pct.toFixed(1)}%`}
            >
              {pct >= 8 ? `${pct.toFixed(0)}%` : ''}
            </div>
          )
        })}
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem' }}>
        {segments.map(seg => {
          const pct = (seg.count / total) * 100
          return (
            <div key={seg.key} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <div style={{
                width: '12px',
                height: '12px',
                borderRadius: '2px',
                backgroundColor: seg.color,
                flexShrink: 0,
              }} />
              <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                {seg.label}: <strong style={{ color: 'var(--text-primary)' }}>{pct.toFixed(1)}%</strong>
              </span>
            </div>
          )
        })}
      </div>

      <dl className="tir-explanation">
        <dt>{t('analytics.tirClinicalTarget')}</dt>
        <dd>{t('analytics.tirClinicalTargetValue')}</dd>
        <dt>{t('analytics.tirInRange')}</dt>
        <dd>{inRangePct.toFixed(1)}%</dd>
        <dt>{t('analytics.tirLow')}</dt>
        <dd>{(((tir.veryLowCount + tir.belowCount) / total) * 100).toFixed(1)}%</dd>
        <dt>{t('analytics.tirHigh2')}</dt>
        <dd>{(((tir.aboveCount + tir.highCount) / total) * 100).toFixed(1)}%</dd>
      </dl>
    </div>
  )
}
