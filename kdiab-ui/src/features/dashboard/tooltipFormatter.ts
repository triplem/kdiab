/** Pure formatter for GlucoseTrendChart tooltip entries.
 *  Returns [displayValue, seriesName] or null to suppress the row. */
export function formatTooltipEntry(
  name: unknown,
  value: unknown,
  payload: { treatmentType?: string; label?: string } | undefined,
  yLabel: string,
  translate: (key: string, opts?: { defaultValue: string }) => string,
): [string, string] | null {
  if (name === 'sgv' && typeof value === 'number') return [`${value} ${yLabel}`, 'CGM']
  if (name === 'bgm' && typeof value === 'number') return [`${value} ${yLabel}`, 'BGM']
  if (name === 'marker') {
    const ttype = payload?.treatmentType ?? ''
    const lbl = payload?.label ?? ''
    const typeName = translate(`treatmentModal.types.${ttype}`, { defaultValue: ttype })
    return [lbl ? `${typeName}: ${lbl}` : typeName, typeName]
  }
  if (name === 'basalSched') return null
  if (name === 'basalDelivered') return null
  return null
}
