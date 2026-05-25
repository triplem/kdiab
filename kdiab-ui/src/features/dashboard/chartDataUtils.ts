// Default half-window when no adjacent CGM reading exists (half of a typical 5-min interval)
const DEFAULT_HALF_MS = 5 * 60_000

/**
 * Returns the actual CGM timestamp that "owns" the given epoch-ms value using
 * midpoint-window ownership: each CGM reading owns the time from the halfway point
 * to the previous reading up to the halfway point to the next reading.
 *
 * Returns null when the value falls outside all ownership windows (no close CGM).
 *
 * @param sortedCgmTimes - CGM timestamps in ascending order (milliseconds)
 * @param ms             - The epoch-ms value to snap
 */
export function snapToCgm(sortedCgmTimes: readonly number[], ms: number): number | null {
  if (sortedCgmTimes.length === 0) return null

  // Binary search: first index i where sortedCgmTimes[i] >= ms
  let lo = 0
  let hi = sortedCgmTimes.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    // mid is always within [0, length-1] while lo < hi
    if (sortedCgmTimes[mid]! < ms) lo = mid + 1
    else hi = mid
  }

  // Nearest neighbour among the two candidates bracketing ms
  let nearestIdx = lo
  if (lo > 0 && lo < sortedCgmTimes.length) {
    nearestIdx =
      Math.abs(sortedCgmTimes[lo]! - ms) <= Math.abs(sortedCgmTimes[lo - 1]! - ms)
        ? lo
        : lo - 1
  } else if (lo === sortedCgmTimes.length) {
    nearestIdx = lo - 1
  }

  const nearest = sortedCgmTimes[nearestIdx]!
  const prev = nearestIdx > 0 ? sortedCgmTimes[nearestIdx - 1]! : null
  const next =
    nearestIdx < sortedCgmTimes.length - 1 ? sortedCgmTimes[nearestIdx + 1]! : null

  // Ownership extends halfway to adjacent readings; DEFAULT_HALF_MS at series edges
  const halfBefore = prev !== null ? (nearest - prev) / 2 : DEFAULT_HALF_MS
  const halfAfter = next !== null ? (next - nearest) / 2 : DEFAULT_HALF_MS

  return ms >= nearest - halfBefore && ms <= nearest + halfAfter ? nearest : null
}
