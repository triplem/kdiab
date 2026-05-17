import { axiosInstance } from './axiosInstance'

const BASE = '/api/analyze/v1'

export interface TimelineResponse {
  measures: Array<{
    id: string
    measuredAt: string
    type: string
    data: Record<string, unknown>
  }>
  treatments: Array<{
    id: string
    treatedAt: string
    type: string
    notes?: string
    data: Record<string, unknown>
  }>
  errors?: string[]
}

export interface TirBreakdown {
  belowCount: number
  inRangeCount: number
  aboveCount: number
  highCount: number
  totalCount: number
}

export interface Hba1cResponse {
  hba1c: number | null
  meanGlucose: number
  readingCount: number
  tir: TirBreakdown
  warnings?: string[]
}

export interface AgpHourlyData {
  hour: number
  p10: number | null
  p25: number | null
  median: number | null
  p75: number | null
  p90: number | null
  count: number
}

export interface AgpResponse {
  hourlyData: AgpHourlyData[]
  totalReadingCount?: number
  sensorWearDays?: number
  warnings?: string[]
}

export interface ProfileSegment {
  startTime: string
  value: number
}

export interface TargetSegment {
  startTime: string
  low: number
  high: number
}

export interface ProfileSummary {
  id: string
  status: string
  name: string
  createdAt?: string
  previousProfileId?: string
  validFrom?: string | null
  activatedAt?: string | null
  archivedAt?: string | null
  insulinType?: string | null
  durationOfAction?: number | null
  basal?: ProfileSegment[] | null
  icr?: ProfileSegment[] | null
  isf?: ProfileSegment[] | null
  targets?: TargetSegment[] | null
}

export interface ProfilesResponse {
  profiles: ProfileSummary[]
}

export interface DeviceAgeResponse {
  catheterChangedAt: string | null
  reservoirChangedAt: string | null
  sensorInsertedAt: string | null
}

export interface DeviceStatusResponse {
  id: string
  userId: string
  recordedAt: string
  device: string
  pumpName?: string | null
  reservoirUnits?: number | null
  batteryLevel?: number | null
  pumpConnected?: boolean | null
}

export const analyzeApi = {
  getTimeline: (userId: string, from: string, to: string) =>
    axiosInstance.get<TimelineResponse>(`${BASE}/users/${userId}/timeline`, {
      params: { from, to },
    }),
  getHba1c: (userId: string, from: string, to: string) =>
    axiosInstance.get<Hba1cResponse>(`${BASE}/users/${userId}/analytics/hba1c`, {
      params: { from, to },
    }),
  getAgp: (userId: string, from: string, to: string) =>
    axiosInstance.get<AgpResponse>(`${BASE}/users/${userId}/analytics/agp`, {
      params: { from, to },
    }),
  getActiveProfiles: (userId: string, from: string, to: string) =>
    axiosInstance.get<ProfilesResponse>(`${BASE}/users/${userId}/profiles/active`, {
      params: { from, to },
    }),
  getDeviceAge: (userId: string) =>
    axiosInstance.get<DeviceAgeResponse>(`${BASE}/users/${userId}/device-age`),
  getLatestDeviceStatus: (userId: string) =>
    axiosInstance.get<DeviceStatusResponse>(`${BASE}/users/${userId}/device-status`),
}
