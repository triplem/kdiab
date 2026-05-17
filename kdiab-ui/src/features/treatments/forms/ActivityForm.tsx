import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface ActivityFormData {
  name: string
  duration: number
  intensity: 'low' | 'moderate' | 'high'
}

interface ActivityFormProps {
  initialData?: Partial<ActivityFormData>
  validationError: string | null
  onDataChange: (data: ActivityFormData | null) => void
}

export function ActivityForm({ initialData, validationError, onDataChange }: ActivityFormProps) {
  const { t } = useTranslation()
  const [activityName, setActivityName] = useState(initialData?.name ?? '')
  const [activityDuration, setActivityDuration] = useState(
    initialData?.duration != null ? String(initialData.duration) : ''
  )
  const [activityIntensity, setActivityIntensity] = useState<'low' | 'moderate' | 'high'>(
    initialData?.intensity ?? 'moderate'
  )

  const buildData = (name: string, duration: string, intensity: 'low' | 'moderate' | 'high'): ActivityFormData | null => {
    const dur = parseInt(duration, 10)
    if (!name.trim() || isNaN(dur) || dur <= 0) return null
    return { name: name.trim(), duration: dur, intensity }
  }

  const handleNameChange = (value: string) => {
    setActivityName(value)
    onDataChange(buildData(value, activityDuration, activityIntensity))
  }

  const handleDurationChange = (value: string) => {
    setActivityDuration(value)
    onDataChange(buildData(activityName, value, activityIntensity))
  }

  const handleIntensityChange = (value: string) => {
    const intensity = value as 'low' | 'moderate' | 'high'
    setActivityIntensity(intensity)
    onDataChange(buildData(activityName, activityDuration, intensity))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.activityName')}</span>
        <input
          type="text"
          placeholder="e.g. running, cycling"
          value={activityName}
          onChange={(e) => handleNameChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.activityDuration')}</span>
        <input
          type="number"
          min="1"
          max="600"
          step="1"
          placeholder="e.g. 45"
          value={activityDuration}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
          required
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.activityIntensity')}</span>
        <select
          value={activityIntensity}
          onChange={(e) => handleIntensityChange(e.target.value)}
          style={inputStyle}
        >
          <option value="low">{t('treatmentModal.intensityLight')}</option>
          <option value="moderate">{t('treatmentModal.intensityModerate')}</option>
          <option value="high">{t('treatmentModal.intensityIntense')}</option>
        </select>
      </label>
    </>
  )
}
