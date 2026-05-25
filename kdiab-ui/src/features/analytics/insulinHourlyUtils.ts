import type { TimelineResponse, ProfileSegment } from '../../api/analyzeApi'

const HOURS_PER_DAY = 24

/**
 * Expand basal profile segments into 24 hourly rate values.
 * Each segment applies from its startTime until the next segment's startTime.
 * Returns an array of 24 values (index = hour), or all-null if segments is empty/null.
 */
export function computeBasalFromProfileSegments(segments: ProfileSegment[] | null | undefined): (number | null)[] {
  if (!segments || segments.length === 0) {
    return Array<number | null>(HOURS_PER_DAY).fill(null)
  }

  // Parse segments into { hour, value } pairs, sorted ascending
  const parsed = segments
    .map(s => {
      const [h] = s.startTime.split(':').map(Number)
      return { hour: h ?? 0, value: s.value }
    })
    .sort((a, b) => a.hour - b.hour)

  const result: (number | null)[] = Array(HOURS_PER_DAY).fill(null)

  for (let hour = 0; hour < HOURS_PER_DAY; hour++) {
    // Find the last segment whose startTime <= hour (step-after semantics)
    let applicable: { hour: number; value: number } | null = null
    for (const seg of parsed) {
      if (seg.hour <= hour) {
        applicable = seg
      }
    }
    // If no segment starts at or before this hour, wrap around to the last segment of the day
    if (applicable === null && parsed.length > 0) {
      applicable = parsed[parsed.length - 1] ?? null
    }
    result[hour] = applicable !== null ? applicable.value : null
  }

  return result
}

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
