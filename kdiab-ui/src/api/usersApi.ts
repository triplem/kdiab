import type { AxiosRequestConfig } from 'axios'
import { axiosInstance } from './axiosInstance'

const BASE = '/api/users/v1'

export interface LocalePreferences {
  timezone: string
  language: string
  timeFormat: 12 | 24
}

export interface UnitPreferences {
  glucoseUnit: 'mg/dL' | 'mmol/L'
  weightUnit: 'kg' | 'lbs'
}

export interface AlarmThresholds {
  urgentHigh: number
  high: number
  low: number
  urgentLow: number
}

export interface DiabetesProfile {
  sensorDurationHours: number
  diabetesSince?: number | null
  carbAbsorptionRateGPerHour?: number | null
}

export interface UserSettings {
  locale: LocalePreferences
  units: UnitPreferences
  alarms?: AlarmThresholds | null
  diabetes: DiabetesProfile
  updatedAt: string
  jwtBackedNote?: string | null
}

export interface UserResponse {
  userId: string
  email: string
  displayName: string
  roles: string[]
  birthday?: string | null
  settings?: UserSettings
}

export interface CreateUserRequest {
  email: string
  displayName: string
  password: string
  role: 'PATIENT' | 'DOCTOR' | 'ADMIN'
}

export interface UpdateUserRequest {
  displayName?: string
  role?: 'PATIENT' | 'DOCTOR' | 'ADMIN'
}

export interface LocalePreferencesPatch {
  timezone?: string
  language?: string
  timeFormat?: 12 | 24
}

export interface UnitPreferencesPatch {
  glucoseUnit?: 'mg/dL' | 'mmol/L'
  weightUnit?: 'kg' | 'lbs'
}

export interface AlarmThresholdsPatch {
  urgentHigh?: number | null
  high?: number | null
  low?: number | null
  urgentLow?: number | null
}

export interface DiabetesProfilePatch {
  sensorDurationHours?: number
  diabetesSince?: number | null
  carbAbsorptionRateGPerHour?: number | null
}

export interface PatchProfileRequest {
  birthday?: string | null
}

export interface PatchSettingsRequest {
  locale?: LocalePreferencesPatch
  units?: UnitPreferencesPatch
  alarms?: AlarmThresholdsPatch
  diabetes?: DiabetesProfilePatch
}

export interface DoctorPatientResponse {
  doctorId: string
  patientId: string
  createdAt: string
}

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED'

export interface InvitationResponse {
  id: string
  doctorId: string
  patientId?: string | null
  patientIdentifier: string
  status: InvitationStatus
  message?: string | null
  createdAt: string
  expiresAt: string
  resolvedAt?: string | null
}

export interface InvitationPage {
  content: InvitationResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  doctorDisplayName?: string | null
  patientDisplayNames: Record<string, string>
}

export interface SendInvitationRequest {
  patientIdentifier: string
  message?: string | null
}

export const usersApi = {
  getMe: () => axiosInstance.get<UserResponse>(`${BASE}/users/me`),
  patchMyProfile: (body: PatchProfileRequest) =>
    axiosInstance.patch<UserResponse>(`${BASE}/users/me/profile`, body),
  patchMySettings: (body: PatchSettingsRequest) =>
    axiosInstance.patch<UserSettings>(`${BASE}/users/me/settings`, body),
  listUsers: (params?: { search?: string; page?: number; size?: number }) =>
    axiosInstance.get<UserResponse[]>(`${BASE}/users`, { params }),
  getUser: (userId: string, config?: AxiosRequestConfig) =>
    axiosInstance.get<UserResponse>(`${BASE}/users/${userId}`, config),
  createUser: (body: CreateUserRequest) =>
    axiosInstance.post<UserResponse>(`${BASE}/users`, body),
  updateUser: (userId: string, body: UpdateUserRequest) =>
    axiosInstance.patch<UserResponse>(`${BASE}/users/${userId}`, body),
  deleteUser: (userId: string) => axiosInstance.delete(`${BASE}/users/${userId}`),
  getPatients: (doctorId: string, params?: { page?: number; size?: number }) =>
    axiosInstance.get<DoctorPatientResponse[]>(`${BASE}/users/${doctorId}/patients`, { params }),
  assignPatient: (doctorId: string, patientId: string) =>
    axiosInstance.post<DoctorPatientResponse>(`${BASE}/users/${doctorId}/patients`, { patientId }),
  removePatient: (doctorId: string, patientId: string) =>
    axiosInstance.delete(`${BASE}/users/${doctorId}/patients/${patientId}`),
  sendInvitation: (doctorId: string, body: SendInvitationRequest) =>
    axiosInstance.post<InvitationResponse>(`${BASE}/users/${doctorId}/invitations`, body),
  listDoctorInvitations: (doctorId: string, params?: { status?: string; page?: number; size?: number }) =>
    axiosInstance.get<InvitationPage>(`${BASE}/users/${doctorId}/invitations`, { params }),
  cancelInvitation: (doctorId: string, invitationId: string) =>
    axiosInstance.delete(`${BASE}/users/${doctorId}/invitations/${invitationId}`),
  listIncomingInvitations: (patientId: string) =>
    axiosInstance.get<InvitationResponse[]>(`${BASE}/users/${patientId}/invitations/incoming`),
  respondToInvitation: (patientId: string, invitationId: string, body: { action: 'ACCEPT' | 'DECLINE' }) =>
    axiosInstance.patch<InvitationResponse>(`${BASE}/users/${patientId}/invitations/${invitationId}`, body),
}
