import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery } from '@tanstack/react-query'
import { calcApi, type DoseResponse } from '../../api/calcApi'
import { measuresApi } from '../../api/measuresApi'
import { treatmentsApi } from '../../api/treatmentsApi'

interface Props {
  userId: string
  glucoseUnit: string
}

const CGM_TRENDS = [
  { value: 'DOUBLE_UP', labelKey: 'doseCalc.trendDoubleUp' },
  { value: 'SINGLE_UP', labelKey: 'doseCalc.trendSingleUp' },
  { value: 'FORTY_FIVE_UP', labelKey: 'doseCalc.trendFortyFiveUp' },
  { value: 'FLAT', labelKey: 'doseCalc.trendFlat' },
  { value: 'FORTY_FIVE_DOWN', labelKey: 'doseCalc.trendFortyFiveDown' },
  { value: 'SINGLE_DOWN', labelKey: 'doseCalc.trendSingleDown' },
  { value: 'DOUBLE_DOWN', labelKey: 'doseCalc.trendDoubleDown' },
  { value: 'NONE', labelKey: 'doseCalc.trendNone' },
]

const CGM_STALE_MIN = 15

function toDisplay(mgdl: number, unit: string): number {
  return unit === 'mmol/L' ? Math.round((mgdl / 18.0) * 10) / 10 : Math.round(mgdl)
}

function nowIso(): string {
  return new Date().toISOString()
}

