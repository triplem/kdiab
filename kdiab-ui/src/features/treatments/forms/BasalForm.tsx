import { useState, useEffect, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { profilesApi } from '../../../api/profilesApi'
import { inputStyle, labelStyle } from './formStyles'

export interface BasalFormData {
  insulin: number
  insulinType?: string
  duration?: number
}

interface BasalFormProps {
  initialData?: Partial<{ insulin: number; insulinType: string; duration: number }>
  validationError: string | null
  userId?: string
  onDataChange: (data: BasalFormData | null) => void
}

export function BasalForm({ initialData, validationError, userId, onDataChange }: BasalFormProps) {
  const { t } = useTranslation()
  // duration stored in minutes from server; display as hours
  const initialDurationHours = initialData?.duration != null ? String(Number(initialData.duration) / 60) : ''
  const [basalInsulin, setBasalInsulin] = useState(initialData?.insulin != null ? String(initialData.insulin) : '')
  const [basalInsulinType, setBasalInsulinType] = useState(initialData?.insulinType ?? '')
  const [basalDurationHours, setBasalDurationHours] = useState(initialDurationHours)
  // Stable ref to avoid stale closure in useEffect without suppressing exhaustive-deps
  const onDataChangeRef = useRef(onDataChange)
  onDataChangeRef.current = onDataChange

  // Fetch the active profile to pre-fill insulin type
  const { data: activeProfile } = useQuery({
    queryKey: ['profiles-active-single', userId],
    queryFn: async () => {
      if (!userId) return null
      const response = await profilesApi.listProfiles(userId, ['ACTIVE'])
      return response.data.items[0] ?? null
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })

  // Fetch insulin catalogue for datalist autocomplete
  const { data: insulins = [] } = useQuery({
    queryKey: ['insulins'],
    queryFn: async () => {
      const response = await profilesApi.getInsulins()
      return response.data
    },
    staleTime: 10 * 60 * 1000,
  })

  const buildData = useCallback((insulin: string, insulinType: string, durationHours: string): BasalFormData | null => {
    const v = parseFloat(insulin)
    if (isNaN(v) || v <= 0) return null
    const durHours = parseFloat(durationHours)
    return {
      insulin: v,
      ...(insulinType && { insulinType }),
      ...(durationHours && !isNaN(durHours) && { duration: Math.round(durHours * 60) }),
    }
  }, [])

  // Pre-fill insulinType from active profile when loaded (only if user hasn't typed anything yet).
  // onDataChangeRef keeps the latest callback without adding it as a reactive dep,
  // avoiding an infinite loop when the parent re-creates the callback on each render.
  useEffect(() => {
    if (initialData?.insulinType != null) return
    if (basalInsulinType !== '') return
    const profileInsulinType = activeProfile?.insulinType
    if (profileInsulinType) {
      setBasalInsulinType(profileInsulinType)
      onDataChangeRef.current(buildData(basalInsulin, profileInsulinType, basalDurationHours))
    }
  }, [activeProfile, initialData?.insulinType, basalInsulinType, basalInsulin, basalDurationHours, buildData])

  const handleInsulinChange = (value: string) => {
    setBasalInsulin(value)
    onDataChange(buildData(value, basalInsulinType, basalDurationHours))
  }

  const handleTypeChange = (value: string) => {
    setBasalInsulinType(value)
    onDataChange(buildData(basalInsulin, value, basalDurationHours))
  }

  const handleDurationChange = (value: string) => {
    setBasalDurationHours(value)
    onDataChange(buildData(basalInsulin, basalInsulinType, value))
  }

  const insulinListId = 'basal-insulin-type-list'

  return (
    <>
      <label style={labelStyle}>
        <span>{t('treatmentModal.insulinUnits')}</span>
        <input
          type="number"
          min="0.1"
          max="200"
          step="0.1"
          placeholder="e.g. 10.0"
          value={basalInsulin}
          onChange={(e) => handleInsulinChange(e.target.value)}
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
          list={insulins.length > 0 ? insulinListId : undefined}
          placeholder={t('treatmentModal.insulinTypePlaceholder', { defaultValue: 'e.g. Lantus' })}
          value={basalInsulinType}
          onChange={(e) => handleTypeChange(e.target.value)}
          style={inputStyle}
        />
        {insulins.length > 0 && (
          <datalist id={insulinListId}>
            {insulins.map((ins) => (
              <option key={ins.id} value={ins.name} />
            ))}
          </datalist>
        )}
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.durationHours')}</span>
        <input
          type="number"
          min="1"
          max="48"
          step="0.5"
          placeholder="e.g. 24"
          value={basalDurationHours}
          onChange={(e) => handleDurationChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
