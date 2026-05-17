import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface PumpSuspendFormData {
  duration: number
  reason?: string
}

interface PumpSuspendFormProps {
  initialData?: Partial<PumpSuspendFormData>
  validationError: string | null
  onDataChange: (data: PumpSuspendFormData | null) => void
}

export function PumpSuspendForm({ initialData, validationError, onDataChange }: PumpSuspendFormProps) {
  const { t } = useTranslation()
  const [pumpSuspendDurationMin, setPumpSuspendDurationMin] = useState(
    initialData?.duration != null ? String(initialData.duration) : ''
  )
  const [pumpSuspendReason, setPumpSuspendReason] = useState(initialData?.reason ?? '')

  const buildData = (duration: string, reason: string): PumpSuspendFormData | null => {
    const dur = parseInt(duration, 10)
    if (isNaN(dur) || dur <= 0) return null
    return { duration: dur, ...(reason.trim() && { reason: reason.trim() }) }
  }

  const handleDurationChange = (value: string) => {
    setPumpSuspendDurationMin(value)
    onDataChange(buildData(value, pumpSuspendReason))
  }

  const handleReasonChange = (value: string) => {
    setPumpSuspendReason(value)
    onDataChange(buildData(pumpSuspendDurationMin, value))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.durationMinutes')}</span>
        <input
          type="number"
          min="1"
          max="1440"
          step="1"
          placeholder="e.g. 30"
          value={pumpSuspendDurationMin}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.pumpSuspendReason')}</span>
        <input
          type="text"
          placeholder="e.g. exercise"
          value={pumpSuspendReason}
          onChange={(e) => handleReasonChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
