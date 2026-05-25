import { describe, test, expect } from 'vitest'
import {
  computeBasalFromProfileSegments,
  computeBasalHourlyAvg,
  computeBolusHourlyAvg,
} from '../features/analytics/insulinHourlyUtils'
import type { TimelineResponse } from '../api/analyzeApi'

// ---------------------------------------------------------------------------
// computeBasalFromProfileSegments
// ---------------------------------------------------------------------------

describe('computeBasalFromProfileSegments', () => {
  test('returns 24 nulls when segments is null', () => {
    const result = computeBasalFromProfileSegments(null)
    expect(result).toHaveLength(24)
    expect(result.every(v => v === null)).toBe(true)
  })

  test('returns 24 nulls when segments is undefined', () => {
    const result = computeBasalFromProfileSegments(undefined)
    expect(result).toHaveLength(24)
    expect(result.every(v => v === null)).toBe(true)
  })

  test('returns 24 nulls when segments array is empty', () => {
    const result = computeBasalFromProfileSegments([])
    expect(result).toHaveLength(24)
    expect(result.every(v => v === null)).toBe(true)
  })

  test('fills all 24 hours with the segment value when only one segment exists at 00:00', () => {
    const result = computeBasalFromProfileSegments([{ startTime: '00:00', value: 0.8 }])
    expect(result).toHaveLength(24)
    expect(result.every(v => v === 0.8)).toBe(true)
  })

  test('correctly assigns rates from two segments (00:00 and 06:00)', () => {
    const result = computeBasalFromProfileSegments([
      { startTime: '00:00', value: 0.85 },
      { startTime: '06:00', value: 1.10 },
    ])
    expect(result).toHaveLength(24)
    // Hours 0-5 → first segment
    for (let h = 0; h <= 5; h++) {
      expect(result[h]).toBe(0.85)
    }
    // Hours 6-23 → second segment
    for (let h = 6; h <= 23; h++) {
      expect(result[h]).toBe(1.10)
    }
  })

  test('handles a typical 4-segment profile correctly', () => {
    const segments = [
      { startTime: '00:00', value: 0.85 },
      { startTime: '06:00', value: 1.10 },
      { startTime: '12:00', value: 0.90 },
      { startTime: '18:00', value: 1.00 },
    ]
    const result = computeBasalFromProfileSegments(segments)
    expect(result).toHaveLength(24)
    expect(result[0]).toBe(0.85)
    expect(result[5]).toBe(0.85)
    expect(result[6]).toBe(1.10)
    expect(result[11]).toBe(1.10)
    expect(result[12]).toBe(0.90)
    expect(result[17]).toBe(0.90)
    expect(result[18]).toBe(1.00)
    expect(result[23]).toBe(1.00)
  })

  test('handles segments provided in unsorted order', () => {
    const segments = [
      { startTime: '12:00', value: 0.90 },
      { startTime: '00:00', value: 0.85 },
      { startTime: '06:00', value: 1.10 },
    ]
    const result = computeBasalFromProfileSegments(segments)
    expect(result[0]).toBe(0.85)
    expect(result[6]).toBe(1.10)
    expect(result[12]).toBe(0.90)
  })

  test('returns all 24 values as numbers (no nulls) when a 00:00 segment exists', () => {
    const result = computeBasalFromProfileSegments([
      { startTime: '00:00', value: 0.70 },
      { startTime: '20:00', value: 0.65 },
    ])
    expect(result.every(v => v !== null)).toBe(true)
  })

  test('wraps around to last segment for hours before the first segment start', () => {
    // If only a segment at 06:00 is defined, hours 0-5 should use the last segment
    const result = computeBasalFromProfileSegments([{ startTime: '06:00', value: 1.10 }])
    // Wrap-around: hours before 06 get the last (only) segment's value
    expect(result[0]).toBe(1.10)
    expect(result[5]).toBe(1.10)
    expect(result[6]).toBe(1.10)
  })

  test('treats a malformed startTime (NaN hours) as hour 0', () => {
    // A segment with a non-numeric hour string must not produce NaN in the output.
    const result = computeBasalFromProfileSegments([{ startTime: 'xx:00', value: 0.9 }])
    expect(result).toHaveLength(24)
    result.forEach(v => expect(typeof v === 'number' && !isNaN(v)).toBe(true))
    // All hours should fall back to the malformed segment (treated as 00:00)
    expect(result[0]).toBe(0.9)
  })
})

// ---------------------------------------------------------------------------
// computeBasalHourlyAvg (existing function — regression guard)
// ---------------------------------------------------------------------------

const makeTimeline = (treatments: TimelineResponse['treatments']): TimelineResponse => ({
  measures: [],
  treatments,
})

describe('computeBasalHourlyAvg', () => {
  test('returns 24 nulls when no TEMP_BASAL treatments exist', () => {
    const result = computeBasalHourlyAvg(makeTimeline([]))
    expect(result).toHaveLength(24)
    expect(result.every(v => v === null)).toBe(true)
  })

  test('computes average rate per UTC hour from TEMP_BASAL treatments', () => {
    const treatments: TimelineResponse['treatments'] = [
      { id: '1', treatedAt: '2024-01-01T06:30:00Z', type: 'TEMP_BASAL', data: { rate: 0.5 } },
      { id: '2', treatedAt: '2024-01-02T06:45:00Z', type: 'TEMP_BASAL', data: { rate: 1.0 } },
    ]
    const result = computeBasalHourlyAvg(makeTimeline(treatments))
    expect(result[6]).toBe(0.75) // average of 0.5 and 1.0
    expect(result[0]).toBeNull()
  })

  test('ignores non-TEMP_BASAL treatments', () => {
    const treatments: TimelineResponse['treatments'] = [
      { id: '1', treatedAt: '2024-01-01T06:00:00Z', type: 'BOLUS', data: { insulin: 2.0 } },
    ]
    const result = computeBasalHourlyAvg(makeTimeline(treatments))
    expect(result.every(v => v === null)).toBe(true)
  })
})

// ---------------------------------------------------------------------------
// computeBolusHourlyAvg (existing function — regression guard)
// ---------------------------------------------------------------------------

describe('computeBolusHourlyAvg', () => {
  test('returns 24 nulls when no BOLUS treatments exist', () => {
    const result = computeBolusHourlyAvg(makeTimeline([]))
    expect(result).toHaveLength(24)
    expect(result.every(v => v === null)).toBe(true)
  })

  test('computes average insulin per UTC hour from BOLUS and CORRECTION_BOLUS treatments', () => {
    const treatments: TimelineResponse['treatments'] = [
      { id: '1', treatedAt: '2024-01-01T08:00:00Z', type: 'BOLUS', data: { insulin: 4.0 } },
      { id: '2', treatedAt: '2024-01-02T08:30:00Z', type: 'CORRECTION_BOLUS', data: { insulin: 2.0 } },
    ]
    const result = computeBolusHourlyAvg(makeTimeline(treatments))
    expect(result[8]).toBe(3.0) // average of 4.0 and 2.0
    expect(result[0]).toBeNull()
  })
})
