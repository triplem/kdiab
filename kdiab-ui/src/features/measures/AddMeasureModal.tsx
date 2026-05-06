import React, { useState, useEffect, useRef } from 'react'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'

type MeasureType = 'BGM' | 'CGM' | 'BLOOD_PRESSURE' | 'WEIGHT' | 'PULSE' | 'BG_CHECK' | 'KETONE_CHECK'

export interface MeasureInput {
  type: string
  measuredAt: string
  source: string
  data: Record<string, unknown>
}

export interface MeasureEditMode {
  id: string
  type: string
  measuredAt: string
  data: Record<string, unknown>
}

interface AddMeasureModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (measure: MeasureInput) => void
  glucoseUnit?: string
  weightUnit?: string
  isSaving?: boolean
  error?: string | null
  editMode?: MeasureEditMode
}

const MEASURE_TYPES: MeasureType[] = [
  'BGM',
  'CGM',
  'BG_CHECK',
  'BLOOD_PRESSURE',
  'WEIGHT',
  'PULSE',
  'KETONE_CHECK',
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

export const AddMeasureModal: React.FC<AddMeasureModalProps> = ({
  isOpen,
  onClose,
  onSave,
  glucoseUnit = 'mg/dL',
  weightUnit = 'kg',
  isSaving = false,
  error,
  editMode,
}) => {
  const { locale } = useTimeFormat()
  const { t } = useTranslation()
  const firstInputRef = useRef<HTMLSelectElement>(null)
  const [type, setType] = useState<MeasureType>('BGM')
  const [measuredAt, setMeasuredAt] = useState(nowRounded)
  const [validationError, setValidationError] = useState<string | null>(null)

  const [bgmValue, setBgmValue] = useState('')
  const [cgmValue, setCgmValue] = useState('')
  const [bgCheckValue, setBgCheckValue] = useState('')
  const [ketones, setKetones] = useState('')
  const [ketoneMethod, setKetoneMethod] = useState<'blood' | 'urine'>('blood')
  const [bpSystolic, setBpSystolic] = useState('')
  const [bpDiastolic, setBpDiastolic] = useState('')
  const [weightValue, setWeightValue] = useState('')
  const [pulseBpm, setPulseBpm] = useState('')

  // Pre-fill form when entering edit mode
  useEffect(() => {
    if (!editMode) return
    setType(editMode.type as MeasureType)
    const dt = new Date(editMode.measuredAt)
    const local = new Date(dt.getTime() - dt.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16)
    setMeasuredAt(local)
    const d = editMode.data
    switch (editMode.type) {
      case 'BGM': setBgmValue(String(d.value ?? '')); break
      case 'CGM': setCgmValue(String(d.value ?? '')); break
      case 'BG_CHECK': setBgCheckValue(String(d.value ?? '')); break
      case 'BLOOD_PRESSURE':
        setBpSystolic(String(d.systolic ?? ''))
        setBpDiastolic(String(d.diastolic ?? ''))
        break
      case 'WEIGHT': setWeightValue(String(d.value ?? '')); break
      case 'PULSE': setPulseBpm(String(d.value ?? '')); break
      case 'KETONE_CHECK':
        setKetones(String(d.value ?? ''))
        setKetoneMethod((d.method as 'blood' | 'urine') ?? 'blood')
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

  if (!isOpen) return null

  const isEditMode = !!editMode

  const buildData = (): Record<string, unknown> | null => {
    switch (type) {
      case 'BGM': {
        const v = parseFloat(bgmValue)
        if (isNaN(v) || v <= 0) return null
        return { value: v, unit: glucoseUnit }
      }
      case 'CGM': {
        const v = parseFloat(cgmValue)
        if (isNaN(v) || v <= 0) return null
        return { value: v, unit: glucoseUnit }
      }
      case 'BLOOD_PRESSURE': {
        const sys = parseInt(bpSystolic, 10)
        const dia = parseInt(bpDiastolic, 10)
        if (isNaN(sys) || isNaN(dia) || sys <= 0 || dia <= 0) return null
        return { systolic: sys, diastolic: dia }
      }
      case 'WEIGHT': {
        const v = parseFloat(weightValue)
        if (isNaN(v) || v <= 0) return null
        return { value: v, unit: weightUnit }
      }
      case 'PULSE': {
        const v = parseInt(pulseBpm, 10)
        if (isNaN(v) || v <= 0) return null
        return { value: v }
      }
      case 'BG_CHECK': {
        const v = parseFloat(bgCheckValue)
        if (isNaN(v) || v <= 0) return null
        return { value: v, unit: glucoseUnit }
      }
      case 'KETONE_CHECK': {
        const v = parseFloat(ketones)
        if (isNaN(v) || v < 0) return null
        return { value: v, method: ketoneMethod }
      }
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const data = buildData()
    if (!data) {
      setValidationError(t('modal.validationError'))
      return
    }
    setValidationError(null)
    onSave({
      type,
      measuredAt: new Date(measuredAt).toISOString(),
      source: 'MANUAL',
      data,
    })
  }

  const renderTypeFields = () => {
    switch (type) {
      case 'BGM':
        return (
          <label style={labelStyle}>
            <span>
              {t('modal.bloodGlucose')} ({glucoseUnit})
            </span>
            <input
              type="number"
              min={glucoseUnit === 'mmol/L' ? '1' : '20'}
              max={glucoseUnit === 'mmol/L' ? '33' : '600'}
              step={glucoseUnit === 'mmol/L' ? '0.1' : '1'}
              placeholder={glucoseUnit === 'mmol/L' ? 'e.g. 5.6' : 'e.g. 120'}
              value={bgmValue}
              onChange={(e) => setBgmValue(e.target.value)}
              style={inputStyle}
              required
              autoFocus
              aria-describedby={validationError ? 'validation-error' : undefined}
            />
          </label>
        )
      case 'CGM':
        return (
          <label style={labelStyle}>
            <span>
              {t('modal.cgmReading')} ({glucoseUnit})
            </span>
            <input
              type="number"
              min={glucoseUnit === 'mmol/L' ? '1' : '20'}
              max={glucoseUnit === 'mmol/L' ? '33' : '600'}
              step={glucoseUnit === 'mmol/L' ? '0.1' : '1'}
              placeholder={glucoseUnit === 'mmol/L' ? 'e.g. 5.6' : 'e.g. 120'}
              value={cgmValue}
              onChange={(e) => setCgmValue(e.target.value)}
              style={inputStyle}
              required
              autoFocus
              aria-describedby={validationError ? 'validation-error' : undefined}
            />
          </label>
        )
      case 'BLOOD_PRESSURE':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.systolic')}</span>
              <input
                type="number"
                min="50"
                max="300"
                step="1"
                placeholder="e.g. 120"
                value={bpSystolic}
                onChange={(e) => setBpSystolic(e.target.value)}
                style={inputStyle}
                required
                autoFocus
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.diastolic')}</span>
              <input
                type="number"
                min="30"
                max="200"
                step="1"
                placeholder="e.g. 80"
                value={bpDiastolic}
                onChange={(e) => setBpDiastolic(e.target.value)}
                style={inputStyle}
                required
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
          </>
        )
      case 'WEIGHT':
        return (
          <label style={labelStyle}>
            <span>
              {t('modal.weight')} ({weightUnit})
            </span>
            <input
              type="number"
              min="1"
              max={weightUnit === 'lbs' ? '1100' : '500'}
              step="0.1"
              placeholder={weightUnit === 'lbs' ? 'e.g. 165' : 'e.g. 75.5'}
              value={weightValue}
              onChange={(e) => setWeightValue(e.target.value)}
              style={inputStyle}
              required
              autoFocus
              aria-describedby={validationError ? 'validation-error' : undefined}
            />
          </label>
        )
      case 'PULSE':
        return (
          <label style={labelStyle}>
            <span>{t('modal.heartRate')}</span>
            <input
              type="number"
              min="20"
              max="300"
              step="1"
              placeholder="e.g. 72"
              value={pulseBpm}
              onChange={(e) => setPulseBpm(e.target.value)}
              style={inputStyle}
              required
              autoFocus
              aria-describedby={validationError ? 'validation-error' : undefined}
            />
          </label>
        )
      case 'BG_CHECK':
        return (
          <label style={labelStyle}>
            <span>
              {t('modal.bloodGlucose')} ({glucoseUnit})
            </span>
            <input
              type="number"
              min={glucoseUnit === 'mmol/L' ? '1' : '18'}
              max={glucoseUnit === 'mmol/L' ? '33' : '600'}
              step={glucoseUnit === 'mmol/L' ? '0.1' : '1'}
              placeholder={glucoseUnit === 'mmol/L' ? 'e.g. 5.6' : 'e.g. 100'}
              value={bgCheckValue}
              onChange={(e) => setBgCheckValue(e.target.value)}
              style={inputStyle}
              required
              autoFocus
              aria-describedby={validationError ? 'validation-error' : undefined}
            />
          </label>
        )
      case 'KETONE_CHECK':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.ketones')}</span>
              <input
                type="number"
                min="0"
                max="20"
                step="0.1"
                placeholder="e.g. 1.5"
                value={ketones}
                onChange={(e) => setKetones(e.target.value)}
                style={inputStyle}
                required
                autoFocus
                aria-describedby={validationError ? 'validation-error' : undefined}
              />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.ketoneMethod')}</span>
              <select
                value={ketoneMethod}
                onChange={(e) => setKetoneMethod(e.target.value as 'blood' | 'urine')}
                style={inputStyle}
              >
                <option value="blood">{t('modal.ketoneBlood')}</option>
                <option value="urine">{t('modal.ketoneUrine')}</option>
              </select>
            </label>
          </>
        )
    }
  }

  return (
    <div
      className="modal-overlay"
      onClick={onClose}
      aria-hidden="true"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        className="modal-box"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="modal-title" className="modal-title">
          {isEditMode ? t('modal.editTitle', { defaultValue: 'Edit Measure' }) : t('modal.title')}
        </h2>
        {validationError && (
          <div role="alert" className="error" id="validation-error" style={{ marginBottom: '1rem' }}>
            {validationError}
          </div>
        )}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <label style={labelStyle}>
            <span>{t('modal.type')}</span>
            <select
              ref={firstInputRef}
              value={type}
              onChange={(e) => setType(e.target.value as MeasureType)}
              style={inputStyle}
              required
              disabled={isEditMode}
            >
              {MEASURE_TYPES.map((val) => (
                <option key={val} value={val}>
                  {t(`modal.types.${val}`)}
                </option>
              ))}
            </select>
          </label>

          <label style={labelStyle}>
            <span>{t('modal.measuredAt')}</span>
            <input
              type="datetime-local"
              lang={locale}
              value={measuredAt}
              onChange={(e) => setMeasuredAt(e.target.value)}
              style={inputStyle}
              required
            />
          </label>

          {renderTypeFields()}

          {error && (
            <div role="alert" className="error">
              {error}
            </div>
          )}

          <div
            style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}
          >
            <button type="button" onClick={onClose} style={{ padding: '8px 16px' }}>
              {t('modal.cancel')}
            </button>
            <button
              type="submit"
              disabled={isSaving}
              className="primary"
              style={{ padding: '8px 16px' }}
            >
              {isSaving ? t('modal.saving') : t('modal.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
