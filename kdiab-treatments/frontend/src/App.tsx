import { useState } from 'react'
import './App.css'
import './i18n'
import { TreatmentList } from './features/treatments/TreatmentList'
import { AddTreatmentModal, TreatmentInput } from './features/treatments/AddTreatmentModal'
import React, { useEffect } from 'react'
import { useAuth } from 'react-oidc-context'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from './api/client'
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
  const auth = useAuth()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [isModalOpen, setModalOpen] = useState(false)
  const [activePatientId, setActivePatientId] = useState<string | null>(null)
  const [createError, setCreateError] = useState<string | null>(null)

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

  const viewingUserId = activePatientId ?? ownUserId;
  const canDelete = true;

  const createMutation = useMutation({
    mutationFn: (input: TreatmentInput) => api.createTreatment(viewingUserId, {
      treatedAt: input.treatedAt,
      type: input.type as Parameters<typeof api.createTreatment>[1]['type'],
      data: input.data,
      ...(input.notes && { notes: input.notes }),
    }),
    onSuccess: () => {
      setModalOpen(false);
      setCreateError(null);
      void queryClient.invalidateQueries({ queryKey: ['treatments', viewingUserId] });
    },
    onError: (err: unknown) => {
      const status = (err as { response?: { status?: number } }).response?.status;
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string };
      setCreateError(
        status === 401
          ? t('modal.sessionExpired')
          : (apiErr?.response?.data?.message ?? apiErr?.message ?? t('modal.saveError'))
      );
    },
  });

  const handleSaved = (input: TreatmentInput) => {
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
            <option value="">{t('app.myOwnTreatments')}</option>
            {allowedPatients.map(pid => (
              <option key={pid} value={pid}>Patient {pid.substring(0, 8)}…</option>
            ))}
          </select>
        </div>
      )}

      <nav className="app-nav">
        <button onClick={() => setModalOpen(true)}>{t('app.addTreatment')}</button>
      </nav>

      <main>
        <TreatmentList
          userId={viewingUserId}
          canDelete={canDelete}
        />
        <AddTreatmentModal
          isOpen={isModalOpen}
          onClose={() => { setModalOpen(false); setCreateError(null); }}
          onSave={handleSaved}
          isSaving={createMutation.isPending}
          error={createError}
        />
      </main>
    </div>
    </ErrorBoundary>
  )
}

export default App
