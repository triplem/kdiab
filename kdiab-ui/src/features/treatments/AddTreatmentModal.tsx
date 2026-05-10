import React, { useState, useEffect, useRef } from 'react'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import { carbsApi } from '../../api/carbsApi'
import type { FoodEntryResponse } from '../../api/carbsApi'

type PatientTreatmentType =
  | 'BOLUS'
  | 'CARBS'
  | 'CORRECTION_BOLUS'
  | 'BASAL'
  | 'COMBO_BOLUS'
  | 'TEMP_BASAL'
  | 'PUMP_SUSPEND'
  | 'EXERCISE'
  | 'NOTE'
  | 'SITE_CHANGE'
  | 'SENSOR_INSERT'
  | 'INSULIN_CHANGE'
  | 'ACTIVITY'
  | 'MEAL'
  | 'HYPO_TREATMENT'

export interface TreatmentInput {
  type: string
  treatedAt: string
  data: Record<string, unknown>
  notes?: string
}

export interface MealInput {
  treatedAt: string
  insulinUnits: number
  carbs: number
}

export interface TreatmentEditMode {
  id: string
  type: string
  treatedAt: string
  data: Record<string, unknown>
  notes?: string
}

interface AddTreatmentModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (treatment: TreatmentInput) => void
  onSaveMeal: (meal: MealInput) => void
  isSaving?: boolean
  error?: string | null
  editMode?: TreatmentEditMode
  userId?: string
}

const TREATMENT_TYPES: PatientTreatmentType[] = [
  'MEAL',
  'BOLUS',
  'CORRECTION_BOLUS',
  'COMBO_BOLUS',
  'CARBS',
  'HYPO_TREATMENT',
  'BASAL',
  'TEMP_BASAL',
  'PUMP_SUSPEND',
  'EXERCISE',
  'NOTE',
  'SITE_CHANGE',
  'SENSOR_INSERT',
  'INSULIN_CHANGE',
  'ACTIVITY',
]

const inputStyle: React.CSSProperties = {
  padding: '8px',
  border: '1px solid var(--border-color)',
  borderRadius: '4px',
  fontSize: '1rem',
  width: '100%',
  boxSizing: 'border-box',
}

const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
}

function nowRounded(): string {
  const d = new Date()
  d.setSeconds(0, 0)
  return d.toISOString().slice(0, 16)
}

