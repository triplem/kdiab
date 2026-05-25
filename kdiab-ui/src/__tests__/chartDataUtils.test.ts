import { describe, test, expect } from 'vitest'
import { snapToCgm } from '../features/dashboard/chartDataUtils'

const MIN = 60_000
const BASE = 1_000 * MIN // arbitrary fixed reference epoch

describe('snapToCgm', () => {
  test('returns null for empty CGM array', () => {
    expect(snapToCgm([], BASE)).toBeNull()
  })

  describe('single CGM point (DEFAULT_HALF = 5 min edge window)', () => {
    const cgm = [BASE]

    test('exact hit returns that timestamp', () => {
      expect(snapToCgm(cgm, BASE)).toBe(BASE)
    })

    test('returns timestamp when within 5-min default half-window', () => {
      expect(snapToCgm(cgm, BASE + 4 * MIN)).toBe(BASE)
      expect(snapToCgm(cgm, BASE - 4 * MIN)).toBe(BASE)
    })

    test('returns timestamp exactly at 5-min boundary (inclusive)', () => {
      expect(snapToCgm(cgm, BASE + 5 * MIN)).toBe(BASE)
      expect(snapToCgm(cgm, BASE - 5 * MIN)).toBe(BASE)
    })

    test('returns null when beyond the 5-min default window', () => {
      expect(snapToCgm(cgm, BASE + 5 * MIN + 1)).toBeNull()
      expect(snapToCgm(cgm, BASE - 5 * MIN - 1)).toBeNull()
    })
  })

  describe('two CGM readings 5 minutes apart', () => {
    const t0 = BASE
    const t1 = BASE + 5 * MIN
    const cgm = [t0, t1]

    test('exact hit on each reading', () => {
      expect(snapToCgm(cgm, t0)).toBe(t0)
      expect(snapToCgm(cgm, t1)).toBe(t1)
    })

    test('one ms before midpoint belongs to t0', () => {
      expect(snapToCgm(cgm, t0 + 2.5 * MIN - 1)).toBe(t0)
    })

    test('exact midpoint belongs to t1 (ties go to later CGM)', () => {
      expect(snapToCgm(cgm, t0 + 2.5 * MIN)).toBe(t1)
    })

    test('one ms after midpoint belongs to t1', () => {
      expect(snapToCgm(cgm, t0 + 2.5 * MIN + 1)).toBe(t1)
    })

    test('BGM 1 min before t1 snaps to t1', () => {
      expect(snapToCgm(cgm, t1 - 1 * MIN)).toBe(t1)
    })

    // First/last CGM readings use DEFAULT_HALF_MS (5 min) on their open side
    test('point 3 min before t0 snaps to t0 (within 5-min default open window)', () => {
      expect(snapToCgm(cgm, t0 - 3 * MIN)).toBe(t0)
    })

    test('point 3 min after t1 snaps to t1 (within 5-min default open window)', () => {
      expect(snapToCgm(cgm, t1 + 3 * MIN)).toBe(t1)
    })

    test('point 6 min before t0 returns null (beyond 5-min default window)', () => {
      expect(snapToCgm(cgm, t0 - 6 * MIN)).toBeNull()
    })

    test('point 6 min after t1 returns null (beyond 5-min default window)', () => {
      expect(snapToCgm(cgm, t1 + 6 * MIN)).toBeNull()
    })
  })

  describe('sensor gap: two CGM readings 15 minutes apart', () => {
    const t0 = BASE
    const t1 = BASE + 15 * MIN
    const cgm = [t0, t1]

    test('point 7 min after t0 snaps to t0 (within 7.5-min half)', () => {
      expect(snapToCgm(cgm, t0 + 7 * MIN)).toBe(t0)
    })

    test('one ms before midpoint (7.5 min) belongs to t0', () => {
      expect(snapToCgm(cgm, t0 + 7.5 * MIN - 1)).toBe(t0)
    })

    test('exact midpoint (7.5 min) belongs to t1 (ties go to later CGM)', () => {
      expect(snapToCgm(cgm, t0 + 7.5 * MIN)).toBe(t1)
    })

    test('one ms past midpoint belongs to t1', () => {
      expect(snapToCgm(cgm, t0 + 7.5 * MIN + 1)).toBe(t1)
    })

    test('point 7 min before t1 snaps to t1', () => {
      expect(snapToCgm(cgm, t1 - 7 * MIN)).toBe(t1)
    })

    test('point far before t0 returns null', () => {
      expect(snapToCgm(cgm, t0 - 10 * MIN)).toBeNull()
    })

    test('point far after t1 returns null', () => {
      expect(snapToCgm(cgm, t1 + 10 * MIN)).toBeNull()
    })
  })

  describe('three CGM readings (typical 5-min sequence)', () => {
    const t0 = BASE
    const t1 = BASE + 5 * MIN
    const t2 = BASE + 10 * MIN
    const cgm = [t0, t1, t2]

    test('point 2 min after t0 snaps to t0', () => {
      expect(snapToCgm(cgm, t0 + 2 * MIN)).toBe(t0)
    })

    test('point 2 min before t1 snaps to t1', () => {
      expect(snapToCgm(cgm, t1 - 2 * MIN)).toBe(t1)
    })

    test('exact t1 snaps to t1', () => {
      expect(snapToCgm(cgm, t1)).toBe(t1)
    })

    test('point 2 min after t1 snaps to t1', () => {
      expect(snapToCgm(cgm, t1 + 2 * MIN)).toBe(t1)
    })

    test('point 2 min before t2 snaps to t2', () => {
      expect(snapToCgm(cgm, t2 - 2 * MIN)).toBe(t2)
    })

    test('point far before t0 returns null', () => {
      expect(snapToCgm(cgm, t0 - 10 * MIN)).toBeNull()
    })

    test('point far after t2 returns null', () => {
      expect(snapToCgm(cgm, t2 + 10 * MIN)).toBeNull()
    })
  })

  describe('unsorted input is handled correctly when pre-sorted by caller', () => {
    // Callers are responsible for sorting; verify a pre-sorted array works
    const t0 = BASE
    const t1 = BASE + 5 * MIN
    const t2 = BASE + 10 * MIN

    test('sorted input returns correct owner', () => {
      expect(snapToCgm([t0, t1, t2], t0 + 1 * MIN)).toBe(t0)
      expect(snapToCgm([t0, t1, t2], t1 + 1 * MIN)).toBe(t1)
      expect(snapToCgm([t0, t1, t2], t2 - 1 * MIN)).toBe(t2)
    })
  })
})
