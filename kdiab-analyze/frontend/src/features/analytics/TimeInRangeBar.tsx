import { useTranslation } from 'react-i18next';

interface TirBreakdown {
  belowCount: number;
  inRangeCount: number;
  aboveCount: number;
  highCount: number;
  totalCount: number;
}

const MGDL_TO_MMOL = 1 / 18.0182;

function fmtThreshold(mgDl: number, unit: string): string {
  if (unit === 'mmol/L') return (mgDl * MGDL_TO_MMOL).toFixed(1);
  return String(mgDl);
}

interface Props {
  tir: TirBreakdown;
  glucoseUnit: string;
}

export function TimeInRangeBar({ tir, glucoseUnit }: Props) {
  const { t } = useTranslation();
  const total = tir.totalCount || 1;
  const u = glucoseUnit;

  const segments = [
    { key: 'below',  count: tir.belowCount,   color: 'var(--tir-below)',  label: t('analytics.tirBelow',  { low: fmtThreshold(70, u), unit: u }) },
    { key: 'target', count: tir.inRangeCount,  color: 'var(--tir-target)', label: t('analytics.tirTarget', { low: fmtThreshold(70, u), high: fmtThreshold(180, u), unit: u }) },
    { key: 'above',  count: tir.aboveCount,    color: 'var(--tir-above)',  label: t('analytics.tirAbove',  { low: fmtThreshold(180, u), high: fmtThreshold(250, u), unit: u }) },
    { key: 'high',   count: tir.highCount,     color: 'var(--tir-high)',   label: t('analytics.tirHigh',   { high: fmtThreshold(250, u), unit: u }) },
  ];

  return (
    <div className="card">
      <h3>{t('analytics.tir')}</h3>

      {/* Stacked horizontal bar */}
      <div style={{
        display: 'flex',
        height: '2.5rem',
        borderRadius: '8px',
        overflow: 'hidden',
        marginBottom: '1rem',
        border: '1px solid var(--border-color)',
      }}>
        {segments.map(seg => {
          const pct = (seg.count / total) * 100;
          if (pct === 0) return null;
          return (
            <div
              key={seg.key}
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
          );
        })}
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem' }}>
        {segments.map(seg => {
          const pct = (seg.count / total) * 100;
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
          );
        })}
      </div>
    </div>
  );
}
