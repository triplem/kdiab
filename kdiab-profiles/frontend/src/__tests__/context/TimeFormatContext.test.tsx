import { render, screen, act } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TimeFormatProvider, useTimeFormat } from '../../context/TimeFormatContext';

// Helper component that exposes context values
function Inspector() {
  const { is24Hour, locale } = useTimeFormat();
  return (
    <div>
      <span data-testid="is24hour">{String(is24Hour)}</span>
      <span data-testid="locale">{locale}</span>
    </div>
  );
}

// Saves and restores navigator.language between tests
function setNavigatorLanguage(lang: string): () => void {
  const descriptor = Object.getOwnPropertyDescriptor(navigator, 'language');
  Object.defineProperty(navigator, 'language', { value: lang, configurable: true });
  return () => {
    if (descriptor) {
      Object.defineProperty(navigator, 'language', descriptor);
    } else {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      delete (navigator as any).language;
    }
  };
}

describe('TimeFormatContext', () => {
  let restoreDocLang: () => void;

  beforeEach(() => {
    // index.html always starts with lang="en"; replicate that here
    const originalLang = document.documentElement.lang;
    document.documentElement.lang = 'en';
    restoreDocLang = () => { document.documentElement.lang = originalLang; };
  });

  afterEach(() => {
    restoreDocLang();
  });

  it('uses navigator.language even when document lang is "en"', () => {
    // German browser + static "en" in HTML → should resolve to 24h
    const restore = setNavigatorLanguage('de-DE');
    try {
      render(<TimeFormatProvider><Inspector /></TimeFormatProvider>);
      expect(screen.getByTestId('is24hour').textContent).toBe('true');
      expect(screen.getByTestId('locale').textContent).toBe('de-DE');
    } finally {
      restore();
    }
  });

  it('is 12h when navigator.language is en-US', () => {
    const restore = setNavigatorLanguage('en-US');
    try {
      render(<TimeFormatProvider><Inspector /></TimeFormatProvider>);
      expect(screen.getByTestId('is24hour').textContent).toBe('false');
    } finally {
      restore();
    }
  });

  it('updates to 24h when document.documentElement.lang is set to de by Keycloak', async () => {
    // Start as English browser
    const restore = setNavigatorLanguage('en-US');
    try {
      render(<TimeFormatProvider><Inspector /></TimeFormatProvider>);
      expect(screen.getByTestId('is24hour').textContent).toBe('false');

      // Simulate App.tsx setting the Keycloak locale on the <html> element
      await act(async () => {
        document.documentElement.lang = 'de';
      });

      expect(screen.getByTestId('is24hour').textContent).toBe('true');
      expect(screen.getByTestId('locale').textContent).toBe('de');
    } finally {
      restore();
    }
  });

  it('formatTime returns 24h string when is24Hour is true', () => {
    const restore = setNavigatorLanguage('de-DE');
    try {
      function TimeDisplay() {
        const { formatTime } = useTimeFormat();
        return <span data-testid="time">{formatTime('14:30')}</span>;
      }
      render(<TimeFormatProvider><TimeDisplay /></TimeFormatProvider>);
      // 24h mode: no AM/PM transformation
      expect(screen.getByTestId('time').textContent).toBe('14:30');
    } finally {
      restore();
    }
  });

  it('formatTime returns 12h AM/PM string when is24Hour is false', () => {
    const restore = setNavigatorLanguage('en-US');
    try {
      function TimeDisplay() {
        const { formatTime } = useTimeFormat();
        return <span data-testid="time">{formatTime('14:30')}</span>;
      }
      render(<TimeFormatProvider><TimeDisplay /></TimeFormatProvider>);
      expect(screen.getByTestId('time').textContent).toBe('2:30 PM');
    } finally {
      restore();
    }
  });
});
