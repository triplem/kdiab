import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'

interface TimeFormatContextProps {
  is24Hour: boolean
  locale: string
  formatTime: (timeStr: string) => string
  formatDate: (isoStr: string) => string
}

const TimeFormatContext = createContext<TimeFormatContextProps | undefined>(undefined)

function detectIs24Hour(locale: string): boolean {
  try {
    const opts = new Intl.DateTimeFormat(locale, { hour: 'numeric' }).resolvedOptions()
    return opts.hourCycle !== 'h11' && opts.hourCycle !== 'h12'
  } catch {
    return true
  }
}

export function TimeFormatProvider({ children }: { children: ReactNode }) {
  const getInitialLocale = () => navigator.language || document.documentElement.lang || 'en'
  const getObservedLocale = () => document.documentElement.lang || navigator.language || 'en'

  const [locale, setLocale] = useState<string>(getInitialLocale())
  const [is24Hour, setIs24Hour] = useState<boolean>(() => detectIs24Hour(getInitialLocale()))

  useEffect(() => {
    const observer = new MutationObserver(() => {
      const newLocale = getObservedLocale()
      setLocale(newLocale)
      setIs24Hour(detectIs24Hour(newLocale))
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] })
    return () => observer.disconnect()
  }, [])

  const formatTime = (timeStr: string): string => {
    if (!timeStr || !timeStr.includes(':')) return timeStr
    if (is24Hour) return timeStr
    const [h, m] = timeStr.split(':')
    let hour = parseInt(h, 10)
    const ampm = hour >= 12 ? 'PM' : 'AM'
    hour = hour % 12 || 12
    return `${hour}:${m} ${ampm}`
  }

  const formatDate = (isoStr: string): string => {
    if (!isoStr) return 'N/A'
    try {
      return new Date(isoStr).toLocaleString(locale, {
        dateStyle: 'short',
        timeStyle: 'short',
        hour12: !is24Hour,
      })
    } catch {
      return isoStr
    }
  }

  return (
    <TimeFormatContext.Provider value={{ is24Hour, locale, formatTime, formatDate }}>
      {children}
    </TimeFormatContext.Provider>
  )
}

export function useTimeFormat() {
  const context = useContext(TimeFormatContext)
  if (!context) {
    throw new Error('useTimeFormat must be used within a TimeFormatProvider')
  }
  return context
}
