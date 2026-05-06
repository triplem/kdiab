import { useState, useEffect, useMemo } from 'react'
import { useForm, useFieldArray, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { profilesApi } from '../../api/profilesApi'
import type { Profile } from '../../api/profilesApi'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { TimeInput } from './TimeInput'
import { useTranslation } from 'react-i18next'

// eslint-disable-next-line react-refresh/only-export-components
export const getNextSegment = <T extends { startTime: string; value: number }>(
  fields: T[],
  defaultValue: number,
) => {
  if (fields.length === 0) return { startTime: '00:00', value: defaultValue }
  const sorted = [...fields].sort((a, b) => a.startTime.localeCompare(b.startTime))
  const last = sorted[sorted.length - 1]
  const [h, m] = last.startTime.split(':').map(Number)
  const totalMinutes = h * 60 + m
  const nextTotalMinutes = Math.min(totalMinutes + 60, 23 * 60 + 45)
  const nextH = Math.floor(nextTotalMinutes / 60)
  const nextM = nextTotalMinutes % 60
  return {
    startTime: `${String(nextH).padStart(2, '0')}:${String(nextM).padStart(2, '0')}`,
    value: last.value,
  }
}

const timeSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, 'Invalid time (HH:MM)'),
  value: z.number().min(0, 'Value must be positive'),
})

const validateChronological = (arr: { startTime: string }[]) => {
  if (arr.length <= 1) return true
  for (let i = 0; i < arr.length - 1; i++) {
    if (arr[i].startTime >= arr[i + 1].startTime) return false
  }
  return true
}

const icrSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, 'Invalid time (HH:MM)'),
  value: z.number().min(1.0, 'ICR must be >= 1.0 g/U').max(50.0, 'ICR must be <= 50.0 g/U'),
})

const targetSegmentSchema = z
  .object({
    startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, 'Invalid time (HH:MM)'),
    low: z.number().min(0, 'Low must be >= 0'),
    high: z.number().min(0, 'High must be >= 0'),
  })
  .refine((data) => data.low <= data.high, { message: 'Low must be <= High', path: ['high'] })

interface ProfileFormValues {
  name: string
  insulinType: string
  durationOfAction: number
  timeZone?: string
  proposalReason?: string
  timeZone?: string
  basal: { startTime: string; value: number }[]
  icr: { startTime: string; value: number }[]
  isf: { startTime: string; value: number }[]
  targets: { startTime: string; low: number; high: number }[]
}

interface ProfileEditorProps {
  userId: string
  initialProfile?: Profile
  onProfileSaved?: () => void
  readOnly?: boolean
  isDoctor?: boolean
  glucoseUnit?: string
}

const generateNextName = (currentName: string) => {
  const match = currentName.match(/(.*)-(\d+)$/)
  if (match) {
    const base = match[1]
    const num = parseInt(match[2], 10) + 1
    return `${base}-${num}`
  }
  return `${currentName}-1`
}

const getNextTargetSegment = (fields: { startTime: string; low: number; high: number }[]) => {
  if (fields.length === 0) return { startTime: '00:00', low: 80, high: 120 }
  const sorted = [...fields].sort((a, b) => a.startTime.localeCompare(b.startTime))
  const last = sorted[sorted.length - 1]
  const [h, m] = last.startTime.split(':').map(Number)
  const next = Math.min(h * 60 + m + 60, 23 * 60 + 45)
  return {
    startTime: `${String(Math.floor(next / 60)).padStart(2, '0')}:${String(next % 60).padStart(2, '0')}`,
    low: last.low,
    high: last.high,
  }
}

function HelpTooltip({ text }: { text: string }) {
  return (
    <span className="tooltip-wrapper">
      <span className="tooltip-icon" aria-label="Help">?</span>
      <span className="tooltip-box">{text}</span>
    </span>
  )
}

