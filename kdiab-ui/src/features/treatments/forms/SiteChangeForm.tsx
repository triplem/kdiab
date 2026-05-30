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

const SITE_CHANGE_LOCATION_DATALIST_ID = 'site-change-location-options'

const PREDEFINED_LOCATIONS = [
  'abdomen',
  'upperArm',
  'thigh',
  'buttock',
  'lowerBack',
] as const

type PredefinedLocation = (typeof PREDEFINED_LOCATIONS)[number]

export function SiteChangeForm({ initialData, onDataChange }: SiteChangeFormProps) {
  const { t } = useTranslation()
  const [siteLocation, setSiteLocation] = useState(initialData?.location ?? '')

  const handleChange = (value: string) => {
    setSiteLocation(value)
    onDataChange(value ? { location: value } : {})
  }

  return (
    <label style={labelStyle}>
      <span>{t('treatmentModal.siteChangeLocation')}</span>
      <input
        type="text"
        list={SITE_CHANGE_LOCATION_DATALIST_ID}
        placeholder={t('treatmentModal.siteChangeLocationPlaceholder')}
        value={siteLocation}
        onChange={(e) => handleChange(e.target.value)}
        style={inputStyle}
        autoFocus
      />
      <datalist id={SITE_CHANGE_LOCATION_DATALIST_ID}>
        {PREDEFINED_LOCATIONS.map((key: PredefinedLocation) => (
          <option
            key={key}
            value={t(`treatmentModal.siteChangeLocations.${key}`)}
          />
        ))}
      </datalist>
    </label>
  )
}
