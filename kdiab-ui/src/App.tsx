import { useEffect, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import { useTranslation } from 'react-i18next'
import {
  setAccessToken,
  parseRolesFromToken,
  parseAllowedPatientsFromToken,
  parseAllowedPatientNamesFromToken,
  parseGlucoseUnitFromToken,
  parseWeightUnitFromToken,
} from './api/tokenProvider'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { PatientBanner } from './components/PatientBanner'
import { MeasureList } from './features/measures/MeasureList'
import { AddMeasureModal } from './features/measures/AddMeasureModal'
import { TreatmentList } from './features/treatments/TreatmentList'
import { AddTreatmentModal } from './features/treatments/AddTreatmentModal'
import { ProfileList } from './features/profiles/ProfileList'
import { ProfileEditor } from './features/profiles/ProfileEditor'
import { AdminInsulinManager } from './features/profiles/AdminInsulinManager'
import { TimelineView } from './features/timeline/TimelineView'
import { AnalyticsView } from './features/analytics/AnalyticsView'
import { ProfilesView } from './features/analytics/ProfilesView'
import { FoodDatabase } from './features/carbs/FoodDatabase'
import { DoseCalculator } from './features/calc/DoseCalculator'
import { measuresApi } from './api/measuresApi'
import { treatmentsApi } from './api/treatmentsApi'
import { useQueryClient } from '@tanstack/react-query'
import type { Profile } from './api/profilesApi'

type Tab = 'measures' | 'treatments' | 'profiles' | 'timeline' | 'analytics' | 'carbs' | 'calc'

export default function App() {
  const auth = useAuth()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<Tab>('measures')

  // Auth-derived state
  const [roles, setRoles] = useState<string[]>([])
  const [allowedPatients, setAllowedPatients] = useState<string[]>([])
  const [patientNames, setPatientNames] = useState<Map<string, string>>(new Map())
  const [glucoseUnit, setGlucoseUnit] = useState('mg/dL')
  const [weightUnit, setWeightUnit] = useState('kg')

  // Doctor patient switching
  const [activePatientId, setActivePatientId] = useState<string | null>(null)

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

  useEffect(() => {
    if (auth.user?.access_token) {
      const token = auth.user.access_token
      setAccessToken(token)
      setRoles(parseRolesFromToken(token))
      const patients = parseAllowedPatientsFromToken(token)
      setAllowedPatients(patients)
      const names = parseAllowedPatientNamesFromToken(token)
      setPatientNames(new Map(patients.map((id, i) => [id, names[i] ?? id])))
      setGlucoseUnit(parseGlucoseUnitFromToken(token))
      setWeightUnit(parseWeightUnitFromToken(token))

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

  const ownUserId = auth.user?.profile?.sub ?? ''
  const viewingUserId = activePatientId ?? ownUserId
  const isDoctorViewingPatient = isDoctor && activePatientId !== null

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
      await treatmentsApi.createTreatment(viewingUserId, {
        type: 'BOLUS',
        treatedAt: meal.treatedAt,
        data: { insulin: meal.insulinUnits },
      })
      await treatmentsApi.createTreatment(viewingUserId, {
        type: 'CARBS',
        treatedAt: meal.treatedAt,
        data: { carbs: meal.carbs },
      })
      setShowAddTreatment(false)
      void queryClient.invalidateQueries({ queryKey: ['treatments', viewingUserId] })
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
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h1>{t('app.title')}</h1>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem' }}>{t('app.welcome')}</p>
        <button className="primary" onClick={() => void auth.signinRedirect()}>
          {t('app.login')}
        </button>
      </div>
    )
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'measures', label: t('nav.measures') },
    { key: 'treatments', label: t('nav.treatments') },
    { key: 'profiles', label: t('nav.profiles') },
    { key: 'timeline', label: t('nav.timeline') },
    { key: 'analytics', label: t('nav.analytics') },
    { key: 'carbs', label: t('nav.foodDatabase') },
    { key: 'calc', label: t('nav.doseCalculator') },
  ]

  const renderTabContent = () => {
    switch (activeTab) {
      case 'measures':
        return (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div />
              <button
                className="primary"
                onClick={() => setShowAddMeasure(true)}
                style={{ padding: '0.5rem 1.2rem' }}
              >
                + {t('nav.measures')}
              </button>
            </div>
            <MeasureList
              userId={viewingUserId}
              glucoseUnit={glucoseUnit}
              canArchive={isPatient || isAdmin}
              canDelete={isAdmin || isDoctor}
            />
            <AddMeasureModal
              isOpen={showAddMeasure}
              onClose={() => { setShowAddMeasure(false); setMeasureError(null) }}
              onSave={(m) => void handleSaveMeasure(m)}
              glucoseUnit={glucoseUnit}
              weightUnit={weightUnit}
              isSaving={measureSaving}
              error={measureError}
            />
          </>
        )

      case 'treatments':
        return (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div />
              <button
                className="primary"
                onClick={() => setShowAddTreatment(true)}
                style={{ padding: '0.5rem 1.2rem' }}
              >
                + {t('nav.treatments')}
              </button>
            </div>
            <TreatmentList
              userId={viewingUserId}
              canArchive={true}
              canDelete={isAdmin || isDoctor}
            />
            <AddTreatmentModal
              isOpen={showAddTreatment}
              onClose={() => { setShowAddTreatment(false); setTreatmentError(null) }}
              onSave={(tr) => void handleSaveTreatment(tr)}
              onSaveMeal={(meal) => void handleSaveMeal(meal)}
              isSaving={treatmentSaving}
              error={treatmentError}
              userId={viewingUserId}
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
                  initialProfile={editingProfile ?? undefined}
                  onProfileSaved={() => { setShowProfileEditor(false); setEditingProfile(null) }}
                  readOnly={isDoctorViewingPatient}
                  isDoctor={isDoctor}
                  glucoseUnit={glucoseUnit}
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
                  glucoseUnit={glucoseUnit}
                />
                {isAdmin && <AdminInsulinManager />}
              </>
            )}
          </>
        )

      case 'timeline':
        return <TimelineView userId={viewingUserId} glucoseUnit={glucoseUnit} />

      case 'analytics':
        return (
          <>
            <AnalyticsView userId={viewingUserId} glucoseUnit={glucoseUnit} />
            <ProfilesView userId={viewingUserId} />
          </>
        )

      case 'carbs':
        return <FoodDatabase userId={viewingUserId} />

      case 'calc':
        return <DoseCalculator userId={viewingUserId} glucoseUnit={glucoseUnit} />
    }
  }

  return (
    <>
      {isDoctorViewingPatient && (
        <PatientBanner
          patientId={activePatientId!}
          patientName={patientNames.get(activePatientId!)}
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
            <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
              {t('app.loggedInAs')} <strong>{auth.user?.profile?.preferred_username as string | undefined}</strong>
            </span>
            <LanguageSwitcher />
            <button
              className="btn outline"
              onClick={() => void auth.signoutRedirect()}
              style={{ fontSize: '0.85rem', padding: '0.3rem 0.75rem' }}
            >
              {t('app.logout')}
            </button>
          </div>
        </header>

        <nav className="tab-nav" aria-label="Main navigation">
          {tabs.map(({ key, label }) => (
            <button
              key={key}
              className={activeTab === key ? 'active-tab' : ''}
              onClick={() => setActiveTab(key)}
            >
              {label}
            </button>
          ))}
        </nav>

        <main>{renderTabContent()}</main>
      </div>
    </>
  )
}
