import { useState, useCallback, useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usersApi } from '../../api/usersApi'
import { ALL_PAGES_SELECTED, REPORT_PAGE_IDS } from './reportPages'
import type { ReportPageId } from './reportPages'

const LOCAL_STORAGE_KEY = 'kdiab.reportPageSelection'

function readFromLocalStorage(): readonly ReportPageId[] | null {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return null
    const valid = parsed.filter((item): item is ReportPageId =>
      typeof item === 'string' && (REPORT_PAGE_IDS as readonly string[]).includes(item),
    )
    // Always ensure SUMMARY is present
    if (!valid.includes('SUMMARY')) valid.unshift('SUMMARY')
    return valid
  } catch {
    return null
  }
}

function writeToLocalStorage(selection: readonly ReportPageId[]): void {
  try {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(selection))
  } catch {
    // localStorage may be unavailable — silently ignore
  }
}

/**
 * Manages report page selection with:
 * - localStorage as optimistic cache (write-through)
 * - UserSettings as persistent server-side storage
 *
 * Falls back to "all pages selected" if no preference is stored.
 *
 * NOTE: The kdiab-users backend `reportPageSelection` field (Wave 2 backend change)
 * may not exist yet. The hook gracefully falls back to localStorage or all-selected
 * when the server field is absent.
 */
export function useReportPageSelection(
  /** Pass UserSettings.reportPageSelection when available */
  serverSelection?: string[] | null,
): {
  selectedPages: readonly ReportPageId[]
  togglePage: (page: ReportPageId) => void
  selectAll: () => void
  deselectAll: () => void
} {
  const queryClient = useQueryClient()

  const [selectedPages, setSelectedPages] = useState<readonly ReportPageId[]>(() => {
    // Priority: localStorage > server > all selected
    const fromStorage = readFromLocalStorage()
    if (fromStorage) return fromStorage
    return ALL_PAGES_SELECTED
  })

  // When server selection arrives, sync if no localStorage preference exists
  useEffect(() => {
    if (!serverSelection) return
    // Validate server-supplied page IDs
    const valid = serverSelection.filter((id): id is ReportPageId =>
      (REPORT_PAGE_IDS as readonly string[]).includes(id),
    )
    if (valid.length === 0) return
    // Only update if localStorage doesn't have a stored preference
    const fromStorage = readFromLocalStorage()
    if (!fromStorage) {
      const withSummary = valid.includes('SUMMARY')
        ? (valid as ReportPageId[])
        : (['SUMMARY' as ReportPageId, ...valid] as ReportPageId[])
      setSelectedPages(withSummary)
    }
  }, [serverSelection])

  const mutation = useMutation({
    mutationFn: (pages: readonly ReportPageId[]) =>
      // TODO(#1118): Remove cast once kdiab-users backend adds reportPageSelection field
      usersApi.patchMySettings({ reportPageSelection: pages as string[] } as Parameters<typeof usersApi.patchMySettings>[0]),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['user', 'me'] })
    },
    // Silently ignore errors — optimistic state already applied
  })

  const applySelection = useCallback(
    (newSelection: readonly ReportPageId[]) => {
      // SUMMARY is always included
      const withSummary = newSelection.includes('SUMMARY')
        ? newSelection
        : (['SUMMARY' as ReportPageId, ...newSelection] as readonly ReportPageId[])
      setSelectedPages(withSummary)
      writeToLocalStorage(withSummary)
      mutation.mutate(withSummary)
    },
    [mutation],
  )

  const togglePage = useCallback(
    (page: ReportPageId) => {
      if (page === 'SUMMARY') return // SUMMARY cannot be toggled off
      setSelectedPages(prev => {
        const newSelection = prev.includes(page)
          ? prev.filter(p => p !== page)
          : [...prev, page]
        writeToLocalStorage(newSelection)
        mutation.mutate(newSelection)
        return newSelection
      })
    },
    [mutation],
  )

  const selectAll = useCallback(() => applySelection(ALL_PAGES_SELECTED), [applySelection])

  const deselectAll = useCallback(
    () => applySelection(['SUMMARY'] as readonly ReportPageId[]),
    [applySelection],
  )

  return { selectedPages, togglePage, selectAll, deselectAll }
}
