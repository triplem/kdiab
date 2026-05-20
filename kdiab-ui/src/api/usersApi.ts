import { axiosInstance } from './axiosInstance'

const BASE = '/api/users/v1'

export interface UserSettings {
  timezone: string
  language: string
  timeFormat: 12 | 24
  glucoseUnit: 'mg/dL' | 'mmol/L'
  weightUnit: 'kg' | 'lbs'
  alarmUrgentHigh: number | null
  alarmHigh: number | null
  alarmLow: number | null
  alarmUrgentLow: number | null
  sensorDurationHours: number
  updatedAt: string
}

export interface UserResponse {
  userId: string
  email: string
  displayName: string
  roles: string[]
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

export interface PatchSettingsRequest {
  timezone?: string
  language?: string
  timeFormat?: 12 | 24
  glucoseUnit?: 'mg/dL' | 'mmol/L'
  weightUnit?: 'kg' | 'lbs'
  alarmUrgentHigh?: number | null
  alarmHigh?: number | null
  alarmLow?: number | null
  alarmUrgentLow?: number | null
  sensorDurationHours?: number
}

export interface RegisterRequest {
  email: string
  displayName: string
  password: string
}

export interface RegisterResponse {
  userId: string
  message: string
}

export interface DoctorPatientResponse {
  doctorId: string
  patientId: string
  createdAt: string
}

export const usersApi = {
  getMe: () => axiosInstance.get<UserResponse>(`${BASE}/users/me`),
  patchMySettings: (body: PatchSettingsRequest) =>
    axiosInstance.patch<UserSettings>(`${BASE}/users/me/settings`, body),
  listUsers: (params?: { search?: string; page?: number; size?: number }) =>
    axiosInstance.get<UserResponse[]>(`${BASE}/users`, { params }),
  getUser: (userId: string) => axiosInstance.get<UserResponse>(`${BASE}/users/${userId}`),
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
  register: (body: RegisterRequest) =>
    axiosInstance.post<RegisterResponse>(`${BASE}/register`, body),
}
