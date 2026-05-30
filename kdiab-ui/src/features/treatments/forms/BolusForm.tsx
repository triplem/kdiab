import { useState, useEffect, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { profilesApi } from '../../../api/profilesApi'
import { inputStyle, labelStyle } from './formStyles'

export interface BolusFormData {
  insulin: number
  insulinType?: string
}

interface BolusFormProps {
  initialData?: Partial<BolusFormData>
  validationError: string | null
  userId?: string
  onDataChange: (data: BolusFormData | null) => void
}

export function BolusForm({ initialData, validationError, userId, onDataChange }: BolusFormProps) {
  const { t } = useTranslation()
  const [insulinUnits, setInsulinUnits] = useState(initialData?.insulin != null ? String(initialData.insulin) : '')
  const [insulinType, setInsulinType] = useState(initialData?.insulinType ?? '')
  // Stable ref to avoid stale closure in useEffect without suppressing exhaustive-deps
  const onDataChangeRef = useRef(onDataChange)
  onDataChangeRef.current = onDataChange

  const { data: activeProfile } = useQuery({
    queryKey: ['profiles-active-single', userId],
    queryFn: async () => {
      if (!userId) return null
      const response = await profilesApi.listProfiles(userId, ['ACTIVE'])
      return response.data.items[0] ?? null
    },
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  const buildData = useCallback((units: string, type: string): BolusFormData | null => {
    const v = parseFloat(units)
    if (isNaN(v) || v <= 0) return null
    return type ? { insulin: v, insulinType: type } : { insulin: v }
  }, [])

  // pre-fill from profile; use ref to avoid callback dep loop
  useEffect(() => {
    if (initialData?.insulinType != null) return
    if (insulinType !== '') return
    const profileInsulinType = activeProfile?.insulinType
    if (profileInsulinType) {
      setInsulinType(profileInsulinType)
      onDataChangeRef.current(buildData(insulinUnits, profileInsulinType))
    }
  }, [activeProfile, initialData?.insulinType, insulinType, insulinUnits, buildData])

  const handleUnitsChange = (value: string) => {
    setInsulinUnits(value)
    onDataChange(buildData(value, insulinType))
  }

  const handleTypeChange = (value: string) => {
    setInsulinType(value)
    onDataChange(buildData(insulinUnits, value))
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
