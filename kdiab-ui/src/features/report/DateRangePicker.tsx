import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { buildRange, MAX_DATE_RANGE_DAYS } from './dateRangeUtils'
import type { DateRange } from './dateRangeUtils'

export type { DateRange }

const PRESETS = [
  { days: 7, labelKey: 'report.preset7d' },
  { days: 14, labelKey: 'report.preset14d' },
  { days: 30, labelKey: 'report.preset30d' },
  { days: 90, labelKey: 'report.preset90d' },
] as const

const MIN_CLINICAL_DAYS = 14
const MAX_WARN_DAYS = 90

function toLocalDateString(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function diffDays(from: Date, to: Date): number {
  return Math.ceil((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24))
}

function presetRange(days: number): DateRange {
  const to = new Date()
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000)
  return buildRange(from, to)
}

interface Props {
  value: DateRange
  onChange: (range: DateRange) => void
}

export function DateRangePicker({ value, onChange }: Props) {
  const { t } = useTranslation()
  const [customFrom, setCustomFrom] = useState<string>(value.from.slice(0, 10))
  const [customTo, setCustomTo] = useState<string>(value.to.slice(0, 10))
  const [activePreset, setActivePreset] = useState<number | null>(14)
  const [customError, setCustomError] = useState<string | null>(null)

  const handlePreset = useCallback(
    (days: number) => {
      setActivePreset(days)
      const range = presetRange(days)
      setCustomFrom(range.from.slice(0, 10))
      setCustomTo(range.to.slice(0, 10))
      setCustomError(null)
      onChange(range)
    },
    [onChange],
  )

  const handleCustomApply = useCallback(() => {
    const fromDate = new Date(customFrom)
    const toDate = new Date(customTo)
    if (isNaN(fromDate.getTime()) || isNaN(toDate.getTime())) {
      setCustomError(t('report.customDateInvalid'))
      return
    }
    if (fromDate >= toDate) {
      setCustomError(t('report.customDateFromAfterTo'))
      return
    }
    const days = diffDays(fromDate, toDate)
    if (days > MAX_DATE_RANGE_DAYS) {
      setCustomError(t('report.customDateTooLong', { max: MAX_DATE_RANGE_DAYS }))
      return
    }
    setCustomError(null)
    setActivePreset(null)
    onChange(buildRange(fromDate, toDate))
  }, [customFrom, customTo, onChange, t])

  const { days } = value
  const today = toLocalDateString(new Date())

  return (
    <div className="date-range-picker">
      {/* Preset buttons */}
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
        {PRESETS.map(({ days: d, labelKey }) => (
          <button
            key={d}
            type="button"
            className={activePreset === d ? 'primary' : 'btn outline'}
            onClick={() => handlePreset(d)}
            style={{ padding: '0.4em 0.9em', fontSize: '0.9rem' }}
          >
            {t(labelKey)}
          </button>
        ))}
      </div>

      {/* Custom date range */}
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
          <label htmlFor="report-from" style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            {t('report.from')}
          </label>
          <input
            id="report-from"
            type="date"
            value={customFrom}
            max={customTo || today}
            onChange={(e) => { setCustomFrom(e.target.value); setActivePreset(null) }}
            style={{ padding: '0.35rem 0.5rem', fontSize: '0.9rem' }}
          />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
          <label htmlFor="report-to" style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            {t('report.to')}
          </label>
          <input
            id="report-to"
            type="date"
            value={customTo}
            min={customFrom}
            max={today}
            onChange={(e) => { setCustomTo(e.target.value); setActivePreset(null) }}
            style={{ padding: '0.35rem 0.5rem', fontSize: '0.9rem' }}
          />
        </div>
        <button
          type="button"
          className="btn outline"
          onClick={handleCustomApply}
          style={{ padding: '0.4em 0.9em', fontSize: '0.9rem' }}
        >
          {t('report.apply')}
        </button>
      </div>

      {/* Validation error for custom range */}
      {customError && (
        <p role="alert" style={{ color: 'var(--danger, #c0392b)', fontSize: '0.85rem', margin: '0.4rem 0 0' }}>
          {customError}
        </p>
      )}

      {/* Advisory banners */}
      {days < MIN_CLINICAL_DAYS && !customError && (
        <div
          role="status"
          style={{
            marginTop: '0.5rem',
            padding: '0.4rem 0.75rem',
            background: 'var(--color-info-bg, #e8f4f8)',
            border: '1px solid var(--color-info, #2980b9)',
            borderRadius: 4,
            fontSize: '0.85rem',
            color: 'var(--color-info-text, #154360)',
          }}
        >
          {t('report.advisoryShortRange')}
        </div>
      )}
      {days > MAX_WARN_DAYS && !customError && (
        <div
          role="status"
          style={{
            marginTop: '0.5rem',
            padding: '0.4rem 0.75rem',
            background: 'var(--color-warn-bg, #fef9e7)',
            border: '1px solid var(--color-warn, #d4ac0d)',
            borderRadius: 4,
            fontSize: '0.85rem',
            color: 'var(--color-warn-text, #7d6608)',
          }}
        >
          {t('report.warningLargeRange')}
        </div>
      )}
    </div>
  )
}
