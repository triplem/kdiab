import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface MealFormData {
  insulin: number
  carbs: number
}

interface MealFormProps {
  initialData?: Partial<MealFormData>
  validationError: string | null
  onDataChange: (data: MealFormData | null) => void
}

export function MealForm({ initialData, validationError, onDataChange }: MealFormProps) {
  const { t } = useTranslation()
  const [mealInsulin, setMealInsulin] = useState(initialData?.insulin != null ? String(initialData.insulin) : '')
  const [mealCarbs, setMealCarbs] = useState(initialData?.carbs != null ? String(initialData.carbs) : '')

  const handleInsulinChange = (value: string) => {
    setMealInsulin(value)
    const insulin = parseFloat(value)
    const carbs = parseFloat(mealCarbs)
    if (!isNaN(insulin) && insulin > 0 && !isNaN(carbs) && carbs > 0) {
      onDataChange({ insulin, carbs })
    } else {
      onDataChange(null)
    }
  }

  const handleCarbsChange = (value: string) => {
    setMealCarbs(value)
    const insulin = parseFloat(mealInsulin)
    const carbs = parseFloat(value)
    if (!isNaN(insulin) && insulin > 0 && !isNaN(carbs) && carbs > 0) {
      onDataChange({ insulin, carbs })
    } else {
      onDataChange(null)
    }
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.mealBolus')}</span>
        <input
          type="number"
          min="0.1"
          max="100"
          step="0.1"
          placeholder="e.g. 2.5"
          value={mealInsulin}
          onChange={(e) => handleInsulinChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.mealCarbs')}</span>
        <input
          type="number"
          min="1"
          max="500"
          step="1"
          placeholder="e.g. 45"
          value={mealCarbs}
          onChange={(e) => handleCarbsChange(e.target.value)}
          style={inputStyle}
          required
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
    </>
  )
}
