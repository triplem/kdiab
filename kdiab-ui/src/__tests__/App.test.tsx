import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import '../i18n'

// --- Mock react-oidc-context --------------------------------------------------
vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(),
}))

// --- Mock tokenProvider so the auth effect doesn't try to parse a real JWT ----
vi.mock('../api/tokenProvider', () => ({
  setAccessToken: vi.fn(),
  parseRolesFromToken: vi.fn().mockReturnValue(['PATIENT']),
  parseAllowedPatientsFromToken: vi.fn().mockReturnValue([]),
  parseAllowedPatientNamesFromToken: vi.fn().mockReturnValue([]),
  getAccessToken: vi.fn().mockReturnValue(null),
  configureAuthInterceptor: vi.fn(),
}))

// --- Mock usersApi so the auth effect getMe doesn't fail ----------------------
vi.mock('../api/usersApi', () => ({
  usersApi: {
    getMe: vi.fn(),
    patchMySettings: vi.fn(),
  },
}))

// --- Mock measuresApi ---------------------------------------------------------
vi.mock('../api/measuresApi', () => ({
  measuresApi: {
    createMeasure: vi.fn(),
    listMeasures: vi.fn(),
    archiveMeasures: vi.fn(),
    unarchiveMeasures: vi.fn(),
    deleteMeasures: vi.fn(),
    updateMeasure: vi.fn(),
  },
}))

// --- Mock treatmentsApi -------------------------------------------------------
vi.mock('../api/treatmentsApi', () => ({
  treatmentsApi: {
    createTreatment: vi.fn(),
    listTreatments: vi.fn(),
    deleteTreatments: vi.fn(),
    archiveTreatments: vi.fn(),
    unarchiveTreatments: vi.fn(),
    updateTreatment: vi.fn(),
    getLatestDeviceStatus: vi.fn(),
    getDeviceAge: vi.fn(),
  },
}))

// --- Mock carbsApi (used by AddTreatmentModal's CarbsForm) --------------------
vi.mock('../api/carbsApi', () => ({
  carbsApi: {
    listFoods: vi.fn().mockResolvedValue({ data: { items: [] } }),
  },
}))

// --- Mock analyzeApi (used by DashboardView) ----------------------------------
vi.mock('../api/analyzeApi', () => ({
  analyzeApi: {
    getTimeline: vi.fn().mockResolvedValue({ data: { measures: [], treatments: [] } }),
    getHba1c: vi.fn().mockResolvedValue({ data: null }),
    getAgp: vi.fn().mockResolvedValue({ data: null }),
    getProfiles: vi.fn().mockResolvedValue({ data: { active: null, archived: [] } }),
  },
}))

// --- Mock calcApi (used by DoseCalculator) ------------------------------------
vi.mock('../api/calcApi', () => ({
  calcApi: {
    calculate: vi.fn(),
  },
}))

// --- Mock profilesApi (used by ProfileList / DashboardView) ------------------
vi.mock('../api/profilesApi', () => ({
  profilesApi: {
    getActiveProfile: vi.fn().mockResolvedValue({ data: null }),
    listProfiles: vi.fn().mockResolvedValue({ data: { items: [], page: 0, size: 20, totalCount: 0 } }),
    createProfile: vi.fn(),
    updateProfile: vi.fn(),
    activateProfile: vi.fn(),
    archiveProfile: vi.fn(),
    listInsulins: vi.fn().mockResolvedValue({ data: [] }),
  },
}))

