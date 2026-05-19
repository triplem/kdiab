import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { analyzeApi } from '../../api/analyzeApi'

interface Props {
  userId: string
  days?: number
}

interface WearRowProps {
  label: string
  avg: number | null
  stddev: number | null
}

function WearRow({ label, avg, stddev }: WearRowProps) {
  const { t } = useTranslation()

  if (avg == null) {
    return (
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.25rem 0', borderBottom: '1px solid var(--border)' }}>
        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{label}</span>
        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', fontStyle: 'italic' }}>
          {t('deviceUsage.noData', { defaultValue: 'no data' })}
        </span>
      </div>
    )
  }

  const stddevStr = stddev !== null ? ` ± ${stddev.toFixed(1)}` : ''
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.25rem 0', borderBottom: '1px solid var(--border)' }}>
      <span style={{ fontSize: '0.85rem', color: 'var(--text-primary)' }}>{label}</span>
      <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
        {avg.toFixed(1)}{stddevStr} {t('deviceUsage.days', { defaultValue: 'd' })}
      </span>
    </div>
  )
}

export function DeviceUsageCard({ userId, days = 90 }: Props) {
  const { t } = useTranslation()

  const { data, isLoading, isError } = useQuery({
    queryKey: ['device-usage', userId, days],
    queryFn: () => analyzeApi.getDeviceUsage(userId, days).then(r => r.data),
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  return (
    <div className="card" style={{ padding: '1rem', marginBottom: '1rem' }}>
      <h3 style={{ margin: '0 0 0.75rem', fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--text-secondary)' }}>
        {t('deviceUsage.title', { defaultValue: 'Avg Device Wear', days })}
        <span style={{ fontWeight: 400, marginLeft: '0.4rem' }}>
          ({t('deviceUsage.lookback', { defaultValue: '{{days}} days', days })})
        </span>
      </h3>

      {isLoading && (
        <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', margin: 0 }}>
          {t('deviceUsage.loading', { defaultValue: 'Loading…' })}
        </p>
      )}

      {isError && (
        <p style={{ fontSize: '0.85rem', color: 'var(--error)', margin: 0 }}>
          {t('deviceUsage.error', { defaultValue: 'Could not load device usage data.' })}
        </p>
      )}

      {data && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          <WearRow
            label={t('deviceUsage.sensor', { defaultValue: 'Sensor' })}
            avg={data.avgSensorDays}
            stddev={data.stddevSensorDays}
          />
          <WearRow
            label={t('deviceUsage.catheter', { defaultValue: 'Catheter / site' })}
            avg={data.avgCatheterDays}
            stddev={data.stddevCatheterDays}
          />
          <WearRow
            label={t('deviceUsage.reservoir', { defaultValue: 'Reservoir' })}
            avg={data.avgReservoirDays}
            stddev={data.stddevReservoirDays}
          />
          <WearRow
            label={t('deviceUsage.battery', { defaultValue: 'Pump battery' })}
            avg={data.avgBatteryDays}
            stddev={data.stddevBatteryDays}
          />
        </div>
      )}
    </div>
  )
}
