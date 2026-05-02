import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';

interface TimeFormatContextProps {
  is24Hour: boolean;
  locale: string;
  formatTime: (timeStr: string) => string;
  formatDate: (isoStr: string) => string;
}

const TimeFormatContext = createContext<TimeFormatContextProps | undefined>(undefined);

/**
 * Detects whether the user's locale uses a 24-hour clock by probing Intl.DateTimeFormat.
 * Returns true (24h) by default, which is the correct default for most of the world.
 */
function detectIs24Hour(locale: string): boolean {
  try {
    const opts = new Intl.DateTimeFormat(locale, { hour: 'numeric' }).resolvedOptions();
    return opts.hourCycle !== 'h11' && opts.hourCycle !== 'h12';
  } catch {
    return true; // Safe default: 24h
  }
}

export function TimeFormatProvider({ children }: { children: ReactNode }) {
  // On initial load: prefer navigator.language (OS/browser locale) so the static lang="en"
  // in index.html doesn't override the user's actual system locale.
  const getInitialLocale = () => navigator.language || document.documentElement.lang || 'en';

  // When Keycloak explicitly sets document.documentElement.lang, trust that value first.
  const getObservedLocale = () => document.documentElement.lang || navigator.language || 'en';

  const [locale, setLocale] = useState<string>(getInitialLocale);
  const [is24Hour, setIs24Hour] = useState<boolean>(() => detectIs24Hour(getInitialLocale()));

  useEffect(() => {
    // Re-detect when App.tsx updates document.documentElement.lang from Keycloak profile
    const observer = new MutationObserver(() => {
      const newLocale = getObservedLocale();
      setLocale(newLocale);
      setIs24Hour(detectIs24Hour(newLocale));
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });
    return () => observer.disconnect();
  }, []);

  const formatTime = (timeStr: string): string => {
    if (!timeStr || !timeStr.includes(':')) return timeStr;
    if (is24Hour) return timeStr;
    const [h, m] = timeStr.split(':');
    let hour = parseInt(h, 10);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12 || 12;
    return `${hour}:${m} ${ampm}`;
  };

  const formatDate = (isoStr: string): string => {
    if (!isoStr) return 'N/A';
    try {
      return new Date(isoStr).toLocaleString(locale, {
        dateStyle: 'short',
        timeStyle: 'short',
        hour12: !is24Hour,
      });
    } catch {
      return isoStr;
    }
  };

  return (
    <TimeFormatContext.Provider value={{ is24Hour, locale, formatTime, formatDate }}>
      {children}
    </TimeFormatContext.Provider>
  );
}

export function useTimeFormat() {
  const context = useContext(TimeFormatContext);
  if (!context) {
    throw new Error('useTimeFormat must be used within a TimeFormatProvider');
  }
  return context;
}
