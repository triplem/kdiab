import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { useTranslation } from 'react-i18next'
import { Toaster, toast } from 'sonner'
import {
  setAccessToken,
  parseRolesFromToken,
  parseAllowedPatientsFromToken,
  parseAllowedPatientNamesFromToken,
} from './api/tokenProvider'
import { usersApi } from './api/usersApi'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { PatientBanner } from './components/PatientBanner'
import { MeasureList } from './features/measures/MeasureList'
import { AddMeasureModal } from './features/measures/AddMeasureModal'
import { TreatmentList } from './features/treatments/TreatmentList'
import { AddTreatmentModal } from './features/treatments/AddTreatmentModal'
import { QuickLogButtons } from './features/treatments/QuickLogButtons'
import { ProfileList } from './features/profiles/ProfileList'
import { ProfileEditor } from './features/profiles/ProfileEditor'
import { ProfileHistory } from './features/profiles/ProfileHistory'
import { AdminInsulinManager } from './features/profiles/AdminInsulinManager'
import { DashboardView } from './features/dashboard/DashboardView'
import { AnalyticsView } from './features/analytics/AnalyticsView'
import { ReportPage } from './features/report/ReportPage'
import { FoodDatabase } from './features/carbs/FoodDatabase'
import { DoseCalculator } from './features/calc/DoseCalculator'
import { UserSettings } from './features/users/UserSettings'
import { AdminUserList } from './features/users/AdminUserList'
import { AdminDoctorPatients } from './features/users/AdminDoctorPatients'
import { DoctorInvitations } from './features/users/DoctorInvitations'
import { PatientInvitations } from './features/users/PatientInvitations'
import { measuresApi } from './api/measuresApi'
import { treatmentsApi } from './api/treatmentsApi'
import { useQueryClient } from '@tanstack/react-query'
import type { Profile } from './api/profilesApi'
import { useProposedProfileCount } from './features/profiles/useProposedProfileCount'
import { ProposedBadge } from './features/profiles/ProposedBadge'

type Tab = 'dashboard' | 'measures' | 'treatments' | 'profiles' | 'analytics' | 'report' | 'carbs' | 'calc' | 'settings' | 'admin-users' | 'admin-doctors' | 'preferences' | 'doctor-invitations' | 'patient-invitations'

// Tabs that require a patient to be selected when the user is a doctor.
// Doctors without a selected patient should not see these tabs.
const PATIENT_CONTEXT_TABS: Tab[] = ['dashboard', 'measures', 'treatments', 'profiles', 'analytics', 'report', 'carbs', 'calc']

// Default OIDC client_id for the kdiab OIDC configuration (matches nginx proxy and Keycloak realm)
const DEFAULT_OIDC_CLIENT_ID = 'kdiab-analyze-frontend'

function buildKeycloakRegistrationUrl(): string | null {
  const env = import.meta.env as Record<string, string>
  const authority = (env['VITE_OIDC_AUTHORITY'] ?? '').replace(/\/$/, '')
  if (!authority) return null
  const clientId = env['VITE_OIDC_CLIENT_ID'] ?? DEFAULT_OIDC_CLIENT_ID
  return `${authority}/protocol/openid-connect/registrations?client_id=${clientId}&response_type=code`
}

