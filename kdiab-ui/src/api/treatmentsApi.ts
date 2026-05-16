import { axiosInstance } from './axiosInstance'

const BASE = '/api/treatments/v1'

export interface TreatmentResponse {
  id: string
  userId: string
  treatedAt: string
  createdAt: string
  type: string
  status: string
  notes?: string
  data: Record<string, unknown>
}

export interface PagedTreatments {
  items: TreatmentResponse[]
  page: number
  size: number
  totalCount: number
}

export interface DeviceStatusResponse {
  id: string
  userId: string
  recordedAt: string
  device: string
  pumpName?: string
  reservoirUnits?: number
  batteryLevel?: number
  pumpConnected?: boolean
}

export const treatmentsApi = {
  getLatestDeviceStatus: (userId: string) =>
    axiosInstance.get<DeviceStatusResponse>(`${BASE}/users/${userId}/device-status/latest`),
  listTreatments: (userId: string, status?: string, page?: number, size?: number) =>
    axiosInstance.get<PagedTreatments>(`${BASE}/users/${userId}/treatments`, {
      params: { ...(status ? { status } : {}), ...(page != null ? { page } : {}), ...(size != null ? { size } : {}) },
    }),
  createTreatment: (userId: string, body: Record<string, unknown>) =>
    axiosInstance.post<TreatmentResponse>(`${BASE}/users/${userId}/treatments`, body),
  deleteTreatments: (userId: string, body: { treatmentIds: string[] }) =>
    axiosInstance.delete(`${BASE}/users/${userId}/treatments`, { data: body }),
  archiveTreatments: (userId: string, body: { treatmentIds: string[] }) =>
    axiosInstance.post(`${BASE}/users/${userId}/treatments/archive`, body),
  unarchiveTreatments: (userId: string, body: { treatmentIds: string[] }) =>
    axiosInstance.post(`${BASE}/users/${userId}/treatments/unarchive`, body),
  updateTreatment: (
    userId: string,
    treatmentId: string,
    body: { treatedAt: string; data: Record<string, unknown>; notes?: string },
  ) =>
    axiosInstance.put<TreatmentResponse>(
      `${BASE}/users/${userId}/treatments/${treatmentId}`,
      body,
    ),
}
