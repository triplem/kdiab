import { axiosInstance } from './axiosInstance'

const BASE = '/api/measures/v1'

interface MeasureResponse {
  id: string
  userId: string
  measuredAt: string
  createdAt: string
  type: string
  source: string
  status: string
  data: Record<string, unknown>
}

interface PagedMeasures {
  items: MeasureResponse[]
  page: number
  size: number
  totalCount: number
}

export const measuresApi = {
  listMeasures: (userId: string, page = 0, size = 50) =>
    axiosInstance.get<PagedMeasures>(`${BASE}/users/${userId}/measures`, {
      params: { page, size },
    }),
  archiveMeasures: (userId: string, body: { measureIds: string[] }) =>
    axiosInstance.post(`${BASE}/users/${userId}/measures/archive`, body),
  deleteMeasures: (userId: string, body: { measureIds: string[] }) =>
    axiosInstance.delete(`${BASE}/users/${userId}/measures`, { data: body }),
  createMeasure: (userId: string, body: Record<string, unknown>) =>
    axiosInstance.post<MeasureResponse>(`${BASE}/users/${userId}/measures`, body),
}