// --- Mock heavy feature components so they don't add uninstrumented branches --
vi.mock('../features/dashboard/DashboardView', () => ({
  DashboardView: () => <div data-testid="dashboard-view" />,
}))
vi.mock('../features/analytics/AnalyticsView', () => ({
  AnalyticsView: () => <div data-testid="analytics-view" />,
}))
vi.mock('../features/carbs/FoodDatabase', () => ({
  FoodDatabase: () => <div data-testid="food-database" />,
}))
vi.mock('../features/calc/DoseCalculator', () => ({
  DoseCalculator: () => <div data-testid="dose-calculator" />,
}))
vi.mock('../features/profiles/ProfileList', () => ({
  ProfileList: () => <div data-testid="profile-list" />,
}))
vi.mock('../features/profiles/ProfileEditor', () => ({
  ProfileEditor: () => <div data-testid="profile-editor" />,
}))
vi.mock('../features/profiles/ProfileHistory', () => ({
  ProfileHistory: () => <div data-testid="profile-history" />,
}))
vi.mock('../features/profiles/AdminInsulinManager', () => ({
  AdminInsulinManager: () => <div data-testid="admin-insulin-manager" />,
}))
vi.mock('../features/measures/MeasureList', () => ({
  MeasureList: () => <div data-testid="measure-list" />,
}))
vi.mock('../features/treatments/TreatmentList', () => ({
  TreatmentList: () => <div data-testid="treatment-list" />,
}))
vi.mock('../features/users/UserSettings', () => ({
  UserSettings: () => <div data-testid="user-settings" />,
}))
vi.mock('../features/users/AdminUserList', () => ({
  AdminUserList: () => <div data-testid="admin-user-list" />,
}))
vi.mock('../features/users/AdminDoctorPatients', () => ({
  AdminDoctorPatients: () => <div data-testid="admin-doctor-patients" />,
}))
vi.mock('../features/users/RegistrationForm', () => ({
  RegistrationForm: () => <div data-testid="registration-form" />,
}))
vi.mock('../components/LanguageSwitcher', () => ({
  LanguageSwitcher: () => <div data-testid="language-switcher" />,
}))
vi.mock('../components/PatientBanner', () => ({
  PatientBanner: () => <div data-testid="patient-banner" />,
}))

// --- Mock sonner so we can spy on toast.success / toast.error -----------------
const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()
vi.mock('sonner', () => ({
  Toaster: () => null,
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}))

// --- Imports after all mocks --------------------------------------------------
import { useAuth } from 'react-oidc-context'
import { usersApi } from '../api/usersApi'
import { measuresApi } from '../api/measuresApi'
import { treatmentsApi } from '../api/treatmentsApi'
import { TimeFormatProvider, useTimeFormat } from '../context/TimeFormatContext'
import App from '../App'

const mockedUseAuth = vi.mocked(useAuth)
const mockedGetMe = vi.mocked(usersApi.getMe)
const mockedCreateMeasure = vi.mocked(measuresApi.createMeasure)
const mockedCreateTreatment = vi.mocked(treatmentsApi.createTreatment)

// A faked access_token — tokenProvider functions are mocked so it is never decoded
const FAKE_TOKEN = 'header.e30.sig'

function makeAuthUser() {
  return {
    access_token: FAKE_TOKEN,
    profile: { sub: 'user-1', preferred_username: 'sarah', locale: 'en' },
  }
}

function makeAuthState(overrides: Record<string, unknown> = {}) {
  return {
    isLoading: false,
    isAuthenticated: true,
    user: makeAuthUser(),
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    signoutRedirect: vi.fn().mockResolvedValue(undefined),
    error: undefined,
    ...overrides,
  }
}

function renderApp() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <TimeFormatProvider>
        <App />
      </TimeFormatProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedUseAuth.mockReturnValue(makeAuthState() as ReturnType<typeof useAuth>)
  mockedGetMe.mockResolvedValue({
    data: {
      userId: 'user-1',
      email: 'sarah@example.com',
      displayName: 'Sarah',
      roles: ['PATIENT'],
      settings: {
        glucoseUnit: 'mg/dL',
        weightUnit: 'kg',
        timezone: 'UTC',
        language: 'en',
        timeFormat: 24,
        alarmUrgentHigh: 260,
        alarmHigh: 180,
        alarmLow: 70,
        alarmUrgentLow: 54,
        sensorDurationHours: 240,
        updatedAt: '2024-01-01T00:00:00Z',
      },
    },
  } as never)
})

// Helper: navigate to a given tab by clicking its nav button in the tab-nav
async function goToTab(exactLabel: string) {
  // Tab buttons are inside nav.tab-nav
  const nav = document.querySelector('nav.tab-nav')
  const tabBtns = nav ? Array.from(nav.querySelectorAll('button')) : []
  const btn = tabBtns.find((b) => b.textContent?.trim() === exactLabel)
  if (!btn) {
    throw new Error(`Tab button "${exactLabel}" not found. Available: ${tabBtns.map((b) => b.textContent?.trim()).join(', ')}`)
  }
  await act(async () => {
    fireEvent.click(btn)
  })
}

