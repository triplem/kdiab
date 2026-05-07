import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { subDays, startOfDay, endOfDay, formatISO } from 'date-fns'

type Preset = '1d' | '7d' | '14d' | '30d' | '90d' | 'custom'

export interface Timeframe {
  from: string
  to: string
}

function presetToTimeframe(preset: Exclude<Preset, 'custom'>): Timeframe {
  const days = preset === '1d' ? 1 : preset === '7d' ? 7 : preset === '14d' ? 14 : preset === '30d' ? 30 : 90
  const to = endOfDay(new Date())
  const from = startOfDay(subDays(to, days - 1))
  return { from: formatISO(from), to: formatISO(to) }
}

interface Props {
  value: Timeframe
  onChange: (tf: Timeframe) => void
}

export function TimeframePicker({ value, onChange }: Props) {
  const { t } = useTranslation()
  const [activePreset, setActivePreset] = useState<Preset>('7d')

  const selectPreset = (preset: Exclude<Preset, 'custom'>) => {
    setActivePreset(preset)
    onChange(presetToTimeframe(preset))
  }

  const handleCustomFrom = (e: React.ChangeEvent<HTMLInputElement>) => {
    setActivePreset('custom')
    onChange({ ...value, from: new Date(e.target.value).toISOString() })
  }

  const handleCustomTo = (e: React.ChangeEvent<HTMLInputElement>) => {
    setActivePreset('custom')
    onChange({ ...value, to: new Date(e.target.value).toISOString() })
  }

  const presets: Array<{ key: Exclude<Preset, 'custom'>; label: string }> = [
    { key: '1d', label: t('timeframe.preset1d') },
    { key: '7d', label: t('timeframe.preset7d') },
    { key: '14d', label: t('timeframe.preset14d') },
    { key: '30d', label: t('timeframe.preset30d') },
    { key: '90d', label: t('timeframe.preset90d') },
  ]

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
      <span style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>{t('timeframe.label')}:</span>
      {presets.map(({ key, label }) => (
        <button
          key={key}
          onClick={() => selectPreset(key)}
          style={{
            padding: '0.4em 0.9em',
            fontSize: '0.9rem',
            background: activePreset === key ? 'var(--accent-primary)' : undefined,
            color: activePreset === key ? '#fff' : undefined,
            borderColor: activePreset === key ? 'var(--accent-primary)' : undefined,
          }}
        >
          {label}
        </button>
      ))}
      <span style={{ color: 'var(--text-secondary)', marginLeft: '0.5rem' }}>{t('timeframe.custom')}:</span>
      <input
        type="datetime-local"
        defaultValue={value.from.slice(0, 16)}
        onChange={handleCustomFrom}
        style={{ fontSize: '0.85rem', padding: '0.3rem 0.5rem' }}
      />
      <span style={{ color: 'var(--text-secondary)' }}>—</span>
      <input
        type="datetime-local"
        defaultValue={value.to.slice(0, 16)}
        onChange={handleCustomTo}
        style={{ fontSize: '0.85rem', padding: '0.3rem 0.5rem' }}
      />
    </div>
  )
}

export function defaultTimeframe(): Timeframe {
  return presetToTimeframe('7d')
}
