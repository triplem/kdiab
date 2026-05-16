import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useMemo, useState } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { HbA1cCard } from './HbA1cCard'
import { TimeInRangeBar } from './TimeInRangeBar'
import { AgpChart } from './AgpChart'
import { ProfilesView } from './ProfilesView'

type Window = '1W' | '2W' | '1M' | '90D'

const WINDOWS: { key: Window; days: number }[] = [
  { key: '1W', days: 7 },
  { key: '2W', days: 14 },
  { key: '1M', days: 30 },
  { key: '90D', days: 90 },
]

function windowDates(days: number): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000)
  return {
    from: from.toISOString().slice(0, 10) + 'T00:00:00Z',
    to: to.toISOString().slice(0, 10) + 'T23:59:59Z',
  }
}

interface Props {
  userId: string
  glucoseUnit: string
}

export function AnalyticsView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const [activeWindow, setActiveWindow] = useState<Window>('2W')

  const { from, to } = useMemo(
    () => windowDates(WINDOWS.find(w => w.key === activeWindow)!.days),
    [activeWindow],
  )

  const enabled = !!userId

  const agpQuery = useQuery({
    queryKey: ['agp', userId, activeWindow],
    queryFn: () => analyzeApi.getAgp(userId, from, to).then(r => r.data),
    enabled,
    staleTime: 5 * 60 * 1000,
  })

  const hba1cQuery = useQuery({
    queryKey: ['hba1c', userId, activeWindow],
    queryFn: () => analyzeApi.getHba1c(userId, from, to).then(r => r.data),
    enabled,
    staleTime: 5 * 60 * 1000,
  })

  return (
    <div>
      {/* Shared window filter */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <span style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>{t('timeframe.label')}:</span>
        {WINDOWS.map(w => (
          <button
            key={w.key}
            onClick={() => setActiveWindow(w.key)}
            className={activeWindow === w.key ? 'primary' : 'btn outline'}
            style={{ padding: '0.4em 0.9em', fontSize: '0.9rem' }}
          >
            {w.key}
          </button>
        ))}
      </div>

      {/* Glucose Profile (AGP) — first */}
      {agpQuery.isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
      {agpQuery.isError && <div className="error-banner" role="alert">{t('analytics.agpError')}</div>}
      {agpQuery.data && (
        <AgpChart
          hourlyData={agpQuery.data.hourlyData}
          glucoseUnit={glucoseUnit}
          warnings={agpQuery.data.warnings}
          totalReadingCount={agpQuery.data.totalReadingCount}
          sensorWearDays={agpQuery.data.sensorWearDays}
        />
      )}

      {/* HbA1c & Time in Range */}
      {hba1cQuery.isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
      {hba1cQuery.isError && <div className="error-banner" role="alert">{t('analytics.hba1cError')}</div>}
      {hba1cQuery.data && (
        <>
          <HbA1cCard
            hba1c={hba1cQuery.data.hba1c}
            meanGlucose={hba1cQuery.data.meanGlucose}
            tir={hba1cQuery.data.tir}
            glucoseUnit={glucoseUnit}
            warnings={hba1cQuery.data.warnings}
          />
          <TimeInRangeBar tir={hba1cQuery.data.tir} glucoseUnit={glucoseUnit} />
        </>
      )}

      {/* Profiles — shares the same window */}
      <ProfilesView userId={userId} from={from} to={to} />
    </div>
  )
}
