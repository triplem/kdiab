import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { treatmentsApi } from '../../api/treatmentsApi'
import { toast } from 'sonner'
import { Syringe, MapPin, Activity } from 'lucide-react'

interface QuickLogButtonsProps {
  userId: string
  onLogged?: () => void
}

type PumpEventType = 'INSULIN_CHANGE' | 'SITE_CHANGE' | 'SENSOR_INSERT'

const PUMP_EVENTS: { type: PumpEventType; icon: React.FC<{ size?: number }> }[] = [
  { type: 'INSULIN_CHANGE', icon: Syringe },
  { type: 'SITE_CHANGE', icon: MapPin },
  { type: 'SENSOR_INSERT', icon: Activity },
]

export const QuickLogButtons: React.FC<QuickLogButtonsProps> = ({ userId, onLogged }) => {
  const { t } = useTranslation()
  const [saving, setSaving] = useState<PumpEventType | null>(null)

  const handleQuickLog = async (type: PumpEventType) => {
    setSaving(type)
    try {
      await treatmentsApi.createTreatment(userId, {
        type,
        treatedAt: new Date().toISOString(),
        data: {},
      })
      toast.success(
        t('treatments.quickLogSuccess', {
          defaultValue: '{{event}} logged',
          event: t(`treatmentModal.types.${type}`),
        }),
      )
      onLogged?.()
    } catch {
      toast.error(t('treatments.quickLogError', { defaultValue: 'Failed to log event — please try again' }))
    } finally {
      setSaving(null)
    }
  }

  return (
    <div
      style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}
      aria-label={t('treatments.quickLogLabel', { defaultValue: 'Quick log pump events' })}
    >
      <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
        {t('treatments.quickLog', { defaultValue: 'Quick log:' })}
      </span>
      {PUMP_EVENTS.map(({ type, icon: Icon }) => (
        <button
          key={type}
          className="btn outline"
          style={{ padding: '0.4rem 0.8rem', fontSize: '0.875rem' }}
          disabled={saving !== null}
          aria-busy={saving === type}
          aria-label={t(`treatmentModal.types.${type}`)}
          onClick={() => void handleQuickLog(type)}
        >
          <Icon size={16} /> {saving === type
            ? t('common.saving')
            : t(`treatmentModal.types.${type}`)}
        </button>
      ))}
    </div>
  )
}
