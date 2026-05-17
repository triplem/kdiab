import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface SensorInsertFormData {
  sensor?: string
}

interface SensorInsertFormProps {
  initialData?: Partial<SensorInsertFormData>
  onDataChange: (data: SensorInsertFormData) => void
}

export function SensorInsertForm({ initialData, onDataChange }: SensorInsertFormProps) {
  const { t } = useTranslation()
  const [sensorModel, setSensorModel] = useState(initialData?.sensor ?? '')

  const handleChange = (value: string) => {
    setSensorModel(value)
    onDataChange(value ? { sensor: value } : {})
  }

  return (
    <label style={labelStyle}>
      <span>{t('treatmentModal.sensorModel')}</span>
      <input
        type="text"
        placeholder="e.g. Dexcom G7"
        value={sensorModel}
        onChange={(e) => handleChange(e.target.value)}
        style={inputStyle}
        autoFocus
      />
    </label>
  )
}
