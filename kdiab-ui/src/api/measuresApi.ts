import { axiosInstance } from './axiosInstance'
import type { MeasureResponse, PagedMeasureResponse } from './generated/measures'

export type { MeasureResponse }

const BASE = '/api/measures/v1'

export const measuresApi = {
  listMeasures: (userId: string, page = 0, size = 50, status?: string) =>
    axiosInstance.get<PagedMeasureResponse>(`${BASE}/users/${userId}/measures`, {
      params: { page, size, ...(status ? { status } : {}) },
    }),
  archiveMeasures: (userId: string, body: { measureIds: string[] }) =>
    axiosInstance.post(`${BASE}/users/${userId}/measures/archive`, body),
  unarchiveMeasures: (userId: string, body: { measureIds: string[] }) =>
    axiosInstance.post(`${BASE}/users/${userId}/measures/unarchive`, body),
  deleteMeasures: (userId: string, body: { measureIds: string[] }) =>
    axiosInstance.delete(`${BASE}/users/${userId}/measures`, { data: body }),
  createMeasure: (userId: string, body: Record<string, unknown>) =>
    axiosInstance.post<MeasureResponse>(`${BASE}/users/${userId}/measures`, body),
  updateMeasure: (userId: string, measureId: string, body: Record<string, unknown>) =>
    axiosInstance.put<MeasureResponse>(`${BASE}/users/${userId}/measures/${measureId}`, body),
}
