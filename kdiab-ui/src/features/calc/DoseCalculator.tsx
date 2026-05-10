import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { calcApi, type DoseResponse } from '../../api/calcApi'

interface Props {
  userId: string
  glucoseUnit: string
}

const CGM_TRENDS = [
  'DOUBLE_UP',
  'SINGLE_UP',
  'FORTY_FIVE_UP',
  'FLAT',
  'FORTY_FIVE_DOWN',
  'SINGLE_DOWN',
  'DOUBLE_DOWN',
  'NONE',
]

export function DoseCalculator({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation()
  const [currentBg, setCurrentBg] = useState('')
  const [trend, setTrend] = useState('FLAT')
  const [carbsGrams, setCarbsGrams] = useState('0')
  const [result, setResult] = useState<DoseResponse | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      calcApi.calculateDose(userId, {
        currentBg: parseFloat(currentBg),
        glucoseUnit,
        trend,
        carbsGrams: parseFloat(carbsGrams) || 0,
      }),
    onSuccess: (response) => {
      setResult(response.data)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate()
  }

  return (
    <div style={{ maxWidth: '480px' }}>
      <h2>{t('doseCalc.title')}</h2>

      <form onSubmit={(e) => void handleSubmit(e)} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
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
            {CGM_TRENDS.map((t) => (
              <option key={t} value={t}>
                {t}
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
          disabled={mutation.isPending || !currentBg}
          style={{ padding: '0.6rem 1.5rem' }}
        >
          {mutation.isPending ? t('doseCalc.calculating') : t('doseCalc.calculate')}
        </button>
      </form>

      {mutation.isError && (
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
        </div>
      )}
    </div>
  )
}
