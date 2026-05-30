import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { usersApi, type PatchSettingsRequest } from '../../api/usersApi'

const TIMEZONES = [
  'UTC', 'Europe/London', 'Europe/Berlin', 'Europe/Paris', 'Europe/Madrid',
  'America/New_York', 'America/Chicago', 'America/Denver', 'America/Los_Angeles',
  'Asia/Tokyo', 'Asia/Shanghai', 'Asia/Kolkata', 'Australia/Sydney',
]

const LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'de', label: 'Deutsch' },
]

const schema = z.object({
  timezone: z.string().min(1),
  language: z.string().min(1),
  timeFormat: z.union([z.literal(12), z.literal(24)]),
  glucoseUnit: z.union([z.literal('mg/dL'), z.literal('mmol/L')]),
  weightUnit: z.union([z.literal('kg'), z.literal('lbs')]),
  alarmUrgentHigh: z.number().nullable(),
  alarmHigh: z.number().nullable(),
  alarmLow: z.number().nullable(),
  alarmUrgentLow: z.number().nullable(),
  sensorDurationHours: z.number().int().min(1).max(8760),
}).refine(
  (d) => {
    const { alarmUrgentLow, alarmLow, alarmHigh, alarmUrgentHigh } = d
    if (alarmUrgentLow != null && alarmLow != null && alarmUrgentLow >= alarmLow) return false
    if (alarmLow != null && alarmHigh != null && alarmLow >= alarmHigh) return false
    if (alarmHigh != null && alarmUrgentHigh != null && alarmHigh >= alarmUrgentHigh) return false
    return true
  },
  { message: 'settings.alarmOrderError', path: ['alarmUrgentLow'] },
)

type FormData = z.infer<typeof schema>

interface UserSettingsProps {
  /** When true, only locale settings (language + timezone) are shown. */
  adminOnly?: boolean
}

