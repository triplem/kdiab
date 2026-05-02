import { useState, useEffect } from 'react';
import './i18n';
import React from 'react';
import { useAuth } from 'react-oidc-context';
import { useTranslation } from 'react-i18next';
import { setAccessToken, parseRolesFromToken, parseAllowedPatientsFromToken, parseGlucoseUnitFromToken } from './api/tokenProvider';
import { TimelineView } from './features/timeline/TimelineView';
import { AnalyticsView } from './features/analytics/AnalyticsView';
import { ProfilesView } from './features/analytics/ProfilesView';

type Tab = 'timeline' | 'analytics' | 'profiles';

class ErrorBoundary extends React.Component<{children: React.ReactNode}, {hasError: boolean, error: Error | null}> {
  constructor(props: {children: React.ReactNode}) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '2rem', color: 'red' }}>
          <h2>Something went wrong in rendering.</h2>
          <pre>{this.state.error?.message}</pre>
        </div>
      );
    }
    return this.props.children;
  }
}

function App() {
  const auth = useAuth();
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('timeline');
  const [activePatientId, setActivePatientId] = useState<string | null>(null);

  useEffect(() => {
    setAccessToken(auth.user?.access_token ?? null);
    return () => { setAccessToken(null); };
  }, [auth.user?.access_token]);

  const userLocale = auth.user?.profile?.locale as string | undefined;
  useEffect(() => {
    const lang = userLocale ? userLocale.split('-')[0] : navigator.language.split('-')[0];
    document.documentElement.lang = lang;
  }, [userLocale]);

  const ownUserId = auth.user?.profile.sub ?? '';
  const accessToken = auth.user?.access_token ?? '';
  const allRoles = accessToken ? parseRolesFromToken(accessToken) : [];
  const isDoctor = allRoles.includes('DOCTOR');
  const allowedPatients = accessToken ? parseAllowedPatientsFromToken(accessToken) : [];
  const glucoseUnit = accessToken ? parseGlucoseUnitFromToken(accessToken) : 'mg/dL';

  const viewingUserId = activePatientId ?? ownUserId;

  if (auth.isLoading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span style={{ color: '#94a3b8', fontSize: '1.1rem' }}>{t('app.loading')}</span>
      </div>
    );
  }

  if (auth.error) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span style={{ color: '#ef4444' }}>Oops... {auth.error.message}</span>
      </div>
    );
  }

  if (!auth.isAuthenticated || !auth.user) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2rem' }}>
        <div style={{
          background: 'rgba(30, 41, 59, 0.8)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: '20px',
          padding: '3rem 2.5rem',
          maxWidth: '420px',
          width: '100%',
          textAlign: 'center',
          boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
        }}>
          <h1 style={{
            background: 'linear-gradient(135deg, #f8fafc 0%, #38bdf8 100%)',
            WebkitBackgroundClip: 'text',
            backgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            marginBottom: '0.5rem',
            fontSize: '2rem',
          }}>{t('app.title')}</h1>
          <p style={{ color: '#94a3b8', marginBottom: '2.5rem' }}>{t('app.welcome')}</p>
          <button
            className="primary"
            onClick={() => void auth.signinRedirect()}
            style={{ width: '100%', padding: '0.85rem', fontSize: '1rem' }}
          >
            {t('app.login')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <ErrorBoundary>
      <div className="app-container">
        <div className="app-header">
          <h1>{t('app.title')}</h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <span style={{ color: 'var(--text-secondary)' }}>
              {t('app.loggedInAs')} <strong style={{ color: 'var(--text-primary)' }}>
                {auth.user.profile.preferred_username ?? auth.user.profile.name}
              </strong>
            </span>
            <button
              style={{ fontSize: '0.8rem', padding: '0.3rem 0.7rem' }}
              onClick={() => void auth.signoutRedirect({ post_logout_redirect_uri: window.location.origin })}
            >
              {t('app.logout')}
            </button>
          </div>
        </div>

        {isDoctor && allowedPatients.length > 0 && (
          <div style={{ marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <label htmlFor="patient-select" style={{ display: 'inline', marginBottom: 0 }}>
              {t('app.viewingPatient')}
            </label>
            <select
              id="patient-select"
              value={activePatientId ?? ''}
              onChange={e => setActivePatientId(e.target.value || null)}
            >
              <option value="">{t('app.myOwnData')}</option>
              {allowedPatients.map(pid => (
                <option key={pid} value={pid}>Patient {pid.slice(0, 8)}…</option>
              ))}
            </select>
          </div>
        )}

        <nav className="tab-nav">
          <button className={tab === 'timeline' ? 'active-tab' : ''} onClick={() => setTab('timeline')}>
            {t('tabs.timeline')}
          </button>
          <button className={tab === 'analytics' ? 'active-tab' : ''} onClick={() => setTab('analytics')}>
            {t('tabs.analytics')}
          </button>
          <button className={tab === 'profiles' ? 'active-tab' : ''} onClick={() => setTab('profiles')}>
            {t('tabs.profiles')}
          </button>
        </nav>

        <main>
          {tab === 'timeline' && (
            <TimelineView userId={viewingUserId} glucoseUnit={glucoseUnit} />
          )}
          {tab === 'analytics' && (
            <AnalyticsView userId={viewingUserId} glucoseUnit={glucoseUnit} />
          )}
          {tab === 'profiles' && (
            <ProfilesView userId={viewingUserId} />
          )}
        </main>
      </div>
    </ErrorBoundary>
  );
}

export default App;
