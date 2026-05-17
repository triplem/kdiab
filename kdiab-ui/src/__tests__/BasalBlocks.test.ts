import { describe, test, expect } from 'vitest'
import { reconstructBasalBlocks } from '../features/dashboard/basalUtils'

// Fixed reference time: 2024-06-01T00:00:00Z (Saturday)
const BASE_MS = new Date('2024-06-01T00:00:00Z').getTime()

function ms(h: number, m = 0): number {
  return BASE_MS + (h * 60 + m) * 60000
}

const PROFILE: Array<{ startTime: string; value: number }> = [
  { startTime: '00:00', value: 0.8 },
  { startTime: '06:00', value: 1.2 },
  { startTime: '12:00', value: 0.9 },
  { startTime: '18:00', value: 1.0 },
]

describe('reconstructBasalBlocks', () => {
  test('returns empty array when no profile segments', () => {
    const result = reconstructBasalBlocks(ms(0), ms(2), [], [])
    expect(result).toEqual([])
  })

  test('single SCHEDULED block when no temp basals', () => {
    const result = reconstructBasalBlocks(ms(0), ms(5), PROFILE, [])
    // 00:00-05:00 is all at 0.8 U/h (profile segment 00:00)
    // but 06:00 boundary is outside 05:00, so one block
    expect(result).toHaveLength(1)
    expect(result[0]!.state).toBe('SCHEDULED')
    expect(result[0]!.deliveredRate).toBeCloseTo(0.8)
  })

  test('profile segment boundaries create multiple SCHEDULED blocks', () => {
    const result = reconstructBasalBlocks(ms(0), ms(13), PROFILE, [])
    // Boundaries at 06:00 and 12:00 within window → 3 blocks
    expect(result).toHaveLength(3)
    expect(result[0]!.state).toBe('SCHEDULED')
    expect(result[0]!.deliveredRate).toBeCloseTo(0.8) // 00:00-06:00
    expect(result[1]!.deliveredRate).toBeCloseTo(1.2) // 06:00-12:00
    expect(result[2]!.deliveredRate).toBeCloseTo(0.9) // 12:00-13:00
  })

  test('TEMP_BASAL absolute creates ABOVE block', () => {
    const treatments = [{
      treatedAt: new Date(ms(1)).toISOString(),
      type: 'TEMP_BASAL',
      data: { rate: 2.0, duration: 60, absolute: true },
    }]
    const result = reconstructBasalBlocks(ms(0), ms(5), PROFILE, treatments)
    const aboveBlock = result.find(b => b.state === 'ABOVE')
    expect(aboveBlock).toBeDefined()
    expect(aboveBlock!.deliveredRate).toBeCloseTo(2.0)
    expect(aboveBlock!.startMs).toBe(ms(1))
    expect(aboveBlock!.endMs).toBe(ms(2))
  })

  test('TEMP_BASAL percentage converts correctly', () => {
    // 50% of 0.8 U/h = 0.4 U/h → BELOW
    const treatments = [{
      treatedAt: new Date(ms(1)).toISOString(),
      type: 'TEMP_BASAL',
      data: { rate: 50, duration: 60, absolute: false },
    }]
    const result = reconstructBasalBlocks(ms(0), ms(3), PROFILE, treatments)
    const belowBlock = result.find(b => b.state === 'BELOW')
    expect(belowBlock).toBeDefined()
    expect(belowBlock!.deliveredRate).toBeCloseTo(0.4)
  })

  test('PUMP_SUSPEND creates SUSPENDED block at rate 0', () => {
    const treatments = [{
      treatedAt: new Date(ms(2)).toISOString(),
      type: 'PUMP_SUSPEND',
      data: { duration: 30 },
    }]
    const result = reconstructBasalBlocks(ms(0), ms(3), PROFILE, treatments)
    const suspended = result.find(b => b.state === 'SUSPENDED')
    expect(suspended).toBeDefined()
    expect(suspended!.deliveredRate).toBe(0)
  })

  test('100% temp basal stays SCHEDULED (not ABOVE)', () => {
    const treatments = [{
      treatedAt: new Date(ms(1)).toISOString(),
      type: 'TEMP_BASAL',
      data: { rate: 100, duration: 60, absolute: false },
    }]
    const result = reconstructBasalBlocks(ms(0), ms(3), PROFILE, treatments)
    // All blocks should be SCHEDULED
    expect(result.every(b => b.state === 'SCHEDULED')).toBe(true)
  })

  test('consecutive same-state blocks are merged', () => {
    // No temps → all SCHEDULED → merges into 1 block (no profile boundary)
    const result = reconstructBasalBlocks(ms(1), ms(5), PROFILE, [])
    expect(result).toHaveLength(1)
    expect(result[0]!.state).toBe('SCHEDULED')
  })

  test('blocks outside window are ignored', () => {
    const treatments = [{
      treatedAt: new Date(ms(-2)).toISOString(), // 2 hours before window
      type: 'TEMP_BASAL',
      data: { rate: 2.0, duration: 60, absolute: true }, // ends 1h before window
    }]
    const result = reconstructBasalBlocks(ms(0), ms(2), PROFILE, treatments)
    expect(result.every(b => b.state === 'SCHEDULED')).toBe(true)
  })

  test('handles overnight window spanning midnight', () => {
    // Window: 22:00 to 02:00 next day (crosses midnight)
    const from = ms(22)
    const to = ms(26) // 02:00 next day
    const result = reconstructBasalBlocks(from, to, PROFILE, [])
    // Should have blocks at scheduled rates without errors
    expect(result.length).toBeGreaterThan(0)
    expect(result.every(b => b.state === 'SCHEDULED')).toBe(true)
  })
})
