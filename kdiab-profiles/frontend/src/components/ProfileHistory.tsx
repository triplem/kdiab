import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Profile } from '../api/generated';
import { startOfDay, endOfDay, subDays, formatISO, parseISO } from 'date-fns';
import { useTimeFormat } from '../context/TimeFormatContext';
import { useTranslation } from 'react-i18next';

interface ProfileHistoryProps {
  userId: string;
  onSelectProfile?: (profile: Profile) => void;
}

const ProfileHistoryItem = ({
  profile, formatTime, is24Hour, onSelectProfile,
}: {
  profile: Profile;
  formatTime: (t: string) => string;
  is24Hour: boolean;
  onSelectProfile?: (p: Profile) => void;
}) => {
  const [activeTab, setActiveTab] = useState<'basal' | 'icr' | 'isf'>('basal');
  const { t } = useTranslation();
  const status = profile.status as string || 'Unknown';

  return (
    <li className="history-item">
      <details>
        <summary>
          <strong>{profile.name}</strong> - <span className={`status-badge status-${status.toLowerCase()}`}>{status}</span>
          <span className="date">({profile.createdAt ? new Date(profile.createdAt).toLocaleString(navigator.language, { dateStyle: 'short', timeStyle: 'short', hour12: !is24Hour }) : t('history.na')})</span>
          {onSelectProfile && (
            <button
              type="button"
              className="btn outline"
              style={{ padding: '0.1rem 0.4rem', fontSize: '0.8rem', marginLeft: '0.5rem' }}
              onClick={(e) => { e.preventDefault(); onSelectProfile(profile); }}
            >
              {t('history.edit')}
            </button>
          )}
        </summary>
        <div className="history-details">
          <p>{t('history.insulin')}: {profile.insulinType || t('history.na')} • {t('history.action')}: {profile.durationOfAction || 0}{t('history.unitMin')}</p>

          <div className="history-tabs" style={{ display: 'flex', gap: '8px', marginTop: '12px', marginBottom: '8px', borderBottom: '1px solid #ccc', paddingBottom: '6px' }}>
            <button type="button"
              style={{ fontWeight: activeTab === 'basal' ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === 'basal' ? 'underline' : 'none' }}
              onClick={() => setActiveTab('basal')}>
              {t('history.basal')} ({profile.basal?.length || 0})
            </button>
            <button type="button"
              style={{ fontWeight: activeTab === 'icr' ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === 'icr' ? 'underline' : 'none' }}
              onClick={() => setActiveTab('icr')}>
              {t('history.icr')} ({profile.icr?.length || 0})
            </button>
            <button type="button"
              style={{ fontWeight: activeTab === 'isf' ? 'bold' : 'normal', border: 'none', background: 'none', cursor: 'pointer', textDecoration: activeTab === 'isf' ? 'underline' : 'none' }}
              onClick={() => setActiveTab('isf')}>
              {t('history.isf')} ({profile.isf?.length || 0})
            </button>
          </div>

          <div className="tab-contents" style={{ padding: '4px 0' }}>
            {activeTab === 'basal' && (
              <div>
                {profile.basal && profile.basal.length > 0 ? (
                  <ul>{profile.basal.map((b, i) => (
                    <li key={i}>{formatTime(b?.startTime || '00:00')} - {b?.value} {t('history.unitUhr')}</li>
                  ))}</ul>
                ) : <p>{t('history.noBasal')}</p>}
              </div>
            )}
            {activeTab === 'icr' && (
              <div>
                {profile.icr && profile.icr.length > 0 ? (
                  <ul>{profile.icr.map((icr, i) => (
                    <li key={i}>{formatTime(icr?.startTime || '00:00')} - {icr?.value} {t('history.unitGperU')}</li>
                  ))}</ul>
                ) : <p>{t('history.noIcr')}</p>}
              </div>
            )}
            {activeTab === 'isf' && (
              <div>
                {profile.isf && profile.isf.length > 0 ? (
                  <ul>{profile.isf.map((isf, i) => (
                    <li key={i}>{formatTime(isf?.startTime || '00:00')} - {isf?.value} {t('history.unitMgdl')}</li>
                  ))}</ul>
                ) : <p>{t('history.noIsf')}</p>}
              </div>
            )}
          </div>
        </div>
      </details>
    </li>
  );
};

export const ProfileHistory: React.FC<ProfileHistoryProps> = ({ userId, onSelectProfile }) => {
  const [history, setHistory] = useState<Profile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeProfileWarning, setActiveProfileWarning] = useState<string | null>(null);
  const { formatTime, is24Hour, locale } = useTimeFormat();
  const { t } = useTranslation();

  const [startDate, setStartDate] = useState(() =>
    formatISO(subDays(new Date(), 30), { representation: 'date' })
  );
  const [endDate, setEndDate] = useState(() =>
    formatISO(new Date(), { representation: 'date' })
  );

  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      setError(null);
      setActiveProfileWarning(null);
      try {
        const fromDate = startOfDay(parseISO(startDate));
        const toDate = endOfDay(parseISO(endDate));

        const historyRes = await api.getProfileHistory(
          userId,
          fromDate.toISOString(),
          toDate.toISOString()
        );

        try {
          const profilesRes = await api.listProfiles(userId);
          const activeProfile = profilesRes.data.find(p => (p.status as string) === 'ACTIVE');
          if (activeProfile && !historyRes.data.find(p => p.id === activeProfile.id)) {
            setHistory([activeProfile, ...historyRes.data]);
          } else {
            setHistory(historyRes.data);
          }
        } catch {
          setHistory(historyRes.data);
          setActiveProfileWarning(t('history.activeProfileWarning'));
        }
      } catch (err) {
        console.error(err);
        setError(t('history.error'));
      } finally {
        setLoading(false);
      }
    };

    if (userId && startDate && endDate) {
      if (startDate > endDate) {
        setError(t('history.error'));
        setLoading(false);
        return;
      }
      fetchHistory();
    }
  }, [userId, startDate, endDate, t]);

  return (
    <div className="profile-history">
      <h3>{t('history.title')}</h3>

      <div className="filters" style={{ marginBottom: '1rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
        <div>
          <label htmlFor="start-date" style={{ marginRight: '0.5rem' }}>{t('history.from')}</label>
          <input type="date" id="start-date" lang={locale} value={startDate}
            onChange={(e) => setStartDate(e.target.value)} />
        </div>
        <div>
          <label htmlFor="end-date" style={{ marginRight: '0.5rem' }}>{t('history.to')}</label>
          <input type="date" id="end-date" lang={locale} value={endDate}
            onChange={(e) => setEndDate(e.target.value)} />
        </div>
      </div>

      {loading && <div>{t('history.loading')}</div>}
      {error && <div style={{ color: 'var(--accent-danger)' }}>{error}</div>}
      {activeProfileWarning && <div style={{ color: 'var(--accent-warning)' }}>{activeProfileWarning}</div>}

      {!loading && !error && history.length === 0 ? (
        <p>{t('history.empty')}</p>
      ) : (
        <ul className="history-list">
          {history.map(profile => (
            <ProfileHistoryItem key={profile.id} profile={profile} formatTime={formatTime} is24Hour={is24Hour} onSelectProfile={onSelectProfile} />
          ))}
        </ul>
      )}
    </div>
  );
};
