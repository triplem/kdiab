import { useTranslation } from 'react-i18next'
import { glucoseColor, trendArrow, toDisplay } from './basalUtils'

interface GlucoseHeroTileProps {
  latestSgv: number
  latestTrend: unknown
  delta: number | null
  delta15: number | null
  minutesAgo: number | null
  isStale: boolean
  isVeryStale: boolean
  glucoseUnit: string
  yLabel: string
}

export function GlucoseHeroTile({
  latestSgv,
  latestTrend,
  delta,
  delta15,
  minutesAgo,
  isStale,
  isVeryStale,
  glucoseUnit,
  yLabel,
}: GlucoseHeroTileProps) {
  const { t } = useTranslation()

  const deltaColor = (d: number | null) => {
    if (d === null) return 'var(--text-secondary)'
    const abs = Math.abs(d)
    if (abs < (glucoseUnit === 'mmol/L' ? 0.5 : 10)) return 'var(--accent-success)'
    if (abs < (glucoseUnit === 'mmol/L' ? 1.5 : 25)) return 'var(--accent-warning)'
    return 'var(--accent-danger)'
  }

  const formatDelta = (d: number | null): string => {
    if (d === null) return '—'
    return `${d > 0 ? '+' : ''}${d} ${yLabel}`
  }

  return (
    <div className="card" style={{ padding: '1rem 1.25rem', marginBottom: '1rem' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.75rem', flexWrap: 'wrap' }}>
        <span style={{ fontSize: '3rem', fontWeight: 800, lineHeight: 1, color: glucoseColor(latestSgv) }}>
          {toDisplay(latestSgv, glucoseUnit)}
        </span>
        <span style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>{yLabel}</span>
        <span style={{ fontSize: '2rem' }}>{trendArrow(latestTrend)}</span>
        <span style={{ fontSize: '0.95rem', color: deltaColor(delta) }}>
          {t('dashboard.delta', { defaultValue: 'Δ' })}: {formatDelta(delta)}
        </span>
        <span style={{ fontSize: '0.95rem', color: deltaColor(delta15) }}>
          {t('dashboard.delta15', { defaultValue: 'Δ15' })}: {formatDelta(delta15)}
        </span>
        {minutesAgo !== null && (
          <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginLeft: 'auto' }}>
            {minutesAgo} {t('dashboard.minAgo', { defaultValue: 'min ago' })}
          </span>
        )}
      </div>
      {isVeryStale && (
        <p style={{ color: 'var(--color-error, #dc2626)', fontSize: '0.75rem', margin: '0.25rem 0 0' }}>
          ⚠ CGM data is more than 30 min old
        </p>
      )}
      {isStale && !isVeryStale && (
        <p style={{ color: 'var(--color-warning, #d97706)', fontSize: '0.75rem', margin: '0.25rem 0 0' }}>
          CGM data may be outdated
        </p>
      )}
    </div>
  )
}
