import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

export interface PumpBatteryChangeFormData {
  batteryType?: string
}

interface PumpBatteryChangeFormProps {
  initialData?: Partial<PumpBatteryChangeFormData>
  onDataChange: (data: PumpBatteryChangeFormData) => void
}

export function PumpBatteryChangeForm({ onDataChange }: PumpBatteryChangeFormProps) {
  const { t } = useTranslation()

  // No required fields — battery change is a timestamped event with no mandatory payload
  useEffect(() => {
    onDataChange({})
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <p style={{ margin: 0, fontSize: '0.9rem', color: '#666' }}>
      {t('treatmentModal.pumpBatteryChangeHint', { defaultValue: 'Records the current time as a pump battery replacement event. No additional data required.' })}
    </p>
  )
}
