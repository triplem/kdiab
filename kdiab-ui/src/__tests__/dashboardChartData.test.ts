import { describe, it, expect } from 'vitest'

// Inline the merge function so tests don't depend on React component internals
const MS_PER_MINUTE = 60_000
function roundMin(ms: number) {
  return Math.round(ms / MS_PER_MINUTE) * MS_PER_MINUTE
}

type ChartPoint = {
  time: number
  sgv: number | null
  bgm: number | null
  marker: number | null
  treatmentType: string | null
  label: string | null
}

function mergeChartData(
  cgmPoints: ChartPoint[],
  bgmPoints: ChartPoint[],
  treatmentMarkers: ChartPoint[],
): ChartPoint[] {
  const byTime = new Map<number, ChartPoint>()

  for (const p of cgmPoints) {
    byTime.set(roundMin(p.time), { ...p })
  }

  for (const p of bgmPoints) {
    const key = roundMin(p.time)
    const existing = byTime.get(key)
    if (existing) {
      byTime.set(key, { ...existing, bgm: p.bgm })
    } else {
      byTime.set(key, { ...p })
    }
  }

  for (const p of treatmentMarkers) {
    const key = roundMin(p.time)
    const existing = byTime.get(key)
    if (existing) {
      const combinedLabel = [existing.label, p.label].filter(Boolean).join(' · ')
      byTime.set(key, { ...existing, marker: p.marker, treatmentType: p.treatmentType, label: combinedLabel })
    } else {
      byTime.set(key, { ...p })
    }
  }

  return Array.from(byTime.values()).sort((a, b) => a.time - b.time)
}

// Use a timestamp that falls on an exact minute boundary so roundMin() is predictable
const T0 = 1699999980000 // 1700000000000 floored to nearest minute boundary
const T1 = T0 + 5 * 60_000 // +5 min (also on a minute boundary)
const makeCgm = (t: number, sgv: number): ChartPoint => ({
  time: t,
  sgv,
  bgm: null,
  marker: null,
  treatmentType: null,
  label: null,
})
const makeBgm = (t: number, bgm: number): ChartPoint => ({
  time: t,
  sgv: null,
  bgm,
  marker: null,
  treatmentType: null,
  label: null,
})
const makeTreatment = (t: number, type: string, label: string): ChartPoint => ({
  time: t,
  sgv: null,
  bgm: null,
  marker: 60,
  treatmentType: type,
  label,
})

describe('mergeChartData', () => {
  it('returns CGM points as-is when no BGM or treatments', () => {
    const result = mergeChartData([makeCgm(T0, 120), makeCgm(T1, 110)], [], [])
    expect(result).toHaveLength(2)
    expect(result[0]!.sgv).toBe(120)
    expect(result[1]!.sgv).toBe(110)
  })

  it('merges a BGM reading at the same minute as a CGM point', () => {
    const cgm = makeCgm(T0, 120)
    const bgm = makeBgm(T0 + 10_000, 118) // 10s later, same minute
    const result = mergeChartData([cgm], [bgm], [])
    expect(result).toHaveLength(1)
    expect(result[0]!.sgv).toBe(120)
    expect(result[0]!.bgm).toBe(118)
  })

  it('adds a standalone BGM point when there is no CGM at that minute', () => {
    const cgm = makeCgm(T0, 120)
    const bgm = makeBgm(T1, 118) // different minute
    const result = mergeChartData([cgm], [bgm], [])
    expect(result).toHaveLength(2)
    expect(result.find(p => p.bgm === 118)?.sgv).toBeNull()
  })

  it('merges a treatment marker into a CGM point at the same minute', () => {
    const cgm = makeCgm(T0, 120)
    const treat = makeTreatment(T0 + 10_000, 'BOLUS', '3.5U')
    const result = mergeChartData([cgm], [], [treat])
    expect(result).toHaveLength(1)
    expect(result[0]!.label).toBe('3.5U')
    expect(result[0]!.sgv).toBe(120)
  })

  it('accumulates labels when two treatments fall in the same minute', () => {
    const bolus = makeTreatment(T0 + 5_000, 'BOLUS', '3.5U')
    const carbs = makeTreatment(T0 + 20_000, 'CARBS', '45g') // 20s later, same minute
    const result = mergeChartData([], [], [bolus, carbs])
    expect(result).toHaveLength(1)
    expect(result[0]!.label).toBe('3.5U · 45g')
  })

  it('sorts output by time', () => {
    const points = [makeCgm(T1, 110), makeCgm(T0, 120)]
    const result = mergeChartData(points, [], [])
    expect(result[0]!.time).toBeLessThan(result[1]!.time)
  })
})
