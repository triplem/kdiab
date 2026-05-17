import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface SiteChangeFormData {
  location?: string
}

interface SiteChangeFormProps {
  initialData?: Partial<SiteChangeFormData>
  onDataChange: (data: SiteChangeFormData) => void
}

export function SiteChangeForm({ initialData, onDataChange }: SiteChangeFormProps) {
  const { t } = useTranslation()
  const [siteLocation, setSiteLocation] = useState(initialData?.location ?? '')

  const handleChange = (value: string) => {
    setSiteLocation(value)
    onDataChange(value ? { location: value } : {})
  }

  return (
    <label style={labelStyle}>
      <span>{t('treatmentModal.location')}</span>
      <input
        type="text"
        placeholder="e.g. left abdomen"
        value={siteLocation}
        onChange={(e) => handleChange(e.target.value)}
        style={inputStyle}
        autoFocus
      />
    </label>
  )
}
