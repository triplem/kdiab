import { useTranslation } from 'react-i18next';

interface TirBreakdown {
  belowCount: number;
  inRangeCount: number;
  aboveCount: number;
  highCount: number;
  totalCount: number;
}

interface Props {
  hba1c: number | null;
  meanGlucose: number;
  tir: TirBreakdown;
  glucoseUnit: string;
}

export function HbA1cCard({ hba1c, meanGlucose, tir, glucoseUnit }: Props) {
  const { t } = useTranslation();
  const displayMean = glucoseUnit === 'mmol/L'
    ? (meanGlucose / 18.0).toFixed(1) + ' mmol/L'
    : Math.round(meanGlucose) + ' mg/dL';

  return (
    <div className="card" style={{ textAlign: 'center' }}>
      <h3>{t('analytics.hba1c')}</h3>
      <div style={{
        fontSize: '4rem',
        fontWeight: 700,
        color: 'var(--accent-primary)',
        lineHeight: 1,
        marginBottom: '0.5rem',
      }}>
        {hba1c != null ? `${hba1c.toFixed(1)}%` : '—'}
      </div>
      <div style={{ color: 'var(--text-secondary)', fontSize: '1rem' }}>
        {t('analytics.meanGlucose')}: <strong style={{ color: 'var(--text-primary)' }}>{displayMean}</strong>
      </div>
      {tir.totalCount > 0 && (
        <div style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginTop: '0.5rem' }}>
          Based on {tir.totalCount} CGM readings
        </div>
      )}
    </div>
  );
}
