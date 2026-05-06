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

export const treatmentsApi = {
  listTreatments: (userId: string) =>
    axiosInstance.get<TreatmentResponse[]>(`${BASE}/users/${userId}/treatments`),
  createTreatment: (userId: string, body: Record<string, unknown>) =>
    axiosInstance.post<TreatmentResponse>(`${BASE}/users/${userId}/treatments`, body),
  deleteTreatments: (userId: string, body: { treatmentIds: string[] }) =>
    axiosInstance.delete(`${BASE}/users/${userId}/treatments`, { data: body }),
  archiveTreatments: (userId: string, body: { treatmentIds: string[] }) =>
    axiosInstance.post(`${BASE}/users/${userId}/treatments/archive`, body),
}
