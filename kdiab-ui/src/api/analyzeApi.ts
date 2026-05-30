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
  veryLowCount: number
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

export interface DeviceUsageResponse {
  userId: string
  avgSensorDays: number | null
  stddevSensorDays: number | null
  avgCatheterDays: number | null
  stddevCatheterDays: number | null
  avgReservoirDays: number | null
  stddevReservoirDays: number | null
  avgBatteryDays: number | null
  stddevBatteryDays: number | null
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

// ---- Report endpoints (Wave 1 — daily-stats, daily-trend, glucose-distribution) ----

export interface DailyStatRow {
  date: string
  cgmCount: number
  veryLowPercent: number | null
  lowPercent: number | null
  inRangePercent: number | null
  highPercent: number | null
  veryHighPercent: number | null
  p25: number | null
  median: number | null
  p75: number | null
  sd: number | null
  eHbA1c: number | null
}

export interface DailyStatsResponse {
  rows: DailyStatRow[]
  summary: DailyStatRow
  warnings?: string[]
}

export interface HourlyTrendRow {
  hour: number
  meanGlucose: number | null
  trendPercent: number | null
  trendZone: 'risingFast' | 'rising' | 'stable' | 'falling' | 'fallingFast' | null
  zone: 'veryHypo' | 'hypo' | 'inRange' | 'hyper' | 'veryHyper' | 'noData' | null
  basalRateIePerH: number | null
  carbsG: number
}

export interface DailyTrendDay {
  date: string
  hours: HourlyTrendRow[]
}

export interface DailyTrendResponse {
  days: DailyTrendDay[]
  warnings?: string[]
}

export interface GlucoseBucket {
  lowerBound: number
  upperBound: number
  count: number
  percent: number
  zone: string
}

export interface ZonePercents {
  veryLow: number
  low: number
  inRange: number
  high: number
  veryHigh: number
}

export interface GlucoseDistributionResponse {
  buckets: GlucoseBucket[]
  zonePercents: ZonePercents
  unit: string
  totalCount: number
  warnings?: string[]
}

export interface CgpResponse {
  tor: number
  varK: number
  hypoIntensity: number
  hyperIntensity: number
  meanGlucose: number
  normTor: number
  normVarK: number
  normHypo: number
  normHyper: number
  normMeanGlucose: number
  refTor: number
  refVarK: number
  refHypo: number
  refHyper: number
  refMeanGlucose: number
  pgr: number
  pgrRisk: string
  warnings?: string[]
}

// ---- Report Summary endpoint ----

/** A single 5-zone TIR bucket (count + percent) */
export interface TirZone {
  count: number
  percent: number
  zone?: string
}

/** Aggregated 5-zone TIR result (profile thresholds or standard 54/70/180/250) */
export interface TirResult {
  veryLow: TirZone
  low: TirZone
  inRange: TirZone
  high: TirZone
  veryHigh: TirZone
  /** true when the profile had no custom targets and standard thresholds were used as fallback */
  customTirFallback: boolean
}

export interface ReportSummaryResponse {
  /** Patient display name */
  displayName: string
  /** Number of days analysed */
  daysAnalysed: number
  /** Total CGM readings in the period */
  cgmReadingCount: number
  /** CGM measurement interval in minutes (typically 5) */
  cgmIntervalMinutes: number
  /** Insulin type(s) from active profile(s) */
  insulinTypes: string[]
  /** Number of insulin reservoir / cartridge changes */
  insulinChanges: number
  /** Average days between cartridge changes (null when fewer than 2 changes) */
  avgDaysPerCartridge: number | null
  /** Number of infusion site changes */
  siteChanges: number
  /** Average days between site changes (null when fewer than 2 changes) */
  avgDaysPerSite: number | null
  /** Number of CGM sensor insertions */
  sensorInserts: number
  /** Average days between sensor insertions (null when fewer than 2 insertions) */
  avgDaysPerSensor: number | null
  /** TIR using custom profile targets (or standard fallback if no profile) */
  tirProfile: TirResult
  /** TIR using standard thresholds: <54 / 54–70 / 70–180 / 180–250 / >250 */
  tirStandard: TirResult
  /** Minimum glucose reading in mg/dL (null when no data) */
  minGlucose: number | null
  /** Maximum glucose reading in mg/dL (null when no data) */
  maxGlucose: number | null
  /** Mean glucose in mg/dL (null when no data) */
  meanGlucose: number | null
  /** Standard deviation of glucose in mg/dL (null when no data) */
  sd: number | null
  /** Glucose Variability Index (null when no data) */
  gvi: number | null
  /** Patient Glycaemic Status (null when no data) */
  pgs: number | null
  /** Glycaemia Risk Index 0–100 (null when no data) */
  gri: number | null
  /** GRI zone letter A–E (null when gri is null) */
  griZone: string | null
  /** Estimated HbA1c % (null when no data) */
  eHbA1c: number | null
  /** Average carbohydrate intake per day in grams */
  avgCarbsPerDayG: number | null
  /** Average bolus insulin per day in IE */
  avgBolusPerDayIe: number | null
  /** Bolus as a percent of total insulin */
  bolusPercent: number | null
  /** Average basal insulin per day in IE */
  avgBasalPerDayIe: number | null
  /** Basal as a percent of total insulin */
  basalPercent: number | null
  /** Average total insulin (bolus + basal) per day in IE */
  avgTotalInsulinPerDayIe: number | null
  /** Advisory warnings (e.g. 'lessThan14Days') */
  warnings: string[]
}

export const analyzeApi = {
  getTimeline: (userId: string, from: string, to: string) =>
    axiosInstance.get<TimelineResponse>(`${BASE}/users/${userId}/timeline`, {
      params: { from, to },
    }),
  getHba1c: (userId: string, from: string, to: string, glucoseUnit?: string) =>
    axiosInstance.get<Hba1cResponse>(`${BASE}/users/${userId}/analytics/hba1c`, {
      params: { from, to, glucoseUnit },
    }),
  getAgp: (userId: string, from: string, to: string, glucoseUnit?: string) =>
    axiosInstance.get<AgpResponse>(`${BASE}/users/${userId}/analytics/agp`, {
      params: { from, to, glucoseUnit },
    }),
  getActiveProfiles: (userId: string, from: string, to: string) =>
    axiosInstance.get<ProfilesResponse>(`${BASE}/users/${userId}/profiles/active`, {
      params: { from, to },
    }),
  getDeviceUsage: (userId: string, days?: number) =>
    axiosInstance.get<DeviceUsageResponse>(`${BASE}/users/${userId}/analytics/device-usage`, {
      params: days !== undefined ? { days } : undefined,
    }),
  getDeviceAge: (userId: string) =>
    axiosInstance.get<DeviceAgeResponse>(`${BASE}/users/${userId}/device-age`),
  getLatestDeviceStatus: (userId: string) =>
    axiosInstance.get<DeviceStatusResponse>(`${BASE}/users/${userId}/device-status`),
  getDailyStats: (userId: string, from: string, to: string, glucoseUnit?: string) =>
    axiosInstance.get<DailyStatsResponse>(`${BASE}/users/${userId}/analytics/daily-stats`, {
      params: { from, to, glucoseUnit },
    }),
  getDailyTrend: (userId: string, from: string, to: string, glucoseUnit?: string) =>
    axiosInstance.get<DailyTrendResponse>(`${BASE}/users/${userId}/analytics/daily-trend`, {
      params: { from, to, glucoseUnit },
    }),
  getGlucoseDistribution: (userId: string, from: string, to: string, glucoseUnit?: string) =>
    axiosInstance.get<GlucoseDistributionResponse>(`${BASE}/users/${userId}/analytics/glucose-distribution`, {
      params: { from, to, glucoseUnit },
    }),
  getCgp: (userId: string, from: string, to: string, glucoseUnit?: string) =>
    axiosInstance.get<CgpResponse>(`${BASE}/users/${userId}/analytics/cgp`, {
      params: { from, to, glucoseUnit },
    }),
  getReportSummary: (
    userId: string,
    from: string,
    to: string,
    glucoseUnit?: string,
    displayName?: string,
  ) =>
    axiosInstance.get<ReportSummaryResponse>(`${BASE}/users/${userId}/analytics/report-summary`, {
      params: { from, to, glucoseUnit, ...(displayName !== undefined && { displayName }) },
    }),
}
