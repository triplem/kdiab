import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { TimeframePicker, type Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker'
import { ProfilesTable } from './ProfilesTable'

interface Props {
  userId: string
}

export function ProfilesView({ userId }: Props) {
  const { t } = useTranslation()
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe())

  const enabled = !!userId

  const profilesQuery = useQuery({
    queryKey: ['profiles-active', userId, timeframe.from, timeframe.to],
    queryFn: () => analyzeApi.getActiveProfiles(userId, timeframe.from, timeframe.to).then(r => r.data),
    enabled,
  })

  return (
    <div>
      <TimeframePicker value={timeframe} onChange={setTimeframe} />

      {profilesQuery.isLoading && (
        <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>
      )}

      {profilesQuery.data && (
        <ProfilesTable profiles={profilesQuery.data.profiles} />
      )}

      {!profilesQuery.isLoading && !profilesQuery.data && (
        <div className="card">
          <h3>{t('analytics.profiles')}</h3>
          <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
        </div>
      )}
    </div>
  )
}
