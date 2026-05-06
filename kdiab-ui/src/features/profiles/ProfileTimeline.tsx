import { useTranslation } from 'react-i18next'
import type { Profile } from '../../api/profilesApi'

interface ProfileTimelineProps {
  profiles: Profile[]
}

export function ProfileTimeline({ profiles }: ProfileTimelineProps) {
  const { t } = useTranslation()

  const sorted = [...profiles].sort((a, b) => {
    const dateA = a.activatedAt ?? a.createdAt ?? ''
    const dateB = b.activatedAt ?? b.createdAt ?? ''
    return dateA.localeCompare(dateB)
  })

  if (sorted.length === 0) {
    return <p>{t('history.empty')}</p>
  }

  return (
    <div className="profile-timeline">
      {sorted.map((profile, index) => {
        const status = profile.status.toLowerCase()
        const dateStr = profile.activatedAt ?? profile.createdAt
        const dateLabel = dateStr
          ? new Date(dateStr).toLocaleDateString(navigator.language, { dateStyle: 'short' })
          : t('history.na')

        return (
          <div key={profile.id} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.5rem' }}>
            <div className="timeline-node">
              <span className={`status-badge status-${status}`}>{profile.status}</span>
              <span className="timeline-node-name">{profile.name}</span>
              <span className="timeline-node-date">{dateLabel}</span>
            </div>
            {index < sorted.length - 1 && (
              <span className="timeline-arrow" aria-hidden="true">→</span>
            )}
          </div>
        )
      })}
    </div>
  )
}
