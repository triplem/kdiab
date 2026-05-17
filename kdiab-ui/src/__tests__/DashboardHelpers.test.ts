import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  toDisplay,
  glucoseColor,
  trendArrow,
  daysSince,
  sensorExpiryLabel,
  calcIOB,
  calcCOB,
  segToMin,
  scheduledRateAt,
  currentBasalRate,
} from '../features/dashboard/basalUtils'

// Fixed reference time for deterministic tests
const NOW = new Date('2024-06-01T10:00:00Z').getTime()

describe('toDisplay', () => {
  test('returns rounded mg/dL when unit is mg/dL', () => {
    expect(toDisplay(180, 'mg/dL')).toBe(180)
  })

  test('converts to mmol/L and rounds to 1 decimal', () => {
    expect(toDisplay(180, 'mmol/L')).toBeCloseTo(10.0, 1)
  })

  test('rounds fractional mg/dL', () => {
    expect(toDisplay(99.7, 'mg/dL')).toBe(100)
  })

  test('converts low value to mmol/L', () => {
    expect(toDisplay(54, 'mmol/L')).toBeCloseTo(3.0, 1)
  })
})

describe('glucoseColor', () => {
  test('returns danger color for hypoglycaemia (< 70)', () => {
    expect(glucoseColor(54)).toBe('var(--accent-danger)')
    expect(glucoseColor(69)).toBe('var(--accent-danger)')
  })

  test('returns success color for target range (70–180)', () => {
    expect(glucoseColor(70)).toBe('var(--accent-success)')
    expect(glucoseColor(120)).toBe('var(--accent-success)')
    expect(glucoseColor(180)).toBe('var(--accent-success)')
  })

  test('returns warning color for hyperglycaemia (> 180)', () => {
    expect(glucoseColor(181)).toBe('var(--accent-warning)')
    expect(glucoseColor(250)).toBe('var(--accent-warning)')
  })
})

describe('trendArrow', () => {
  test('returns correct arrow for DoubleUp', () => {
    expect(trendArrow('DoubleUp')).toBe('↑↑')
  })

  test('returns correct arrow for SingleUp', () => {
    expect(trendArrow('SingleUp')).toBe('↑')
  })

  test('returns correct arrow for FortyFiveUp', () => {
    expect(trendArrow('FortyFiveUp')).toBe('↗')
  })

  test('returns correct arrow for Flat', () => {
    expect(trendArrow('Flat')).toBe('→')
  })

  test('returns correct arrow for FortyFiveDown', () => {
    expect(trendArrow('FortyFiveDown')).toBe('↘')
  })

  test('returns correct arrow for SingleDown', () => {
    expect(trendArrow('SingleDown')).toBe('↓')
  })

  test('returns correct arrow for DoubleDown', () => {
    expect(trendArrow('DoubleDown')).toBe('↓↓')
  })

  test('returns empty string for unknown trend', () => {
    expect(trendArrow('Unknown')).toBe('')
    expect(trendArrow(null)).toBe('')
    expect(trendArrow(undefined)).toBe('')
    expect(trendArrow(42)).toBe('')
  })
})

describe('daysSince', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('returns em dash for undefined input', () => {
    expect(daysSince(undefined)).toBe('—')
  })

  test('returns 0.0 d for now', () => {
    expect(daysSince(new Date(NOW).toISOString())).toBe('0.0 d')
  })

  test('returns 1.0 d for exactly 24 hours ago', () => {
    const oneDay = new Date(NOW - 86400000).toISOString()
    expect(daysSince(oneDay)).toBe('1.0 d')
  })

  test('returns 3.5 d for 84 hours ago', () => {
    const halfweek = new Date(NOW - 3.5 * 86400000).toISOString()
    expect(daysSince(halfweek)).toBe('3.5 d')
  })
})

