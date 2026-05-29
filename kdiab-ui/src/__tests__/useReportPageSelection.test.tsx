import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'

vi.mock('../api/usersApi', () => ({
  usersApi: {
    getMe: vi.fn().mockResolvedValue({ data: { settings: {} } }),
    patchMySettings: vi.fn().mockResolvedValue({ data: {} }),
  },
}))

import { usersApi } from '../api/usersApi'
import { useReportPageSelection } from '../features/report/useReportPageSelection'
import { ALL_PAGES_SELECTED, REPORT_PAGE_IDS } from '../features/report/reportPages'

const mockedPatch = vi.mocked(usersApi.patchMySettings)

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

// Helpers for mocking localStorage
const mockStorage: Record<string, string> = {}
const localStorageMock = {
  getItem: (key: string) => mockStorage[key] ?? null,
  setItem: (key: string, value: string) => { mockStorage[key] = value },
  removeItem: (key: string) => { delete mockStorage[key] },
  clear: () => { Object.keys(mockStorage).forEach(k => delete mockStorage[k]) },
  length: 0,
  key: vi.fn(),
}

beforeEach(() => {
  vi.clearAllMocks()
  Object.keys(mockStorage).forEach(k => delete mockStorage[k])
  vi.stubGlobal('localStorage', localStorageMock)
})

describe('useReportPageSelection', () => {
  test('falls back to all-selected when no localStorage preference and no server preference', () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    expect(result.current.selectedPages).toEqual(ALL_PAGES_SELECTED)
  })

  test('always includes SUMMARY in selectedPages', () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    expect(result.current.selectedPages).toContain('SUMMARY')
  })

  test('togglePage removes a page that is currently selected', () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    act(() => { result.current.togglePage('AGP') })
    expect(result.current.selectedPages).not.toContain('AGP')
  })

  test('togglePage adds a page that is not currently selected', () => {
    // Start with only SUMMARY selected
    mockStorage['kdiab.reportPageSelection'] = JSON.stringify(['SUMMARY'])
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    act(() => { result.current.togglePage('AGP') })
    expect(result.current.selectedPages).toContain('AGP')
  })

  test('togglePage(SUMMARY) is a no-op — SUMMARY cannot be removed', () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    const before = [...result.current.selectedPages]
    act(() => { result.current.togglePage('SUMMARY') })
    expect(result.current.selectedPages).toEqual(before)
  })

  test('selectAll restores all pages', () => {
    mockStorage['kdiab.reportPageSelection'] = JSON.stringify(['SUMMARY'])
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    act(() => { result.current.selectAll() })
    REPORT_PAGE_IDS.forEach(id => {
      expect(result.current.selectedPages).toContain(id)
    })
  })

  test('deselectAll leaves only SUMMARY selected', () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    act(() => { result.current.deselectAll() })
    expect(result.current.selectedPages).toEqual(['SUMMARY'])
  })

  test('persists selection to localStorage on toggle', () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    act(() => { result.current.togglePage('AGP') })
    const stored = JSON.parse(mockStorage['kdiab.reportPageSelection'] ?? '[]') as string[]
    expect(stored).not.toContain('AGP')
  })

  test('sends PATCH to UserSettings when selection changes', async () => {
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    act(() => { result.current.togglePage('AGP') })
    await waitFor(() => expect(mockedPatch).toHaveBeenCalled())
  })

  test('restores selection from localStorage on mount', () => {
    mockStorage['kdiab.reportPageSelection'] = JSON.stringify(['SUMMARY', 'AGP'])
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    expect(result.current.selectedPages).toContain('SUMMARY')
    expect(result.current.selectedPages).toContain('AGP')
    expect(result.current.selectedPages).not.toContain('DAILY_STATS')
  })

  test('ignores invalid page IDs in localStorage', () => {
    mockStorage['kdiab.reportPageSelection'] = JSON.stringify(['SUMMARY', 'INVALID_PAGE', 'AGP'])
    const { result } = renderHook(() => useReportPageSelection(undefined), { wrapper })
    expect(result.current.selectedPages).not.toContain('INVALID_PAGE')
    expect(result.current.selectedPages).toContain('AGP')
  })

  test('uses server selection when no localStorage preference exists', async () => {
    // No localStorage set — server supplies a selection
    const serverSelection = ['SUMMARY', 'AGP', 'DAILY_STATS']
    const { result } = renderHook(
      () => useReportPageSelection(serverSelection),
      { wrapper },
    )
    await waitFor(() => {
      expect(result.current.selectedPages).toContain('AGP')
      expect(result.current.selectedPages).toContain('DAILY_STATS')
    })
  })

  test('always adds SUMMARY to server selection even if missing', async () => {
    // Server selection without SUMMARY (should not happen in practice, but guard against it)
    const serverSelection = ['AGP', 'DAILY_STATS']
    const { result } = renderHook(
      () => useReportPageSelection(serverSelection),
      { wrapper },
    )
    await waitFor(() => {
      expect(result.current.selectedPages).toContain('SUMMARY')
    })
  })
})
