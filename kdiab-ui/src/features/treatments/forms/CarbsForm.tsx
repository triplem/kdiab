import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { carbsApi } from '../../../api/carbsApi'
import type { FoodEntryResponse } from '../../../api/carbsApi'
import { profilesApi } from '../../../api/profilesApi'
import { inputStyle, labelStyle } from './formStyles'

export interface CarbsFormData {
  carbs: number
  absorptionTime?: number
}

interface CarbsFormProps {
  initialData?: Partial<CarbsFormData>
  validationError: string | null
  userId?: string
  onDataChange: (data: CarbsFormData | null) => void
}

export function CarbsForm({ initialData, validationError, userId, onDataChange }: CarbsFormProps) {
  const { t } = useTranslation()
  const [carbs, setCarbs] = useState(initialData?.carbs != null ? String(initialData.carbs) : '')
  const [absorptionTime, setAbsorptionTime] = useState(
    initialData?.absorptionTime != null ? String(initialData.absorptionTime) : ''
  )
  const [foodSearchQuery, setFoodSearchQuery] = useState('')
  const [foodSearchResults, setFoodSearchResults] = useState<FoodEntryResponse[]>([])
  const foodSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Track whether the user has manually overridden the auto-computed absorptionTime
  const absorptionManuallySet = useRef(false)

  // Uses the same cache key as BolusForm and BasalForm — no extra network request.
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

  useEffect(() => {
    if (!userId) {
      setFoodSearchResults([])
      return
    }
    if (foodSearchTimerRef.current) clearTimeout(foodSearchTimerRef.current)
    foodSearchTimerRef.current = setTimeout(() => {
      carbsApi.listFoods(userId, 0, 10, foodSearchQuery || undefined)
        .then((res) => setFoodSearchResults(res.data.items))
        .catch(() => setFoodSearchResults([]))
    }, 300)
    return () => {
      if (foodSearchTimerRef.current) clearTimeout(foodSearchTimerRef.current)
    }
  }, [foodSearchQuery, userId])

  const buildData = (carbsVal: string, absorption: string): CarbsFormData | null => {
    const v = parseFloat(carbsVal)
    if (isNaN(v) || v <= 0) return null
    const abs = parseFloat(absorption)
    return absorption && !isNaN(abs) ? { carbs: v, absorptionTime: abs } : { carbs: v }
  }

  const computeAbsorption = (carbsVal: number): number => {
    const rate = activeProfile?.carbAbsorptionRateGPerHour ?? 20
    return Math.round((carbsVal / rate) * 10) / 10
  }

  const handleCarbsChange = (value: string) => {
    setCarbs(value)
    const carbsVal = parseFloat(value)
    if (!isNaN(carbsVal) && carbsVal > 0 && !absorptionManuallySet.current) {
      const computed = computeAbsorption(carbsVal)
      const computedStr = String(computed)
      setAbsorptionTime(computedStr)
      onDataChange({ carbs: carbsVal, absorptionTime: computed })
    } else {
      onDataChange(buildData(value, absorptionTime))
    }
  }

  const handleAbsorptionChange = (value: string) => {
    absorptionManuallySet.current = true
    setAbsorptionTime(value)
    onDataChange(buildData(carbs, value))
  }

  const handleFoodSelect = (food: FoodEntryResponse) => {
    const carbValue = String(Math.round(food.carbsForPortion))
    setCarbs(carbValue)
    setFoodSearchQuery('')
    setFoodSearchResults([])
    if (!absorptionManuallySet.current) {
      const carbsVal = Math.round(food.carbsForPortion)
      const computed = computeAbsorption(carbsVal)
      const computedStr = String(computed)
      setAbsorptionTime(computedStr)
      onDataChange({ carbs: carbsVal, absorptionTime: computed })
    } else {
      onDataChange(buildData(carbValue, absorptionTime))
    }
  }

  return (
    <>
      {userId && (
        <div style={{ position: 'relative' }}>
          <label style={labelStyle}>
            <span>{t('treatmentModal.foodSearch')}</span>
            <input
              type="text"
              placeholder={t('treatmentModal.foodSearchPlaceholder')}
              value={foodSearchQuery}
              onChange={(e) => setFoodSearchQuery(e.target.value)}
              style={inputStyle}
              autoComplete="off"
            />
          </label>
          {foodSearchResults.length > 0 && (
            <ul style={{
              position: 'absolute',
              zIndex: 100,
              background: 'var(--bg-surface, #fff)',
              border: '1px solid var(--border-color)',
              borderRadius: '4px',
              margin: 0,
              padding: 0,
              listStyle: 'none',
              width: '100%',
              maxHeight: '180px',
              overflowY: 'auto',
              boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
            }}>
              {foodSearchResults.map((food) => (
                <li
                  key={food.id}
                  style={{ padding: '0.5rem 0.75rem', cursor: 'pointer', borderBottom: '1px solid var(--border-color)' }}
                  onClick={() => handleFoodSelect(food)}
                >
                  <strong>{food.name}</strong>
                  <span style={{ color: 'var(--text-secondary)', marginLeft: '0.5rem', fontSize: '0.85rem' }}>
                    {food.portionGrams}g → {food.carbsForPortion.toFixed(1)}g carbs
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <label style={labelStyle}>
        <span>{t('treatmentModal.carbs')}</span>
        <input
          type="number"
          min="1"
          max="500"
          step="1"
          placeholder="e.g. 45"
          value={carbs}
          onChange={(e) => handleCarbsChange(e.target.value)}
          style={inputStyle}
          required
          autoFocus={!userId}
          aria-describedby={validationError ? 'validation-error' : undefined}
        />
      </label>
      <label style={labelStyle}>
        <span>{t('treatmentModal.absorptionTime')}</span>
        <input
          type="number"
          min="0.5"
          max="10"
          step="0.5"
          placeholder="e.g. 3"
          value={absorptionTime}
          onChange={(e) => handleAbsorptionChange(e.target.value)}
          style={inputStyle}
        />
      </label>
    </>
  )
}