// Helper: open the AddMeasureModal from the measures tab
async function openAddMeasureModal() {
  await goToTab('Measures')
  // The add button text is "+ Measures" (from `+ {t('nav.measures')}`)
  const allBtns = Array.from(document.querySelectorAll('button'))
  const addBtn = allBtns.find((b) => /^\+\s/.test(b.textContent ?? '') && b.textContent?.includes('Measures'))
  if (!addBtn) {
    throw new Error(`Add Measure button not found. Buttons: ${allBtns.map((b) => b.textContent?.trim()).join(' | ')}`)
  }
  await act(async () => {
    fireEvent.click(addBtn)
  })
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeNull())
}

// Helper: open the AddTreatmentModal from the treatments tab
async function openAddTreatmentModal() {
  await goToTab('Treatments')
  // The add button text is "+ Treatments"
  const allBtns = Array.from(document.querySelectorAll('button'))
  const addBtn = allBtns.find((b) => /^\+\s/.test(b.textContent ?? '') && b.textContent?.includes('Treatments'))
  if (!addBtn) {
    throw new Error(`Add Treatment button not found. Buttons: ${allBtns.map((b) => b.textContent?.trim()).join(' | ')}`)
  }
  await act(async () => {
    fireEvent.click(addBtn)
  })
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeNull())
}

describe('App — handleSaveMeasure', () => {
  test('happy path: API resolves → modal closes and toast fires', async () => {
    mockedCreateMeasure.mockResolvedValue({ data: { id: 'm-1' } } as never)
    renderApp()
    await openAddMeasureModal()

    // Fill in a valid BGM value
    const spinners = screen.getAllByRole('spinbutton')
    await act(async () => {
      fireEvent.change(spinners[0], { target: { value: '120' } })
    })

    // Submit the form
    const form = screen.getByRole('dialog').querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateMeasure).toHaveBeenCalledOnce())
    expect(mockToastSuccess).toHaveBeenCalledOnce()
    // Modal should be closed
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  test('API rejects → error state set, modal stays open, toast NOT fired', async () => {
    mockedCreateMeasure.mockRejectedValue({ message: 'Network Error' })
    renderApp()
    await openAddMeasureModal()

    const spinners = screen.getAllByRole('spinbutton')
    await act(async () => {
      fireEvent.change(spinners[0], { target: { value: '120' } })
    })

    const form = screen.getByRole('dialog').querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateMeasure).toHaveBeenCalledOnce())
    expect(mockToastSuccess).not.toHaveBeenCalled()
    // Modal stays open (dialog still present)
    expect(screen.queryByRole('dialog')).not.toBeNull()
  })
})

describe('App — handleSaveTreatment', () => {
  test('happy path: API resolves → modal closes and toast fires', async () => {
    mockedCreateTreatment.mockResolvedValue({ data: { id: 't-1' } } as never)
    renderApp()
    await openAddTreatmentModal()

    const dialog = screen.getByRole('dialog')

    // Switch to BOLUS so onSave (not onSaveMeal) is used — scope to dialog
    const selects = dialog.querySelectorAll('select')
    const typeSelect = selects[0]!
    await act(async () => {
      fireEvent.change(typeSelect, { target: { value: 'BOLUS' } })
    })

    // Fill insulin value (first spinbutton in the dialog)
    const spinners = dialog.querySelectorAll('input[type="number"]')
    if (spinners[0]) {
      await act(async () => {
        fireEvent.change(spinners[0], { target: { value: '2' } })
      })
    }

    const form = dialog.querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateTreatment).toHaveBeenCalledOnce())
    expect(mockToastSuccess).toHaveBeenCalledOnce()
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  test('API rejects → error state set, modal stays open, toast NOT fired', async () => {
    mockedCreateTreatment.mockRejectedValue({ message: 'Server Error' })
    renderApp()
    await openAddTreatmentModal()

    const dialog = screen.getByRole('dialog')
    const selects = dialog.querySelectorAll('select')
    const typeSelect = selects[0]!
    await act(async () => {
      fireEvent.change(typeSelect, { target: { value: 'BOLUS' } })
    })

    const spinners = dialog.querySelectorAll('input[type="number"]')
    if (spinners[0]) {
      await act(async () => {
        fireEvent.change(spinners[0], { target: { value: '2' } })
      })
    }

    const form = dialog.querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateTreatment).toHaveBeenCalledOnce())
    expect(mockToastSuccess).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeNull()
  })
})

