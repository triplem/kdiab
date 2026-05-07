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

export const treatmentsApi = {
  listTreatments: (userId: string, status?: string) =>
    axiosInstance.get<PagedTreatments>(`${BASE}/users/${userId}/treatments`, {
      params: status ? { status } : {},
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