export function UserSettings({ adminOnly = false }: UserSettingsProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: () => usersApi.getMe().then((r) => r.data),
  })

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { register, handleSubmit, reset, control, formState: { errors, isDirty } } = useForm<FormData, any, FormData>({
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resolver: zodResolver(schema) as any,
    ...(data?.settings !== undefined && {
      values: {
        timezone: data.settings.locale.timezone,
        language: data.settings.locale.language,
        timeFormat: data.settings.locale.timeFormat,
        glucoseUnit: data.settings.units.glucoseUnit,
        weightUnit: data.settings.units.weightUnit,
        alarmUrgentHigh: data.settings.alarms?.urgentHigh ?? null,
        alarmHigh: data.settings.alarms?.high ?? null,
        alarmLow: data.settings.alarms?.low ?? null,
        alarmUrgentLow: data.settings.alarms?.urgentLow ?? null,
        sensorDurationHours: data.settings.diabetes.sensorDurationHours ?? 240,
      },
    }),
  })

  const [toast, setToast] = useState<{ kind: 'success' | 'error'; msg: string } | null>(null)

  const mutation = useMutation({
    mutationFn: (body: PatchSettingsRequest) => usersApi.patchMySettings(body).then((r) => r.data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['user', 'me'] })
      setToast({ kind: 'success', msg: t('settings.savedSuccess') })
      setTimeout(() => setToast(null), 4000)
    },
    onError: () => {
      setToast({ kind: 'error', msg: t('settings.savedError') })
      setTimeout(() => setToast(null), 4000)
    },
  })

  const onSubmit = (values: FormData) => {
    mutation.mutate({
      locale: { timezone: values.timezone, language: values.language, timeFormat: values.timeFormat },
      units: { glucoseUnit: values.glucoseUnit, weightUnit: values.weightUnit },
      alarms: {
        urgentHigh: values.alarmUrgentHigh,
        high: values.alarmHigh,
        low: values.alarmLow,
        urgentLow: values.alarmUrgentLow,
      },
      diabetes: { sensorDurationHours: values.sensorDurationHours },
    })
  }

  if (isLoading) return <p>{t('common.loading')}</p>
  if (error) return <p style={{ color: 'var(--danger)' }}>{t('common.unknownError')}</p>

  return (
    <div style={{ maxWidth: 540 }}>
      <h2>{t('settings.title')}</h2>

      {toast && (
        <div className={`banner ${toast.kind}`} role="status" style={{ marginBottom: '1rem' }}>
          {toast.msg}
        </div>
      )}

      <form onSubmit={(e) => { void handleSubmit(onSubmit)(e) }} noValidate>
        <div className="form-group">
          <label htmlFor="timezone">{t('settings.timezone')}</label>
          <select id="timezone" {...register('timezone')}>
            {TIMEZONES.map((tz) => (
              <option key={tz} value={tz}>{tz}</option>
            ))}
          </select>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', margin: '0.25rem 0 0' }}>
            {t('settings.timezoneHint')}
          </p>
        </div>

        <div className="form-group">
          <label htmlFor="language">{t('settings.language')}</label>
          <select id="language" {...register('language')}>
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>{l.label}</option>
            ))}
          </select>
        </div>

        {!adminOnly && (
          <div className="form-group">
            <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend style={{ fontWeight: 600, marginBottom: '0.5rem' }}>{t('settings.timeFormat')}</legend>
              <div style={{ display: 'flex', gap: '1rem' }}>
                <Controller
                  name="timeFormat"
                  control={control}
                  render={({ field }) => (
                    <>
                      {([12, 24] as const).map((v) => (
                        <label key={v} style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', cursor: 'pointer' }}>
                          <input
                            type="radio"
                            value={v}
                            checked={field.value === v}
                            onChange={() => field.onChange(v)}
                            onBlur={field.onBlur}
                            name={field.name}
                          />
                          {v}h
                        </label>
                      ))}
                    </>
                  )}
                />
              </div>
            </fieldset>
          </div>
        )}

        {!adminOnly && (
          <div className="form-group">
            <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend style={{ fontWeight: 600, marginBottom: '0.5rem' }}>{t('settings.glucoseUnit')}</legend>
              <div style={{ display: 'flex', gap: '1rem' }}>
                {(['mg/dL', 'mmol/L'] as const).map((v) => (
                  <label key={v} style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', cursor: 'pointer' }}>
                    <input type="radio" value={v} {...register('glucoseUnit')} />
                    {v}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        )}

        {!adminOnly && (
          <div className="form-group">
            <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend style={{ fontWeight: 600, marginBottom: '0.5rem' }}>{t('settings.weightUnit')}</legend>
              <div style={{ display: 'flex', gap: '1rem' }}>
                {(['kg', 'lbs'] as const).map((v) => (
                  <label key={v} style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', cursor: 'pointer' }}>
                    <input type="radio" value={v} {...register('weightUnit')} />
                    {v}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        )}

        {!adminOnly && (
          <div className="form-group">
            <label htmlFor="sensorDurationHours">{t('settings.sensorDurationHours')}</label>
            <input
              id="sensorDurationHours"
              type="number"
              min={1}
              max={8760}
              style={{ width: 100 }}
              {...register('sensorDurationHours', { valueAsNumber: true })}
            />
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginLeft: '0.4rem' }}>
              {t('settings.sensorDurationHint')}
            </span>
            {errors.sensorDurationHours && (
              <p style={{ color: 'var(--danger)', fontSize: '0.85rem', margin: '0.25rem 0 0' }}>
                {t('settings.sensorDurationError')}
              </p>
            )}
          </div>
        )}

        {!adminOnly && (
          <fieldset style={{ border: '1px solid var(--border)', borderRadius: 4, padding: '0.75rem', marginBottom: '1rem' }}>
            <legend style={{ padding: '0 0.3rem', fontSize: '0.9rem' }}>{t('settings.alarmThresholds')}</legend>

            {errors.alarmUrgentLow?.message && (
              <p style={{ color: 'var(--danger)', fontSize: '0.85rem', margin: '0 0 0.5rem' }}>
                {t('settings.alarmOrderError')}
              </p>
            )}

            {(['alarmUrgentHigh', 'alarmHigh', 'alarmLow', 'alarmUrgentLow'] as const).map((field) => (
              <div className="form-group" key={field} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <label htmlFor={field} style={{ minWidth: 140 }}>{t(`settings.${field}`)}</label>
                <input
                  id={field}
                  type="number"
                  style={{ width: 80 }}
                  {...register(field, { setValueAs: (v: string) => (v === '' ? null : Number(v)) })}
                />
                <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>mg/dL</span>
              </div>
            ))}
          </fieldset>
        )}

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button type="submit" className="primary" disabled={!isDirty || mutation.isPending}>
            {mutation.isPending ? t('common.saving') : t('common.save')}
          </button>
          <button type="button" className="btn outline" onClick={() => reset()}>
            {t('common.reset')}
          </button>
        </div>
      </form>
    </div>
  )
}

