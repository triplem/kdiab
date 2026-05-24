import { describe, test, expect } from 'vitest'
import { deriveDeliveredLine } from '../features/dashboard/basalUtils'
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
