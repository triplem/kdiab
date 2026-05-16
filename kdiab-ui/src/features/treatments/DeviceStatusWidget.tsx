import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { treatmentsApi } from '../../api/treatmentsApi'
import { useTimeFormat } from '../../context/TimeFormatContext'

interface DeviceStatusData {
  device?: string
  pumpName?: string
  reservoirUnits?: number
  batteryLevel?: number
  pumpConnected?: boolean
}

interface DeviceStatusWidgetProps {
  userId: string
}

export function DeviceStatusWidget({ userId }: DeviceStatusWidgetProps) {
  const { t } = useTranslation()
  const { formatDate } = useTimeFormat()

  const { data } = useQuery({
    queryKey: ['deviceStatus', userId],
    queryFn: async () => {
      const res = await treatmentsApi.listTreatments(userId, 'ACTIVE', 0, 50)
      return res.data.items.find((tr) => tr.type === 'DEVICE_STATUS') ?? null
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })

  if (!data) return null

  const d = data.data as DeviceStatusData

  return (
    <div className="card" style={{ padding: '0.75rem 1rem', marginBottom: '1rem', fontSize: '0.875rem' }}>
      <div style={{ fontWeight: 600, marginBottom: '0.4rem', color: 'var(--text-secondary)', fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {t('deviceStatus.title', { defaultValue: 'Device Status' })}
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem' }}>
        {d.device && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.device', { defaultValue: 'Device' })}: </span>
            <strong>{d.device}</strong>
          </div>
        )}
        {d.pumpName && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.pump', { defaultValue: 'Pump' })}: </span>
            <strong>{d.pumpName}</strong>
          </div>
        )}
        {d.reservoirUnits != null && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.reservoir', { defaultValue: 'Reservoir' })}: </span>
            <strong>{d.reservoirUnits.toFixed(1)} U</strong>
          </div>
        )}
        {d.batteryLevel != null && (
          <div>
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.75rem' }}>{t('deviceStatus.battery', { defaultValue: 'Battery' })}: </span>
            <strong>{d.batteryLevel}%</strong>
          </div>
        )}
      </div>
      <div style={{ marginTop: '0.3rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
        {t('deviceStatus.lastSeen', { defaultValue: 'Last seen' })}: {formatDate(data.treatedAt)}
      </div>
    </div>
  )
}
