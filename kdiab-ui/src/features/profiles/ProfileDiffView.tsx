import React from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { profilesApi } from '../../api/profilesApi'
import type { Profile, ProfileSegment, TargetSegment } from '../../api/profilesApi'
import { useTimeFormat } from '../../context/TimeFormatContext'

interface ProfileDiffViewProps {
  userId: string
  activeProfile: Profile
  proposedProfile: Profile
  glucoseUnit?: string
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (typeof err === 'object' && err !== null) {
    const e = err as Record<string, unknown>
    const data = (e['response'] as Record<string, unknown> | undefined)?.['data'] as Record<string, unknown> | undefined
    if (typeof data?.['message'] === 'string') return data['message']
    if (typeof e['message'] === 'string') return e['message']
  }
  return fallback
}

function segmentsEqual(a: ProfileSegment[] | undefined, b: ProfileSegment[] | undefined): boolean {
  if ((a?.length ?? 0) !== (b?.length ?? 0)) return false
  return (a ?? []).every((seg, i) => seg.startTime === b![i]!.startTime && seg.value === b![i]!.value)
}

function targetsEqual(a: TargetSegment[] | undefined, b: TargetSegment[] | undefined): boolean {
  if ((a?.length ?? 0) !== (b?.length ?? 0)) return false
  return (a ?? []).every(
    (seg, i) =>
      seg.startTime === b![i]!.startTime &&
      seg.low === b![i]!.low &&
      seg.high === b![i]!.high,
  )
}

interface SegmentRowsProps {
  label: string
  activeSegs: ProfileSegment[] | undefined
  proposedSegs: ProfileSegment[] | undefined
  unit: string
  formatTime: (t: string) => string
}

function SegmentRows({ label, activeSegs, proposedSegs, unit, formatTime }: SegmentRowsProps) {
  const changed = !segmentsEqual(activeSegs, proposedSegs)
  const highlightStyle: React.CSSProperties = changed
    ? { background: 'var(--diff-highlight, #fff8c5)' }
    : {}

  const maxLen = Math.max(activeSegs?.length ?? 0, proposedSegs?.length ?? 0)
  if (maxLen === 0) return null

  return (
    <>
      <tr>
        <th
          colSpan={2}
          style={{ textAlign: 'left', padding: '0.5rem 0.25rem', fontSize: '0.8rem', color: 'var(--text-secondary)', borderBottom: '1px solid var(--border-color)' }}
        >
          {label}
        </th>
      </tr>
      {Array.from({ length: maxLen }, (_, i) => {
        const a = activeSegs?.[i]
        const p = proposedSegs?.[i]
        const rowChanged =
          a?.startTime !== p?.startTime || a?.value !== p?.value
        const rowStyle: React.CSSProperties = rowChanged ? { background: 'var(--diff-highlight, #fff8c5)' } : highlightStyle
        return (
          <tr key={i} style={rowStyle}>
            <td style={{ padding: '0.25rem 0.5rem' }}>
              {a ? `${formatTime(a.startTime)} — ${a.value} ${unit}` : '—'}
            </td>
            <td style={{ padding: '0.25rem 0.5rem' }}>
              {p ? `${formatTime(p.startTime)} — ${p.value} ${unit}` : '—'}
            </td>
          </tr>
        )
      })}
    </>
  )
}

interface ScalarRowProps {
  label: string
  activeVal: string | number | undefined | null
  proposedVal: string | number | undefined | null
}

function ScalarRow({ label, activeVal, proposedVal }: ScalarRowProps) {
  const changed = activeVal !== proposedVal
  const style: React.CSSProperties = changed ? { background: 'var(--diff-highlight, #fff8c5)' } : {}
  return (
    <tr style={style}>
      <th scope="row" style={{ padding: '0.25rem 0.5rem', fontWeight: 500 }}>{label}</th>
      <td style={{ padding: '0.25rem 0.5rem' }}>{activeVal ?? '—'}</td>
      <td style={{ padding: '0.25rem 0.5rem' }}>{proposedVal ?? '—'}</td>
    </tr>
  )
}

