import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, customApi } from '../api/client';
import type { Profile } from '../api/generated';
import { useTimeFormat } from '../context/TimeFormatContext';
import { useTranslation } from 'react-i18next';

interface ProfileListProps {
  userId: string;
  onSelectProfile?: (profile: Profile) => void;
  readOnly?: boolean;
}

export function ProfileList({ userId, onSelectProfile, readOnly = false }: ProfileListProps) {
  const queryClient = useQueryClient();
  const [expandedProfileId, setExpandedProfileId] = React.useState<string | null>(null);
  const [mutationError, setMutationError] = React.useState<string | null>(null);
  const { formatDate, formatTime } = useTimeFormat();
  const { t } = useTranslation();

  const { data: profiles = [] as Profile[], isLoading, isError, error } = useQuery<Profile[]>({
    queryKey: ['profiles', userId],
    queryFn: async () => {
      const response = await api.listProfiles(userId);
      return response.data;
    },
    enabled: !!userId,
  });

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string };
    const msg = apiErr?.response?.data?.message ?? apiErr?.message ?? 'Operation failed. Please try again.';
    setMutationError(msg);
  };

  const acceptMutation = useMutation({
    mutationFn: (profileId: string) => customApi.acceptProposedProfile(userId, profileId),
    onSuccess: () => { setMutationError(null); queryClient.invalidateQueries({ queryKey: ['profiles', userId] }); },
    onError: onMutationError,
  });

  const rejectMutation = useMutation({
    mutationFn: (profileId: string) => customApi.rejectProposedProfile(userId, profileId),
    onSuccess: () => { setMutationError(null); queryClient.invalidateQueries({ queryKey: ['profiles', userId] }); },
    onError: onMutationError,
  });

  const activateMutation = useMutation({
    mutationFn: (profileId: string) => api.activateProfile(userId, profileId),
    onSuccess: () => { setMutationError(null); queryClient.invalidateQueries({ queryKey: ['profiles', userId] }); },
    onError: onMutationError,
  });

  const handleAccept = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation();
    if (window.confirm(t('profileList.confirmAccept'))) {
      acceptMutation.mutate(profileId);
    }
  };

  const handleReject = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation();
    if (window.confirm(t('profileList.confirmReject'))) {
      rejectMutation.mutate(profileId);
    }
  };

  const handleActivate = (e: React.MouseEvent, profileId: string) => {
    e.stopPropagation();
    if (window.confirm(t('profileList.confirmActivate'))) {
      activateMutation.mutate(profileId);
    }
  };

  if (isLoading) return <div className="loading">{t('profileList.loading')}</div>;
  if (isError) return <div className="error">{t('profileList.error')} {(error as Error).message}</div>;

  const proposedProfiles = profiles.filter(p => (p.status as string || '') === 'PROPOSED');
  const otherProfiles = profiles.filter(p => (p.status as string || '') === 'ACTIVE' || (p.status as string || '') === 'DRAFT');

  const toggleExpand = (profileId: string) => {
    setExpandedProfileId(prev => prev === profileId ? null : profileId);
  };

  const renderProfileCard = (profile: Profile) => {
    const isExpanded = expandedProfileId === profile.id;
    const isActive = (profile.status as string) === 'ACTIVE';
    return (
      <div key={profile.id} className={`profile-card ${isActive ? 'active' : ''}`} onClick={() => {
        toggleExpand(profile.id);
      }}>
        {isActive && <div className="active-glow" />}
        <div className="profile-card-header">
          <strong>{profile.name}{isActive && <span className="active-label" aria-label="Currently active profile"> ✓ {t('profileList.active')}</span>}</strong>
          <div>
            <span className={`status-badge status-${(profile.status as string || 'Unknown').toLowerCase()}`}>{profile.status as string || 'Unknown'}</span>
            {!readOnly && (
              <button
                onClick={(e) => { e.stopPropagation(); onSelectProfile?.(profile); }}
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
          <p>Insulin: {profile.insulinType || 'N/A'} • Action: {profile.durationOfAction || 0}m</p>
          <p className="segments-count">
            {profile.basal?.length || 0} Basal • {profile.icr?.length || 0} ICR • {profile.isf?.length || 0} ISF
          </p>
          {isExpanded && (
            <div className="profile-details">
              {profile.basal && profile.basal.length > 0 && (
                <div className="detail-section">
                  <h4>Basal Segments</h4>
                  <ul>
                    {profile.basal.map((b, i) => <li key={i}>{formatTime(b?.startTime || '00:00')} - {b?.value} U/hr</li>)}
                  </ul>
                </div>
              )}
              {profile.icr && profile.icr.length > 0 && (
                <div className="detail-section">
                  <h4>ICR Segments</h4>
                  <ul>
                    {profile.icr.map((icr, i) => <li key={i}>{formatTime(icr?.startTime || '00:00')} - {icr?.value} g/U</li>)}
                  </ul>
                </div>
              )}
              {profile.isf && profile.isf.length > 0 && (
                <div className="detail-section">
                  <h4>ISF Segments</h4>
                  <ul>
                    {profile.isf.map((isf, i) => <li key={i}>{formatTime(isf?.startTime || '00:00')} - {isf?.value} mg/dL</li>)}
                  </ul>
                </div>
              )}
              {profile.targets && profile.targets.length > 0 && (
                <div className="detail-section">
                  <h4>BG Targets</h4>
                  <ul>
                    {profile.targets.map((tgt, i) => <li key={i}>{formatTime(tgt?.startTime || '00:00')} - {tgt?.low}–{tgt?.high} mg/dL</li>)}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
        {(profile.status as string) === 'PROPOSED' && !readOnly && (
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
    );
  };

  return (
    <div className="profile-list">
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
              <h3 style={{ color: '#fbbf24', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                ⚠️ {t('profileList.pendingTitle')}
              </h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1rem' }}>
                {t('profileList.pendingDescription')}
              </p>
              <div className="card-grid">
                {proposedProfiles.map(renderProfileCard)}
              </div>
            </div>
          )}

          {otherProfiles.length > 0 && (
            <div className="active-section">
              <h3>{t('profileList.yourConfigurations')}</h3>
              <div className="card-grid">
                {otherProfiles.map(renderProfileCard)}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
