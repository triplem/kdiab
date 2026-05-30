import { axiosInstance } from './axiosInstance'

const BASE = '/api/profiles/v1'

export interface ProfileSegment {
  startTime: string
  value: number
}

export interface TargetSegment {
  startTime: string
  low: number
  high: number
}

export interface Profile {
  id: string
  userId: string
  name: string
  status: string
  insulinType?: string
  durationOfAction?: number
  timeZone?: string
  basal?: ProfileSegment[]
  icr?: ProfileSegment[]
  isf?: ProfileSegment[]
  targets?: TargetSegment[]
  createdAt?: string
  activatedAt?: string
  archivedAt?: string
  previousProfileId?: string
  proposalReason?: string | null
  createdBy?: string | null
  rejectionReason?: string | null
}

export interface Insulin {
  id: string
  name: string
}

export interface PagedProfiles {
  items: Profile[]
  page: number
  size: number
  totalCount: number
}

export const profilesApi = {
  listProfiles: (userId: string, status?: string[]) =>
    axiosInstance.get<PagedProfiles>(`${BASE}/users/${userId}/profiles`, {
      params: status ? { status } : undefined,
    }),
  createProfile: (userId: string, body: Record<string, unknown>) =>
    axiosInstance.post<Profile>(`${BASE}/users/${userId}/profiles`, body),
  updateProfile: (userId: string, profileId: string, body: Record<string, unknown>) =>
    axiosInstance.put<Profile>(`${BASE}/users/${userId}/profiles/${profileId}`, body),
  activateProfile: (userId: string, profileId: string) =>
    axiosInstance.post(`${BASE}/users/${userId}/profiles/${profileId}/activate`),
  acceptProposedProfile: (userId: string, profileId: string) =>
    axiosInstance.post(`${BASE}/users/${userId}/profiles/${profileId}/accept`),
  rejectProposedProfile: (userId: string, profileId: string, reason?: string) =>
    axiosInstance.post(
      `${BASE}/users/${userId}/profiles/${profileId}/reject`,
      reason != null ? { reason } : undefined,
    ),
  getProfileHistory: (userId: string, from: string, to: string) =>
    axiosInstance.get<Profile[]>(`${BASE}/users/${userId}/profiles/history`, {
      params: { from, to },
    }),
  getInsulins: () => axiosInstance.get<Insulin[]>(`${BASE}/insulins`),
  createInsulin: (body: { name: string }) =>
    axiosInstance.post<Insulin>(`${BASE}/insulins`, body),
  updateInsulin: (id: string, body: { name: string }) =>
    axiosInstance.put<Insulin>(`${BASE}/insulins/${id}`, body),
  deleteInsulin: (id: string) => axiosInstance.delete(`${BASE}/insulins/${id}`),
}
