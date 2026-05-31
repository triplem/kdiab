import { axiosInstance } from './axiosInstance'

export interface DoseBreakdown {
  currentBgMgDl: number
  targetBgMgDl: number
  isf: number
  icr: number
  trend: string
  carbsGrams: number
}

export interface DoseResponse {
  correctionDose: number
  carbDose: number
  trendAdjustment: number
  totalRecommended: number
  breakdown: DoseBreakdown
  profileId: string
  warnings: string[]
  recommendedWaitMinutes: number | null
}

export interface DoseRequestBody {
  currentBg: number
  glucoseUnit: string
  trend: string
  carbsGrams?: number
  activeIob?: number
  useProfileTime?: string
}

const BASE = '/api/calc/v1'

export const calcApi = {
  calculateDose: (userId: string, body: DoseRequestBody) =>
    axiosInstance.post<DoseResponse>(`${BASE}/users/${userId}/calc/dose`, body),
}
