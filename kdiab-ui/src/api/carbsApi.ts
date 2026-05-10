import { axiosInstance } from './axiosInstance'

const BASE = '/api/carbs/v1'

export interface FoodEntryResponse {
  id: string
  userId: string
  name: string
  portionGrams: number
  carbsPer100g: number
  carbsForPortion: number
  createdAt: string
  updatedAt: string
}

interface PagedFoods {
  items: FoodEntryResponse[]
  page: number
  size: number
  totalCount: number
}

export const carbsApi = {
  listFoods: (userId: string, page = 0, size = 50, q?: string) =>
    axiosInstance.get<PagedFoods>(`${BASE}/users/${userId}/foods`, {
      params: { page, size, ...(q ? { q } : {}) },
    }),
  createFood: (userId: string, body: { name: string; portionGrams: number; carbsPer100g: number }) =>
    axiosInstance.post<FoodEntryResponse>(`${BASE}/users/${userId}/foods`, body),
  updateFood: (
    userId: string,
    foodId: string,
    body: { name: string; portionGrams: number; carbsPer100g: number },
  ) => axiosInstance.put<FoodEntryResponse>(`${BASE}/users/${userId}/foods/${foodId}`, body),
  deleteFood: (userId: string, foodId: string) =>
    axiosInstance.delete(`${BASE}/users/${userId}/foods/${foodId}`),
}
