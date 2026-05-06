import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { TimeframePicker, type Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker'
import { TimelineChart } from './TimelineChart'

interface Props {
  userId: string
  glucoseUnit: string
}

export function TimelineView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe())

  const { data, isLoading, error } = useQuery({
    queryKey: ['timeline', userId, timeframe.from, timeframe.to],
    queryFn: () => analyzeApi.getTimeline(userId, timeframe.from, timeframe.to).then(r => r.data),
    enabled: !!userId,
  })

  const { data: profiles } = useQuery({
    queryKey: ['profiles', userId, timeframe.from, timeframe.to],
    queryFn: () => analyzeApi.getActiveProfiles(userId, timeframe.from, timeframe.to).then(r => r.data),
    enabled: !!userId,
  })

  const fromMs = new Date(timeframe.from).getTime()
  const toMs = new Date(timeframe.to).getTime()

  const profileChangeDates: number[] = (profiles?.profiles ?? [])
    .filter(p => p.validFrom != null)
    .map(p => new Date(p.validFrom!).getTime())
    .filter(ms => ms >= fromMs && ms <= toMs)

  return (
    <div>
      <TimeframePicker value={timeframe} onChange={setTimeframe} />
      <div className="card">
        <h3>{t('timeline.title')}</h3>
        {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
        {error && <p style={{ color: 'var(--accent-danger)' }}>Error loading timeline.</p>}
        {data && data.measures.length === 0 && data.treatments.length === 0 && (
          <p style={{ color: 'var(--text-secondary)' }}>{t('timeline.noData')}</p>
        )}
        {data && (data.measures.length > 0 || data.treatments.length > 0) && (
          <TimelineChart
            measures={data.measures}
            treatments={data.treatments}
            glucoseUnit={glucoseUnit}
            profileChangeDates={profileChangeDates}
          />
        )}
        {data && data.errors && data.errors.length > 0 && (
          <div className="upstream-error-banner" role="alert">
            {t('timeline.partialData')}: {data.errors.join(', ')}
          </div>
        )}
      </div>
    </div>
  )
}
