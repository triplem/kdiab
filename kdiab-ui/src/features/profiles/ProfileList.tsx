import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { profilesApi } from '../../api/profilesApi'
import type { Profile } from '../../api/profilesApi'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import { ConfirmModal } from '../../components/ConfirmModal'

interface ProfileListProps {
  userId: string
  onSelectProfile?: (profile: Profile) => void
  readOnly?: boolean
}

interface ConfirmAction {
  title: string
  message: string
  danger?: boolean
  action: () => void
}

export function ProfileList({ userId, onSelectProfile, readOnly = false }: ProfileListProps) {
  const queryClient = useQueryClient()
  const [expandedProfileId, setExpandedProfileId] = useState<string | null>(null)
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const { formatDate, formatTime } = useTimeFormat()
  const { t } = useTranslation()

  const { data: profiles = [] as Profile[], isLoading, isError, error } = useQuery<Profile[]>({
    queryKey: ['profiles', userId],
    queryFn: async () => {
      const response = await profilesApi.listProfiles(userId)
      return response.data
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
    mutationFn: (profileId: string) => profilesApi.rejectProposedProfile(userId, profileId),
    onSuccess: () => {
      setMutationError(null)
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
      title: t('confirm.archiveTitle'),
      message: t('profileList.confirmAccept'),
      action: () => acceptMutation.mutate(profileId),
    })
  }

  const handleReject = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation()
    setConfirmAction({
      title: t('confirm.deleteTitle'),
      message: t('profileList.confirmReject'),
      danger: true,
      action: () => rejectMutation.mutate(profileId),
    })
  }

  const handleActivate = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation()
    setConfirmAction({
      title: t('confirm.archiveTitle'),
      message: t('profileList.confirmActivate'),
      action: () => activateMutation.mutate(profileId),
    })
  }

  if (isLoading) return <div className="loading">{t('profileList.loading')}</div>
  if (isError) return <div className="error">{t('profileList.error')} {(error as Error).message}</div>

  const proposedProfiles = profiles.filter((p) => p.status === 'PROPOSED')
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
                        {formatTime(isf?.startTime || '00:00')} - {isf?.value} mg/dL
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
                        {formatTime(tgt?.startTime || '00:00')} - {tgt?.low}–{tgt?.high} mg/dL
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
              onClick={(e) => handleReject(e, profile.id)}
              className="btn danger outline"
              disabled={rejectMutation.isPending}
              aria-label={`Reject proposed profile ${profile.name}`}
            >
              {rejectMutation.isPending && rejectMutation.variables === profile.id
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
      {confirmAction && (
        <ConfirmModal
          isOpen={true}
          title={confirmAction.title}
          message={confirmAction.message}
          danger={confirmAction.danger}
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
