import { describe, it, expect } from 'vitest'
import { renderDataSummary } from '../features/measures/MeasureList'
import type { MeasureResponse } from '../api/generated'

function measure(type: MeasureResponse['type'], data: Record<string, unknown>): MeasureResponse {
  return {
    id: 'test-id',
    userId: 'user-id',
    measuredAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
    type,
    source: 'MANUAL',
    data,
    status: 'ACTIVE',
  }
}

describe('renderDataSummary', () => {
  describe('CGM', () => {
    it('renders value with unit and trend', () => {
      expect(renderDataSummary(measure('CGM', { value: 120, unit: 'mg/dL', trend: 'Flat' }))).toBe('120 mg/dL (Flat)')
    })
    it('renders value with mmol/L unit', () => {
      expect(renderDataSummary(measure('CGM', { value: 6.7, unit: 'mmol/L', trend: 'FortyFiveUp' }))).toBe('6.7 mmol/L (FortyFiveUp)')
    })
    it('defaults unit to mg/dL when absent', () => {
      expect(renderDataSummary(measure('CGM', { value: 110 }))).toBe('110 mg/dL')
    })
    it('falls back to JSON when value is absent', () => {
      const d = { unknown: 42 }
      expect(renderDataSummary(measure('CGM', d))).toBe(JSON.stringify(d))
    })
  })

  describe('BGM', () => {
    it('renders value with unit', () => {
      expect(renderDataSummary(measure('BGM', { value: 95, unit: 'mg/dL' }))).toBe('95 mg/dL')
    })
    it('defaults unit to mg/dL when absent', () => {
      expect(renderDataSummary(measure('BGM', { value: 85 }))).toBe('85 mg/dL')
    })
    it('falls back to JSON when value is absent', () => {
      const d = { unknown: 42 }
      expect(renderDataSummary(measure('BGM', d))).toBe(JSON.stringify(d))
    })
  })

  describe('BLOOD_PRESSURE', () => {
    it('renders systolic/diastolic', () => {
      expect(renderDataSummary(measure('BLOOD_PRESSURE', { systolic: 120, diastolic: 80 }))).toBe('120/80 mmHg')
    })
  })

  describe('WEIGHT', () => {
    it('renders value and unit', () => {
      expect(renderDataSummary(measure('WEIGHT', { value: 75.5, unit: 'kg' }))).toBe('75.5 kg')
    })
  })

  describe('PULSE', () => {
    it('renders bpm', () => {
      expect(renderDataSummary(measure('PULSE', { value: 72 }))).toBe('72 bpm')
    })
  })
})
