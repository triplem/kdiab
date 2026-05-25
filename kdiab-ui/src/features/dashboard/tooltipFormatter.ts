/** Pure formatter for GlucoseTrendChart tooltip entries.
 *  Returns [displayValue, seriesName] or null to suppress the row.
 *  Returns null for null/undefined values so other-series nulls are hidden
 *  when the cursor is positioned over a point from a different series. */
export function formatTooltipEntry(
  name: unknown,
  value: unknown,
  payload: { treatmentType?: string; label?: string } | undefined,
  yLabel: string,
  translate: (key: string, opts?: { defaultValue: string }) => string,
): [string, string] | null {
  if (name === 'sgv') return typeof value === 'number' ? [`${value} ${yLabel}`, 'CGM'] : null
  if (name === 'bgm') return typeof value === 'number' ? [`${value} ${yLabel}`, 'BGM'] : null
  if (name === 'marker') {
    if (value === null || value === undefined) return null
    const ttype = payload?.treatmentType ?? ''
    if (!ttype) return null
    const lbl = payload?.label ?? ''
    const typeName = translate(`treatmentModal.types.${ttype}`, { defaultValue: ttype })
    return [lbl ? `${typeName}: ${lbl}` : typeName, typeName]
  }
  if (name === 'basalSched') return null
  if (name === 'basalDelivered') return null
  return null
}
