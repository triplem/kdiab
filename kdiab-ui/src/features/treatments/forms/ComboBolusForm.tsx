import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface ComboBolusFormData {
  insulin: number
  splitNow: number
  splitExt: number
  duration?: number
}

interface ComboBolusFormProps {
  initialData?: Partial<{ insulin: number; splitNow: number; duration: number }>
  validationError: string | null
  onDataChange: (data: ComboBolusFormData | null) => void
}

export function ComboBolusForm({ initialData, validationError, onDataChange }: ComboBolusFormProps) {
  const { t } = useTranslation()
  const [comboInsulin, setComboInsulin] = useState(initialData?.insulin != null ? String(initialData.insulin) : '')
  const [comboSplitNow, setComboSplitNow] = useState(initialData?.splitNow != null ? String(initialData.splitNow) : '50')
  const [comboDurationMin, setComboDurationMin] = useState(initialData?.duration != null ? String(initialData.duration) : '')

  const buildData = (insulin: string, splitNow: string, duration: string): ComboBolusFormData | null => {
    const v = parseFloat(insulin)
    if (isNaN(v) || v <= 0) return null
    const split = parseFloat(splitNow)
    const dur = parseInt(duration, 10)
    return {
      insulin: v,
      splitNow: isNaN(split) ? 50 : split,
      splitExt: isNaN(split) ? 50 : 100 - split,
      ...(duration && !isNaN(dur) && { duration: dur }),
    }
  }

  const handleInsulinChange = (value: string) => {
    setComboInsulin(value)
    onDataChange(buildData(value, comboSplitNow, comboDurationMin))
  }

  const handleSplitChange = (value: string) => {
    setComboSplitNow(value)
    onDataChange(buildData(comboInsulin, value, comboDurationMin))
  }

  const handleDurationChange = (value: string) => {
    setComboDurationMin(value)
    onDataChange(buildData(comboInsulin, comboSplitNow, value))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.insulinUnits')}</span>
        <input
          type="number"
          min="0.1"
          max="100"
          step="0.1"
          placeholder="e.g. 3.0"
          value={comboInsulin}
          onChange={(e) => handleInsulinChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.comboSplitNow')}</span>
        <input
          type="number"
          min="0"
          max="100"
          step="5"
          placeholder="e.g. 50"
          value={comboSplitNow}
          onChange={(e) => handleSplitChange(e.target.value)}
          style={inputStyle}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.comboDuration')}</span>
        <input
          type="number"
          min="5"
          max="480"
          step="5"
          placeholder="e.g. 120"
          value={comboDurationMin}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
