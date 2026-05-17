import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface BolusFormData {
  insulin: number
  insulinType?: string
}

interface BolusFormProps {
  initialData?: Partial<BolusFormData>
  validationError: string | null
  onDataChange: (data: BolusFormData | null) => void
}

export function BolusForm({ initialData, validationError, onDataChange }: BolusFormProps) {
  const { t } = useTranslation()
  const [insulinUnits, setInsulinUnits] = useState(initialData?.insulin != null ? String(initialData.insulin) : '')
  const [insulinType, setInsulinType] = useState(initialData?.insulinType ?? '')

  const handleUnitsChange = (value: string) => {
    setInsulinUnits(value)
    const v = parseFloat(value)
    if (!isNaN(v) && v > 0) {
      onDataChange(insulinType ? { insulin: v, insulinType } : { insulin: v })
    } else {
      onDataChange(null)
    }
  }

  const handleTypeChange = (value: string) => {
    setInsulinType(value)
    const v = parseFloat(insulinUnits)
    if (!isNaN(v) && v > 0) {
      onDataChange(value ? { insulin: v, insulinType: value } : { insulin: v })
    } else {
      onDataChange(null)
    }
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
          placeholder="e.g. 2.5"
          value={insulinUnits}
          onChange={(e) => handleUnitsChange(e.target.value)}
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
          placeholder="e.g. NovoRapid"
          value={insulinType}
          onChange={(e) => handleTypeChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
