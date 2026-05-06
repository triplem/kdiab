import { useEffect, useState } from 'react'
import { profilesApi } from '../../api/profilesApi'
import type { Profile } from '../../api/profilesApi'
import { startOfDay, endOfDay, subDays, formatISO, parseISO } from 'date-fns'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import { ProfileTimeline } from './ProfileTimeline'

interface ProfileHistoryProps {
  userId: string
  onSelectProfile?: (profile: Profile) => void
}

function ProfileHistoryItem({
  profile, formatTime, is24Hour, onSelectProfile,
}: {
  profile: Profile
  formatTime: (t: string) => string
  is24Hour: boolean
  onSelectProfile?: (p: Profile) => void
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
          {onSelectProfile && (
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
                ? <ul>{profile.isf.map((isf, i) => <li key={i}>{formatTime(isf?.startTime || '00:00')} — {isf?.value} {t('history.unitMgdl')}</li>)}</ul>
                : <p>{t('history.noIsf')}</p>
            )}
          </div>
        </div>
      </details>
    </li>
  )
}

export function ProfileHistory({ userId, onSelectProfile }: ProfileHistoryProps) {
  const [history, setHistory] = useState<Profile[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeProfileWarning, setActiveProfileWarning] = useState<string | null>(null)
  const [viewMode, setViewMode] = useState<'list' | 'timeline'>('list')
  const { formatTime, is24Hour, locale } = useTimeFormat()
  const { t } = useTranslation()

  const [startDate, setStartDate] = useState(() =>
    formatISO(subDays(new Date(), 30), { representation: 'date' })
  )
  const [endDate, setEndDate] = useState(() =>
    formatISO(new Date(), { representation: 'date' })
  )

  useEffect(() => {
    if (!userId || !startDate || !endDate) return
    if (startDate > endDate) { setError(t('history.error')); return }

    const fetchHistory = async () => {
      setLoading(true)
      setError(null)
      setActiveProfileWarning(null)
      try {
        const from = startOfDay(parseISO(startDate)).toISOString()
        const to = endOfDay(parseISO(endDate)).toISOString()
        const historyRes = await profilesApi.getProfileHistory(userId, from, to)
        try {
          const profilesRes = await profilesApi.listProfiles(userId)
          const activeProfile = profilesRes.data.find(p => p.status === 'ACTIVE')
          if (activeProfile && !historyRes.data.find(p => p.id === activeProfile.id)) {
            setHistory([activeProfile, ...historyRes.data])
          } else {
            setHistory(historyRes.data)
          }
        } catch {
          setHistory(historyRes.data)
          setActiveProfileWarning(t('history.activeProfileWarning'))
        }
      } catch {
        setError(t('history.error'))
      } finally {
        setLoading(false)
      }
    }

    void fetchHistory()
  }, [userId, startDate, endDate, t])

  return (
    <div className="profile-history">
      <h3>{t('history.title')}</h3>

      <div style={{ marginBottom: '1rem', display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <label htmlFor="start-date" style={{ marginRight: '0.5rem' }}>{t('history.from')}</label>
          <input type="date" id="start-date" lang={locale} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </div>
        <div>
          <label htmlFor="end-date" style={{ marginRight: '0.5rem' }}>{t('history.to')}</label>
          <input type="date" id="end-date" lang={locale} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        </div>
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

      {loading && <div>{t('history.loading')}</div>}
      {error && <div style={{ color: 'var(--accent-danger)' }}>{error}</div>}
      {activeProfileWarning && <div style={{ color: 'var(--accent-warning)' }}>{activeProfileWarning}</div>}

      {!loading && !error && history.length === 0 ? (
        <p>{t('history.empty')}</p>
      ) : viewMode === 'timeline' ? (
        <ProfileTimeline profiles={history} />
      ) : (
        <ul className="history-list" style={{ listStyle: 'none', padding: 0 }}>
          {history.map(profile => (
            <ProfileHistoryItem key={profile.id} profile={profile} formatTime={formatTime} is24Hour={is24Hour} onSelectProfile={onSelectProfile} />
          ))}
        </ul>
      )}
    </div>
  )
}
