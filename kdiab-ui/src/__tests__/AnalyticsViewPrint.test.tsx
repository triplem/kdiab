import { render, screen, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import '../i18n'

vi.mock('../api/analyzeApi', () => ({
  analyzeApi: {
    getAgp: vi.fn().mockResolvedValue({ data: null }),
    getHba1c: vi.fn().mockResolvedValue({ data: null }),
    getTimeline: vi.fn().mockResolvedValue({ data: { measures: [], treatments: [] } }),
    getActiveProfiles: vi.fn().mockResolvedValue({ data: { profiles: [] } }),
  },
}))

vi.mock('../features/analytics/AgpChart', () => ({
  AgpChart: () => <div data-testid="agp-chart" />,
}))
vi.mock('../features/analytics/HbA1cCard', () => ({
  HbA1cCard: () => <div data-testid="hba1c-card" />,
}))
vi.mock('../features/analytics/TimeInRangeBar', () => ({
  TimeInRangeBar: () => <div data-testid="tir-bar" />,
}))
vi.mock('../features/analytics/BasalAvgChart', () => ({
  BasalAvgChart: () => <div data-testid="basal-chart" />,
}))
vi.mock('../features/analytics/BolusAvgChart', () => ({
  BolusAvgChart: () => <div data-testid="bolus-chart" />,
}))
vi.mock('../features/analytics/ProfilesView', () => ({
  ProfilesView: () => <div data-testid="profiles-view" />,
}))

import { AnalyticsView } from '../features/analytics/AnalyticsView'

function renderAnalytics(patientName?: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AnalyticsView
        userId="user-1"
        glucoseUnit="mg/dL"
        {...(patientName !== undefined ? { patientName } : {})}
      />
    </QueryClientProvider>,
  )
}

describe('AnalyticsView — print / export', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('print', vi.fn())
  })

  test('renders Print / Export PDF button', () => {
    renderAnalytics('sarah')
    expect(screen.getByRole('button', { name: /Print \/ Export PDF/i })).toBeInTheDocument()
  })

  test('clicking Print / Export PDF calls window.print', () => {
    renderAnalytics('sarah')
    const btn = screen.getByRole('button', { name: /Print \/ Export PDF/i })
    fireEvent.click(btn)
    expect(window.print).toHaveBeenCalledOnce()
  })

  test('PrintHeader is rendered with patient name', () => {
    renderAnalytics('sarah')
    expect(screen.getByText(/sarah/)).toBeInTheDocument()
  })

  test('PrintHeader shows date range separating the two formatted dates', () => {
    renderAnalytics('mike')
    const content = document.body.textContent ?? ''
    expect(content).toContain('mike')
    // Separator between dates
    expect(content).toContain('–')
  })

  test('works without patientName prop — falls back to userId', () => {
    renderAnalytics(undefined)
    expect(screen.getByText(/user-1/)).toBeInTheDocument()
  })
})
