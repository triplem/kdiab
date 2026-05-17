import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface ExerciseFormData {
  duration: number
  intensity: 'light' | 'moderate' | 'intense'
}

interface ExerciseFormProps {
  initialData?: Partial<ExerciseFormData>
  validationError: string | null
  onDataChange: (data: ExerciseFormData | null) => void
}

export function ExerciseForm({ initialData, validationError, onDataChange }: ExerciseFormProps) {
  const { t } = useTranslation()
  const [exerciseDuration, setExerciseDuration] = useState(
    initialData?.duration != null ? String(initialData.duration) : ''
  )
  const [exerciseIntensity, setExerciseIntensity] = useState<'light' | 'moderate' | 'intense'>(
    initialData?.intensity ?? 'moderate'
  )

  const buildData = (duration: string, intensity: 'light' | 'moderate' | 'intense'): ExerciseFormData | null => {
    const dur = parseInt(duration, 10)
    if (isNaN(dur) || dur <= 0) return null
    return { duration: dur, intensity }
  }

  const handleDurationChange = (value: string) => {
    setExerciseDuration(value)
    onDataChange(buildData(value, exerciseIntensity))
  }

  const handleIntensityChange = (value: string) => {
    const intensity = value as 'light' | 'moderate' | 'intense'
    setExerciseIntensity(intensity)
    onDataChange(buildData(exerciseDuration, intensity))
  }

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.exerciseDuration')}</span>
        <input
          type="number"
          min="1"
          max="600"
          step="1"
          placeholder="e.g. 60"
          value={exerciseDuration}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.exerciseIntensity')}</span>
        <select
          value={exerciseIntensity}
          onChange={(e) => handleIntensityChange(e.target.value)}
          style={inputStyle}
        >
          <option value="light">{t('treatmentModal.intensityLight')}</option>
          <option value="moderate">{t('treatmentModal.intensityModerate')}</option>
          <option value="intense">{t('treatmentModal.intensityIntense')}</option>
        </select>
      </label>
    </>
  )
}