export function DoseCalculator({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const [currentBg, setCurrentBg] = useState('')
  const [trend, setTrend] = useState('FLAT')
  const [carbsGrams, setCarbsGrams] = useState('0')
  const [result, setResult] = useState<DoseResponse | null>(null)
  const [correction, setCorrection] = useState('0')
  const [cgmAgeMin, setCgmAgeMin] = useState<number | null>(null)
  const [logSuccess, setLogSuccess] = useState(false)

  // Fetch the latest CGM reading to pre-fill BG and trend
  const { data: cgmData } = useQuery({
    queryKey: ['latestCgm', userId],
    queryFn: () => measuresApi.listMeasures(userId, 0, 10),
    staleTime: 60_000,
  })

  useEffect(() => {
    if (!cgmData?.data?.items) return
    const cgmReadings = cgmData.data.items
      .filter((m) => m.type === 'CGM' && typeof m.data['value'] === 'number')
      .sort((a, b) => new Date(b.measuredAt).getTime() - new Date(a.measuredAt).getTime())
    const latest = cgmReadings[0]
    if (!latest) return

    const ageMs = Date.now() - new Date(latest.measuredAt).getTime()
    const ageMin = Math.floor(ageMs / 60_000)
    setCgmAgeMin(ageMin)

    if (ageMin <= CGM_STALE_MIN) {
      const displayBg = toDisplay(latest.data['value'] as number, glucoseUnit)
      setCurrentBg(String(displayBg))

      const cgmTrend = latest.data['trend'] as string | undefined
      if (cgmTrend && CGM_TRENDS.some((tr) => tr.value === cgmTrend)) {
        setTrend(cgmTrend)
      }
    }
  }, [cgmData, glucoseUnit])

  const calcMutation = useMutation({
    mutationFn: () =>
      calcApi.calculateDose(userId, {
        currentBg: parseFloat(currentBg),
        glucoseUnit,
        trend,
        carbsGrams: parseFloat(carbsGrams) || 0,
      }),
    onSuccess: (response) => {
      setResult(response.data)
      setCorrection('0')
      setLogSuccess(false)
    },
  })

  const logMutation = useMutation({
    mutationFn: (dose: number) =>
      treatmentsApi.createTreatment(userId, {
        type: 'BOLUS',
        treatedAt: nowIso(),
        data: { insulin: dose },
      }),
    onSuccess: () => {
      setLogSuccess(true)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    calcMutation.mutate()
  }

  const finalDose =
    result !== null
      ? Math.max(0, Math.round((result.totalRecommended + parseFloat(correction || '0')) * 10) / 10)
      : 0

  const handleAcceptLog = () => {
    logMutation.mutate(finalDose)
  }

  const cgmBannerStyle: React.CSSProperties = {
    padding: '0.5rem 0.75rem',
    borderRadius: '6px',
    fontSize: '0.85rem',
    marginBottom: '0.5rem',
  }

  return (
    <div style={{ maxWidth: '480px' }}>
      <h2>{t('doseCalc.title')}</h2>

      {cgmAgeMin !== null && cgmAgeMin <= CGM_STALE_MIN && (
        <div style={{ ...cgmBannerStyle, background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.3)', color: 'var(--accent-success)' }}>
          {t('doseCalc.cgmPrefilled', { age: cgmAgeMin })}
        </div>
      )}
      {cgmAgeMin !== null && cgmAgeMin > CGM_STALE_MIN && (
        <div style={{ ...cgmBannerStyle, background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.3)', color: 'var(--accent-warning)' }}>
          {t('doseCalc.cgmStale', { age: cgmAgeMin })}
        </div>
      )}

      <form onSubmit={(e) => void handleSubmit(e)} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
        <div>
          <label htmlFor="currentBg">{t('doseCalc.currentBg')}</label>
          <input
            id="currentBg"
            type="number"
            step="0.1"
            value={currentBg}
            onChange={(e) => setCurrentBg(e.target.value)}
            required
            style={{ width: '100%', marginTop: '0.25rem' }}
          />
        </div>

        <div>
          <label htmlFor="trend">{t('doseCalc.trend')}</label>
          <select
            id="trend"
            value={trend}
            onChange={(e) => setTrend(e.target.value)}
            style={{ width: '100%', marginTop: '0.25rem' }}
          >
            {CGM_TRENDS.map((tr) => (
              <option key={tr.value} value={tr.value}>
                {t(tr.labelKey)}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="carbsGrams">{t('doseCalc.carbsGrams')}</label>
          <input
            id="carbsGrams"
            type="number"
            step="1"
            min="0"
            value={carbsGrams}
            onChange={(e) => setCarbsGrams(e.target.value)}
            style={{ width: '100%', marginTop: '0.25rem' }}
          />
        </div>

        <button
          type="submit"
          className="primary"
          disabled={calcMutation.isPending || !currentBg}
          style={{ padding: '0.6rem 1.5rem' }}
        >
          {calcMutation.isPending ? t('doseCalc.calculating') : t('doseCalc.calculate')}
        </button>
      </form>

      {calcMutation.isError && (
        <p style={{ color: 'var(--color-danger)', marginTop: '1rem' }}>{t('doseCalc.error')}</p>
      )}

      {result && (
        <div style={{ marginTop: '1.5rem', padding: '1rem', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
          <div style={{ textAlign: 'center', marginBottom: '1rem' }}>
            <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>{t('doseCalc.totalDose')}</p>
            <p style={{ margin: 0, fontSize: '2.5rem', fontWeight: 'bold', color: 'var(--color-primary)' }}>
              {result.totalRecommended} {t('doseCalc.units')}
            </p>
          </div>

          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
            <tbody>
              <tr>
                <td style={{ padding: '0.3rem 0', color: 'var(--text-secondary)' }}>{t('doseCalc.correction')}</td>
                <td style={{ padding: '0.3rem 0', textAlign: 'right' }}>
                  {result.correctionDose} {t('doseCalc.units')}
                </td>
              </tr>
              <tr>
                <td style={{ padding: '0.3rem 0', color: 'var(--text-secondary)' }}>{t('doseCalc.carbDose')}</td>
                <td style={{ padding: '0.3rem 0', textAlign: 'right' }}>
                  {result.carbDose} {t('doseCalc.units')}
                </td>
              </tr>
              <tr>
                <td style={{ padding: '0.3rem 0', color: 'var(--text-secondary)' }}>{t('doseCalc.trendAdj')}</td>
                <td style={{ padding: '0.3rem 0', textAlign: 'right' }}>
                  {result.trendAdjustment >= 0 ? '+' : ''}{result.trendAdjustment} {t('doseCalc.units')}
                </td>
              </tr>
              <tr style={{ borderTop: '1px solid var(--border-color)', fontWeight: 'bold' }}>
                <td style={{ padding: '0.5rem 0' }}>{t('doseCalc.total')}</td>
                <td style={{ padding: '0.5rem 0', textAlign: 'right' }}>
                  {result.totalRecommended} {t('doseCalc.units')}
                </td>
              </tr>
            </tbody>
          </table>

          {result.warnings.length > 0 && (
            <div
              style={{
                marginTop: '1rem',
                padding: '0.75rem',
                backgroundColor: 'rgba(255, 160, 0, 0.1)',
                border: '1px solid rgba(255, 160, 0, 0.4)',
                borderRadius: '6px',
              }}
            >
              <strong style={{ color: 'var(--color-warning, #d97706)' }}>{t('doseCalc.warnings')}</strong>
              <ul style={{ margin: '0.5rem 0 0 0', paddingLeft: '1.2rem' }}>
                {result.warnings.map((w, i) => (
                  <li key={i} style={{ color: 'var(--color-warning, #d97706)', fontSize: '0.875rem' }}>
                    {w}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Patient correction and accept */}
          <div style={{ marginTop: '1.25rem', borderTop: '1px solid var(--border-color)', paddingTop: '1rem' }}>
            <div style={{ marginBottom: '0.75rem' }}>
              <label htmlFor="correctionAdj" style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                {t('doseCalc.correctionAdj')}
              </label>
              <input
                id="correctionAdj"
                type="number"
                step="0.5"
                value={correction}
                onChange={(e) => { setCorrection(e.target.value); setLogSuccess(false) }}
                style={{ width: '100%', marginTop: '0.25rem' }}
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
              <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>{t('doseCalc.finalDose')}</span>
              <span style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>
                {finalDose} {t('doseCalc.units')}
              </span>
            </div>

            {logSuccess ? (
              <p style={{ textAlign: 'center', color: 'var(--accent-success)', margin: 0 }}>
                {t('doseCalc.logSuccess')}
              </p>
            ) : (
              <button
                type="button"
                className="primary"
                onClick={handleAcceptLog}
                disabled={logMutation.isPending || finalDose <= 0}
                style={{ width: '100%', padding: '0.6rem 1.5rem' }}
              >
                {logMutation.isPending ? t('doseCalc.logging') : t('doseCalc.acceptLog', { dose: finalDose })}
              </button>
            )}

            {logMutation.isError && (
              <p style={{ color: 'var(--color-danger)', marginTop: '0.5rem', textAlign: 'center', fontSize: '0.875rem' }}>
                {t('doseCalc.logError')}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
