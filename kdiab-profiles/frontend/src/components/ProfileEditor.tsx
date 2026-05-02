import React, { useState, useEffect } from 'react';
import { useForm, useFieldArray, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api } from '../api/client';
import type { CreateProfileRequest, Profile } from '../api/generated';
import { useQuery, useMutation } from '@tanstack/react-query';
import { useTimeFormat } from '../context/TimeFormatContext';
import { TimeInput } from './TimeInput';

// eslint-disable-next-line react-refresh/only-export-components
export const getNextSegment = <T extends { startTime: string, value: number }>(
  fields: T[], 
  defaultValue: number
) => {
  if (fields.length === 0) return { startTime: "00:00", value: defaultValue };
  
  // Sort fields to find the actual last one chronologically
  const sorted = [...fields].sort((a, b) => a.startTime.localeCompare(b.startTime));
  const last = sorted[sorted.length - 1];
  
  const [h, m] = last.startTime.split(':').map(Number);
  const totalMinutes = h * 60 + m;
  const nextTotalMinutes = Math.min(totalMinutes + 60, 23 * 60 + 45); // Max 23:45
  
  const nextH = Math.floor(nextTotalMinutes / 60);
  const nextM = nextTotalMinutes % 60;
  
  const nextStartTime = `${String(nextH).padStart(2, '0')}:${String(nextM).padStart(2, '0')}`;
  
  // If we already reached the end, just return the last one + a tiny increment if possible, 
  // but usually 60m is fine for UX.
  return { startTime: nextStartTime, value: last.value };
};


// Define Validation Schema
const timeSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  value: z.number().min(0, "Value must be positive"),
});

const validateChronological = (arr: { startTime: string }[]) => {
  if (arr.length <= 1) return true;
  for (let i = 0; i < arr.length - 1; i++) {
    if (arr[i].startTime >= arr[i+1].startTime) return false;
  }
  return true;
};

const icrSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  value: z.number().min(1.0, "ICR must be >= 1.0 g/U").max(50.0, "ICR must be <= 50.0 g/U"),
});

const isfSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  value: z.number().min(10.0, "ISF must be >= 10.0 mg/dL/U").max(200.0, "ISF must be <= 200.0 mg/dL/U"),
});

const targetSegmentSchema = z.object({
  startTime: z.string().regex(/^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/, "Invalid time (HH:MM)"),
  low: z.number().min(0, "Low must be >= 0"),
  high: z.number().min(0, "High must be >= 0"),
}).refine(data => data.low <= data.high, { message: "Low must be <= High", path: ["high"] });

const profileSchema = z.object({
  name: z.string().trim().min(1, "Name is required"),
  insulinType: z.string().min(1, "Insulin type is required"),
  durationOfAction: z.number().int().min(1, "Duration must be positive (minutes)"),
  basal: z.array(timeSegmentSchema)
    .nonempty("At least one basal segment required")
    .refine(arr => arr[0].startTime === "00:00", "Basal must start at 00:00")
    .refine(validateChronological, "Basal segments must be chronological"),
  icr: z.array(icrSegmentSchema)
    .refine(arr => arr.length === 0 || arr[0].startTime === "00:00", "ICR must start at 00:00")
    .refine(validateChronological, "ICR segments must be chronological"),
  isf: z.array(isfSegmentSchema)
    .refine(arr => arr.length === 0 || arr[0].startTime === "00:00", "ISF must start at 00:00")
    .refine(validateChronological, "ISF segments must be chronological"),
  targets: z.array(targetSegmentSchema)
    .refine(arr => arr.length === 0 || arr[0].startTime === "00:00", "Targets must start at 00:00")
    .refine(validateChronological, "Target segments must be chronological")
}).refine((data) => {
  if (!data.basal || data.basal.length === 0) return true;
  let totalDailyBasal = 0;
  for (let i = 0; i < data.basal.length; i++) {
    const current = data.basal[i];
    let nextTimeStr = "24:00";
    if (i + 1 < data.basal.length) {
       nextTimeStr = data.basal[i + 1].startTime;
    }
    
    const [currH, currM] = current.startTime.split(':').map(Number);
    const [nextH, nextM] = nextTimeStr.split(':').map(Number);
    
    const currMinutes = currH * 60 + currM;
    const nextMinutes = nextH === 24 ? 24 * 60 : nextH * 60 + nextM;
    
    const durationHours = (nextMinutes - currMinutes) / 60.0;
    totalDailyBasal += current.value * durationHours;
  }
  return totalDailyBasal <= 150.0;
}, {
  message: "Total Daily Basal exceeds safe clinical limit of 150.0 U/day",
  path: ["basal"]
});

type ProfileFormValues = z.infer<typeof profileSchema>;

