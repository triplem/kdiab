import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { profilesApi } from '../../api/profilesApi'
import type { Profile } from '../../api/profilesApi'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import { ProfileTimeline } from './ProfileTimeline'

const PAGE_SIZE = 10

interface ProfileHistoryProps {
  userId: string
  onSelectProfile?: (profile: Profile) => void
  glucoseUnit?: string
}

function ProfileHistoryItem({
  profile, formatTime, is24Hour, onSelectProfile, glucoseUnit,
}: {
  profile: Profile
  formatTime: (t: string) => string
  is24Hour: boolean
  onSelectProfile?: (p: Profile) => void
  glucoseUnit?: string
}) {
  const [activeTab, setActiveTab] = useState<'basal' | 'icr' | 'isf'>('basal')
  const { t } = useTranslation()
  const status = profile.status

  return (
    <li className="history-item">
      <details>
        <summary>
          <strong>{profile.name}</strong> — <span className={`status-badge status-${status.toLowerCase()}`}>{status}</span>
          <span className="date" style={{ marginLeft: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
            ({profile.createdAt ? new Date(profile.createdAt).toLocaleString(navigator.language, { dateStyle: 'short', timeStyle: 'short', hour12: !is24Hour }) : t('history.na')})
          </span>
          {onSelectProfile && status !== 'ARCHIVED' && (
            <button
              type="button"
              className="btn outline"
              style={{ padding: '0.1rem 0.4rem', fontSize: '0.8rem', marginLeft: '0.5rem' }}
              onClick={(e) => { e.preventDefault(); onSelectProfile(profile) }}
            >
              {t('history.edit')}
            </button>
          )}
        </summary>
        <div className="history-details" style={{ padding: '0.75rem 0 0 1rem' }}>
          <p>{t('history.insulin')}: {profile.insulinType || t('history.na')} • {t('history.action')}: {profile.durationOfAction || 0}{t('history.unitMin')}</p>

          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', marginBottom: '0.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.375rem' }}>
            {(['basal', 'icr', 'isf'] as const).map(tab => (
              <button
                key={tab}
                type="button"
                style={{ fontWeight: activeTab === tab ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === tab ? 'underline' : 'none', color: 'var(--text-primary)' }}
                onClick={() => setActiveTab(tab)}
              >
                {t(`history.${tab}`)} ({profile[tab]?.length || 0})
              </button>
            ))}
          </div>

          <div style={{ padding: '4px 0' }}>
            {activeTab === 'basal' && (
              profile.basal && profile.basal.length > 0
                ? <ul>{profile.basal.map((b, i) => <li key={i}>{formatTime(b?.startTime || '00:00')} — {b?.value} {t('history.unitUhr')}</li>)}</ul>
                : <p>{t('history.noBasal')}</p>
            )}
            {activeTab === 'icr' && (
              profile.icr && profile.icr.length > 0
                ? <ul>{profile.icr.map((icr, i) => <li key={i}>{formatTime(icr?.startTime || '00:00')} — {icr?.value} {t('history.unitGperU')}</li>)}</ul>
                : <p>{t('history.noIcr')}</p>
            )}
            {activeTab === 'isf' && (
              profile.isf && profile.isf.length > 0
                ? <ul>{profile.isf.map((isf, i) => <li key={i}>{formatTime(isf?.startTime || '00:00')} — {isf?.value} {glucoseUnit === 'mmol/L' ? t('history.unitMmol') : t('history.unitMgdl')}</li>)}</ul>
                : <p>{t('history.noIsf')}</p>
            )}
          </div>
        </div>
      </details>
    </li>
  )
}

export function ProfileHistory({ userId, onSelectProfile, glucoseUnit }: ProfileHistoryProps) {
  const [page, setPage] = useState(0)
  const [viewMode, setViewMode] = useState<'list' | 'timeline'>('list')
  const { formatTime, is24Hour } = useTimeFormat()
  const { t } = useTranslation()

  // Fetch the active/draft profiles list
  const { data: profilesData, isLoading: listLoading, isError: listError } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => profilesApi.listProfiles(userId).then(r => r.data.items),
    enabled: !!userId,
  })

  // Fetch all-time history (10 years back to cover everything)
  const tenYearsAgo = useMemo(() => new Date(Date.now() - 10 * 365 * 24 * 60 * 60 * 1000).toISOString(), [])
  const nowIso = useMemo(() => new Date().toISOString(), [])
  const { data: historyData, isLoading: historyLoading, isError: historyError } = useQuery({
    queryKey: ['profile-history', userId],
    queryFn: () => profilesApi.getProfileHistory(userId, tenYearsAgo, nowIso).then(r => r.data),
    enabled: !!userId,
  })

  const isLoading = listLoading || historyLoading
  const isError = listError || historyError

  // Merge: active/draft profiles + history (archived), deduplicate by id
  const allProfiles: Profile[] = []
  const seen = new Set<string>()
  for (const p of profilesData ?? []) {
    if (!seen.has(p.id)) { seen.add(p.id); allProfiles.push(p) }
  }
  for (const p of historyData ?? []) {
    if (!seen.has(p.id)) { seen.add(p.id); allProfiles.push(p) }
  }
  allProfiles.sort((a, b) => new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime())

  const totalPages = Math.ceil(allProfiles.length / PAGE_SIZE)
  const visibleProfiles = allProfiles.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <div className="profile-history">
      <h3>{t('history.title')}</h3>

      <div style={{ marginBottom: '1rem', display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: '0.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.25rem' }}>
          {(['list', 'timeline'] as const).map(mode => (
            <button
              key={mode}
              type="button"
              style={{
                fontWeight: viewMode === mode ? 'bold' : 'normal',
                border: 'none',
                background: 'none',
                cursor: 'pointer',
                textDecoration: viewMode === mode ? 'underline' : 'none',
                color: 'var(--text-primary)',
              }}
              onClick={() => setViewMode(mode)}
            >
              {mode === 'list' ? t('history.listView') : t('history.timelineView')}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <div>{t('history.loading')}</div>}
      {isError && <div style={{ color: 'var(--accent-danger)' }}>{t('history.error')}</div>}

      {!isLoading && !isError && allProfiles.length === 0 ? (
        <p>{t('history.empty')}</p>
      ) : viewMode === 'timeline' ? (
        <ProfileTimeline profiles={allProfiles} />
      ) : (
        <>
          <ul className="history-list" style={{ listStyle: 'none', padding: 0 }}>
            {visibleProfiles.map(profile => (
              <ProfileHistoryItem key={profile.id} profile={profile} formatTime={formatTime} is24Hour={is24Hour} {...(onSelectProfile !== undefined && { onSelectProfile })} {...(glucoseUnit !== undefined && { glucoseUnit })} />
            ))}
          </ul>

          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginTop: '1rem', alignItems: 'center' }}>
              <button
                type="button"
                className="btn outline"
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                style={{ padding: '0.3rem 0.8rem' }}
              >
                ←
              </button>
              <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                className="btn outline"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                style={{ padding: '0.3rem 0.8rem' }}
              >
                →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