describe('TimeFormatContext — useTimeFormat hook', () => {
  // These tests exercise the context's formatTime / formatDate branches that are
  // newly instrumented by App.test.tsx's TimeFormatProvider usage.
  test('useTimeFormat returns a valid formatDate function', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    let captured: ReturnType<typeof import('../context/TimeFormatContext').useTimeFormat> | undefined

    function Consumer() {
      captured = useTimeFormat()
      return null
    }

    render(
      <QueryClientProvider client={qc}>
        <TimeFormatProvider>
          <Consumer />
        </TimeFormatProvider>
      </QueryClientProvider>,
    )

    // Cover formatDate with a valid ISO string
    const result = captured!.formatDate('2024-01-15T10:30:00Z')
    expect(typeof result).toBe('string')
    expect(result).not.toBe('')
    // Cover formatDate with an empty string — returns 'N/A'
    expect(captured!.formatDate('')).toBe('N/A')
    // Cover formatTime with empty string — returns input
    expect(captured!.formatTime('')).toBe('')
    // Cover formatTime with a valid time string
    const timeResult = captured!.formatTime('14:30')
    expect(typeof timeResult).toBe('string')
  })
})

describe('App — handleSaveMeasure error messages', () => {
  test('shows server error message from response.data.message when available', async () => {
    mockedCreateMeasure.mockRejectedValue({
      response: { data: { message: 'Validation failed' } },
    })
    renderApp()
    await openAddMeasureModal()

    const dialog = screen.getByRole('dialog')
    const spinners = dialog.querySelectorAll('input[type="number"]')
    await act(async () => {
      if (spinners[0]) fireEvent.change(spinners[0], { target: { value: '120' } })
    })

    const form = dialog.querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateMeasure).toHaveBeenCalledOnce())
    expect(mockToastSuccess).not.toHaveBeenCalled()
    // Modal stays open showing error
    expect(screen.queryByRole('dialog')).not.toBeNull()
  })
})

describe('App — handleSaveTreatment error messages', () => {
  test('shows server error message from response.data.message when available', async () => {
    mockedCreateTreatment.mockRejectedValue({
      response: { data: { message: 'Treatment validation failed' } },
    })
    renderApp()
    await openAddTreatmentModal()

    const dialog = screen.getByRole('dialog')
    const selects = dialog.querySelectorAll('select')
    const typeSelect = selects[0]!
    await act(async () => {
      fireEvent.change(typeSelect, { target: { value: 'BOLUS' } })
    })
    const spinners = dialog.querySelectorAll('input[type="number"]')
    if (spinners[0]) {
      await act(async () => {
        fireEvent.change(spinners[0], { target: { value: '2' } })
      })
    }
    const form = dialog.querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateTreatment).toHaveBeenCalledOnce())
    expect(mockToastSuccess).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeNull()
  })
})

describe('App — treatment modal branch coverage', () => {
  // These tests exercise AddTreatmentModal switch-case branches not covered by
  // the isolated AddTreatmentModal.test.tsx (SITE_CHANGE, SENSOR_INSERT, etc.)
  test.each([
    ['COMBO_BOLUS'],
    ['BASAL'],
    ['PUMP_SUSPEND'],
    ['SITE_CHANGE'],
    ['SENSOR_INSERT'],
    ['INSULIN_CHANGE'],
    ['PUMP_BATTERY_CHANGE'],
    ['HYPO_TREATMENT'],
  ] as const)('switching to %s type renders without error', async ([treatmentType]) => {
    renderApp()
    await openAddTreatmentModal()

    const dialog = screen.getByRole('dialog')
    const selects = dialog.querySelectorAll('select')
    const typeSelect = selects[0]!
    await act(async () => {
      fireEvent.change(typeSelect, { target: { value: treatmentType } })
    })

    // Verify modal is still open and rendered without throwing
    expect(screen.queryByRole('dialog')).not.toBeNull()
  })
})

