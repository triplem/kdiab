import React, { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'

export interface AddFoodModalProps {
  isOpen: boolean
  initialFood?: { id: string; name: string; portionGrams: number; carbsPer100g: number } | null
  onSave: (data: { name: string; portionGrams: number; carbsPer100g: number }) => void
  onCancel: () => void
  isSaving?: boolean
}

const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
  fontSize: '0.9rem',
  color: 'var(--text-primary)',
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px',
  border: '1px solid var(--border-color)',
  borderRadius: '4px',
  fontSize: '0.9rem',
  width: '100%',
  boxSizing: 'border-box',
  background: 'var(--bg-surface, #fff)',
  color: 'var(--text-primary, #1e293b)',
}

const errorStyle: React.CSSProperties = {
  color: 'var(--color-danger, red)',
  fontSize: '0.8rem',
  marginTop: '2px',
}

export const AddFoodModal: React.FC<AddFoodModalProps> = ({
  isOpen,
  initialFood,
  onSave,
  onCancel,
  isSaving = false,
}) => {
  const { t } = useTranslation()
  const isEditMode = initialFood != null
  const titleId = 'add-food-modal-title'

  const [name, setName] = useState('')
  const [portionGrams, setPortionGrams] = useState('')
  const [carbsPer100g, setCarbsPer100g] = useState('')
  const [errors, setErrors] = useState<{ name?: string; portionGrams?: string; carbsPer100g?: string }>({})

  const firstInputRef = useRef<HTMLInputElement>(null)
  const saveButtonRef = useRef<HTMLButtonElement>(null)
  const cancelButtonRef = useRef<HTMLButtonElement>(null)

  // Sync fields whenever the modal opens or the initial food changes
  useEffect(() => {
    if (!isOpen) return
    if (initialFood) {
      setName(initialFood.name)
      setPortionGrams(String(initialFood.portionGrams))
      setCarbsPer100g(String(initialFood.carbsPer100g))
    } else {
      setName('')
      setPortionGrams('')
      setCarbsPer100g('')
    }
    setErrors({})
  }, [isOpen, initialFood])

  // Focus first input and set up keyboard handlers
  useEffect(() => {
    if (!isOpen) return
    firstInputRef.current?.focus()

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
        return
      }
      // Focus trap between cancel and save buttons
      if (e.key === 'Tab') {
        const focusable = document.querySelectorAll<HTMLElement>(
          `[data-focus-scope="${titleId}"] input, [data-focus-scope="${titleId}"] button`,
        )
        const focusableArr = Array.from(focusable).filter((el) => !el.hasAttribute('disabled'))
        if (focusableArr.length === 0) return
        const first = focusableArr[0]
        const last = focusableArr[focusableArr.length - 1]
        if (e.shiftKey) {
          if (document.activeElement === first) {
            e.preventDefault()
            last.focus()
          }
        } else {
          if (document.activeElement === last) {
            e.preventDefault()
            first.focus()
          }
        }
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onCancel, titleId])

  if (!isOpen) return null

  const validate = (): boolean => {
    const newErrors: typeof errors = {}
    if (!name.trim()) {
      newErrors.name = t('foodDatabase.validationError')
    }
    const portion = parseFloat(portionGrams)
    if (!portionGrams || isNaN(portion) || portion <= 0) {
      newErrors.portionGrams = t('foodDatabase.validationError')
    }
    const carbs = parseFloat(carbsPer100g)
    if (!carbsPer100g || isNaN(carbs) || carbs <= 0) {
      newErrors.carbsPer100g = t('foodDatabase.validationError')
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    onSave({
      name: name.trim(),
      portionGrams: parseFloat(portionGrams),
      carbsPer100g: parseFloat(carbsPer100g),
    })
  }

  return (
    <div className="modal-overlay" onClick={onCancel} role="presentation">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="modal-box"
        data-focus-scope={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id={titleId} className="modal-title">
          {isEditMode
            ? t('foodDatabase.editTitle', { defaultValue: 'Edit Food' })
            : t('foodDatabase.addTitle', { defaultValue: 'Add Food' })}
        </h2>

        <form
          onSubmit={handleSubmit}
          style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}
          noValidate
        >
          <label style={labelStyle}>
            <span>{t('foodDatabase.name')}</span>
            <input
              ref={firstInputRef}
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('foodDatabase.namePlaceholder')}
              style={{
                ...inputStyle,
                ...(errors.name ? { borderColor: 'var(--color-danger, red)' } : {}),
              }}
              aria-invalid={!!errors.name}
              aria-describedby={errors.name ? 'error-name' : undefined}
            />
            {errors.name && (
              <span id="error-name" role="alert" style={errorStyle}>
                {errors.name}
              </span>
            )}
          </label>

          <label style={labelStyle}>
            <span>{t('foodDatabase.portionGrams')}</span>
            <input
              type="number"
              min="0.1"
              step="0.1"
              value={portionGrams}
              onChange={(e) => setPortionGrams(e.target.value)}
              placeholder="e.g. 100"
              style={{
                ...inputStyle,
                ...(errors.portionGrams ? { borderColor: 'var(--color-danger, red)' } : {}),
              }}
              aria-invalid={!!errors.portionGrams}
              aria-describedby={errors.portionGrams ? 'error-portion' : undefined}
            />
            {errors.portionGrams && (
              <span id="error-portion" role="alert" style={errorStyle}>
                {errors.portionGrams}
              </span>
            )}
          </label>

          <label style={labelStyle}>
            <span>{t('foodDatabase.carbsPer100g')}</span>
            <input
              type="number"
              min="0.1"
              max="100"
              step="0.1"
              value={carbsPer100g}
              onChange={(e) => setCarbsPer100g(e.target.value)}
              placeholder="e.g. 28"
              style={{
                ...inputStyle,
                ...(errors.carbsPer100g ? { borderColor: 'var(--color-danger, red)' } : {}),
              }}
              aria-invalid={!!errors.carbsPer100g}
              aria-describedby={errors.carbsPer100g ? 'error-carbs' : undefined}
            />
            {errors.carbsPer100g && (
              <span id="error-carbs" role="alert" style={errorStyle}>
                {errors.carbsPer100g}
              </span>
            )}
          </label>

          <div className="modal-footer">
            <button
              ref={cancelButtonRef}
              type="button"
              onClick={onCancel}
              className="btn outline"
            >
              {t('foodDatabase.cancel')}
            </button>
            <button
              ref={saveButtonRef}
              type="submit"
              disabled={isSaving}
              className="btn primary"
            >
              {isSaving ? t('common.saving') : t('foodDatabase.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
