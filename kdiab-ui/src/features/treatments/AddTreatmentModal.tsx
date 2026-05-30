import React, { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { MealForm } from './forms/MealForm'
import { BolusForm } from './forms/BolusForm'
import { ComboBolusForm } from './forms/ComboBolusForm'
import { BasalForm } from './forms/BasalForm'
import { TempBasalForm } from './forms/TempBasalForm'
import { PumpSuspendForm } from './forms/PumpSuspendForm'
import { CarbsForm } from './forms/CarbsForm'
import { ExerciseForm } from './forms/ExerciseForm'
import { NoteForm } from './forms/NoteForm'
import { SiteChangeForm } from './forms/SiteChangeForm'
import { SensorInsertForm } from './forms/SensorInsertForm'
import { InsulinChangeForm } from './forms/InsulinChangeForm'
import { PumpBatteryChangeForm } from './forms/PumpBatteryChangeForm'
import { ActivityForm } from './forms/ActivityForm'
import { HypoTreatmentForm } from './forms/HypoTreatmentForm'
import { inputStyle, labelStyle } from './forms/formStyles'

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
  | 'PUMP_BATTERY_CHANGE'
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
  glucoseUnit?: string
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
  'PUMP_BATTERY_CHANGE',
  'ACTIVITY',
]

function nowDate(): string {
  const d = new Date()
  return `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}.${d.getFullYear()}`
}

