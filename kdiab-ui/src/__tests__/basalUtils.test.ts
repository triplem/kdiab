import { describe, test, expect } from 'vitest'
import { deriveDeliveredLine, buildBasalProfileLine } from '../features/dashboard/basalUtils'
import type { BasalBlock } from '../features/dashboard/basalUtils'

describe('deriveDeliveredLine', () => {
  test('returns empty array for empty input', () => {
    expect(deriveDeliveredLine([])).toEqual([])
  })

  test('emits two step points per block with negated deliveredRate', () => {
    const blocks: BasalBlock[] = [
      { startMs: 0, endMs: 60000, deliveredRate: 0.9, scheduledRate: 0.9, state: 'SCHEDULED' },
    ]
    const result = deriveDeliveredLine(blocks)
    expect(result).toHaveLength(2)
    expect(result[0]).toEqual({ time: 0, basalDelivered: -0.9 })
    expect(result[1]).toEqual({ time: 59999, basalDelivered: -0.9 })
  })

  test('ABOVE block emits negated higher rate', () => {
    const blocks: BasalBlock[] = [
      { startMs: 0, endMs: 60000, deliveredRate: 1.8, scheduledRate: 0.9, state: 'ABOVE' },
    ]
    const result = deriveDeliveredLine(blocks)
    expect(result[0]!.basalDelivered).toBeCloseTo(-1.8)
    expect(result[1]!.basalDelivered).toBeCloseTo(-1.8)
  })

  test('BELOW block emits negated lower rate', () => {
    const blocks: BasalBlock[] = [
      { startMs: 0, endMs: 60000, deliveredRate: 0.45, scheduledRate: 0.9, state: 'BELOW' },
    ]
    const result = deriveDeliveredLine(blocks)
    expect(result[0]!.basalDelivered).toBeCloseTo(-0.45)
  })

  test('SUSPENDED block emits basalDelivered: 0 (top edge, no fill)', () => {
    const blocks: BasalBlock[] = [
      { startMs: 0, endMs: 60000, deliveredRate: 0, scheduledRate: 0.9, state: 'SUSPENDED' },
    ]
    const result = deriveDeliveredLine(blocks)
    // deliveredRate is 0; -0 and +0 are numerically equal so use toBeCloseTo
    expect(result[0]!.basalDelivered).toBeCloseTo(0)
    expect(result[1]!.basalDelivered).toBeCloseTo(0)
  })

  test('multiple blocks produce sorted output', () => {
    const blocks: BasalBlock[] = [
      { startMs: 60000, endMs: 120000, deliveredRate: 1.8, scheduledRate: 0.9, state: 'ABOVE' },
      { startMs: 0, endMs: 60000, deliveredRate: 0.9, scheduledRate: 0.9, state: 'SCHEDULED' },
    ]
    const result = deriveDeliveredLine(blocks)
    expect(result[0]!.time).toBe(0)
    expect(result[result.length - 1]!.time).toBe(119999)
  })
})

// ---------------------------------------------------------------------------
// buildBasalProfileLine
// ---------------------------------------------------------------------------

const DAY_MS = 86400000

describe('buildBasalProfileLine', () => {
  const from = new Date('2024-01-01T00:00:00Z').getTime()
  const to = new Date('2024-01-01T23:59:00Z').getTime()

  test('first point uses fromMs as time', () => {
    const line = buildBasalProfileLine(from, to, [{ startTime: '00:00', value: 0.8 }])
    expect(line[0]!.time).toBe(from)
  })

  test('last point uses toMs as time', () => {
    const line = buildBasalProfileLine(from, to, [{ startTime: '00:00', value: 0.8 }])
    expect(line[line.length - 1]!.time).toBe(to)
  })

  test('points are ordered by timestamp', () => {
    const segments = [
      { startTime: '00:00', value: 0.8 },
      { startTime: '06:00', value: 1.2 },
      { startTime: '12:00', value: 0.9 },
    ]
    const line = buildBasalProfileLine(from, to, segments)
    for (let i = 1; i < line.length; i++) {
      expect(line[i]!.time).toBeGreaterThanOrEqual(line[i - 1]!.time)
    }
  })

  test('emits one point per segment boundary (not two)', () => {
    // Two segments → 1 boundary at 06:00 inside the window → total 3 points: from, 06:00, to
    const segments = [
      { startTime: '00:00', value: 0.8 },
      { startTime: '06:00', value: 1.2 },
    ]
    const line = buildBasalProfileLine(from, to, segments)
    // from + one 06:00 boundary + to = 3 points
    expect(line).toHaveLength(3)
  })

  test('segment boundary point carries the segment value', () => {
    const segments = [
      { startTime: '00:00', value: 0.8 },
      { startTime: '06:00', value: 1.5 },
    ]
    const line = buildBasalProfileLine(from, to, segments)
    const boundary = line.find(p => p.time !== from && p.time !== to)
    expect(boundary).toBeDefined()
    expect(boundary!.sched).toBe(1.5)
  })

  test('two-day window includes boundaries from both days', () => {
    const from2 = new Date('2024-01-01T00:00:00Z').getTime()
    const to2 = new Date('2024-01-03T00:00:00Z').getTime()
    const segments = [
      { startTime: '00:00', value: 0.8 },
      { startTime: '06:00', value: 1.2 },
    ]
    const line = buildBasalProfileLine(from2, to2, segments)
    // Each day has one 06:00 boundary inside the window; two calendar days → 2 06:00 boundaries
    // Plus from and to anchors → at least 4 points
    expect(line.length).toBeGreaterThanOrEqual(4)
  })

  test('window that spans exactly one day has no duplicate boundary', () => {
    const fromDay = Math.floor(from / DAY_MS) * DAY_MS
    const toDay = fromDay + DAY_MS
    const segments = [{ startTime: '00:00', value: 0.8 }, { startTime: '12:00', value: 1.0 }]
    const line = buildBasalProfileLine(fromDay, toDay, segments)
    const times = line.map(p => p.time)
    const uniqueTimes = new Set(times)
    expect(times.length).toBe(uniqueTimes.size)
  })
})
