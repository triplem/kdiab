import { useQuery } from '@tanstack/react-query'
import { useState, useEffect, useMemo } from 'react'
import { analyzeApi } from '../../api/analyzeApi'
import { profilesApi } from '../../api/profilesApi'
import { treatmentsApi } from '../../api/treatmentsApi'
import { usersApi } from '../../api/usersApi'
import { WINDOWS } from './basalUtils'

export function useDashboardData(userId: string) {
  const [windowKey, setWindowKey] = useState('6h')
  const [windowEndOffset, setWindowEndOffset] = useState(0) // ms shift from "now" (0 = current)

  const windowHours = WINDOWS.find(w => w.key === windowKey)?.hours ?? 6
  const windowMs = windowHours * 60 * 60 * 1000

  const { windowEnd, windowFrom, windowTo } = useMemo(() => {
    const end = new Date(Date.now() - windowEndOffset)
    const start = new Date(end.getTime() - windowMs)
    return {
      windowEnd: end,
      windowFrom: start.toISOString(),
      windowTo: end.toISOString(),
    }
  }, [windowMs, windowEndOffset])

  const atNow = windowEndOffset === 0

  // Rolling "now" that refreshes every 5 min so the 6h IOB/COB window doesn't freeze at mount
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 5 * 60 * 1000)
    return () => clearInterval(id)
  }, [])
  const sixHoursAgo = useMemo(() => new Date(now.getTime() - 6 * 60 * 60 * 1000).toISOString(), [now])
  const nowIso = useMemo(() => now.toISOString(), [now])

  const { data: recentTimeline } = useQuery({
    queryKey: ['dashboard-recent', userId],
    queryFn: () => analyzeApi.getTimeline(userId, sixHoursAgo, nowIso).then(r => r.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
    refetchInterval: 5 * 60 * 1000,
  })

  const { data: windowTimeline, isLoading } = useQuery({
    queryKey: ['dashboard-window', userId, windowFrom, windowTo],
    queryFn: () => analyzeApi.getTimeline(userId, windowFrom, windowTo).then(r => r.data),
    enabled: !!userId,
    staleTime: 2 * 60 * 1000,
  })

  const { data: profiles } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: () => profilesApi.listProfiles(userId).then(r => r.data.items),
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  const { data: deviceAge } = useQuery({
    queryKey: ['device-age', userId],
    queryFn: () => treatmentsApi.getDeviceAge(userId).then(r => r.data),
    enabled: !!userId,
    staleTime: 10 * 60 * 1000,
  })

  const { data: deviceStatus } = useQuery({
    queryKey: ['device-status', userId],
    queryFn: () => treatmentsApi.getLatestDeviceStatus(userId).then(r => r.data).catch(() => null),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  })

  const { data: userMe } = useQuery({
    queryKey: ['user', 'me'],
    queryFn: () => usersApi.getMe().then(r => r.data),
    staleTime: 10 * 60 * 1000,
  })

  const activeProfile = profiles?.find(p => p.status === 'ACTIVE')

  return {
    // Window state
    windowKey,
    setWindowKey,
    windowEndOffset,
    setWindowEndOffset,
    windowHours,
    windowMs,
    windowEnd,
    windowFrom,
    windowTo,
    atNow,
    // Data
    recentTimeline,
    windowTimeline,
    isLoading,
    activeProfile,
    deviceAge,
    deviceStatus,
    userMe,
  }
}
