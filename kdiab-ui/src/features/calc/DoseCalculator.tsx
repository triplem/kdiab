import { useState, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { calcApi, type DoseResponse } from '../../api/calcApi'
import { measuresApi } from '../../api/measuresApi'
import { treatmentsApi } from '../../api/treatmentsApi'
import { calcIOB } from '../dashboard/basalUtils'

// Substring-match map: first hit wins — more-specific prefixes must come before shorter ones.
// 'Calculated dose is unusually high' must precede 'Calculated dose' or it would never match.
const WARNING_KEYS: Record<string, string> = {
  'BG is hypoglycemic': 'doseCalc.warning.hypoglycemic',
  'BG is below target': 'doseCalc.warning.belowTarget',
  'IOB covers the full correction': 'doseCalc.warning.iobCoversCorrection',
  'Calculated dose is unusually high': 'doseCalc.warning.unusuallyHighDose',
  'Calculated dose': 'doseCalc.warning.doseCapped', // must follow the longer match above
}

// Unknown strings fall through to their original English text as a safe default.
function translateWarning(w: string, t: TFunction): string {
  const matchedKey = Object.keys(WARNING_KEYS).find(k => w.toLowerCase().includes(k.toLowerCase()))
  const i18nKey = matchedKey !== undefined ? WARNING_KEYS[matchedKey] : undefined
  return i18nKey !== undefined ? t(i18nKey) : w
}

interface Props {
  userId: string
  glucoseUnit: string
  /** Pre-computed active IOB (units) from the dashboard. When omitted, the
   *  component fetches recent treatments and computes IOB internally. */
  activeIob?: number
}

/** Default DIA (minutes) used for IOB decay when no profile data is available. */
const DEFAULT_DIA_MINUTES = 240

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
const TOAST_DURATION_MS = 4_000

function toDisplay(mgdl: number, unit: string): number {
  return unit === 'mmol/L' ? Math.round((mgdl / 18.0) * 10) / 10 : Math.round(mgdl)
}

function nowIso(): string {
  return new Date().toISOString()
}

export function DoseCalculator({ userId, glucoseUnit, activeIob: activeIobProp }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [currentBg, setCurrentBg] = useState('')
  const [trend, setTrend] = useState('FLAT')
  const [carbsGrams, setCarbsGrams] = useState('0')
  const [carbsInMinutes, setCarbsInMinutes] = useState(0)
  const [result, setResult] = useState<DoseResponse | null>(null)
  const [correction, setCorrection] = useState('0')
  const [cgmAgeMin, setCgmAgeMin] = useState<number | null>(null)
  const [logSuccess, setLogSuccess] = useState(false)
  const [calcError, setCalcError] = useState<string | null>(null)

  // Fetch the latest CGM reading to pre-fill BG and trend
  const { data: cgmData } = useQuery({
    queryKey: ['latestCgm', userId],
    queryFn: () => measuresApi.listMeasures(userId, 0, 10, 'ACTIVE'),
    staleTime: 60_000,
  })

  // Fetch recent treatments (last 6 h) to compute active IOB when no prop is supplied
  const { data: recentTreatmentsData } = useQuery({
    queryKey: ['recentTreatmentsForIob', userId],
    queryFn: () => treatmentsApi.listTreatments(userId, 'ACTIVE', 0, 100),
    staleTime: 60_000,
    enabled: activeIobProp === undefined,
  })

  const computedIob = useMemo(() => {
    if (activeIobProp !== undefined) return activeIobProp
    const items = recentTreatmentsData?.data?.items ?? []
    return calcIOB(
      items.map(t => ({
        treatedAt: t.treatedAt,
        type: t.type,
        data: t.data as Record<string, unknown>,
      })),
      DEFAULT_DIA_MINUTES,
    )
  }, [activeIobProp, recentTreatmentsData])

  useEffect(() => {
    if (!cgmData?.data?.items) return
    const cgmReadings = cgmData.data.items
      .filter((m) => m.type === 'CGM' && typeof (m.data as unknown as Record<string, unknown>)['value'] === 'number')
      .sort((a, b) => new Date(b.measuredAt).getTime() - new Date(a.measuredAt).getTime())
    const latest = cgmReadings[0]
    if (!latest) return

    const ageMs = Date.now() - new Date(latest.measuredAt).getTime()
    const ageMin = Math.floor(ageMs / 60_000)
    setCgmAgeMin(ageMin)

    if (ageMin <= CGM_STALE_MIN) {
      const latestData = latest.data as unknown as Record<string, unknown>
      const displayBg = toDisplay(latestData['value'] as number, glucoseUnit)
      setCurrentBg(String(displayBg))

      const cgmTrend = latestData['trend'] as string | undefined
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
        activeIob: computedIob,
      }),
    onSuccess: (response) => {
      setResult(response.data)
      setCorrection('0')
      setLogSuccess(false)
      setCalcError(null)
    },
    onError: (err: unknown) => {
      const detail: string =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? ''
      if (detail.includes('no ISF')) setCalcError(t('doseCalc.errorNoIsf'))
      else if (detail.includes('no ICR')) setCalcError(t('doseCalc.errorNoIcr'))
      else if (detail.includes('no target')) setCalcError(t('doseCalc.errorNoTarget'))
      else if (detail.includes('No active profile')) setCalcError(t('doseCalc.errorNoProfile'))
      else setCalcError(t('doseCalc.errorGeneric', { detail: detail || 'unknown' }))
    },
  })

  const logMutation = useMutation({
    mutationFn: async (dose: number) => {
      const bolusAt = nowIso()
      const carbs = parseFloat(carbsGrams) || 0
      // Offset the carbs treatment time by carbsInMinutes so the record reflects actual carb intake time
      const carbsDate = new Date()
      carbsDate.setMinutes(carbsDate.getMinutes() + carbsInMinutes)
      const carbsAt = carbsInMinutes > 0 ? carbsDate.toISOString() : bolusAt
      const bolusResponse = await treatmentsApi.createTreatment(userId, {
        type: 'BOLUS',
        treatedAt: bolusAt,
        data: {
          insulin: dose,
          calculatedDose: result?.totalRecommended,
          carbDose: result?.carbDose,
          correctionDose: result?.correctionDose,
          trendAdjustment: result?.trendAdjustment,
          icr: result?.breakdown?.icr,
          isf: result?.breakdown?.isf,
          targetBgMgDl: result?.breakdown?.targetBgMgDl,
          currentBgMgDl: result?.breakdown?.currentBgMgDl,
          trend: result?.breakdown?.trend,
          carbsGrams: carbs,
          // TODO: pass carbsInMinutes once calc API supports it in DoseRequest
        },
      })
      const bolusId = bolusResponse.data.id
      if (carbs > 0) {
        try {
          await treatmentsApi.createTreatment(userId, {
            type: 'CARBS',
            treatedAt: carbsAt,
            data: { carbs },
          })
        } catch (carbsErr: unknown) {
          // Compensate: archive the orphaned BOLUS (archiveTreatments is available to all roles; deleteTreatments is DOCTOR/ADMIN-only)
          try {
            await treatmentsApi.archiveTreatments(userId, { treatmentIds: [bolusId] })
          } catch {
            // best-effort — original CARBS error is still re-thrown below
          }
          throw carbsErr
        }
      }
    },
    onSuccess: (_data, dose) => {
      setLogSuccess(true)
      setCarbsGrams('0')
      setCarbsInMinutes(0)
      setResult(null)
      void queryClient.invalidateQueries({ queryKey: ['latestCgm', userId] })
      const now = new Date()
      const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      toast.success(t('doseCalc.toastSaved', { dose: dose.toFixed(1), time: timeStr }), {
        duration: 4000,
      })
    },
    onError: () => {
      toast.error(t('doseCalc.toastError'), { duration: TOAST_DURATION_MS })
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
    <div>
      <h2>{t('doseCalc.title')}</h2>

      <div style={{ display: 'flex', gap: '2rem', alignItems: 'flex-start', flexWrap: 'wrap' }}>
        {/* Left column: input form */}
        <div style={{ flex: '0 0 320px', minWidth: '280px' }}>
          {cgmAgeMin !== null && cgmAgeMin <= CGM_STALE_MIN && (
            <div style={{ ...cgmBannerStyle, background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.3)', color: 'var(--accent-success)' }}>
              {t('doseCalc.cgmPrefilled', { age: cgmAgeMin })}
            </div>
          )}
          {cgmAgeMin !== null && cgmAgeMin > CGM_STALE_MIN && (
            <div
              role="alert"
              aria-live="assertive"
              style={{ ...cgmBannerStyle, background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.3)', color: 'var(--accent-warning)' }}
            >
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

            <div>
              <label htmlFor="carbsInMinutes">{t('doseCalc.carbsInMinutes')}</label>
              <input
                id="carbsInMinutes"
                type="number"
                min={0}
                max={120}
                step={5}
                value={carbsInMinutes}
                placeholder={t('doseCalc.carbsInMinutesPlaceholder')}
                onChange={(e) => setCarbsInMinutes(Number(e.target.value))}
                style={{ width: '100%', marginTop: '0.25rem' }}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
              <span>{t('doseCalc.activeIob')}</span>
              <span style={{ fontWeight: 600, color: computedIob > 0 ? 'var(--color-primary)' : 'var(--text-secondary)' }}>
                {computedIob.toFixed(2)} {t('doseCalc.units')}
              </span>
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

          {calcError && (
            <div role="alert" aria-live="assertive" style={{ color: 'var(--color-error, #dc2626)', marginTop: '1rem' }}>
              {calcError}
            </div>
          )}
        </div>

        {/* Right column: result panel */}
        {result && (
        <div style={{ flex: '1 1 300px', padding: '1rem', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
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

          <p
            role="note"
            aria-label="Disclaimer"
            style={{
              marginTop: '0.75rem',
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
              fontStyle: 'italic',
            }}
          >
            {t('doseCalc.disclaimer')}
          </p>

          {result.warnings.length > 0 && (
            <div
              role="alert"
              aria-live="assertive"
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
                    {translateWarning(w, t)}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {result.recommendedWaitMinutes !== null && result.recommendedWaitMinutes !== undefined && (
            <div
              role="note"
              style={{
                marginTop: '1rem',
                padding: '0.75rem',
                backgroundColor: 'rgba(59, 130, 246, 0.1)',
                border: '1px solid rgba(59, 130, 246, 0.3)',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
              }}
            >
              <span style={{ fontSize: '1.25rem' }}>⏱</span>
              <span style={{ fontSize: '0.9rem' }}>
                <strong>{t('doseCalc.seaWait', { minutes: result.recommendedWaitMinutes })}</strong>
                <span style={{ color: 'var(--text-secondary)', marginLeft: '0.4rem' }}>
                  {t('doseCalc.seaLabel')}
                </span>
              </span>
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
              <div role="alert" aria-live="assertive" style={{ color: 'var(--color-error, #dc2626)', marginTop: '0.5rem', textAlign: 'center', fontSize: '0.875rem' }}>
                {t('doseCalc.logError')}
              </div>
            )}
          </div>
        </div>
      )}
      </div>
    </div>
  )
}
