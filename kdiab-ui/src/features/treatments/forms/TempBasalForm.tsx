import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface TempBasalFormData {
  rate: number
  duration: number
  absolute: boolean
}

interface TempBasalFormProps {
  initialData?: Partial<TempBasalFormData>
  validationError: string | null
  onDataChange: (data: TempBasalFormData | null) => void
}

export function TempBasalForm({ initialData, validationError, onDataChange }: TempBasalFormProps) {
  const { t } = useTranslation()
  const [tempBasalRate, setTempBasalRate] = useState(initialData?.rate != null ? String(initialData.rate) : '')
  const [tempBasalDurationMin, setTempBasalDurationMin] = useState(initialData?.duration != null ? String(initialData.duration) : '')
  const [tempBasalAbsolute, setTempBasalAbsolute] = useState(initialData?.absolute !== false)

  const buildData = (rate: string, duration: string, absolute: boolean): TempBasalFormData | null => {
    const r = parseFloat(rate)
    if (isNaN(r) || r < 0) return null
    const dur = parseInt(duration, 10)
    if (isNaN(dur) || dur <= 0) return null
    return { rate: r, duration: dur, absolute }
  }

  const handleRateChange = (value: string) => {
    setTempBasalRate(value)
    onDataChange(buildData(value, tempBasalDurationMin, tempBasalAbsolute))
  }

  const handleDurationChange = (value: string) => {
    setTempBasalDurationMin(value)
    onDataChange(buildData(tempBasalRate, value, tempBasalAbsolute))
  }

  const handleAbsoluteToggle = () => {
    const next = !tempBasalAbsolute
    setTempBasalAbsolute(next)
    onDataChange(buildData(tempBasalRate, tempBasalDurationMin, next))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.tempBasalRate')}</span>
        <input
          type="number"
          min="0"
          max="20"
          step="0.05"
          placeholder="e.g. 0.5"
          value={tempBasalRate}
          onChange={(e) => handleRateChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.durationMinutes')}</span>
        <input
          type="number"
          min="5"
          max="1440"
          step="5"
          placeholder="e.g. 30"
          value={tempBasalDurationMin}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
          required
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label
        style={{ ...labelStyle, flexDirection: 'row', alignItems: 'center', gap: '8px', cursor: 'pointer' }}
        onClick={handleAbsoluteToggle}
      >
        <input
          type="checkbox"
          checked={tempBasalAbsolute}
          onChange={handleAbsoluteToggle}
        />
        <span>{t('treatmentModal.tempBasalAbsolute')}</span>
      </label>
    </>
  )
}