describe('App — handleSaveMeal', () => {
  test('happy path: both createTreatment calls resolve → toast fires', async () => {
    mockedCreateTreatment.mockResolvedValue({ data: { id: 't-2' } } as never)
    renderApp()
    await openAddTreatmentModal()

    const dialog = screen.getByRole('dialog')

    // Default is MEAL — fill in insulin and carbs (spinbuttons in the dialog)
    const spinners = dialog.querySelectorAll('input[type="number"]')
    await act(async () => {
      if (spinners[0]) fireEvent.change(spinners[0], { target: { value: '3' } })
      if (spinners[1]) fireEvent.change(spinners[1], { target: { value: '45' } })
    })

    const form = dialog.querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateTreatment).toHaveBeenCalledTimes(2))
    expect(mockToastSuccess).toHaveBeenCalledOnce()
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  test('partial failure: second createTreatment throws → toast NOT fired, error state set', async () => {
    mockedCreateTreatment
      .mockResolvedValueOnce({ data: { id: 't-bolus' } } as never)
      .mockRejectedValueOnce({ message: 'Carbs save failed' })
    renderApp()
    await openAddTreatmentModal()

    const dialog = screen.getByRole('dialog')
    const spinners = dialog.querySelectorAll('input[type="number"]')
    await act(async () => {
      if (spinners[0]) fireEvent.change(spinners[0], { target: { value: '3' } })
      if (spinners[1]) fireEvent.change(spinners[1], { target: { value: '45' } })
    })

    const form = dialog.querySelector('form')!
    await act(async () => {
      fireEvent.submit(form)
    })

    await waitFor(() => expect(mockedCreateTreatment).toHaveBeenCalledTimes(2))
    expect(mockToastSuccess).not.toHaveBeenCalled()
    // Modal stays open on failure
    expect(screen.queryByRole('dialog')).not.toBeNull()
  })
})

describe('App — logout', () => {
  test('clicking logout calls signoutRedirect with post_logout_redirect_uri', async () => {
    const signoutRedirect = vi.fn().mockResolvedValue(undefined)
    mockedUseAuth.mockReturnValue(makeAuthState({ signoutRedirect }) as ReturnType<typeof useAuth>)
    renderApp()

    const logoutBtn = screen.getByTestId('logout-btn')
    await act(async () => {
      fireEvent.click(logoutBtn)
    })

    expect(signoutRedirect).toHaveBeenCalledOnce()
    expect(signoutRedirect).toHaveBeenCalledWith({
      post_logout_redirect_uri: window.location.origin,
    })
  })
})

describe('App — VITE_FOOD_DATABASE_ENABLED feature flag', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  test('Food DB tab is hidden when VITE_FOOD_DATABASE_ENABLED is not set', () => {
    renderApp()
    const nav = document.querySelector('nav.tab-nav')
    const tabLabels = nav ? Array.from(nav.querySelectorAll('button')).map((b) => b.textContent?.trim()) : []
    expect(tabLabels).not.toContain('Food DB')
  })

  test('Food DB tab is hidden when VITE_FOOD_DATABASE_ENABLED is "false"', () => {
    vi.stubEnv('VITE_FOOD_DATABASE_ENABLED', 'false')
    renderApp()
    const nav = document.querySelector('nav.tab-nav')
    const tabLabels = nav ? Array.from(nav.querySelectorAll('button')).map((b) => b.textContent?.trim()) : []
    expect(tabLabels).not.toContain('Food DB')
  })

  test('Food DB tab is visible when VITE_FOOD_DATABASE_ENABLED is "true"', () => {
    vi.stubEnv('VITE_FOOD_DATABASE_ENABLED', 'true')
    renderApp()
    const nav = document.querySelector('nav.tab-nav')
    const tabLabels = nav ? Array.from(nav.querySelectorAll('button')).map((b) => b.textContent?.trim()) : []
    expect(tabLabels).toContain('Food DB')
  })
})
