import type { TimelineResponse } from '../../api/analyzeApi'

const HOURS_PER_DAY = 24

/**
 * Compute hourly average basal rate (U/hr) from TEMP_BASAL timeline treatments.
 * Groups by UTC hour of the treatment timestamp and averages the rate values.
 * Returns an array of 24 values (index = hour), null where there are no readings.
 */
export function computeBasalHourlyAvg(timeline: TimelineResponse): (number | null)[] {
  const buckets: number[][] = Array.from({ length: HOURS_PER_DAY }, () => [])

  for (const t of timeline.treatments) {
    if (t.type !== 'TEMP_BASAL') continue
    const rate = (t.data as Record<string, unknown>)['rate']
    if (typeof rate !== 'number') continue
    const hour = new Date(t.treatedAt).getUTCHours()
    buckets[hour]?.push(rate)
  }

  return buckets.map(vals =>
    vals.length === 0 ? null : vals.reduce((a, b) => a + b, 0) / vals.length,
  )
}

/**
 * Compute hourly average bolus dose (U) from BOLUS and CORRECTION_BOLUS timeline treatments.
 * Groups by UTC hour of the treatment timestamp and averages the insulin values.
 * Returns an array of 24 values (index = hour), null where there are no readings.
 */
export function computeBolusHourlyAvg(timeline: TimelineResponse): (number | null)[] {
  const buckets: number[][] = Array.from({ length: HOURS_PER_DAY }, () => [])

  for (const t of timeline.treatments) {
    if (t.type !== 'BOLUS' && t.type !== 'CORRECTION_BOLUS') continue
    const insulin = (t.data as Record<string, unknown>)['insulin']
    if (typeof insulin !== 'number') continue
    const hour = new Date(t.treatedAt).getUTCHours()
    buckets[hour]?.push(insulin)
  }

  return buckets.map(vals =>
    vals.length === 0 ? null : vals.reduce((a, b) => a + b, 0) / vals.length,
  )
}