interface ProfileEditorProps {
  userId: string;
  initialProfile?: Profile;
  onProfileSaved?: () => void;
  readOnly?: boolean;
}

const generateNextName = (currentName: string) => {
  const match = currentName.match(/(.*)-(\d+)$/);
  if (match) {
    const base = match[1];
    const num = parseInt(match[2], 10) + 1;
    return `${base}-${num}`;
  }
  return `${currentName}-1`;
};

const getNextTargetSegment = (fields: { startTime: string; low: number; high: number }[]) => {
  if (fields.length === 0) return { startTime: '00:00', low: 80, high: 120 };
  const sorted = [...fields].sort((a, b) => a.startTime.localeCompare(b.startTime));
  const last = sorted[sorted.length - 1];
  const [h, m] = last.startTime.split(':').map(Number);
  const next = Math.min(h * 60 + m + 60, 23 * 60 + 45);
  const nextTime = `${String(Math.floor(next / 60)).padStart(2, '0')}:${String(next % 60).padStart(2, '0')}`;
  return { startTime: nextTime, low: last.low, high: last.high };
};

export function ProfileEditor({ userId, initialProfile, onProfileSaved, readOnly = false }: ProfileEditorProps) {
  const { register, control, handleSubmit, setValue, getValues, formState: { errors, isDirty } } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: initialProfile ? {
      name: generateNextName(initialProfile.name),
      insulinType: initialProfile.insulinType || 'Humalog',
      durationOfAction: initialProfile.durationOfAction || 300,
      basal: initialProfile.basal?.length ? initialProfile.basal : [{ startTime: '00:00', value: 0.5 }],
      icr: initialProfile.icr || [],
      isf: initialProfile.isf || [],
      targets: initialProfile.targets || [],
    } : {
      name: '',
      insulinType: 'Humalog',
      durationOfAction: 300,
      basal: [{ startTime: '00:00', value: 0.5 }],
      icr: [],
      isf: [],
      targets: [],
    },
  });

  const { formatDate } = useTimeFormat();

  const { data: insulins = [], isLoading: insulinsLoading, isError: insulinsError } = useQuery({
    queryKey: ['insulins'],
    queryFn: () => api.getInsulins().then(res => res.data)
  });

  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isDirty) e.preventDefault();
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isDirty]);

  const { data: allProfiles = [] } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => api.listProfiles(userId).then(res => res.data),
    enabled: !!initialProfile
  });

  
  const { fields: basalFields, append: appendBasal, remove: removeBasal } = useFieldArray({
    control,
    name: "basal"
  });

  const { fields: icrFields, append: appendIcr, remove: removeIcr } = useFieldArray({
    control,
    name: "icr"
  });

  const { fields: isfFields, append: appendIsf, remove: removeIsf } = useFieldArray({
    control,
    name: "isf"
  });

  const { fields: targetFields, append: appendTarget, remove: removeTarget } = useFieldArray({
    control,
    name: "targets"
  });

  const [activeTab, setActiveTab] = useState<'basal' | 'icr' | 'isf' | 'targets'>('basal');
  const [apiError, setApiError] = useState<string | null>(null);
  const [isAddingNewInsulin, setIsAddingNewInsulin] = useState(false);

  const saveMutation = useMutation({
    mutationFn: async (data: ProfileFormValues) => {
      if (data.insulinType && !insulins.find(i => i.name === data.insulinType)) {
        await api.createInsulin({ name: data.insulinType });
      }

      const request: CreateProfileRequest = {
        name: data.name,
        insulinType: data.insulinType,
        durationOfAction: data.durationOfAction,
        basal: data.basal,
        icr: data.icr,
        isf: data.isf,
        targets: data.targets
      };

      if (initialProfile?.id) {
        return api.updateProfile(userId, initialProfile.id, { ...initialProfile, ...request } as Profile);
      } else {
        return api.createProfile(userId, request);
      }
    },
    onSuccess: () => {
      onProfileSaved?.();
    },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    onError: (err: any) => {
      console.error(err);
      let errorMessage = "Failed to save profile. Please try again.";
      const data = err.response?.data;
      // Reject HTML responses (e.g. Nginx 502 pages) to prevent XSS
      const isSafeString = (s: unknown): s is string =>
        typeof s === 'string' && s.length > 0 && !s.trimStart().startsWith('<');
      if (isSafeString(data)) {
        errorMessage = data;
      } else if (isSafeString(data?.message)) {
        errorMessage = data.message;
      } else if (isSafeString(data?.detail)) {
        errorMessage = data.detail;
      } else if (isSafeString(err.message)) {
        errorMessage = err.message;
      }
      setApiError(errorMessage);
    },
  });

  const onSubmit = (data: ProfileFormValues) => {
    setApiError(null);
    saveMutation.mutate(data);
  };

  return (
    <div className="profile-editor">
      <h3>
        {initialProfile ? (readOnly ? 'View Profile' : 'Edit Profile') : 'Create Profile'}
        {!readOnly && isDirty && <span className="unsaved-indicator" aria-live="polite"> — Unsaved changes</span>}
      </h3>
      {readOnly && (
        <div role="status" style={{ marginBottom: '1rem', padding: '0.5rem 1rem', background: '#f0f4ff', border: '1px solid #aac', borderRadius: '6px', fontSize: '0.9rem' }}>
          Read-only view — you cannot edit this patient's profile directly. Use "Propose Profile for Patient" to suggest a new configuration.
        </div>
      )}
      
      {initialProfile && (
        <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '1rem', padding: '0.75rem', background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '6px' }}>
          <div style={{ marginBottom: '4px' }}>
            <strong>Activation Date:</strong> {initialProfile.createdAt ? formatDate(initialProfile.createdAt) : 'N/A'}
          </div>
          {initialProfile.status === 'ARCHIVED' && (
            <div>
              <strong>Deactivation Date:</strong> {(() => {
                const nextP = allProfiles.find(p => p.previousProfileId === initialProfile.id && (p.status === 'ACTIVE' || p.status === 'ARCHIVED'));
                return nextP?.createdAt ? formatDate(nextP.createdAt) : 'N/A';
              })()}
            </div>
          )}
          {initialProfile.status === 'ACTIVE' && <div style={{ color: 'var(--accent-success)', marginTop: '4px' }}>Currently Active Configuration</div>}
        </div>
      )}

      {apiError && <div className="error">{apiError}</div>}
      <form onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label htmlFor="name">Name</label>
          <input id="name" {...register("name")} aria-describedby="name-error" aria-required="true" />
          {errors.name && <span id="name-error" role="alert" className="error-text">{errors.name.message}</span>}
        </div>

        <div>
          <label htmlFor="insulinType">Insulin Type</label>
          {!isAddingNewInsulin ? (
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <select
                id="insulinType"
                {...register("insulinType")}
                disabled={insulinsLoading}
                aria-describedby="insulinType-error"
                aria-required="true"
              >
                <option value="">{insulinsLoading ? 'Loading insulins…' : insulinsError ? 'Could not load insulins' : '-- Select Insulin --'}</option>
                {insulins.map((insulin) => (
                  <option key={insulin.id} value={insulin.name}>{insulin.name}</option>
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
                {...register("insulinType")}
                placeholder="Enter new insulin name"
                autoFocus
                style={{ flex: 1 }}
                aria-describedby="insulinType-error"
                aria-required="true"
              />
              <button
                type="button"
                className="btn small"
                onClick={() => {
                  setIsAddingNewInsulin(false);
                  setValue('insulinType', insulins[0]?.name || '');
                }}
              >
                Cancel
              </button>
            </div>
          )}
          {insulinsError && <div className="error-text">Could not load insulin list. Please refresh the page.</div>}
          {errors.insulinType && <span id="insulinType-error" role="alert" className="error-text">{errors.insulinType.message}</span>}
        </div>

        <div>
          <label htmlFor="durationOfAction">Duration of Action (min)</label>
          <input id="durationOfAction" type="number" {...register("durationOfAction", { valueAsNumber: true })} aria-describedby="doa-error" aria-required="true" />
          {errors.durationOfAction && <span id="doa-error" role="alert" className="error-text">{errors.durationOfAction.message}</span>}
        </div>

        <div className="tabs">
          <button 
            type="button" 
            className={`tab-button ${activeTab === 'basal' ? 'active' : ''}`} 
            onClick={() => setActiveTab('basal')}
          >
            Basal
          </button>
          <button 
            type="button" 
            className={`tab-button ${activeTab === 'icr' ? 'active' : ''}`} 
            onClick={() => setActiveTab('icr')}
          >
            ICR
          </button>
          <button
            type="button"
            className={`tab-button ${activeTab === 'isf' ? 'active' : ''}`}
            onClick={() => setActiveTab('isf')}
          >
            ISF
          </button>
          <button
            type="button"
            className={`tab-button ${activeTab === 'targets' ? 'active' : ''}`}
            onClick={() => setActiveTab('targets')}
          >
            Targets
          </button>
        </div>

        {activeTab === 'basal' && (
          <div className="tab-content">
            <h4>Basal Schedule</h4>
            {basalFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`basal.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput 
                        {...field}
                    />
                  )}
                />
                <input 
                    type="number" 
                    step="0.05"
                    {...register(`basal.${index}.value` as const, { valueAsNumber: true })} 
                    placeholder="Rate (U/hr)"
                    aria-label={`Value ${index}`}
                />
                <button type="button" onClick={() => removeBasal(index)}>Remove</button>
                {errors.basal?.[index]?.startTime && <span role="alert" className="error-text">{errors.basal[index]?.startTime?.message || "Invalid start time"}</span>}
                {errors.basal?.[index]?.value && <span role="alert" className="error-text">{errors.basal[index]?.value?.message || "Invalid value"}</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendBasal(getNextSegment(getValues('basal') || basalFields, 0.5))}>Add Segment</button>
            {errors.basal && <div className="error-text">{errors.basal.message}</div>}
          </div>
        )}

        {activeTab === 'icr' && (
          <div className="tab-content">
            <h4>Insulin to Carb Ratio (ICR)</h4>
            {icrFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`icr.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput 
                        {...field}
                    />
                  )}
                />
                <input 
                    type="number" 
                    step="0.1"
                    {...register(`icr.${index}.value` as const, { valueAsNumber: true })} 
                    placeholder="Ratio (g/U)"
                    aria-label={`ICR Value ${index}`}
                />
                <button type="button" onClick={() => removeIcr(index)}>Remove</button>
                {errors.icr?.[index]?.startTime && <span role="alert" className="error-text">{errors.icr[index]?.startTime?.message || "Invalid start time"}</span>}
                {errors.icr?.[index]?.value && <span role="alert" className="error-text">{errors.icr[index]?.value?.message || "Value Error"}</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendIcr(getNextSegment(getValues('icr') || icrFields, 10.0))}>Add ICR Segment</button>
            {errors.icr && <div className="error-text">{errors.icr.message}</div>}
          </div>
        )}

        {activeTab === 'isf' && (
          <div className="tab-content">
            <h4>Insulin Sensitivity Factor (ISF)</h4>
            {isfFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`isf.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput 
                        {...field}
                    />
                  )}
                />
                <input 
                    type="number" 
                    step="1"
                    {...register(`isf.${index}.value` as const, { valueAsNumber: true })} 
                    placeholder="Factor (mg/dL)"
                    aria-label={`ISF Value ${index}`}
                />
                <button type="button" onClick={() => removeIsf(index)}>Remove</button>
                {errors.isf?.[index]?.startTime && <span role="alert" className="error-text">{errors.isf[index]?.startTime?.message || "Invalid start time"}</span>}
                {errors.isf?.[index]?.value && <span role="alert" className="error-text">{errors.isf[index]?.value?.message || "Value Error"}</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendIsf(getNextSegment(getValues('isf') || isfFields, 50.0))}>Add ISF Segment</button>
            {errors.isf && <div className="error-text">{errors.isf.message}</div>}
          </div>
        )}

        {activeTab === 'targets' && (
          <div className="tab-content">
            <h4>Blood Glucose Targets</h4>
            {targetFields.map((field, index) => (
              <div key={field.id} className="segment-row">
                <Controller
                  control={control}
                  name={`targets.${index}.startTime` as const}
                  render={({ field }) => (
                    <TimeInput {...field} />
                  )}
                />
                <input
                  type="number"
                  step="1"
                  {...register(`targets.${index}.low` as const, { valueAsNumber: true })}
                  placeholder="Low (mg/dL)"
                  aria-label={`Target Low ${index}`}
                />
                <input
                  type="number"
                  step="1"
                  {...register(`targets.${index}.high` as const, { valueAsNumber: true })}
                  placeholder="High (mg/dL)"
                  aria-label={`Target High ${index}`}
                />
                <button type="button" onClick={() => removeTarget(index)}>Remove</button>
                {errors.targets?.[index]?.startTime && <span role="alert" className="error-text">{errors.targets[index]?.startTime?.message || "Invalid start time"}</span>}
                {errors.targets?.[index]?.low && <span role="alert" className="error-text">{errors.targets[index]?.low?.message || "Low Error"}</span>}
                {errors.targets?.[index]?.high && <span role="alert" className="error-text">{errors.targets[index]?.high?.message || "High Error"}</span>}
              </div>
            ))}
            <button type="button" onClick={() => appendTarget(getNextTargetSegment(getValues('targets') || (targetFields as { startTime: string; low: number; high: number }[])))}>Add Target Segment</button>
            {errors.targets && <div className="error-text">{errors.targets.message}</div>}
          </div>
        )}

        {!readOnly && (
          <div style={{ marginTop: '20px' }}>
            <button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Saving...' : (initialProfile ? 'Update Profile' : 'Create Profile')}
            </button>
          </div>
        )}
      </form>
    </div>
  );
};
