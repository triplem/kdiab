import React, { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { profilesApi } from '../../api/profilesApi'
import type { Profile } from '../../api/profilesApi'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import { ConfirmModal } from '../../components/ConfirmModal'
import { ProfileDiffView } from './ProfileDiffView'

interface ProfileListProps {
  userId: string
  onSelectProfile?: (profile: Profile) => void
  readOnly?: boolean
  glucoseUnit?: string
}

interface ConfirmAction {
  title: string
  message: string
  danger?: boolean
  action: () => void
}

export function ProfileList({ userId, onSelectProfile, readOnly = false, glucoseUnit = 'mg/dL' }: ProfileListProps) {
  const queryClient = useQueryClient()
  const [expandedProfileId, setExpandedProfileId] = useState<string | null>(null)
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const [pendingRejectProfileId, setPendingRejectProfileId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState<string>('')
  const { formatDate, formatTime } = useTimeFormat()
  const { t } = useTranslation()

  // Focus management for reject modal
  const rejectModalRef = useRef<HTMLDivElement>(null)
  const rejectTriggerRefs = useRef<Map<string, HTMLButtonElement>>(new Map())

  useEffect(() => {
    if (pendingRejectProfileId) rejectModalRef.current?.focus()
  }, [pendingRejectProfileId])

  // Escape key closes the reject modal
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && pendingRejectProfileId) {
        const id = pendingRejectProfileId
        setPendingRejectProfileId(null)
        setRejectReason('')
        rejectTriggerRefs.current.get(id)?.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [pendingRejectProfileId])

  const { data: profiles = [] as Profile[], isLoading, isError, error } = useQuery<Profile[]>({
    queryKey: ['profiles', userId],
    queryFn: async () => {
      const response = await profilesApi.listProfiles(userId)
      return response.data.items
    },
    enabled: !!userId,
  })

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
    setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? 'Operation failed.')
  }

  const acceptMutation = useMutation({
    mutationFn: (profileId: string) => profilesApi.acceptProposedProfile(userId, profileId),
    onSuccess: () => {
      setMutationError(null)
      void queryClient.invalidateQueries({ queryKey: ['profiles', userId] })
    },
    onError: onMutationError,
  })

  const rejectMutation = useMutation({
    mutationFn: ({ profileId, reason }: { profileId: string; reason?: string }) =>
      profilesApi.rejectProposedProfile(userId, profileId, reason),
    onSuccess: () => {
      setMutationError(null)
      setRejectReason('')
      void queryClient.invalidateQueries({ queryKey: ['profiles', userId] })
    },
    onError: onMutationError,
  })

  const activateMutation = useMutation({
    mutationFn: (profileId: string) => profilesApi.activateProfile(userId, profileId),
    onSuccess: () => {
      setMutationError(null)
      void queryClient.invalidateQueries({ queryKey: ['profiles', userId] })
    },
    onError: onMutationError,
  })

  const handleAccept = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation()
    setConfirmAction({
      title: t('confirm.acceptTitle'),
      message: t('profileList.confirmAccept'),
      action: () => acceptMutation.mutate(profileId),
    })
  }

  const handleReject = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation()
    setRejectReason('')
    setPendingRejectProfileId(profileId)
  }

  const handleActivate = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation()
    setConfirmAction({
      title: t('confirm.activateTitle'),
      message: t('profileList.confirmActivate'),
      action: () => activateMutation.mutate(profileId),
    })
  }

  if (isLoading) return <div className="loading">{t('profileList.loading')}</div>
  if (isError) return <div className="error">{t('profileList.error')} {error instanceof Error ? error.message : t('common.unknownError')}</div>

  const proposedProfiles = profiles.filter((p) => p.status === 'PROPOSED')
  const activeProfile = profiles.find((p) => p.status === 'ACTIVE')
  const proposedProfile = proposedProfiles[0]
  const otherProfiles = profiles.filter((p) => p.status === 'ACTIVE' || p.status === 'DRAFT')

  const toggleExpand = (profileId: string) => {
    setExpandedProfileId((prev) => (prev === profileId ? null : profileId))
  }

  const renderProfileCard = (profile: Profile) => {
    const isExpanded = expandedProfileId === profile.id
    const isActive = profile.status === 'ACTIVE'
    return (
      <div
        key={profile.id}
        className={`profile-card ${isActive ? 'active' : ''}`}
        onClick={() => toggleExpand(profile.id)}
      >
        <div className="profile-card-header">
          <strong>
            {profile.name}
            {isActive && (
              <span className="active-label" aria-label="Currently active profile">
                {' '}
                ✓ {t('profileList.active')}
              </span>
            )}
          </strong>
          <div>
            <span className={`status-badge status-${profile.status.toLowerCase()}`}>
              {profile.status}
            </span>
            {!readOnly && (
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  onSelectProfile?.(profile)
                }}
                className="btn small"
              >
                {t('profileList.edit')}
              </button>
            )}
            {!readOnly && profile.status !== 'ACTIVE' && profile.status !== 'PROPOSED' && (
              <button
                onClick={(e) => handleActivate(e, profile.id)}
                className="btn primary small"
                disabled={activateMutation.isPending}
                aria-label={`Activate profile ${profile.name}`}
              >
                {activateMutation.isPending && activateMutation.variables === profile.id
                  ? t('profileList.activating')
                  : t('profileList.activate')}
              </button>
            )}
          </div>
        </div>
        <div className="profile-card-body">
          <p>
            Insulin: {profile.insulinType ?? 'N/A'} • Action: {profile.durationOfAction ?? 0}m
            {profile.timeZone && <span> • {t('profile.timeZone')}: {profile.timeZone}</span>}
          </p>
          <p className="segments-count">
            {profile.basal?.length ?? 0} Basal • {profile.icr?.length ?? 0} ICR •{' '}
            {profile.isf?.length ?? 0} ISF
          </p>
          {isExpanded && (
            <div className="profile-details">
              {profile.basal && profile.basal.length > 0 && (
                <div className="detail-section">
                  <h4>Basal Segments</h4>
                  <ul>
                    {profile.basal.map((b, i) => (
                      <li key={i}>
                        {formatTime(b?.startTime || '00:00')} - {b?.value} U/hr
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {profile.icr && profile.icr.length > 0 && (
                <div className="detail-section">
                  <h4>ICR Segments</h4>
                  <ul>
                    {profile.icr.map((icr, i) => (
                      <li key={i}>
                        {formatTime(icr?.startTime || '00:00')} - {icr?.value} g/U
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {profile.isf && profile.isf.length > 0 && (
                <div className="detail-section">
                  <h4>ISF Segments</h4>
                  <ul>
                    {profile.isf.map((isf, i) => (
                      <li key={i}>
                        {formatTime(isf?.startTime || '00:00')} - {isf?.value} {glucoseUnit}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {profile.targets && profile.targets.length > 0 && (
                <div className="detail-section">
                  <h4>BG Targets</h4>
                  <ul>
                    {profile.targets.map((tgt, i) => (
                      <li key={i}>
                        {formatTime(tgt?.startTime || '00:00')} - {tgt?.low}–{tgt?.high} {glucoseUnit}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
        {profile.status === 'PROPOSED' && !readOnly && (
          <div className="proposal-actions">
            {profile.createdAt && (
              <p style={{ margin: '0 0 0.5rem', fontSize: '0.8rem', color: 'var(--text-secondary)', width: '100%' }}>
                {t('profileList.proposedOn', { date: formatDate(profile.createdAt) })}
              </p>
            )}
            {profile.createdBy && (
              <p style={{ margin: '0 0 0.5rem', fontSize: '0.8rem', color: 'var(--text-secondary)', width: '100%' }}>
                {t('profileList.proposedByDoctor')}
              </p>
            )}
            {profile.proposalReason && (
              <p className="proposal-reason">{profile.proposalReason}</p>
            )}
            <button
              onClick={(e) => handleAccept(e, profile.id)}
              className="btn primary"
              disabled={acceptMutation.isPending}
              aria-label={`Accept proposed profile ${profile.name}`}
            >
              {acceptMutation.isPending && acceptMutation.variables === profile.id
                ? t('profileList.accepting')
                : t('profileList.accept')}
            </button>
            <button
              ref={(el) => { if (el) rejectTriggerRefs.current.set(profile.id, el) }}
              onClick={(e) => handleReject(e, profile.id)}
              className="btn danger outline"
              disabled={rejectMutation.isPending}
              aria-label={`Reject proposed profile ${profile.name}`}
            >
              {rejectMutation.isPending && rejectMutation.variables?.profileId === profile.id
                ? t('profileList.rejecting')
                : t('profileList.reject')}
            </button>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="profile-list">
      {pendingRejectProfileId != null && (
        <div
          ref={rejectModalRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby="reject-modal-title"
          className="modal-overlay"
          tabIndex={-1}
          onClick={() => {
            const id = pendingRejectProfileId
            setPendingRejectProfileId(null)
            setRejectReason('')
            rejectTriggerRefs.current.get(id)?.focus()
          }}
        >
          <div
            className="modal-box"
            style={{ maxWidth: '400px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="reject-modal-title" className="modal-title">{t('confirm.deleteTitle')}</h2>
            <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>{t('profileList.confirmReject')}</p>
            <div style={{ marginBottom: '1rem' }}>
              <label
                htmlFor="reject-reason"
                style={{ display: 'block', marginBottom: '0.25rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}
              >
                {t('profileList.rejectReasonLabel')}
              </label>
              <textarea
                id="reject-reason"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder={t('profileList.rejectReasonPlaceholder')}
                rows={3}
                style={{
                  width: '100%',
                  boxSizing: 'border-box',
                  padding: '0.5rem',
                  borderRadius: '4px',
                  border: '1px solid var(--border-color)',
                  background: 'var(--bg-input)',
                  color: 'var(--text-primary)',
                  resize: 'vertical',
                }}
              />
            </div>
            <div className="modal-footer">
              <button
                type="button"
                onClick={() => {
                  const id = pendingRejectProfileId
                  setPendingRejectProfileId(null)
                  setRejectReason('')
                  if (id) rejectTriggerRefs.current.get(id)?.focus()
                }}
                className="btn outline"
              >
                {t('confirm.cancelLabel')}
              </button>
              <button
                type="button"
                onClick={() => {
                  const id = pendingRejectProfileId
                  rejectMutation.mutate({ profileId: id, ...(rejectReason ? { reason: rejectReason } : {}) })
                  setPendingRejectProfileId(null)
                  setRejectReason('')
                  if (id) rejectTriggerRefs.current.get(id)?.focus()
                }}
                className="btn danger"
                disabled={rejectMutation.isPending}
              >
                {t('confirm.confirmLabel')}
              </button>
            </div>
          </div>
        </div>
      )}
      {confirmAction && (
        <ConfirmModal
          isOpen={true}
          title={confirmAction.title}
          message={confirmAction.message}
          {...(confirmAction.danger !== undefined && { danger: confirmAction.danger })}
          confirmLabel={t('confirm.confirmLabel')}
          cancelLabel={t('confirm.cancelLabel')}
          onConfirm={() => {
            confirmAction.action()
            setConfirmAction(null)
          }}
          onCancel={() => setConfirmAction(null)}
        />
      )}
      <h2>{t('profileList.title')}</h2>
      {mutationError && (
        <div role="alert" className="error" style={{ marginBottom: '1rem' }}>
          {mutationError}
        </div>
      )}
      {!readOnly && activeProfile && proposedProfile && (
        <ProfileDiffView
          userId={userId}
          activeProfile={activeProfile}
          proposedProfile={proposedProfile}
          glucoseUnit={glucoseUnit}
        />
      )}
      {profiles.length === 0 ? (
        <p>{t('profileList.noProfiles')}</p>
      ) : (
        <>
          {proposedProfiles.length > 0 && (
            <div className="proposed-section" style={{ marginBottom: '2rem' }}>
              <h3 style={{ color: 'var(--accent-warning)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                ⚠️ {t('profileList.pendingTitle')}
              </h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                {t('profileList.pendingDescription')}
              </p>
              <div className="card-grid">{proposedProfiles.map(renderProfileCard)}</div>
            </div>
          )}
          {otherProfiles.length > 0 && (
            <div className="active-section">
              <h3>{t('profileList.yourConfigurations')}</h3>
              <div className="card-grid">{otherProfiles.map(renderProfileCard)}</div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
