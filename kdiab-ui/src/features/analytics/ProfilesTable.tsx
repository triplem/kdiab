import { useTranslation } from 'react-i18next'
import { useTimeFormat } from '../../context/TimeFormatContext'
import type { ProfileSummary } from '../../api/analyzeApi'

interface Props {
  profiles: ProfileSummary[]
}

export function ProfilesTable({ profiles }: Props) {
  const { t } = useTranslation()
  const { formatDate } = useTimeFormat()

  if (profiles.length === 0) {
    return (
      <div className="card">
        <h3>{t('analytics.profiles')}</h3>
        <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
      </div>
    )
  }

  return (
    <div className="card">
      <h3>{t('analytics.profiles')}</h3>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border-color)' }}>
              {[t('profile.name'), t('profile.status'), t('profile.validFrom'), t('profile.id')].map(h => (
                <th key={h} style={{ textAlign: 'left', padding: '0.5rem 0.75rem', color: 'var(--text-secondary)', fontWeight: 500 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {profiles.map(p => (
              <tr key={p.id} style={{ borderBottom: '1px solid var(--table-border)' }}>
                <td style={{ padding: '0.5rem 0.75rem', fontWeight: 500 }}>{p.name}</td>
                <td style={{ padding: '0.5rem 0.75rem' }}>
                  <span className={`status-badge status-${p.status.toLowerCase()}`}>{p.status}</span>
                </td>
                <td style={{ padding: '0.5rem 0.75rem', color: 'var(--text-secondary)' }}>
                  {p.validFrom ? formatDate(p.validFrom) : p.createdAt ? formatDate(p.createdAt) : '—'}
                </td>
                <td style={{ padding: '0.5rem 0.75rem', fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                  {p.id.slice(0, 8)}…
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