describe('sensorExpiryLabel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('returns em dash for undefined insertedAt', () => {
    expect(sensorExpiryLabel(undefined, 168)).toBe('—')
  })

  test('returns "expired" when sensor lifetime has passed', () => {
    const inserted = new Date(NOW - 200 * 3600000).toISOString()
    expect(sensorExpiryLabel(inserted, 168)).toBe('expired')
  })

  test('returns remaining hours when sensor is still active', () => {
    const inserted = new Date(NOW - 24 * 3600000).toISOString() // inserted 24h ago
    expect(sensorExpiryLabel(inserted, 168)).toBe('exp 144h')   // 168 - 24 = 144h remaining
  })

  test('returns "expired" when exactly at expiry', () => {
    const inserted = new Date(NOW - 168 * 3600000).toISOString()
    expect(sensorExpiryLabel(inserted, 168)).toBe('expired')
  })
})

describe('calcIOB', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const DIA = 240 // 4 hours

  test('returns 0 for empty treatment list', () => {
    expect(calcIOB([], DIA)).toBe(0)
  })

  test('returns 0 for non-insulin treatment types', () => {
    const treatments = [{ treatedAt: new Date(NOW - 30 * 60000).toISOString(), type: 'CARBS', data: { insulin: 5 } }]
    expect(calcIOB(treatments, DIA)).toBe(0)
  })

  test('returns full insulin amount for BOLUS administered just now', () => {
    const treatments = [{ treatedAt: new Date(NOW).toISOString(), type: 'BOLUS', data: { insulin: 10 } }]
    expect(calcIOB(treatments, DIA)).toBeCloseTo(10, 5)
  })

  test('returns 75% insulin amount for BOLUS at halfway through DIA (parabolic decay)', () => {
    const halfDia = new Date(NOW - (DIA / 2) * 60000).toISOString()
    const treatments = [{ treatedAt: halfDia, type: 'BOLUS', data: { insulin: 10 } }]
    // Parabolic (Scheiner): remaining = 1 - (0.5)^2 = 0.75 → 10 * 0.75 = 7.5
    expect(calcIOB(treatments, DIA)).toBeCloseTo(7.5, 5)
  })

  test('returns 0 for BOLUS older than DIA', () => {
    const old = new Date(NOW - (DIA + 1) * 60000).toISOString()
    const treatments = [{ treatedAt: old, type: 'BOLUS', data: { insulin: 5 } }]
    expect(calcIOB(treatments, DIA)).toBe(0)
  })

  test('includes COMBO_BOLUS in IOB calculation', () => {
    const treatments = [{ treatedAt: new Date(NOW).toISOString(), type: 'COMBO_BOLUS', data: { insulin: 8 } }]
    expect(calcIOB(treatments, DIA)).toBeCloseTo(8, 5)
  })

  test('sums multiple boluses', () => {
    const treatments = [
      { treatedAt: new Date(NOW - 60 * 60000).toISOString(), type: 'BOLUS', data: { insulin: 4 } },
      { treatedAt: new Date(NOW - 60 * 60000).toISOString(), type: 'CORRECTION_BOLUS', data: { insulin: 2 } },
    ]
    const iob = calcIOB(treatments, DIA)
    expect(iob).toBeGreaterThan(0)
  })

  test('skips future treatments (negative minutes)', () => {
    const future = new Date(NOW + 30 * 60000).toISOString()
    const treatments = [{ treatedAt: future, type: 'BOLUS', data: { insulin: 5 } }]
    expect(calcIOB(treatments, DIA)).toBe(0)
  })
})

