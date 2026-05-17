import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface BasalFormData {
  insulin: number
  insulinType?: string
  duration?: number
}

interface BasalFormProps {
  initialData?: Partial<{ insulin: number; insulinType: string; duration: number }>
  validationError: string | null
  onDataChange: (data: BasalFormData | null) => void
}

export function BasalForm({ initialData, validationError, onDataChange }: BasalFormProps) {
  const { t } = useTranslation()
  // duration stored in minutes from server; display as hours
  const initialDurationHours = initialData?.duration != null ? String(Number(initialData.duration) / 60) : ''
  const [basalInsulin, setBasalInsulin] = useState(initialData?.insulin != null ? String(initialData.insulin) : '')
  const [basalInsulinType, setBasalInsulinType] = useState(initialData?.insulinType ?? '')
  const [basalDurationHours, setBasalDurationHours] = useState(initialDurationHours)

  const buildData = (insulin: string, insulinType: string, durationHours: string): BasalFormData | null => {
    const v = parseFloat(insulin)
    if (isNaN(v) || v <= 0) return null
    const durHours = parseFloat(durationHours)
    return {
      insulin: v,
      ...(insulinType && { insulinType }),
      ...(durationHours && !isNaN(durHours) && { duration: Math.round(durHours * 60) }),
    }
  }

  const handleInsulinChange = (value: string) => {
    setBasalInsulin(value)
    onDataChange(buildData(value, basalInsulinType, basalDurationHours))
  }

  const handleTypeChange = (value: string) => {
    setBasalInsulinType(value)
    onDataChange(buildData(basalInsulin, value, basalDurationHours))
  }

  const handleDurationChange = (value: string) => {
    setBasalDurationHours(value)
    onDataChange(buildData(basalInsulin, basalInsulinType, value))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.insulinUnits')}</span>
        <input
          type="number"
          min="0.1"
          max="200"
          step="0.1"
          placeholder="e.g. 10.0"
          value={basalInsulin}
          onChange={(e) => handleInsulinChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.insulinType')}</span>
        <input
          type="text"
          placeholder="e.g. Lantus"
          value={basalInsulinType}
          onChange={(e) => handleTypeChange(e.target.value)}
          style={inputStyle}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.durationHours')}</span>
        <input
          type="number"
          min="1"
          max="48"
          step="0.5"
          placeholder="e.g. 24"
          value={basalDurationHours}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
