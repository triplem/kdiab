import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface InsulinChangeFormData {
  insulinType?: string
}

interface InsulinChangeFormProps {
  initialData?: Partial<InsulinChangeFormData>
  onDataChange: (data: InsulinChangeFormData) => void
}

export function InsulinChangeForm({ initialData, onDataChange }: InsulinChangeFormProps) {
  const { t } = useTranslation()
  const [newInsulinType, setNewInsulinType] = useState(initialData?.insulinType ?? '')

  const handleChange = (value: string) => {
    setNewInsulinType(value)
    onDataChange(value ? { insulinType: value } : {})
  }

  return (
    <label style={labelStyle}>
      <span>{t('treatmentModal.newInsulinType')}</span>
      <input
        type="text"
        placeholder="e.g. NovoRapid"
        value={newInsulinType}
        onChange={(e) => handleChange(e.target.value)}
        style={inputStyle}
        autoFocus
      />
    </label>
  )
}
