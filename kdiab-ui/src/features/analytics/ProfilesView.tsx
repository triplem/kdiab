import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { TimeframePicker, type Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker'
import { ProfilesTable } from './ProfilesTable'

interface Props {
  userId: string
  from?: string
  to?: string
}

export function ProfilesView({ userId, from: fromProp, to: toProp }: Props) {
  const { t } = useTranslation()
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe())

  const from = fromProp ?? timeframe.from
  const to = toProp ?? timeframe.to
  const enabled = !!userId

  const profilesQuery = useQuery({
    queryKey: ['profiles-active', userId, from, to],
    queryFn: () => analyzeApi.getActiveProfiles(userId, from, to).then(r => r.data),
    enabled,
  })

  return (
    <div>
      {fromProp == null && <TimeframePicker value={timeframe} onChange={setTimeframe} />}

      {profilesQuery.isLoading && (
        <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>
      )}

      {profilesQuery.isError && (
        <div className="error-banner" role="alert">{t('analytics.profilesError')}</div>
      )}

      {profilesQuery.data && (
        <ProfilesTable profiles={profilesQuery.data.profiles} />
      )}

      {!profilesQuery.isLoading && !profilesQuery.isError && !profilesQuery.data && (
        <div className="card">
          <h3>{t('analytics.profiles')}</h3>
          <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
        </div>
      )}
    </div>
  )
}
