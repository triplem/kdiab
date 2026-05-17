import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface HypoTreatmentFormData {
  carbs: number
  reason?: string
}

interface HypoTreatmentFormProps {
  initialData?: Partial<HypoTreatmentFormData>
  validationError: string | null
  glucoseUnit?: string
  onDataChange: (data: HypoTreatmentFormData | null) => void
}

export function HypoTreatmentForm({ initialData, validationError, glucoseUnit = 'mg/dL', onDataChange }: HypoTreatmentFormProps) {
  const { t } = useTranslation()
  const [hypoCarbs, setHypoCarbs] = useState(initialData?.carbs != null ? String(initialData.carbs) : '')
  const [hypoReason, setHypoReason] = useState(initialData?.reason ?? '')

  const buildData = (carbs: string, reason: string): HypoTreatmentFormData | null => {
    const v = parseFloat(carbs)
    if (isNaN(v) || v < 1) return null
    return reason.trim() ? { carbs: v, reason: reason.trim() } : { carbs: v }
  }

  const handleCarbsChange = (value: string) => {
    setHypoCarbs(value)
    onDataChange(buildData(value, hypoReason))
  }

  const handleReasonChange = (value: string) => {
    setHypoReason(value)
    onDataChange(buildData(hypoCarbs, value))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.carbs')}</span>
        <input
          type="number"
          min="1"
          max="500"
          step="1"
          placeholder="e.g. 15"
          value={hypoCarbs}
          onChange={(e) => handleCarbsChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.hypoReason')}</span>
        <input
          type="text"
          placeholder={glucoseUnit === 'mmol/L' ? 'e.g. glucose below 3.9 mmol/L' : 'e.g. glucose below 70 mg/dL'}
          value={hypoReason}
          onChange={(e) => handleReasonChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
