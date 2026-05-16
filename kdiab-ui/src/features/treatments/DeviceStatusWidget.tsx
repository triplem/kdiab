import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { treatmentsApi } from '../../api/treatmentsApi'
import { useTimeFormat } from '../../context/TimeFormatContext'

interface DeviceStatusWidgetProps {
  userId: string
}

export function DeviceStatusWidget({ userId }: DeviceStatusWidgetProps) {
  const { t } = useTranslation()
  const { formatDate } = useTimeFormat()

  const { data } = useQuery({
    queryKey: ['deviceStatus', userId],
    queryFn: async () => {
      try {
        const res = await treatmentsApi.getLatestDeviceStatus(userId)
        return res.data
      } catch {
        return null
      }
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })

  if (!data) return null

  return (
    <div className="card" style={{ padding: '0.75rem 1rem', marginBottom: '1rem', fontSize: '0.875rem' }}>
      <div style={{ fontWeight: 600, marginBottom: '0.4rem', color: 'var(--text-secondary)', fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {t('deviceStatus.title', { defaultValue: 'Device Status' })}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.device', { defaultValue: 'Device' })}: </span>
          <strong>{data.device}</strong>
        </div>
        {data.pumpName && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.pump', { defaultValue: 'Pump' })}: </span>
            <strong>{data.pumpName}</strong>
          </div>
        )}
        {data.reservoirUnits != null && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.reservoir', { defaultValue: 'Reservoir' })}: </span>
            <strong>{data.reservoirUnits.toFixed(1)} U</strong>
          </div>
        )}
        {data.batteryLevel != null && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.battery', { defaultValue: 'Battery' })}: </span>
            <strong>{data.batteryLevel}%</strong>
          </div>
        )}
      </div>
      <div style={{ marginTop: '0.3rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
        {t('deviceStatus.lastSeen', { defaultValue: 'Last seen' })}: {formatDate(data.recordedAt)}
      </div>
    </div>
  )
}
