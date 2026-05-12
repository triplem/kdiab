import React, { useRef } from 'react'

interface Props {
  value: string // local datetime string "YYYY-MM-DDTHH:MM" (or "YYYY-MM-DD" when dateOnly)
  onChange: (value: string) => void
  lang: string
  dateOnly?: boolean
  style?: React.CSSProperties
  required?: boolean
}

const btnStyle: React.CSSProperties = {
  display: 'block',
  width: '100%',
  padding: '8px',
  border: '1px solid var(--border-color)',
  borderRadius: '4px',
  fontSize: '1rem',
  background: 'var(--bg-input, var(--bg-secondary))',
  color: 'var(--text-primary)',
  cursor: 'pointer',
  textAlign: 'left',
  fontFamily: 'inherit',
  boxSizing: 'border-box',
}

// Positioned just below the button so Chrome opens the picker in a natural spot
const hiddenInputStyle: React.CSSProperties = {
  position: 'absolute',
  top: '100%',
  left: 0,
  width: '100%',
  height: '1px',
  opacity: 0,
  border: 'none',
  padding: 0,
  pointerEvents: 'none',
  margin: 0,
}

function formatDate(dateStr: string, lang: string): string {
  if (!dateStr || dateStr.length < 10) return dateStr
  const d = new Date(dateStr + 'T12:00:00')
  if (isNaN(d.getTime())) return dateStr
  return new Intl.DateTimeFormat(lang, { year: 'numeric', month: '2-digit', day: '2-digit' }).format(d)
}

export function LocalDateTimeInput({ value, onChange, lang, dateOnly, style, required }: Props) {
  const dateRef = useRef<HTMLInputElement>(null)
  const timeRef = useRef<HTMLInputElement>(null)

  const date = value.slice(0, 10)
  const time = value.slice(11, 16) || '00:00'

  return (
    <div style={{ display: 'flex', gap: '6px', ...style }}>
      {/* Date slot */}
      <div style={{ position: 'relative', flex: '1' }}>
        <button
          type="button"
          style={btnStyle}
          onClick={() => dateRef.current?.showPicker?.()}
        >
          {formatDate(date, lang)}
        </button>
        <input
          ref={dateRef}
          type="date"
          lang={lang}
          value={date}
          onChange={(e) => onChange(dateOnly ? e.target.value : `${e.target.value}T${time}`)}
          style={hiddenInputStyle}
          required={required}
        />
      </div>

      {/* Time slot — hidden when dateOnly */}
      {!dateOnly && (
        <div style={{ position: 'relative', width: '8rem' }}>
          <button
            type="button"
            style={{ ...btnStyle, width: '8rem' }}
            onClick={() => timeRef.current?.showPicker?.()}
          >
            {time}
          </button>
          <input
            ref={timeRef}
            type="time"
            lang={lang}
            value={time}
            onChange={(e) => onChange(`${date}T${e.target.value}`)}
            style={hiddenInputStyle}
            required={required}
          />
        </div>
      )}
    </div>
  )
}