describe('calcCOB', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('returns 0 for empty treatment list', () => {
    expect(calcCOB([])).toBe(0)
  })

  test('returns 0 for non-carb treatment types', () => {
    const treatments = [{ treatedAt: new Date(NOW - 30 * 60000).toISOString(), type: 'BOLUS', data: { carbs: 30 } }]
    expect(calcCOB(treatments)).toBe(0)
  })

  test('returns full carbs for CARBS treatment just now', () => {
    const treatments = [{ treatedAt: new Date(NOW).toISOString(), type: 'CARBS', data: { carbs: 40 } }]
    expect(calcCOB(treatments)).toBeCloseTo(40, 5)
  })

  test('returns half carbs at halfway through default 180 min absorption', () => {
    const half = new Date(NOW - 90 * 60000).toISOString()
    const treatments = [{ treatedAt: half, type: 'CARBS', data: { carbs: 60 } }]
    expect(calcCOB(treatments)).toBeCloseTo(30, 5)
  })

  test('uses absorptionTime field when present', () => {
    const half = new Date(NOW - 60 * 60000).toISOString() // 60 min ago
    const treatments = [{ treatedAt: half, type: 'CARBS', data: { carbs: 50, absorptionTime: 2 } }] // 2h = 120min
    // 60 min elapsed out of 120 min → 50% remaining = 25g
    expect(calcCOB(treatments)).toBeCloseTo(25, 5)
  })

  test('returns 0 for fully absorbed carbs', () => {
    const old = new Date(NOW - 200 * 60000).toISOString()
    const treatments = [{ treatedAt: old, type: 'CARBS', data: { carbs: 30 } }]
    expect(calcCOB(treatments)).toBe(0)
  })

  test('handles HYPO_TREATMENT type', () => {
    const treatments = [{ treatedAt: new Date(NOW).toISOString(), type: 'HYPO_TREATMENT', data: { carbs: 15 } }]
    expect(calcCOB(treatments)).toBeCloseTo(15, 5)
  })
})

describe('segToMin', () => {
  test('converts 00:00 to 0', () => {
    expect(segToMin('00:00')).toBe(0)
  })

  test('converts 06:00 to 360', () => {
    expect(segToMin('06:00')).toBe(360)
  })

  test('converts 12:30 to 750', () => {
    expect(segToMin('12:30')).toBe(750)
  })

  test('converts 23:59 to 1439', () => {
    expect(segToMin('23:59')).toBe(1439)
  })
})

// scheduledRateAt uses d.getHours() (local time), so tests must build timestamps
// using local midnight + offset to remain correct across all timezones.
function localTimeMs(hours: number, minutes = 0): number {
  const d = new Date()
  d.setHours(hours, minutes, 0, 0)
  return d.getTime()
}

describe('scheduledRateAt', () => {
  const profile = [
    { startTime: '00:00', value: 0.8 },
    { startTime: '06:00', value: 1.2 },
    { startTime: '12:00', value: 0.9 },
    { startTime: '18:00', value: 1.0 },
  ]

  test('returns midnight rate at 00:00 local', () => {
    expect(scheduledRateAt(profile, localTimeMs(0))).toBeCloseTo(0.8)
  })

  test('returns morning rate at 06:00 local', () => {
    expect(scheduledRateAt(profile, localTimeMs(6))).toBeCloseTo(1.2)
  })

  test('returns noon rate at 12:00 local', () => {
    expect(scheduledRateAt(profile, localTimeMs(12))).toBeCloseTo(0.9)
  })

  test('returns evening rate at 18:00 local', () => {
    expect(scheduledRateAt(profile, localTimeMs(18))).toBeCloseTo(1.0)
  })

  test('handles single-segment profile', () => {
    const single = [{ startTime: '00:00', value: 1.5 }]
    expect(scheduledRateAt(single, localTimeMs(14))).toBeCloseTo(1.5)
  })
})

describe('currentBasalRate', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // Set system time to local 08:00 so getHours() returns 8 in any timezone
    vi.setSystemTime(localTimeMs(8))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const profile = [
    { startTime: '00:00', value: 0.8 },
    { startTime: '06:00', value: 1.2 },
    { startTime: '12:00', value: 0.9 },
  ]

  test('returns null for undefined basal', () => {
    expect(currentBasalRate(undefined)).toBeNull()
  })

  test('returns null for empty basal array', () => {
    expect(currentBasalRate([])).toBeNull()
  })

  test('returns scheduled rate at current local time', () => {
    // local 08:00 → falls in 06:00 segment → 1.2 U/h
    const result = currentBasalRate(profile)
    expect(result).toBeCloseTo(1.2)
  })
})