export const AddTreatmentModal: React.FC<AddTreatmentModalProps> = ({
  isOpen,
  onClose,
  onSave,
  onSaveMeal,
  isSaving = false,
  error,
  editMode,
  userId,
}) => {
  const { locale } = useTimeFormat()
  const { t } = useTranslation()
  const firstInputRef = useRef<HTMLSelectElement>(null)
  const [type, setType] = useState<PatientTreatmentType>('MEAL')
  const [treatedAt, setTreatedAt] = useState(nowRounded)
  const [notes, setNotes] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  // Meal
  const [mealInsulin, setMealInsulin] = useState('')
  const [mealCarbs, setMealCarbs] = useState('')

  // Bolus
  const [insulinUnits, setInsulinUnits] = useState('')
  const [insulinType, setInsulinType] = useState('')

  const [basalInsulin, setBasalInsulin] = useState('')
  const [basalInsulinType, setBasalInsulinType] = useState('')
  const [basalDurationHours, setBasalDurationHours] = useState('')

  const [comboInsulin, setComboInsulin] = useState('')
  const [comboSplitNow, setComboSplitNow] = useState('50')
  const [comboDurationMin, setComboDurationMin] = useState('')

  const [tempBasalRate, setTempBasalRate] = useState('')
  const [tempBasalDurationMin, setTempBasalDurationMin] = useState('')
  const [tempBasalAbsolute, setTempBasalAbsolute] = useState(true)

  const [pumpSuspendDurationMin, setPumpSuspendDurationMin] = useState('')
  const [pumpSuspendReason, setPumpSuspendReason] = useState('')

  const [carbs, setCarbs] = useState('')
  const [absorptionTime, setAbsorptionTime] = useState('')

  const [exerciseDuration, setExerciseDuration] = useState('')
  const [exerciseIntensity, setExerciseIntensity] = useState<'light' | 'moderate' | 'intense'>('moderate')

  const [noteText, setNoteText] = useState('')
  const [siteLocation, setSiteLocation] = useState('')
  const [sensorModel, setSensorModel] = useState('')
  const [newInsulinType, setNewInsulinType] = useState('')
  const [activityName, setActivityName] = useState('')
  const [activityDuration, setActivityDuration] = useState('')
  const [activityIntensity, setActivityIntensity] = useState<'low' | 'moderate' | 'high'>('moderate')

  const [hypoCarbs, setHypoCarbs] = useState('')
  const [hypoReason, setHypoReason] = useState('')

  // Food search for CARBS quick-fill
  const [foodSearchQuery, setFoodSearchQuery] = useState('')
  const [foodSearchResults, setFoodSearchResults] = useState<FoodEntryResponse[]>([])
  const foodSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Pre-fill form when entering edit mode
  useEffect(() => {
    if (!editMode) return
    setType(editMode.type as PatientTreatmentType)
    const dt = new Date(editMode.treatedAt)
    const local = new Date(dt.getTime() - dt.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16)
    setTreatedAt(local)
    setNotes(editMode.notes ?? '')
    const d = editMode.data
    switch (editMode.type) {
      case 'BOLUS':
      case 'CORRECTION_BOLUS':
        setInsulinUnits(String(d.insulin ?? ''))
        setInsulinType(String(d.insulinType ?? ''))
        break
      case 'BASAL':
        setBasalInsulin(String(d.insulin ?? ''))
        setBasalInsulinType(String(d.insulinType ?? ''))
        setBasalDurationHours(d.duration != null ? String(Number(d.duration) / 60) : '')
        break
      case 'COMBO_BOLUS':
        setComboInsulin(String(d.insulin ?? ''))
        setComboSplitNow(String(d.splitNow ?? '50'))
        setComboDurationMin(String(d.duration ?? ''))
        break
      case 'TEMP_BASAL':
        setTempBasalRate(String(d.rate ?? ''))
        setTempBasalDurationMin(String(d.duration ?? ''))
        setTempBasalAbsolute(d.absolute !== false)
        break
      case 'PUMP_SUSPEND':
        setPumpSuspendDurationMin(String(d.duration ?? ''))
        setPumpSuspendReason(String(d.reason ?? ''))
        break
      case 'CARBS':
        setCarbs(String(d.carbs ?? ''))
        setAbsorptionTime(String(d.absorptionTime ?? ''))
        break
      case 'EXERCISE':
        setExerciseDuration(String(d.duration ?? ''))
        setExerciseIntensity((d.intensity as 'light' | 'moderate' | 'intense') ?? 'moderate')
        break
      case 'NOTE':
        setNoteText(String(d.text ?? ''))
        break
      case 'SITE_CHANGE':
        setSiteLocation(String(d.location ?? ''))
        break
      case 'SENSOR_INSERT':
        setSensorModel(String(d.sensor ?? ''))
        break
      case 'INSULIN_CHANGE':
        setNewInsulinType(String(d.insulinType ?? ''))
        break
      case 'ACTIVITY':
        setActivityName(String(d.name ?? ''))
        setActivityDuration(String(d.duration ?? ''))
        setActivityIntensity((d.intensity as 'low' | 'moderate' | 'high') ?? 'moderate')
        break
      case 'HYPO_TREATMENT':
        setHypoCarbs(String(d.carbs ?? ''))
        setHypoReason(String(d.reason ?? ''))
        break
      default: break
    }
  }, [editMode])

  useEffect(() => {
    if (!isOpen) return
    firstInputRef.current?.focus()

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  useEffect(() => {
    if (type !== 'CARBS' || !userId) {
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
  }, [foodSearchQuery, type, userId])

  if (!isOpen) return null

  const isEditMode = !!editMode

  const buildData = (): Record<string, unknown> | null => {
    switch (type) {
      case 'MEAL':
        return null // handled separately via onSaveMeal
      case 'BOLUS':
      case 'CORRECTION_BOLUS': {
        const v = parseFloat(insulinUnits)
        if (isNaN(v) || v <= 0) return null
        return insulinType ? { insulin: v, insulinType } : { insulin: v }
      }
      case 'COMBO_BOLUS': {
        const v = parseFloat(comboInsulin)
        if (isNaN(v) || v <= 0) return null
        const splitNow = parseFloat(comboSplitNow)
        const dur = parseInt(comboDurationMin, 10)
        return {
          insulin: v,
          splitNow: isNaN(splitNow) ? 50 : splitNow,
          splitExt: isNaN(splitNow) ? 50 : 100 - splitNow,
          ...(comboDurationMin && !isNaN(dur) && { duration: dur }),
        }
      }
      case 'BASAL': {
        const v = parseFloat(basalInsulin)
        if (isNaN(v) || v <= 0) return null
        const durHours = parseFloat(basalDurationHours)
        return {
          insulin: v,
          ...(basalInsulinType && { insulinType: basalInsulinType }),
          ...(basalDurationHours && !isNaN(durHours) && { duration: Math.round(durHours * 60) }),
        }
      }
      case 'TEMP_BASAL': {
        const rate = parseFloat(tempBasalRate)
        if (isNaN(rate) || rate < 0) return null
        const dur = parseInt(tempBasalDurationMin, 10)
        if (isNaN(dur) || dur <= 0) return null
        return { rate, duration: dur, absolute: tempBasalAbsolute }
      }
      case 'PUMP_SUSPEND': {
        const dur = parseInt(pumpSuspendDurationMin, 10)
        if (isNaN(dur) || dur <= 0) return null
        return { duration: dur, ...(pumpSuspendReason.trim() && { reason: pumpSuspendReason.trim() }) }
      }
      case 'CARBS': {
        const v = parseFloat(carbs)
        if (isNaN(v) || v <= 0) return null
        const abs = parseFloat(absorptionTime)
        return absorptionTime && !isNaN(abs) ? { carbs: v, absorptionTime: abs } : { carbs: v }
      }
      case 'EXERCISE': {
        const dur = parseInt(exerciseDuration, 10)
        if (isNaN(dur) || dur <= 0) return null
        return { duration: dur, intensity: exerciseIntensity }
      }
      case 'NOTE': {
        if (!noteText.trim()) return null
        return { text: noteText.trim() }
      }
      case 'SITE_CHANGE':
        return siteLocation ? { location: siteLocation } : {}
      case 'SENSOR_INSERT':
        return sensorModel ? { sensor: sensorModel } : {}
      case 'INSULIN_CHANGE':
        return newInsulinType ? { insulinType: newInsulinType } : {}
      case 'ACTIVITY': {
        const dur = parseInt(activityDuration, 10)
        if (!activityName.trim() || isNaN(dur) || dur <= 0) return null
        return { name: activityName.trim(), duration: dur, intensity: activityIntensity }
      }
      case 'HYPO_TREATMENT': {
        const v = parseFloat(hypoCarbs)
        if (isNaN(v) || v < 1) return null
        return hypoReason.trim() ? { carbs: v, reason: hypoReason.trim() } : { carbs: v }
      }
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setValidationError(null)

    if (type === 'MEAL' && !isEditMode) {
      const insulin = parseFloat(mealInsulin)
      const carbsVal = parseFloat(mealCarbs)
      if (isNaN(insulin) || insulin <= 0 || isNaN(carbsVal) || carbsVal <= 0) {
        setValidationError(t('treatmentModal.validationError'))
        return
      }
      onSaveMeal({
        treatedAt: new Date(treatedAt).toISOString(),
        insulinUnits: insulin,
        carbs: carbsVal,
      })
      return
    }

    const data = buildData()
    if (data === null) {
      setValidationError(t('treatmentModal.validationError'))
      return
    }
    onSave({
      type,
      treatedAt: new Date(treatedAt).toISOString(),
      data,
      ...(notes.trim() && { notes: notes.trim() }),
    })
  }

  const renderTypeFields = () => {
    switch (type) {
      case 'MEAL':
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
                onChange={(e) => setMealInsulin(e.target.value)}
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
                onChange={(e) => setMealCarbs(e.target.value)}
                style={inputStyle}
                required
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
          </>
        )
      case 'BOLUS':
      case 'CORRECTION_BOLUS':
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
                onChange={(e) => setInsulinUnits(e.target.value)}
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
                onChange={(e) => setInsulinType(e.target.value)}
                style={inputStyle}
              />
            </label>
          </>
        )
      case 'COMBO_BOLUS':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('treatmentModal.insulinUnits')}</span>
              <input
                type="number"
                min="0.1"
                max="100"
                step="0.1"
                placeholder="e.g. 3.0"
                value={comboInsulin}
                onChange={(e) => setComboInsulin(e.target.value)}
                style={inputStyle}
                required
                autoFocus
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('treatmentModal.comboSplitNow')}</span>
              <input
                type="number"
                min="0"
                max="100"
                step="5"
                placeholder="e.g. 50"
                value={comboSplitNow}
                onChange={(e) => setComboSplitNow(e.target.value)}
                style={inputStyle}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('treatmentModal.comboDuration')}</span>
              <input
                type="number"
                min="5"
                max="480"
                step="5"
                placeholder="e.g. 120"
                value={comboDurationMin}
                onChange={(e) => setComboDurationMin(e.target.value)}
                style={inputStyle}
              />
            </label>
          </>
        )
      case 'BASAL':
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
                onChange={(e) => setBasalInsulin(e.target.value)}
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
                placeholder="e.g. Lantus"
                value={basalInsulinType}
                onChange={(e) => setBasalInsulinType(e.target.value)}
                style={inputStyle}
              />
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
                onChange={(e) => setBasalDurationHours(e.target.value)}
                style={inputStyle}
              />
            </label>
          </>
        )
      case 'TEMP_BASAL':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('treatmentModal.tempBasalRate')}</span>
              <input
                type="number"
                min="0"
                max="20"
                step="0.05"
                placeholder="e.g. 0.5"
                value={tempBasalRate}
                onChange={(e) => setTempBasalRate(e.target.value)}
                style={inputStyle}
                required
                autoFocus
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('treatmentModal.durationMinutes')}</span>
              <input
                type="number"
                min="5"
                max="1440"
                step="5"
                placeholder="e.g. 30"
                value={tempBasalDurationMin}
                onChange={(e) => setTempBasalDurationMin(e.target.value)}
                style={inputStyle}
                required
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label
              style={{ ...labelStyle, flexDirection: 'row', alignItems: 'center', gap: '8px', cursor: 'pointer' }}
              onClick={() => setTempBasalAbsolute(!tempBasalAbsolute)}
            >
              <input
                type="checkbox"
                checked={tempBasalAbsolute}
                onChange={() => setTempBasalAbsolute(!tempBasalAbsolute)}
              />
              <span>{t('treatmentModal.tempBasalAbsolute')}</span>
            </label>
          </>
        )
      case 'PUMP_SUSPEND':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('treatmentModal.durationMinutes')}</span>
              <input
                type="number"
                min="1"
                max="1440"
                step="1"
                placeholder="e.g. 30"
                value={pumpSuspendDurationMin}
                onChange={(e) => setPumpSuspendDurationMin(e.target.value)}
                style={inputStyle}
                required
                autoFocus
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('treatmentModal.pumpSuspendReason')}</span>
              <input
                type="text"
                placeholder="e.g. exercise"
                value={pumpSuspendReason}
                onChange={(e) => setPumpSuspendReason(e.target.value)}
                style={inputStyle}
              />
            </label>
          </>
        )
      case 'CARBS':
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
                        onClick={() => {
                          setCarbs(String(Math.round(food.carbsForPortion)))
                          setFoodSearchQuery('')
                          setFoodSearchResults([])
                        }}
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
                onChange={(e) => setCarbs(e.target.value)}
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
                onChange={(e) => setAbsorptionTime(e.target.value)}
                style={inputStyle}
              />
            </label>
          </>
        )
      case 'EXERCISE':
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
                onChange={(e) => setExerciseDuration(e.target.value)}
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
                onChange={(e) => setExerciseIntensity(e.target.value as 'light' | 'moderate' | 'intense')}
                style={inputStyle}
              >
                <option value="light">{t('treatmentModal.intensityLight')}</option>
                <option value="moderate">{t('treatmentModal.intensityModerate')}</option>
                <option value="intense">{t('treatmentModal.intensityIntense')}</option>
              </select>
            </label>
          </>
        )
      case 'NOTE':
        return (
          <label style={labelStyle}>
            <span>{t('treatmentModal.noteText')}</span>
            <textarea
              placeholder="e.g. felt low before dinner"
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
              style={{ ...inputStyle, minHeight: '80px', resize: 'vertical' }}
              required
              autoFocus
              aria-describedby={validationError ? 'validation-error' : undefined}
            />
          </label>
        )
      case 'SITE_CHANGE':
        return (
          <label style={labelStyle}>
            <span>{t('treatmentModal.location')}</span>
            <input
              type="text"
              placeholder="e.g. left abdomen"
              value={siteLocation}
              onChange={(e) => setSiteLocation(e.target.value)}
              style={inputStyle}
              autoFocus
            />
          </label>
        )
      case 'SENSOR_INSERT':
        return (
          <label style={labelStyle}>
            <span>{t('treatmentModal.sensorModel')}</span>
            <input
              type="text"
              placeholder="e.g. Dexcom G7"
              value={sensorModel}
              onChange={(e) => setSensorModel(e.target.value)}
              style={inputStyle}
              autoFocus
            />
          </label>
        )
      case 'INSULIN_CHANGE':
        return (
          <label style={labelStyle}>
            <span>{t('treatmentModal.newInsulinType')}</span>
            <input
              type="text"
              placeholder="e.g. NovoRapid"
              value={newInsulinType}
              onChange={(e) => setNewInsulinType(e.target.value)}
              style={inputStyle}
              autoFocus
            />
          </label>
        )
      case 'ACTIVITY':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('treatmentModal.activityName')}</span>
              <input
                type="text"
                placeholder="e.g. running, cycling"
                value={activityName}
                onChange={(e) => setActivityName(e.target.value)}
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
                onChange={(e) => setActivityDuration(e.target.value)}
                style={inputStyle}
                required
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('treatmentModal.activityIntensity')}</span>
              <select
                value={activityIntensity}
                onChange={(e) => setActivityIntensity(e.target.value as 'low' | 'moderate' | 'high')}
                style={inputStyle}
              >
                <option value="low">{t('treatmentModal.intensityLight')}</option>
                <option value="moderate">{t('treatmentModal.intensityModerate')}</option>
                <option value="high">{t('treatmentModal.intensityIntense')}</option>
              </select>
            </label>
          </>
        )
      case 'HYPO_TREATMENT':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('treatmentModal.carbs')}</span>
              <input
                type="number"
                min="1"
                max="500"
                step="1"
                placeholder="e.g. 15"
                value={hypoCarbs}
                onChange={(e) => setHypoCarbs(e.target.value)}
                style={inputStyle}
                required
                autoFocus
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('treatmentModal.hypoReason')}</span>
              <input
                type="text"
                placeholder="e.g. glucose below 70 mg/dL"
                value={hypoReason}
                onChange={(e) => setHypoReason(e.target.value)}
                style={inputStyle}
              />
            </label>
          </>
        )
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose} aria-hidden="true">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="treatment-modal-title"
        className="modal-box"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="treatment-modal-title" className="modal-title">
          {isEditMode
            ? t('treatmentModal.editTitle', { defaultValue: 'Edit Treatment' })
            : t('treatmentModal.title')}
        </h2>
        {validationError && (
          <div role="alert" className="error" id="validation-error" style={{ marginBottom: '1rem' }}>
            {validationError}
          </div>
        )}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <label style={labelStyle}>
            <span>{t('treatmentModal.type')}</span>
            <select
              ref={firstInputRef}
              value={type}
              onChange={(e) => setType(e.target.value as PatientTreatmentType)}
              style={inputStyle}
              required
              disabled={isEditMode}
            >
              {TREATMENT_TYPES.map((val) => (
                <option key={val} value={val}>
                  {t(`treatmentModal.types.${val}`)}
                </option>
              ))}
            </select>
          </label>

          <label style={labelStyle}>
            <span>{t('treatmentModal.treatedAt')}</span>
            <input
              type="datetime-local"
              lang={locale}
              value={treatedAt}
              onChange={(e) => setTreatedAt(e.target.value)}
              style={inputStyle}
              required
            />
          </label>

          {renderTypeFields()}

          {type !== 'MEAL' && (
            <label style={labelStyle}>
              <span>{t('treatmentModal.notes')}</span>
              <input
                type="text"
                placeholder={t('treatmentModal.notesPlaceholder')}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                style={inputStyle}
              />
            </label>
          )}

          {error && (
            <div role="alert" className="error">
              {error}
            </div>
          )}

          <div
            style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}
          >
            <button type="button" onClick={onClose} style={{ padding: '8px 16px' }}>
              {t('treatmentModal.cancel')}
            </button>
            <button
              type="submit"
              disabled={isSaving}
              className="primary"
              style={{ padding: '8px 16px' }}
            >
              {isSaving ? t('treatmentModal.saving') : t('treatmentModal.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
