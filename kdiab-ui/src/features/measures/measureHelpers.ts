import type { MeasureResponse } from '../../api/measuresApi'

function trendToArrow(trend: string): string {
  const map: Record<string, string> = {
    DoubleUp: '⬆⬆',
    SingleUp: '↑',
    FortyFiveUp: '↗',
    Flat: '→',
    FortyFiveDown: '↘',
    SingleDown: '↓',
    DoubleDown: '⬇⬇',
  }
  return map[trend] ?? ''
}

export const renderDataSummary = (m: MeasureResponse): string => {
  const d = m.data as Record<string, unknown>
  switch (m.type) {
    case 'CGM': {
      const trend = typeof d.trend === 'string' ? ` ${trendToArrow(d.trend)}` : ''
      const unit = typeof d.unit === 'string' ? d.unit : 'mg/dL'
      return d.value != null ? `${d.value as number} ${unit}${trend}` : JSON.stringify(d)
    }
    case 'BGM': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mg/dL'
      return d.value != null ? `${d.value as number} ${unit}` : JSON.stringify(d)
    }
    case 'BLOOD_PRESSURE': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mmHg'
      return d.systolic != null && d.diastolic != null
        ? `${d.systolic as number}/${d.diastolic as number} ${unit}`
        : JSON.stringify(d)
    }
    case 'WEIGHT': {
      const unit = typeof d.unit === 'string' ? d.unit : 'kg'
      return d.value != null ? `${d.value as number} ${unit}` : JSON.stringify(d)
    }
    case 'PULSE': {
      const unit = typeof d.unit === 'string' ? d.unit : 'bpm'
      return d.value != null ? `${d.value as number} ${unit}` : JSON.stringify(d)
    }
    case 'BG_CHECK': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mg/dL'
      return d.value != null ? `${d.value as number} ${unit}` : JSON.stringify(d)
    }
    case 'KETONE_CHECK': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mmol/L'
      const method = typeof d.method === 'string' ? ` (${d.method})` : ''
      return d.value != null ? `${d.value as number} ${unit}${method}` : JSON.stringify(d)
    }
    default:
      return JSON.stringify(d)
  }
}
