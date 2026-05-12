import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { TimeframePicker, type Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker'
import { HbA1cCard } from './HbA1cCard'
import { TimeInRangeBar } from './TimeInRangeBar'
import { AgpChart } from './AgpChart'

interface Props {
  userId: string
  glucoseUnit: string
}

export function AnalyticsView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe())

  const enabled = !!userId

  const hba1cQuery = useQuery({
    queryKey: ['hba1c', userId, timeframe.from, timeframe.to],
    queryFn: () => analyzeApi.getHba1c(userId, timeframe.from, timeframe.to).then(r => r.data),
    enabled,
  })

  const agpQuery = useQuery({
    queryKey: ['agp', userId, timeframe.from, timeframe.to],
    queryFn: () => analyzeApi.getAgp(userId, timeframe.from, timeframe.to).then(r => r.data),
    enabled,
  })

  const loading = hba1cQuery.isLoading || agpQuery.isLoading

  return (
    <div>
      <TimeframePicker value={timeframe} onChange={setTimeframe} />

      {loading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}

      {hba1cQuery.isError && (
        <div className="error-banner" role="alert">{t('analytics.hba1cError')}</div>
      )}
      {agpQuery.isError && (
        <div className="error-banner" role="alert">{t('analytics.agpError')}</div>
      )}

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

      {agpQuery.data && (
        <AgpChart
          hourlyData={agpQuery.data.hourlyData}
          glucoseUnit={glucoseUnit}
          warnings={agpQuery.data.warnings}
          totalReadingCount={agpQuery.data.totalReadingCount}
          sensorWearDays={agpQuery.data.sensorWearDays}
        />
      )}
    </div>
  )
}