export default function App() {
  const auth = useAuth()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')

  // Auth-derived state
  const [roles, setRoles] = useState<string[]>([])
  const [allowedPatients, setAllowedPatients] = useState<string[]>([])
  const [patientNames, setPatientNames] = useState<Map<string, string>>(new Map())
  const [glucoseUnit, setGlucoseUnit] = useState('mg/dL')
  const [weightUnit, setWeightUnit] = useState('kg')

  // Doctor patient switching
  const [activePatientId, setActivePatientId] = useState<string | null>(null)
  const [patientGlucoseUnit, setPatientGlucoseUnit] = useState<string>('mg/dL')

  // Modal state
  const [showAddMeasure, setShowAddMeasure] = useState(false)
  const [measureSaving, setMeasureSaving] = useState(false)
  const [measureError, setMeasureError] = useState<string | null>(null)

  const [showAddTreatment, setShowAddTreatment] = useState(false)
  const [treatmentSaving, setTreatmentSaving] = useState(false)
  const [treatmentError, setTreatmentError] = useState<string | null>(null)

  // Profile editor
  const [editingProfile, setEditingProfile] = useState<Profile | null>(null)
  const [showProfileEditor, setShowProfileEditor] = useState(false)

  const [loginError, setLoginError] = useState<string | null>(null)

  useEffect(() => {
    const handler = () => { void auth.signinRedirect() }
    window.addEventListener('auth:unauthorized', handler)
    return () => window.removeEventListener('auth:unauthorized', handler)
  }, [auth])

  useEffect(() => {
    if (auth.user?.access_token) {
      const token = auth.user.access_token
      setAccessToken(token)
      setRoles(parseRolesFromToken(token))
      const patients = parseAllowedPatientsFromToken(token)
      setAllowedPatients(patients)
      const names = parseAllowedPatientNamesFromToken(token)
      setPatientNames(new Map(patients.map((id, i) => [id, names[i] ?? id])))

      // Fetch glucose/weight units from users service (DB is the source of truth)
      void usersApi.getMe().then(res => {
        setGlucoseUnit(res.data.settings?.units?.glucoseUnit ?? 'mg/dL')
        setWeightUnit(res.data.settings?.units?.weightUnit ?? 'kg')
      }).catch(() => {
        // Keep defaults on failure — they are already set to mg/dL / kg
      })

      // Set locale from Keycloak profile
      const locale = auth.user.profile?.locale as string | undefined
      if (locale) document.documentElement.lang = locale
    } else {
      setAccessToken(null)
    }
  }, [auth.user])

  const isPatient = roles.includes('PATIENT')
  const isDoctor = roles.includes('DOCTOR')
  const isAdmin = roles.includes('ADMIN')

  // Tabs that are valid for admins — used to decide whether to redirect on login.
  const ADMIN_TABS: Tab[] = ['admin-users', 'admin-doctors', 'preferences']

  // Reset active tab to 'admin-users' when the user is an admin so they don't
  // land on a tab that is hidden from them (e.g. 'dashboard'). Use a functional
  // update so we only redirect if the current tab is not already an admin tab —
  // this preserves navigation when the user manually switches to Preferences.
  useEffect(() => {
    if (isAdmin) {
      setActiveTab((prev) => (ADMIN_TABS.includes(prev) ? prev : 'admin-users'))
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin])

  // When a doctor (without a patient role) has no patient selected and is on a
  // patient-context tab, fall back to doctor-invitations so they are not stranded.
  useEffect(() => {
    if (isDoctor && !isPatient && activePatientId === null) {
      setActiveTab((prev) => (PATIENT_CONTEXT_TABS.includes(prev) ? 'doctor-invitations' : prev))
    }
  }, [isDoctor, isPatient, activePatientId])

  const ownUserId = auth.user?.profile?.sub ?? ''
  const viewingUserId = activePatientId ?? ownUserId
  const isDoctorViewingPatient = isDoctor && activePatientId !== null

  // Fetch patient glucose unit when doctor switches patients
  useEffect(() => {
    if (!activePatientId || !isDoctor) {
      setPatientGlucoseUnit('mg/dL')
      return
    }
    const controller = new AbortController()
    void usersApi.getUser(activePatientId, { signal: controller.signal })
      .then(res => {
        setPatientGlucoseUnit(res.data.settings?.units?.glucoseUnit ?? 'mg/dL')
      })
      .catch((err: unknown) => {
        // Ignore cancellation — a newer request is already in flight
        if ((err as { name?: string }).name !== 'CanceledError') {
          setPatientGlucoseUnit('mg/dL')
        }
      })
    return () => controller.abort()
  }, [activePatientId, isDoctor])

  const activeGlucoseUnit = isDoctorViewingPatient ? patientGlucoseUnit : glucoseUnit
  const proposedProfileCount = useProposedProfileCount(viewingUserId)

  const handleSaveMeasure = async (measure: {
    type: string
    measuredAt: string
    source: string
    data: Record<string, unknown>
  }) => {
    setMeasureSaving(true)
    setMeasureError(null)
    try {
      await measuresApi.createMeasure(viewingUserId, measure)
      setShowAddMeasure(false)
      void queryClient.invalidateQueries({ queryKey: ['measures', viewingUserId] })
      toast.success(t('modal.saveSuccess'))
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setMeasureError(
        apiErr?.response?.data?.message ?? apiErr?.message ?? t('modal.saveError'),
      )
    } finally {
      setMeasureSaving(false)
    }
  }

  const handleSaveTreatment = async (treatment: {
    type: string
    treatedAt: string
    data: Record<string, unknown>
    notes?: string
  }) => {
    setTreatmentSaving(true)
    setTreatmentError(null)
    try {
      await treatmentsApi.createTreatment(viewingUserId, treatment)
      setShowAddTreatment(false)
      void queryClient.invalidateQueries({ queryKey: ['treatments', viewingUserId] })
      toast.success(t('treatmentModal.saveSuccess'))
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setTreatmentError(
        apiErr?.response?.data?.message ?? apiErr?.message ?? t('treatmentModal.saveError'),
      )
    } finally {
      setTreatmentSaving(false)
    }
  }

  const handleSaveMeal = async (meal: {
    treatedAt: string
    insulinUnits: number
    carbs: number
  }) => {
    setTreatmentSaving(true)
    setTreatmentError(null)
    try {
      const bolusResponse = await treatmentsApi.createTreatment(viewingUserId, {
        type: 'BOLUS',
        treatedAt: meal.treatedAt,
        data: { insulin: meal.insulinUnits },
      })
      const bolusId = bolusResponse.data.id
      try {
        await treatmentsApi.createTreatment(viewingUserId, {
          type: 'CARBS',
          treatedAt: meal.treatedAt,
          data: { carbs: meal.carbs },
        })
      } catch (carbsErr: unknown) {
        // Compensate: archive the orphaned BOLUS (archiveTreatments is available to all roles; deleteTreatments is DOCTOR/ADMIN-only)
        try {
          await treatmentsApi.archiveTreatments(viewingUserId, { treatmentIds: [bolusId] })
        } catch {
          // best-effort — original CARBS error is still re-thrown below
        }
        throw carbsErr
      }
      setShowAddTreatment(false)
      void queryClient.invalidateQueries({ queryKey: ['treatments', viewingUserId] })
      toast.success(t('treatmentModal.saveSuccess'))
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setTreatmentError(
        apiErr?.response?.data?.message ?? apiErr?.message ?? t('treatmentModal.saveError'),
      )
    } finally {
      setTreatmentSaving(false)
    }
  }

  if (auth.isLoading) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
        {t('app.loading')}
      </div>
    )
  }

  if (!auth.isAuthenticated) {
    const oidcError = auth.error?.message ?? loginError
    const registrationUrl = buildKeycloakRegistrationUrl()
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h1>{t('app.title')}</h1>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem' }}>{t('app.welcome')}</p>
        <button
          className="primary"
          onClick={() => {
            setLoginError(null)
            auth.signinRedirect().catch((err: unknown) => {
              setLoginError(err instanceof Error ? err.message : t('app.loginError'))
            })
          }}
        >
          {t('app.login')}
        </button>
        {oidcError && (
          <p style={{ color: 'var(--color-error, #c0392b)', marginTop: '1rem', fontSize: '0.875rem' }}>
            {t('app.loginError')}: {oidcError}
          </p>
        )}
        {registrationUrl && (
          <p style={{ marginTop: '1rem' }}>
            <a href={registrationUrl} className="btn outline" data-testid="register-link">
              {t('app.register')}
            </a>
          </p>
        )}
      </div>
    )
  }

  const foodDatabaseEnabled = (import.meta.env as Record<string, string>)['VITE_FOOD_DATABASE_ENABLED'] === 'true'

  const tabs: { key: Tab; label: string; roles?: string[] }[] = [
    { key: 'dashboard', label: t('nav.dashboard'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'measures', label: t('nav.measures'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'treatments', label: t('nav.treatments'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'profiles', label: t('nav.profiles'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'analytics', label: t('nav.analytics'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'report', label: t('nav.report'), roles: ['PATIENT', 'DOCTOR'] },
    ...(foodDatabaseEnabled ? [{ key: 'carbs' as Tab, label: t('nav.foodDatabase'), roles: ['PATIENT', 'DOCTOR'] }] : []),
    { key: 'calc', label: t('nav.doseCalculator'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'settings', label: t('nav.settings'), roles: ['PATIENT', 'DOCTOR'] },
    { key: 'doctor-invitations', label: t('nav.doctorInvitations'), roles: ['DOCTOR'] },
    { key: 'patient-invitations', label: t('nav.patientInvitations'), roles: ['PATIENT'] },
    { key: 'admin-users', label: t('nav.adminUsers'), roles: ['ADMIN'] },
    { key: 'admin-doctors', label: t('nav.adminDoctors'), roles: ['ADMIN'] },
    { key: 'preferences', label: t('nav.preferences') },
  ]

  const visibleTabs = tabs.filter((tab) => {
    if (!tab.roles?.length || tab.roles.some((r) => roles.includes(r))) {
      // Doctors without a selected patient must not see patient-context tabs
      if (isDoctor && !isPatient && activePatientId === null && PATIENT_CONTEXT_TABS.includes(tab.key)) {
        return false
      }
      return true
    }
    return false
  })

  const renderTabContent = () => {
    if (isDoctor && !isPatient && activePatientId === null && PATIENT_CONTEXT_TABS.includes(activeTab)) {
      return (
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
          <p>{t('doctor.selectPatientPrompt')}</p>
        </div>
      )
    }

    switch (activeTab) {
      case 'dashboard':
        return <DashboardView userId={viewingUserId} glucoseUnit={activeGlucoseUnit} />

      case 'measures':
        return (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div />
              {!isDoctorViewingPatient && (
                <button
                  className="primary"
                  onClick={() => setShowAddMeasure(true)}
                  style={{ padding: '0.5rem 1.2rem' }}
                >
                  + {t('nav.measures')}
                </button>
              )}
            </div>
            <MeasureList
              userId={viewingUserId}
              glucoseUnit={activeGlucoseUnit}
              canArchive={isPatient || isAdmin}
              canDelete={isAdmin}
            />
            <AddMeasureModal
              isOpen={showAddMeasure}
              onClose={() => { setShowAddMeasure(false); setMeasureError(null) }}
              onSave={(m) => void handleSaveMeasure(m)}
              glucoseUnit={activeGlucoseUnit}
              weightUnit={weightUnit}
              isSaving={measureSaving}
              error={measureError}
            />
          </>
        )

      case 'treatments':
        return (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
              {!isDoctorViewingPatient ? (
                <QuickLogButtons
                  userId={viewingUserId}
                  onLogged={() => void queryClient.invalidateQueries({ queryKey: ['treatments', viewingUserId] })}
                />
              ) : (
                <div />
              )}
              {!isDoctorViewingPatient && (
                <button
                  className="primary"
                  onClick={() => setShowAddTreatment(true)}
                  style={{ padding: '0.5rem 1.2rem' }}
                >
                  + {t('nav.treatments')}
                </button>
              )}
            </div>
            <TreatmentList
              userId={viewingUserId}
              canArchive={true}
              canDelete={isAdmin}
            />
            <AddTreatmentModal
              isOpen={showAddTreatment}
              onClose={() => { setShowAddTreatment(false); setTreatmentError(null) }}
              onSave={(tr) => void handleSaveTreatment(tr)}
              onSaveMeal={(meal) => void handleSaveMeal(meal)}
              isSaving={treatmentSaving}
              error={treatmentError}
              userId={viewingUserId}
              glucoseUnit={glucoseUnit}
            />
          </>
        )

      case 'profiles':
        return (
          <>
            {showProfileEditor ? (
              <>
                <button
                  className="btn outline"
                  onClick={() => { setShowProfileEditor(false); setEditingProfile(null) }}
                  style={{ marginBottom: '1rem' }}
                >
                  ← Back to profiles
                </button>
                <ProfileEditor
                  userId={viewingUserId}
                  {...(editingProfile !== null && { initialProfile: editingProfile })}
                  onProfileSaved={() => { setShowProfileEditor(false); setEditingProfile(null) }}
                  readOnly={isDoctorViewingPatient}
                  isDoctor={isDoctor}
                  glucoseUnit={activeGlucoseUnit}
                />
              </>
            ) : (
              <>
                <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem' }}>
                  {!isDoctorViewingPatient && (
                    <button
                      className="primary"
                      onClick={() => { setEditingProfile(null); setShowProfileEditor(true) }}
                    >
                      + Create Profile
                    </button>
                  )}
                </div>
                <ProfileList
                  userId={viewingUserId}
                  onSelectProfile={(p) => { setEditingProfile(p); setShowProfileEditor(true) }}
                  readOnly={isDoctorViewingPatient}
                  glucoseUnit={activeGlucoseUnit}
                />
                {isAdmin && <AdminInsulinManager />}
                <hr style={{ margin: '2rem 0 1rem', borderColor: 'var(--border)' }} />
                <ProfileHistory userId={viewingUserId} />
              </>
            )}
          </>
        )

      case 'analytics': {
        const analyticsPatientName = isDoctorViewingPatient
          ? activePatientName
          : (auth.user?.profile?.preferred_username as string | undefined)
        return (
          <AnalyticsView
            userId={viewingUserId}
            glucoseUnit={activeGlucoseUnit}
            {...(analyticsPatientName !== undefined && { patientName: analyticsPatientName })}
          />
        )
      }

      case 'report': {
        const reportPatientName = isDoctorViewingPatient
          ? activePatientName
          : (auth.user?.profile?.preferred_username as string | undefined)
        return (
          <ReportPage
            userId={viewingUserId}
            glucoseUnit={activeGlucoseUnit}
            {...(reportPatientName !== undefined && { patientName: reportPatientName })}
          />
        )
      }

      case 'carbs':
        return <FoodDatabase userId={viewingUserId} />

      case 'calc':
        return <DoseCalculator userId={viewingUserId} glucoseUnit={activeGlucoseUnit} />

      case 'settings':
        return <UserSettings />

      case 'doctor-invitations':
        return isDoctor ? <DoctorInvitations doctorId={ownUserId} /> : null

      case 'patient-invitations':
        return isPatient ? <PatientInvitations patientId={ownUserId} /> : null

      case 'admin-users':
        return isAdmin ? <AdminUserList /> : null

      case 'admin-doctors':
        return isAdmin ? <AdminDoctorPatients /> : null

      case 'preferences':
        return <UserSettings localeOnly={isAdmin} />
    }
  }

  const activePatientName = activePatientId !== null ? patientNames.get(activePatientId) : undefined

  return (
    <>
      <Toaster position="top-right" richColors />
      {isDoctorViewingPatient && (
        <PatientBanner
          patientId={activePatientId!}
          {...(activePatientName !== undefined && { patientName: activePatientName })}
          onReturnToOwn={() => setActivePatientId(null)}
        />
      )}

      <div className="app-container" style={{ padding: '1.5rem' }}>
        <header className="app-header">
          <h1 style={{ fontSize: '1.5rem', margin: 0 }}>{t('app.title')}</h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            {/* Doctor: patient selector */}
            {isDoctor && allowedPatients.length > 0 && (
              <div className="patient-selector">
                <label htmlFor="patient-select" style={{ margin: 0, fontSize: '0.85rem' }}>
                  {t('app.viewingPatient')}
                </label>
                <select
                  id="patient-select"
                  value={activePatientId ?? ''}
                  onChange={(e) => setActivePatientId(e.target.value || null)}
                  style={{ fontSize: '0.85rem', padding: '0.3rem 0.5rem' }}
                >
                  <option value="">{t('app.myOwnData')}</option>
                  {allowedPatients.map((pid) => (
                    <option key={pid} value={pid}>
                      {patientNames.get(pid) ?? pid}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {/* Glucose unit toggle when doctor is viewing a patient */}
            {isDoctorViewingPatient && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem' }}>
                <span style={{ color: 'var(--text-secondary)' }}>{t('app.glucoseUnit', { defaultValue: 'Unit:' })}</span>
                {(['mg/dL', 'mmol/L'] as const).map((unit) => (
                  <button
                    key={unit}
                    className={`btn outline${patientGlucoseUnit === unit ? ' active-tab' : ''}`}
                    style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
                    onClick={() => setPatientGlucoseUnit(unit)}
                  >
                    {unit}
                  </button>
                ))}
              </div>
            )}
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
              {t('app.loggedInAs')} <strong>{auth.user?.profile?.preferred_username as string | undefined}</strong>
            </span>
            <LanguageSwitcher />
            <button
              className="btn outline"
              data-testid="logout-btn"
              onClick={() => void auth.signoutRedirect({ post_logout_redirect_uri: window.location.origin })}
              style={{ fontSize: '0.85rem', padding: '0.3rem 0.75rem' }}
            >
              {t('app.logout')}
            </button>
          </div>
        </header>

        <nav className="tab-nav" aria-label="Main navigation">
          {visibleTabs.map(({ key, label }) => (
            <button
              key={key}
              className={activeTab === key ? 'active-tab' : ''}
              aria-current={activeTab === key ? 'page' : undefined}
              onClick={() => setActiveTab(key)}
            >
              {label}
              {key === 'profiles' && <ProposedBadge count={proposedProfileCount} />}
            </button>
          ))}
        </nav>

        <main>{renderTabContent()}</main>
      </div>
    </>
  )
}
