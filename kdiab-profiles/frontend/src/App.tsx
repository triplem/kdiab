import { useState } from 'react'
import './App.css'
import './i18n'
import { ProfileList } from './components/ProfileList'
import { ProfileEditor } from './components/ProfileEditor'
import { ProfileHistory } from './components/ProfileHistory'
import { AdminInsulinManager } from './components/AdminInsulinManager'
import { useQueryClient } from '@tanstack/react-query'
import type { Profile } from './api/generated'
import React from 'react'
import { useAuth } from 'react-oidc-context'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { setAccessToken, parseRolesFromToken, parseAllowedPatientsFromToken } from './api/tokenProvider'

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
        <div style={{ padding: '2rem', color: 'var(--accent-danger)' }}>
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

  const [view, setView] = useState<'list' | 'create' | 'history' | 'edit' | 'admin'>('list')
  const [selectedProfile, setSelectedProfile] = useState<Profile | null>(null)
  const [activePatientId, setActivePatientId] = useState<string | null>(null)
  const queryClient = useQueryClient()

  useEffect(() => {
    setAccessToken(auth.user?.access_token ?? null);
    return () => { setAccessToken(null); };
  }, [auth.user?.access_token]);

  const userLocale = auth.user?.profile?.locale as string | undefined;
  useEffect(() => {
    const lang = userLocale ? userLocale.split('-')[0] : navigator.language.split('-')[0];
    document.documentElement.lang = lang;
  }, [userLocale]);

  if (auth.isLoading) {
    return <div>{t('app.loading')}</div>;
  }

  if (auth.error) {
    return <div>Oops... {auth.error.message}</div>;
  }

  if (!auth.isAuthenticated || !auth.user) {
    void auth.signinRedirect();
    return <div>Redirecting to login...</div>;
  }

  const userId = auth.user?.profile?.sub || 'unknown';

  const allRoles = auth.user?.access_token
    ? parseRolesFromToken(auth.user.access_token)
    : [];

  const isAdmin = allRoles.includes('ADMIN');
  const isDoctor = allRoles.includes('DOCTOR');

  const allowedPatients = auth.user?.access_token
    ? parseAllowedPatientsFromToken(auth.user.access_token)
    : [];

  const viewingUserId = activePatientId ?? userId;

  const handleSaved = () => {
    setView('list')
    setSelectedProfile(null)
    queryClient.invalidateQueries({ queryKey: ['profiles', viewingUserId] })
  }

  return (
    <ErrorBoundary>
    <div className="app-container">
      <div className="app-header">
        <h1>{t('app.title')}</h1>
      </div>

      <div className="user-control" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <span>{t('app.loggedInAs')} <strong>{auth.user?.profile.preferred_username || auth.user?.profile.name}</strong></span>
          <button style={{ marginLeft: '1rem', fontSize: '0.8rem', padding: '0.2rem 0.5rem' }} onClick={() => void auth.signoutRedirect({ post_logout_redirect_uri: window.location.origin })}>{t('app.logout')}</button>
        </div>
      </div>

      {isDoctor && allowedPatients.length > 0 && (
        <div className="patient-selector">
          <label htmlFor="patient-select" style={{ marginRight: '0.5rem', fontWeight: 500 }}>{t('app.viewingPatient')}</label>
          <select
            id="patient-select"
            value={activePatientId ?? ''}
            onChange={e => {
              setActivePatientId(e.target.value || null);
              setSelectedProfile(null);
              setView('list');
            }}
          >
            <option value="">{t('app.myOwnProfiles')}</option>
            {allowedPatients.map(pid => (
              <option key={pid} value={pid}>{pid}</option>
            ))}
          </select>
        </div>
      )}

      <nav className="app-nav">
        <button onClick={() => { setSelectedProfile(null); setView('list'); }} disabled={view === 'list'}>
          {activePatientId ? t('app.patientProfiles') : t('app.myProfiles')}
        </button>
        {!activePatientId && (
          <button onClick={() => { setSelectedProfile(null); setView('create'); }} disabled={view === 'create'}>{t('app.createProfile')}</button>
        )}
        <button onClick={() => { setSelectedProfile(null); setView('history'); }} disabled={view === 'history'}>{t('app.profileHistory')}</button>
        {isDoctor && activePatientId && (
          <button onClick={() => { setSelectedProfile(null); setView('create'); }} disabled={view === 'create'}>{t('app.proposeProfile')}</button>
        )}
        {isAdmin && (
          <button onClick={() => { setSelectedProfile(null); setView('admin'); }} disabled={view === 'admin'}>{t('app.manageInsulins')}</button>
        )}
      </nav>

      <main>
        {view === 'list' && <ProfileList userId={viewingUserId} readOnly={!!activePatientId} onSelectProfile={(p) => { setSelectedProfile(p); setView('edit'); }} />}
        {view === 'create' && <ProfileEditor key={`create-${viewingUserId}`} userId={viewingUserId} onProfileSaved={handleSaved} />}
        {view === 'edit' && selectedProfile && <ProfileEditor key={`edit-${selectedProfile.id}`} userId={viewingUserId} initialProfile={selectedProfile} onProfileSaved={handleSaved} readOnly={!!activePatientId} />}
        {view === 'history' && <ProfileHistory userId={viewingUserId} onSelectProfile={(p) => { setSelectedProfile(p); setView('edit'); }} />}
        {view === 'admin' && <AdminInsulinManager />}
      </main>
    </div>
    </ErrorBoundary>
  )
}

export default App
