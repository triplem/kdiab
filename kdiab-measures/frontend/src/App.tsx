import { useState } from 'react'
import './App.css'
import './i18n'
import { MeasureList } from './features/measures/MeasureList'
import { AddMeasureModal, MeasureInput } from './features/measures/AddMeasureModal'
import React, { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './api/client'
import { setAccessToken, parseRolesFromToken, parseAllowedPatientsFromToken, parseGlucoseUnitFromToken, parseWeightUnitFromToken } from './api/tokenProvider'

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
  const auth = useAuth()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [isModalOpen, setModalOpen] = useState(false)
  const [activePatientId, setActivePatientId] = useState<string | null>(null)

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
  const isAdmin = allRoles.includes('ADMIN');
  const isDoctor = allRoles.includes('DOCTOR');
  const allowedPatients = accessToken ? parseAllowedPatientsFromToken(accessToken) : [];
  const glucoseUnit = accessToken ? parseGlucoseUnitFromToken(accessToken) : 'mg/dL';
  const weightUnit = accessToken ? parseWeightUnitFromToken(accessToken) : 'kg';

  const viewingUserId = activePatientId ?? ownUserId;
  const canArchive = true;
  const canDelete = isAdmin || isDoctor;

  const createMutation = useMutation({
    mutationFn: (input: MeasureInput) => api.createMeasure(viewingUserId, {
      measuredAt: input.measuredAt,
      type: input.type as Parameters<typeof api.createMeasure>[1]['type'],
      source: input.source as Parameters<typeof api.createMeasure>[1]['source'],
      data: input.data,
    }),
    onSuccess: () => {
      setModalOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['measures', viewingUserId] });
    },
  });

  const handleSaved = (input: MeasureInput) => {
    createMutation.mutate(input);
  };

  if (auth.isLoading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span style={{ color: 'var(--text-secondary)', fontSize: '1.1rem' }}>{t('app.loading')}</span>
      </div>
    );
  }
  if (auth.error) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span style={{ color: 'var(--accent-danger)' }}>Oops... {auth.error.message}</span>
      </div>
    );
  }
  if (!auth.isAuthenticated || !auth.user) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '2rem' }}>
        <div style={{
          background: 'var(--surface-color)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          border: '1px solid var(--border-color)',
          borderRadius: '20px',
          padding: '3rem 2.5rem',
          maxWidth: '420px',
          width: '100%',
          textAlign: 'center',
          boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
        }}>
          <h1 style={{
            background: 'linear-gradient(135deg, var(--text-primary) 0%, var(--accent-primary) 100%)',
            WebkitBackgroundClip: 'text',
            backgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            marginBottom: '0.5rem',
            fontSize: '2rem',
          }}>{t('app.title')}</h1>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '2.5rem', fontSize: '1rem' }}>{t('app.welcome')}</p>
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
      </div>

      <div className="user-control" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <span>{t('app.loggedInAs')} <strong>{auth.user.profile.preferred_username || auth.user.profile.name}</strong></span>
          <button style={{ marginLeft: '1rem', fontSize: '0.8rem', padding: '0.2rem 0.5rem' }} onClick={() => void auth.signoutRedirect({ post_logout_redirect_uri: window.location.origin })}>{t('app.logout')}</button>
        </div>
      </div>

      {isDoctor && allowedPatients.length > 0 && (
        <div className="patient-selector">
          <label htmlFor="patient-select" style={{ marginRight: '0.5rem', fontWeight: 500 }}>{t('app.viewingPatient')}</label>
          <select
            id="patient-select"
            value={activePatientId ?? ''}
            onChange={e => { setActivePatientId(e.target.value || null); setModalOpen(false); }}
          >
            <option value="">{t('app.myOwnMeasures')}</option>
            {allowedPatients.map(pid => (
              <option key={pid} value={pid}>Patient {pid.substring(0, 8)}…</option>
            ))}
          </select>
        </div>
      )}

      <nav className="app-nav">
        <button onClick={() => setModalOpen(true)}>{t('app.addMeasure')}</button>
      </nav>

      <main>
        <MeasureList
          userId={viewingUserId}
          canArchive={canArchive}
          canDelete={canDelete}
        />
        <AddMeasureModal
          isOpen={isModalOpen}
          onClose={() => setModalOpen(false)}
          onSave={handleSaved}
          glucoseUnit={glucoseUnit}
          weightUnit={weightUnit}
          isSaving={createMutation.isPending}
        />
      </main>
    </div>
    </ErrorBoundary>
  )
}

export default App
