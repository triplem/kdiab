import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { inputStyle, labelStyle } from './formStyles'

export interface NoteFormData {
  text: string
}

interface NoteFormProps {
  initialData?: Partial<NoteFormData>
  validationError: string | null
  onDataChange: (data: NoteFormData | null) => void
}

export function NoteForm({ initialData, validationError, onDataChange }: NoteFormProps) {
  const { t } = useTranslation()
  const [noteText, setNoteText] = useState(initialData?.text ?? '')

  const handleChange = (value: string) => {
    setNoteText(value)
    onDataChange(value.trim() ? { text: value.trim() } : null)
  }

  return (
    <label style={labelStyle}>
      <span>{t('treatmentModal.noteText')}</span>
      <textarea
        placeholder="e.g. felt low before dinner"
        value={noteText}
        onChange={(e) => handleChange(e.target.value)}
        style={{ ...inputStyle, minHeight: '80px', resize: 'vertical' }}
        required
        autoFocus
        aria-describedby={validationError ? 'validation-error' : undefined}
      />
    </label>
  )
}