function nowTime(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function isoToDisplay(isoDate: string): string {
  return `${isoDate.slice(8, 10)}.${isoDate.slice(5, 7)}.${isoDate.slice(0, 4)}`
}

function combineDatetime(date: string, time: string): string {
  const [dd, mm, yyyy] = date.split('.')
  return new Date(`${yyyy}-${mm}-${dd}T${time}:00`).toISOString()
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
  glucoseUnit = 'mg/dL',
}) => {
  const { t } = useTranslation()
  const firstInputRef = useRef<HTMLSelectElement>(null)
  const [type, setType] = useState<PatientTreatmentType>('MEAL')
  const [treatedDate, setTreatedDate] = useState(nowDate)
  const [treatedTime, setTreatedTime] = useState(nowTime)
  const [timeError, setTimeError] = useState<string | null>(null)
  const [notes, setNotes] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/

  const handleTimeBlur = () => {
    setTimeError(TIME_PATTERN.test(treatedTime) ? null : t('modal.invalidTime', { defaultValue: 'Enter time as HH:mm (00:00–23:59)' }))
  }

  // Pending form data from the active sub-form
  const [pendingData, setPendingData] = useState<Record<string, unknown> | null>(null)

  // Extract initial data for each form type from editMode
  const editData = editMode?.data ?? {}

  // Pre-fill form when entering edit mode
  useEffect(() => {
    if (!editMode) return
    setType(editMode.type as PatientTreatmentType)
    const dt = new Date(editMode.treatedAt)
    const local = new Date(dt.getTime() - dt.getTimezoneOffset() * 60000)
    setTreatedDate(isoToDisplay(local.toISOString().slice(0, 10)))
    setTreatedTime(local.toISOString().slice(11, 16))
    setNotes(editMode.notes ?? '')
    setPendingData(null)
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

  if (!isOpen) return null

  const isEditMode = !!editMode

  const handleTypeChange = (newType: PatientTreatmentType) => {
    setType(newType)
    setPendingData(null)
    setValidationError(null)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setValidationError(null)

    if (!TIME_PATTERN.test(treatedTime)) {
      setTimeError(t('modal.invalidTime', { defaultValue: 'Enter time as HH:mm (00:00–23:59)' }))
      return
    }

    if (type === 'MEAL') {
      const data = pendingData as { insulin: number; carbs: number } | null
      if (!data || data.insulin <= 0 || data.carbs <= 0) {
        setValidationError(t('treatmentModal.validationError'))
        return
      }
      if (isEditMode) {
        onSave({
          type: 'MEAL',
          treatedAt: combineDatetime(treatedDate, treatedTime),
          data: { insulin: data.insulin, carbs: data.carbs },
          ...(notes.trim() && { notes: notes.trim() }),
        })
      } else {
        onSaveMeal({
          treatedAt: combineDatetime(treatedDate, treatedTime),
          insulinUnits: data.insulin,
          carbs: data.carbs,
        })
      }
      return
    }

    if (pendingData === null) {
      setValidationError(t('treatmentModal.validationError'))
      return
    }

    onSave({
      type,
      treatedAt: combineDatetime(treatedDate, treatedTime),
      data: pendingData,
      ...(notes.trim() && { notes: notes.trim() }),
    })
  }

  const renderTypeFields = () => {
    switch (type) {
      case 'MEAL':
        return (
          <MealForm
            {...(isEditMode && { initialData: { insulin: editData.insulin as number, carbs: editData.carbs as number } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'BOLUS':
      case 'CORRECTION_BOLUS':
        return (
          <BolusForm
            {...(isEditMode && { initialData: { insulin: editData.insulin as number, insulinType: editData.insulinType as string } })}
            validationError={validationError}
            {...(userId !== undefined && { userId })}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'COMBO_BOLUS':
        return (
          <ComboBolusForm
            {...(isEditMode && { initialData: { insulin: editData.insulin as number, splitNow: editData.splitNow as number, duration: editData.duration as number } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'BASAL':
        return (
          <BasalForm
            {...(isEditMode && { initialData: { insulin: editData.insulin as number, insulinType: editData.insulinType as string, duration: editData.duration as number } })}
            validationError={validationError}
            {...(userId !== undefined && { userId })}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'TEMP_BASAL':
        return (
          <TempBasalForm
            {...(isEditMode && { initialData: { rate: editData.rate as number, duration: editData.duration as number, absolute: editData.absolute as boolean } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'PUMP_SUSPEND':
        return (
          <PumpSuspendForm
            {...(isEditMode && { initialData: { duration: editData.duration as number, reason: editData.reason as string } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'CARBS':
        return (
          <CarbsForm
            {...(isEditMode && { initialData: { carbs: editData.carbs as number, absorptionTime: editData.absorptionTime as number } })}
            validationError={validationError}
            {...(userId !== undefined && { userId })}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'EXERCISE':
        return (
          <ExerciseForm
            {...(isEditMode && { initialData: { duration: editData.duration as number, intensity: editData.intensity as 'light' | 'moderate' | 'intense' } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'NOTE':
        return (
          <NoteForm
            {...(isEditMode && { initialData: { text: editData.text as string } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'SITE_CHANGE':
        return (
          <SiteChangeForm
            {...(isEditMode && { initialData: { location: editData.location as string } })}
            onDataChange={(data) => setPendingData(data as Record<string, unknown>)}
          />
        )
      case 'SENSOR_INSERT':
        return (
          <SensorInsertForm
            {...(isEditMode && { initialData: { sensor: editData.sensor as string } })}
            onDataChange={(data) => setPendingData(data as Record<string, unknown>)}
          />
        )
      case 'INSULIN_CHANGE':
        return (
          <InsulinChangeForm
            {...(isEditMode && { initialData: { insulinType: editData.insulinType as string } })}
            onDataChange={(data) => setPendingData(data as Record<string, unknown>)}
          />
        )
      case 'PUMP_BATTERY_CHANGE':
        return (
          <PumpBatteryChangeForm
            onDataChange={(data) => setPendingData(data as Record<string, unknown>)}
          />
        )
      case 'ACTIVITY':
        return (
          <ActivityForm
            {...(isEditMode && { initialData: { name: editData.name as string, duration: editData.duration as number, intensity: editData.intensity as 'low' | 'moderate' | 'high' } })}
            validationError={validationError}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
      case 'HYPO_TREATMENT':
        return (
          <HypoTreatmentForm
            {...(isEditMode && { initialData: { carbs: editData.carbs as number, reason: editData.reason as string } })}
            validationError={validationError}
            glucoseUnit={glucoseUnit}
            onDataChange={(data) => setPendingData(data as Record<string, unknown> | null)}
          />
        )
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose} role="presentation">
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
              onChange={(e) => handleTypeChange(e.target.value as PatientTreatmentType)}
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

          <div style={{ display: 'flex', gap: '8px' }}>
            <label style={{ ...labelStyle, flex: 1 }}>
              <span>{t('treatmentModal.treatedAt')}</span>
              <input
                type="text"
                value={treatedDate}
                onChange={(e) => setTreatedDate(e.target.value)}
                placeholder="dd.MM.yyyy"
                pattern="\d{2}\.\d{2}\.\d{4}"
                inputMode="numeric"
                style={inputStyle}
                required
              />
            </label>
            <label style={{ ...labelStyle, flex: '0 0 120px' }}>
              <span>{t('modal.measuredTime', { defaultValue: 'Time' })}</span>
              <input
                type="text"
                placeholder="HH:mm"
                pattern="([01][0-9]|2[0-3]):[0-5][0-9]"
                maxLength={5}
                value={treatedTime}
                onChange={(e) => { setTreatedTime(e.target.value.replace(/[^0-9:]/g, '').slice(0, 5)); setTimeError(null) }}
                onBlur={handleTimeBlur}
                style={{ ...inputStyle, borderColor: timeError ? '#ef4444' : undefined }}
                required
              />
              {timeError && <span style={{ color: '#ef4444', fontSize: '0.75rem' }}>{timeError}</span>}
            </label>
          </div>

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

          <div className="modal-footer">
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
