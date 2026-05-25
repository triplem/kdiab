import { describe, test, expect } from 'vitest'
import { formatTooltipEntry } from '../features/dashboard/tooltipFormatter'

const t = (key: string, opts?: { defaultValue: string }): string => {
  const map: Record<string, string> = {
    'treatmentModal.types.BOLUS': 'Bolus',
    'treatmentModal.types.CORRECTION_BOLUS': 'Correction bolus',
    'treatmentModal.types.CARBS': 'Carbohydrates',
    'treatmentModal.types.SITE_CHANGE': 'Site change (infusion set)',
    'treatmentModal.types.SENSOR_INSERT': 'Insert CGM sensor',
    'treatmentModal.types.INSULIN_CHANGE': 'Change insulin cartridge',
    'treatmentModal.types.MEAL': 'Quick Meal (bolus + carbs)',
  }
  return map[key] ?? opts?.defaultValue ?? key
}

describe('formatTooltipEntry', () => {
  test('sgv returns glucose value with unit', () => {
    expect(formatTooltipEntry('sgv', 7.4, undefined, 'mmol/L', t)).toEqual(['7.4 mmol/L', 'CGM'])
  })

  test('bgm returns BGM value with unit', () => {
    expect(formatTooltipEntry('bgm', 6.2, undefined, 'mmol/L', t)).toEqual(['6.2 mmol/L', 'BGM'])
  })

  test('marker with label returns "TypeName: label"', () => {
    expect(formatTooltipEntry('marker', 59, { treatmentType: 'BOLUS', label: '4.0 U' }, 'mg/dL', t))
      .toEqual(['Bolus: 4.0 U', 'Bolus'])
  })

  test('marker with empty label returns just the type name', () => {
    expect(formatTooltipEntry('marker', 59, { treatmentType: 'SITE_CHANGE', label: '' }, 'mg/dL', t))
      .toEqual(['Site change (infusion set)', 'Site change (infusion set)'])
  })

  test('basalSched is suppressed (returns null)', () => {
    expect(formatTooltipEntry('basalSched', 0.9, undefined, 'mg/dL', t)).toBeNull()
  })

  test('basalDelivered is suppressed (returns null)', () => {
    expect(formatTooltipEntry('basalDelivered', -0.9, undefined, 'mg/dL', t)).toBeNull()
  })

  test('unknown marker type falls back to raw type string', () => {
    const result = formatTooltipEntry('marker', 59, { treatmentType: 'UNKNOWN_TYPE', label: '' }, 'mg/dL', t)
    expect(result).toEqual(['UNKNOWN_TYPE', 'UNKNOWN_TYPE'])
  })

  test('sgv null is suppressed — avoids "null" row in tooltip when cursor is on a BGM/treatment point', () => {
    expect(formatTooltipEntry('sgv', null, undefined, 'mg/dL', t)).toBeNull()
  })

  test('bgm null is suppressed — avoids "null" row in tooltip when cursor is on a CGM/treatment point', () => {
    expect(formatTooltipEntry('bgm', null, undefined, 'mg/dL', t)).toBeNull()
  })

  test('marker null is suppressed — avoids empty row in tooltip when cursor is on a CGM/BGM point', () => {
    expect(formatTooltipEntry('marker', null, { treatmentType: 'BOLUS', label: '4.0 U' }, 'mg/dL', t)).toBeNull()
  })

  test('marker with empty treatmentType is suppressed — guards against default empty payload', () => {
    expect(formatTooltipEntry('marker', 59, { treatmentType: '', label: '' }, 'mg/dL', t)).toBeNull()
  })

  test('marker with undefined payload treatmentType is suppressed', () => {
    expect(formatTooltipEntry('marker', 59, { label: '' }, 'mg/dL', t)).toBeNull()
  })

  test('unknown name is suppressed — no stray rows from unexpected dataKeys', () => {
    expect(formatTooltipEntry('unknown', 42, undefined, 'mg/dL', t)).toBeNull()
  })
})