export function ProfileDiffView({ userId, activeProfile, proposedProfile, glucoseUnit = 'mg/dL' }: ProfileDiffViewProps) {
  const { t } = useTranslation()
  const { formatTime } = useTimeFormat()
  const queryClient = useQueryClient()

  const acceptMutation = useMutation({
    mutationFn: () => profilesApi.acceptProposedProfile(userId, proposedProfile.id),
    onSuccess: () => {
      toast.success(t('profileDiff.acceptSuccess'))
      void queryClient.invalidateQueries({ queryKey: ['profiles', userId] })
    },
    onError: (err: unknown) => {
      toast.error(extractErrorMessage(err, t('profileDiff.acceptError')))
    },
  })

  const rejectMutation = useMutation({
    mutationFn: () => profilesApi.rejectProposedProfile(userId, proposedProfile.id),
    onSuccess: () => {
      toast.success(t('profileDiff.rejectSuccess'))
      void queryClient.invalidateQueries({ queryKey: ['profiles', userId] })
    },
    onError: (err: unknown) => {
      toast.error(extractErrorMessage(err, t('profileDiff.rejectError')))
    },
  })

  const basalChanged = !segmentsEqual(activeProfile.basal, proposedProfile.basal)
  const icrChanged = !segmentsEqual(activeProfile.icr, proposedProfile.icr)
  const isfChanged = !segmentsEqual(activeProfile.isf, proposedProfile.isf)
  const targetsChanged = !targetsEqual(activeProfile.targets, proposedProfile.targets)
  const nameChanged = activeProfile.name !== proposedProfile.name
  const insulinTypeChanged = activeProfile.insulinType !== proposedProfile.insulinType
  const durationChanged = activeProfile.durationOfAction !== proposedProfile.durationOfAction

  return (
    <section
      aria-label={t('profileDiff.sectionLabel')}
      style={{ marginBottom: '2rem', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '1.25rem' }}
    >
      <h3 style={{ color: 'var(--accent-warning)', marginTop: 0, marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        {t('profileDiff.title')}
      </h3>
      {proposedProfile.proposalReason && (
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '0.75rem' }}>
          {t('profileDiff.reason')}: {proposedProfile.proposalReason}
        </p>
      )}

      <div
        style={{ overflowX: 'auto' }}
      >
        <table
          style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}
        >
          <colgroup>
            <col style={{ width: '25%' }} />
            <col style={{ width: '37.5%' }} />
            <col style={{ width: '37.5%' }} />
          </colgroup>
          <thead>
            <tr>
              <th style={{ padding: '0.5rem', textAlign: 'left', borderBottom: '2px solid var(--border-color)', fontWeight: 600 }}></th>
              <th style={{ padding: '0.5rem', textAlign: 'left', borderBottom: '2px solid var(--border-color)', fontWeight: 600 }}>
                {t('profileDiff.current')}
              </th>
              <th style={{ padding: '0.5rem', textAlign: 'left', borderBottom: '2px solid var(--border-color)', fontWeight: 600 }}>
                {t('profileDiff.proposed')}
              </th>
            </tr>
          </thead>
          <tbody>
            <ScalarRow
              label={t('profileDiff.name')}
              activeVal={activeProfile.name}
              proposedVal={proposedProfile.name}
            />
            <ScalarRow
              label={t('profileDiff.insulin')}
              activeVal={activeProfile.insulinType}
              proposedVal={proposedProfile.insulinType}
            />
            <ScalarRow
              label={t('profileDiff.duration')}
              activeVal={activeProfile.durationOfAction != null ? `${activeProfile.durationOfAction}m` : undefined}
              proposedVal={proposedProfile.durationOfAction != null ? `${proposedProfile.durationOfAction}m` : undefined}
            />
            {(basalChanged || (activeProfile.basal?.length ?? 0) > 0 || (proposedProfile.basal?.length ?? 0) > 0) && (
              <SegmentRows
                label={t('profileDiff.basal')}
                activeSegs={activeProfile.basal}
                proposedSegs={proposedProfile.basal}
                unit="U/hr"
                formatTime={formatTime}
              />
            )}
            {(icrChanged || (activeProfile.icr?.length ?? 0) > 0 || (proposedProfile.icr?.length ?? 0) > 0) && (
              <SegmentRows
                label={t('profileDiff.icr')}
                activeSegs={activeProfile.icr}
                proposedSegs={proposedProfile.icr}
                unit="g/U"
                formatTime={formatTime}
              />
            )}
            {(isfChanged || (activeProfile.isf?.length ?? 0) > 0 || (proposedProfile.isf?.length ?? 0) > 0) && (
              <SegmentRows
                label={t('profileDiff.isf')}
                activeSegs={activeProfile.isf}
                proposedSegs={proposedProfile.isf}
                unit={glucoseUnit}
                formatTime={formatTime}
              />
            )}
            {targetsChanged && activeProfile.targets && proposedProfile.targets && (
              <tr style={{ background: 'var(--diff-highlight, #fff8c5)' }}>
                <td style={{ padding: '0.25rem 0.5rem', fontWeight: 500 }}>{t('profileDiff.targets')}</td>
                <td style={{ padding: '0.25rem 0.5rem' }}>
                  {activeProfile.targets.map((tgt, i) => (
                    <div key={i}>{`${formatTime(tgt.startTime)} — ${tgt.low}–${tgt.high} ${glucoseUnit}`}</div>
                  ))}
                </td>
                <td style={{ padding: '0.25rem 0.5rem' }}>
                  {proposedProfile.targets.map((tgt, i) => (
                    <div key={i}>{`${formatTime(tgt.startTime)} — ${tgt.low}–${tgt.high} ${glucoseUnit}`}</div>
                  ))}
                </td>
              </tr>
            )}
            {!nameChanged && !insulinTypeChanged && !durationChanged && !basalChanged && !icrChanged && !isfChanged && !targetsChanged && (
              <tr>
                <td colSpan={3} style={{ padding: '0.5rem', color: 'var(--text-secondary)', fontStyle: 'italic' }}>
                  {t('profileDiff.noChanges')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem', flexWrap: 'wrap' }}>
        <button
          className="btn primary"
          onClick={() => acceptMutation.mutate()}
          disabled={acceptMutation.isPending || rejectMutation.isPending}
          aria-label={t('profileDiff.acceptAriaLabel')}
        >
          {acceptMutation.isPending ? t('profileList.accepting') : t('profileList.accept')}
        </button>
        <button
          className="btn danger outline"
          onClick={() => rejectMutation.mutate()}
          disabled={acceptMutation.isPending || rejectMutation.isPending}
          aria-label={t('profileDiff.rejectAriaLabel')}
        >
          {rejectMutation.isPending ? t('profileList.rejecting') : t('profileList.reject')}
        </button>
      </div>
    </section>
  )
}