export function ProfileEditor({
  userId,
  initialProfile,
  onProfileSaved,
  readOnly = false,
  isDoctor = false,
  glucoseUnit,
}: ProfileEditorProps) {
  const { t } = useTranslation()

  const isfSegmentSchema = useMemo(
    () =>
      z.object({
        startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, 'Invalid time (HH:MM)'),
        value:
          (glucoseUnit ?? 'mg/dL') === 'mmol/L'
            ? z.number().min(0.5, 'ISF must be >= 0.5 mmol/L/U').max(11.1, 'ISF must be <= 11.1 mmol/L/U')
            : z.number().min(10.0, 'ISF must be >= 10.0 mg/dL/U').max(200.0, 'ISF must be <= 200.0 mg/dL/U'),
      }),
    [glucoseUnit],
  )

  const profileSchema = useMemo(
    () =>
      z
        .object({
          name: z.string().trim().min(1, 'Name is required'),
          insulinType: z.string().min(1, 'Insulin type is required'),
          durationOfAction: z.number().int().min(1, 'Duration must be positive (minutes)'),
          timeZone: z.string().optional(),
          proposalReason: z.string().optional(),
          timeZone: z.string().optional(),
          basal: z
            .array(timeSegmentSchema)
            .nonempty('At least one basal segment required')
            .refine((arr) => arr[0].startTime === '00:00', 'Basal must start at 00:00')
            .refine(validateChronological, 'Basal segments must be chronological'),
          icr: z
            .array(icrSegmentSchema)
            .refine((arr) => arr.length === 0 || arr[0].startTime === '00:00', 'ICR must start at 00:00')
            .refine(validateChronological, 'ICR segments must be chronological'),
          isf: z
            .array(isfSegmentSchema)
            .refine((arr) => arr.length === 0 || arr[0].startTime === '00:00', 'ISF must start at 00:00')
            .refine(validateChronological, 'ISF segments must be chronological'),
          targets: z
            .array(targetSegmentSchema)
            .refine(
              (arr) => arr.length === 0 || arr[0].startTime === '00:00',
              'Targets must start at 00:00',
            )
            .refine(validateChronological, 'Target segments must be chronological'),
        })
        .refine(
          (data) => {
            if (!data.basal || data.basal.length === 0) return true
            let totalDailyBasal = 0
            for (let i = 0; i < data.basal.length; i++) {
              const current = data.basal[i]
              let nextTimeStr = '24:00'
              if (i + 1 < data.basal.length) nextTimeStr = data.basal[i + 1].startTime
              const [currH, currM] = current.startTime.split(':').map(Number)
              const [nextH, nextM] = nextTimeStr.split(':').map(Number)
              const currMinutes = currH * 60 + currM
              const nextMinutes = nextH === 24 ? 24 * 60 : nextH * 60 + nextM
              const durationHours = (nextMinutes - currMinutes) / 60.0
              totalDailyBasal += current.value * durationHours
            }
            return totalDailyBasal <= 150.0
          },
          {
            message: 'Total Daily Basal exceeds safe clinical limit of 150.0 U/day',
            path: ['basal'],
          },
        ),
    [isfSegmentSchema],
  )

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    getValues,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: initialProfile
      ? {
          name: generateNextName(initialProfile.name),
          insulinType: initialProfile.insulinType || 'Humalog',
          durationOfAction: initialProfile.durationOfAction || 300,
          timeZone: initialProfile.timeZone || '',
          proposalReason: '',
          timeZone: initialProfile.timeZone || '',
          basal: initialProfile.basal?.length ? initialProfile.basal : [{ startTime: '00:00', value: 0.5 }],
          icr: initialProfile.icr || [],
          isf: initialProfile.isf || [],
          targets: initialProfile.targets || [],
        }
      : {
          name: '',
          insulinType: 'Humalog',
          durationOfAction: 300,
          timeZone: '',
          proposalReason: '',
          timeZone: '',
          basal: [{ startTime: '00:00', value: 0.5 }],
          icr: [],
          isf: [],
          targets: [],
        },
  })

  const basalSegments = watch('basal')
  const hasZeroBasalSegment = basalSegments?.some((seg) => seg.value === 0) ?? false

  const { formatDate } = useTimeFormat()

  const { data: insulins = [], isLoading: insulinsLoading, isError: insulinsError } = useQuery({
    queryKey: ['insulins'],
    queryFn: () => profilesApi.getInsulins().then((res) => res.data),
  })

  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) e.preventDefault()
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty])

  const { data: allProfiles = [] } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => profilesApi.listProfiles(userId).then((res) => res.data),
    enabled: !!initialProfile,
  })

  const { fields: basalFields, append: appendBasal, remove: removeBasal } = useFieldArray({ control, name: 'basal' })
  const { fields: icrFields, append: appendIcr, remove: removeIcr } = useFieldArray({ control, name: 'icr' })
  const { fields: isfFields, append: appendIsf, remove: removeIsf } = useFieldArray({ control, name: 'isf' })
  const { fields: targetFields, append: appendTarget, remove: removeTarget } = useFieldArray({ control, name: 'targets' })

  const [activeTab, setActiveTab] = useState<'basal' | 'icr' | 'isf' | 'targets'>('basal')
  const [apiError, setApiError] = useState<string | null>(null)
  const [isAddingNewInsulin, setIsAddingNewInsulin] = useState(false)

  const saveMutation = useMutation({
    mutationFn: async (data: ProfileFormValues) => {
      if (data.insulinType && !insulins.find((i) => i.name === data.insulinType)) {
        await profilesApi.createInsulin({ name: data.insulinType })
      }
      const request = {
        name: data.name,
        insulinType: data.insulinType,
        durationOfAction: data.durationOfAction,
        timeZone: data.timeZone || undefined,
        proposalReason: data.proposalReason || undefined,
        timeZone: data.timeZone || undefined,
        basal: data.basal,
        icr: data.icr,
        isf: data.isf,
        targets: data.targets,
      }
      if (initialProfile?.id) {
        return profilesApi.updateProfile(userId, initialProfile.id, {
          ...initialProfile,
          ...request,
        } as Record<string, unknown>)
      } else {
        return profilesApi.createProfile(userId, request)
      }
    },
    onSuccess: () => {
      onProfileSaved?.()
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: unknown }; message?: string }
      let errorMessage = 'Failed to save profile. Please try again.'
      const data = e.response?.data
      const isSafeString = (s: unknown): s is string =>
        typeof s === 'string' && s.length > 0 && !s.trimStart().startsWith('<')
      if (isSafeString(data)) errorMessage = data
      else if (isSafeString((data as Record<string, unknown>)?.message))
        errorMessage = (data as Record<string, string>).message
      else if (isSafeString(e.message)) errorMessage = e.message
      setApiError(errorMessage)
    },
  })

  const onSubmit = (data: ProfileFormValues) => {
    setApiError(null)
    saveMutation.mutate(data)
  }

  return (
    <div className="profile-editor">
      <h3>
        {initialProfile ? (readOnly ? 'View Profile' : 'Edit Profile') : 'Create Profile'}
        {!readOnly && isDirty && (
          <span className="unsaved-indicator" aria-live="polite">
            {' '}
            — Unsaved changes
          </span>
        )}
      </h3>
      {readOnly && (
        <div
          role="status"
          style={{
            marginBottom: '1rem',
            padding: '0.5rem 1rem',
            background: 'var(--surface-color)',
            border: '1px solid var(--border-color)',
            borderRadius: '6px',
            fontSize: '0.9rem',
          }}
        >
          Read-only view — you cannot edit this patient's profile directly.
        </div>
      )}

      {initialProfile && (
        <div
          style={{
            fontSize: '0.85rem',
            color: 'var(--text-secondary)',
            marginBottom: '1rem',
            padding: '0.75rem',
            background: 'rgba(0,0,0,0.2)',
            border: '1px solid rgba(255,255,255,0.05)',
            borderRadius: '6px',
          }}
        >
          <div style={{ marginBottom: '4px' }}>
            <strong>Activation Date:</strong>{' '}
            {initialProfile.createdAt ? formatDate(initialProfile.createdAt) : 'N/A'}
          </div>
          {initialProfile.status === 'ARCHIVED' && (
            <div>
              <strong>Deactivation Date:</strong>{' '}
              {(() => {
                const nextP = allProfiles.find(
                  (p) =>
                    p.previousProfileId === initialProfile.id &&
                    (p.status === 'ACTIVE' || p.status === 'ARCHIVED'),
                )
                return nextP?.createdAt ? formatDate(nextP.createdAt) : 'N/A'
              })()}
            </div>
          )}
          {initialProfile.status === 'ACTIVE' && (
            <div style={{ color: 'var(--accent-success)', marginTop: '4px' }}>
              Currently Active Configuration
            </div>
          )}
        </div>
      )}

      {apiError && <div className="error">{apiError}</div>}
      <form onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label htmlFor="name">Name</label>
          <input id="name" {...register('name')} aria-describedby="name-error" aria-required="true" />
          {errors.name && (
            <span id="name-error" role="alert" className="error-text">
              {errors.name.message}
            </span>
          )}
        </div>

        <div>
          <label htmlFor="insulinType">Insulin Type</label>
          {!isAddingNewInsulin ? (
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <select
                id="insulinType"
                {...register('insulinType')}
                disabled={insulinsLoading}
                aria-describedby="insulinType-error"
                aria-required="true"
              >
                <option value="">
                  {insulinsLoading
                    ? 'Loading insulins…'
                    : insulinsError
                      ? 'Could not load insulins'
                      : '-- Select Insulin --'}
                </option>
                {insulins.map((insulin) => (
                  <option key={insulin.id} value={insulin.name}>
                    {insulin.name}
                  </option>
                ))}
              </select>
              <button type="button" className="btn small" onClick={() => setIsAddingNewInsulin(true)}>
                + Add New
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                id="insulinType"
                {...register('insulinType')}
                placeholder="Enter new insulin name"
                autoFocus
                style={{ flex: 1 }}
              />
              <button
                type="button"
                className="btn small"
                onClick={() => {
                  setIsAddingNewInsulin(false)
                  setValue('insulinType', insulins[0]?.name || '')
                }}
              >
                Cancel
              </button>
            </div>
          )}
          {errors.insulinType && (
            <span id="insulinType-error" role="alert" className="error-text">
              {errors.insulinType.message}
            </span>
          )}
        </div>

        <div>
          <label htmlFor="durationOfAction">Duration of Action (min)</label>
          <input
            id="durationOfAction"
            type="number"
            {...register('durationOfAction', { valueAsNumber: true })}
          />
          {errors.durationOfAction && (
            <span role="alert" className="error-text">
              {errors.durationOfAction.message}
            </span>
          )}
        </div>

        <div>
          <label htmlFor="timeZone">{t('profile.timeZone')}</label>
          <input
            id="timeZone"
            type="text"
            placeholder={t('profile.timeZonePlaceholder')}
            {...register('timeZone')}
          />
        </div>

        {isDoctor && (
          <div>
            <label htmlFor="proposalReason">{t('profileList.proposalReason')}</label>
            <textarea
              id="proposalReason"
              {...register('proposalReason')}
              placeholder={t('profileList.proposalReasonPlaceholder')}
              rows={3}
              style={{ width: '100%', resize: 'vertical' }}
            />
          </div>
        )}

        <div className="form-group">
          <label htmlFor="timeZone">{t('profile.timeZone')}</label>
          <input
            id="timeZone"
            type="text"
            placeholder={t('profile.timeZonePlaceholder')}
            {...register('timeZone')}
          />
        </div>

        <div className="tabs">
          {(['basal', 'icr', 'isf', 'targets'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              className={`tab-button ${activeTab === tab ? 'active' : ''}`}
              onClick={() => setActiveTab(tab)}
            >
              {tab.toUpperCase()}
            </button>
          ))}
        </div>

        {activeTab === 'basal' && (
          <div className="tab-content">
            <h4>
              <span className="tooltip-wrapper">
                Basal Schedule
                <HelpTooltip text={t('profile.tooltipBasal')} />
              </span>
            </h4>
            {hasZeroBasalSegment && (
              <div className="warning-banner" role="alert">{t('profile.zeroBasalWarning')}</div>
            )}
            {basalFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`basal.${index}.startTime`}
                  render={({ field: f }) => <TimeInput {...f} />}
                />
                <input
                  type="number"
                  step="0.05"
                  {...register(`basal.${index}.value`, { valueAsNumber: true })}
                  placeholder="Rate (U/hr)"
                  aria-label={`Value ${index}`}
                />
                <button type="button" onClick={() => removeBasal(index)}>
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => appendBasal(getNextSegment(getValues('basal') || basalFields, 0.5))}
            >
              Add Segment
            </button>
            {errors.basal && <div className="error-text">{errors.basal.message}</div>}
          </div>
        )}

        {activeTab === 'icr' && (
          <div className="tab-content">
            <h4>
              <span className="tooltip-wrapper">
                Insulin to Carb Ratio (ICR)
                <HelpTooltip text={t('profile.tooltipIcr')} />
              </span>
            </h4>
            {icrFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`icr.${index}.startTime`}
                  render={({ field: f }) => <TimeInput {...f} />}
                />
                <input
                  type="number"
                  step="0.1"
                  {...register(`icr.${index}.value`, { valueAsNumber: true })}
                  placeholder="Ratio (g/U)"
                  aria-label={`ICR Value ${index}`}
                />
                <button type="button" onClick={() => removeIcr(index)}>
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => appendIcr(getNextSegment(getValues('icr') || icrFields, 10.0))}
            >
              Add ICR Segment
            </button>
            {errors.icr && <div className="error-text">{errors.icr.message}</div>}
          </div>
        )}

        {activeTab === 'isf' && (
          <div className="tab-content">
            <h4>
              <span className="tooltip-wrapper">
                Insulin Sensitivity Factor (ISF)
                <HelpTooltip text={t('profile.tooltipIsf')} />
              </span>
            </h4>
            {isfFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`isf.${index}.startTime`}
                  render={({ field: f }) => <TimeInput {...f} />}
                />
                <input
                  type="number"
                  step="1"
                  {...register(`isf.${index}.value`, { valueAsNumber: true })}
                  placeholder={`Factor (${glucoseUnit ?? 'mg/dL'})`}
                  aria-label={`ISF Value ${index}`}
                />
                <button type="button" onClick={() => removeIsf(index)}>
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => appendIsf(getNextSegment(getValues('isf') || isfFields, 50.0))}
            >
              Add ISF Segment
            </button>
            {errors.isf && <div className="error-text">{errors.isf.message}</div>}
          </div>
        )}

        {activeTab === 'targets' && (
          <div className="tab-content">
            <h4>
              <span className="tooltip-wrapper">
                Blood Glucose Targets
                <HelpTooltip text={t('profile.tooltipTarget')} />
              </span>
            </h4>
            {targetFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`targets.${index}.startTime`}
                  render={({ field: f }) => <TimeInput {...f} />}
                />
                <input
                  type="number"
                  step="1"
                  {...register(`targets.${index}.low`, { valueAsNumber: true })}
                  placeholder={`Low (${glucoseUnit ?? 'mg/dL'})`}
                  aria-label={`Target Low ${index}`}
                />
                <input
                  type="number"
                  step="1"
                  {...register(`targets.${index}.high`, { valueAsNumber: true })}
                  placeholder={`High (${glucoseUnit ?? 'mg/dL'})`}
                  aria-label={`Target High ${index}`}
                />
                <button type="button" onClick={() => removeTarget(index)}>
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() =>
                appendTarget(
                  getNextTargetSegment(
                    getValues('targets') || (targetFields as { startTime: string; low: number; high: number }[]),
                  ),
                )
              }
            >
              Add Target Segment
            </button>
            {errors.targets && <div className="error-text">{errors.targets.message}</div>}
          </div>
        )}

        {!readOnly && (
          <div style={{ marginTop: '20px' }}>
            <button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Saving...' : initialProfile ? 'Update Profile' : 'Create Profile'}
            </button>
          </div>
        )}
      </form>
    </div>
  )
}
