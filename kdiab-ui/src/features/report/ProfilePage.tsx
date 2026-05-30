import { useTranslation } from 'react-i18next'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import type { ProfileSummary, ProfileSegment, TargetSegment } from '../../api/analyzeApi'

interface Props {
  profiles: ProfileSummary[]
  glucoseUnit: string
}

/**
 * ProfilePage renders the insulin pump profile section for the patient report.
 *
 * It is a pure presentation component — all data is received via props from
 * the parent report orchestrator (ReportView). No data fetching occurs here.
 *
 * Sections:
 *  1. Profile header: name, status, insulin type, duration of action, validFrom
 *  2. Basal rate schedule: table + small bar chart
 *  3. ICR (Insulin-to-Carb Ratio): table startTime → IE per 10g carbs
 *  4. ISF (Insulin Sensitivity Factor): table startTime → mg/dL per IE
 *  5. Glucose targets: table startTime → low–high range
 *
 * Only the ACTIVE profile is shown. If no active profile exists a note is displayed.
 */
export function ProfilePage({ profiles, glucoseUnit }: Props) {
  const { t } = useTranslation()

  const activeProfile = profiles.find(p => p.status === 'ACTIVE') ?? null

  if (activeProfile === null) {
    return (
      <p style={{ color: 'var(--text-secondary)', fontStyle: 'italic' }}>
        {t('report.profile.noActiveProfile')}
      </p>
    )
  }

  return (
    <div className="profile-page">
      <ProfileHeader profile={activeProfile} />

      {activeProfile.basal && activeProfile.basal.length > 0 && (
        <ProfileSection title={t('report.profile.basalRate')}>
          <BasalTable segments={activeProfile.basal} />
          <BasalChart segments={activeProfile.basal} />
        </ProfileSection>
      )}

      {activeProfile.icr && activeProfile.icr.length > 0 && (
        <ProfileSection title={t('report.profile.icr')}>
          <SegmentTable
            segments={activeProfile.icr}
            valueLabel={t('report.profile.icrUnit')}
            formatValue={(v) => v.toFixed(1)}
          />
        </ProfileSection>
      )}

      {activeProfile.isf && activeProfile.isf.length > 0 && (
        <ProfileSection title={t('report.profile.isf')}>
          <SegmentTable
            segments={activeProfile.isf}
            valueLabel={glucoseUnit === 'mmol/L'
              ? t('report.profile.isfUnitMmol')
              : t('report.profile.isfUnitMgdl')}
            formatValue={(v) => {
              if (glucoseUnit === 'mmol/L') {
                return (v / 18.0).toFixed(1)
              }
              return Math.round(v).toString()
            }}
          />
        </ProfileSection>
      )}

      {activeProfile.targets && activeProfile.targets.length > 0 && (
        <ProfileSection title={t('report.profile.targets')}>
          <TargetTable segments={activeProfile.targets} glucoseUnit={glucoseUnit} />
        </ProfileSection>
      )}
    </div>
  )
}

// ---- Sub-components ----

interface ProfileHeaderProps {
  profile: ProfileSummary
}

function ProfileHeader({ profile }: ProfileHeaderProps) {
  const { t } = useTranslation()

  const validFromDisplay = profile.validFrom
    ? profile.validFrom.slice(0, 10)
    : profile.createdAt
      ? profile.createdAt.slice(0, 10)
      : '—'

  return (
    <div
      className="profile-header"
      style={{
        marginBottom: '1.25rem',
        padding: '0.75rem 1rem',
        background: 'var(--surface)',
        borderRadius: '0.375rem',
        border: '1px solid var(--border)',
      }}
    >
      <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.05rem' }}>{profile.name}</h3>
      <dl
        style={{
          display: 'grid',
          gridTemplateColumns: 'max-content 1fr',
          columnGap: '0.75rem',
          rowGap: '0.2rem',
          fontSize: '0.85rem',
          margin: 0,
        }}
      >
        <dt style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>{t('profile.status')}</dt>
        <dd style={{ margin: 0 }}>
          <span className={`status-badge status-${profile.status.toLowerCase()}`}>
            {profile.status}
          </span>
        </dd>

        {profile.insulinType != null && (
          <>
            <dt style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>
              {t('report.profile.insulinType')}
            </dt>
            <dd style={{ margin: 0 }}>{profile.insulinType}</dd>
          </>
        )}

        {profile.durationOfAction != null && (
          <>
            <dt style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>
              {t('report.profile.durationOfAction')}
            </dt>
            <dd style={{ margin: 0 }}>
              {t('report.profile.durationOfActionValue', { minutes: profile.durationOfAction })}
            </dd>
          </>
        )}

        <dt style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>{t('profile.validFrom')}</dt>
        <dd style={{ margin: 0 }}>{validFromDisplay}</dd>
      </dl>
    </div>
  )
}

interface ProfileSectionProps {
  title: string
  children: React.ReactNode
}

function ProfileSection({ title, children }: ProfileSectionProps) {
  return (
    <div style={{ marginBottom: '1.25rem' }}>
      <h4
        style={{
          fontSize: '0.9rem',
          fontWeight: 600,
          margin: '0 0 0.5rem',
          color: 'var(--text-secondary)',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {title}
      </h4>
      {children}
    </div>
  )
}

// ---- Basal table + chart ----

interface BasalTableProps {
  segments: ProfileSegment[]
}

function BasalTable({ segments }: BasalTableProps) {
  const { t } = useTranslation()

  return (
    <div style={{ overflowX: 'auto', marginBottom: '0.75rem' }}>
      <table
        style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}
        aria-label={t('report.profile.basalTableAriaLabel')}
      >
        <thead>
          <tr style={{ background: 'var(--surface)', borderBottom: '2px solid var(--border)' }}>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'left' }}>
              {t('report.profile.startTime')}
            </th>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'right' }}>
              {t('report.profile.basalRateUnit')}
            </th>
          </tr>
        </thead>
        <tbody>
          {segments.map((seg) => (
            <tr key={seg.startTime} style={{ borderBottom: '1px solid var(--border)' }}>
              <td style={{ padding: '0.3rem 0.5rem', fontFamily: 'monospace' }}>
                {formatTime(seg.startTime)}
              </td>
              <td
                style={{
                  padding: '0.3rem 0.5rem',
                  textAlign: 'right',
                  color: seg.value === 0 ? 'var(--color-warning, #b45309)' : undefined,
                  fontWeight: seg.value === 0 ? 600 : undefined,
                }}
              >
                {seg.value.toFixed(3)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

interface BasalChartProps {
  segments: ProfileSegment[]
}

function BasalChart({ segments }: BasalChartProps) {
  const { t } = useTranslation()

  // Build 24-hour bar data by expanding each segment to its end (next segment start or 24:00)
  const chartData = buildHourlyBasalData(segments)

  return (
    <div
      style={{ height: 120, marginBottom: '0.5rem' }}
      aria-label={t('report.profile.basalChartAriaLabel')}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={chartData}
          margin={{ top: 4, right: 4, left: -20, bottom: 0 }}
          barCategoryGap={0}
        >
          <XAxis
            dataKey="hour"
            tick={{ fontSize: 10 }}
            tickFormatter={(h: number) => `${String(h).padStart(2, '0')}:00`}
            interval={5}
          />
          <YAxis tick={{ fontSize: 10 }} />
          <Tooltip
            formatter={(value: unknown) => [typeof value === 'number' ? value.toFixed(3) : String(value), t('report.profile.basalRateUnit')]}
            labelFormatter={(label: unknown) =>
              typeof label === 'number'
                ? `${String(label).padStart(2, '0')}:00`
                : String(label)
            }
          />
          <Bar dataKey="rate" isAnimationActive={false}>
            {chartData.map((entry) => (
              <Cell
                key={entry.hour}
                fill={entry.rate === 0 ? '#f59e0b' : '#3b82f6'}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

// ---- Generic segment table ----

interface SegmentTableProps {
  segments: ProfileSegment[]
  valueLabel: string
  formatValue: (v: number) => string
}

function SegmentTable({ segments, valueLabel, formatValue }: SegmentTableProps) {
  const { t } = useTranslation()

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        <thead>
          <tr style={{ background: 'var(--surface)', borderBottom: '2px solid var(--border)' }}>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'left' }}>
              {t('report.profile.startTime')}
            </th>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'right' }}>{valueLabel}</th>
          </tr>
        </thead>
        <tbody>
          {segments.map((seg) => (
            <tr key={seg.startTime} style={{ borderBottom: '1px solid var(--border)' }}>
              <td style={{ padding: '0.3rem 0.5rem', fontFamily: 'monospace' }}>
                {formatTime(seg.startTime)}
              </td>
              <td style={{ padding: '0.3rem 0.5rem', textAlign: 'right' }}>
                {formatValue(seg.value)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ---- Target table ----

interface TargetTableProps {
  segments: TargetSegment[]
  glucoseUnit: string
}

function TargetTable({ segments, glucoseUnit }: TargetTableProps) {
  const { t } = useTranslation()
  const isMmol = glucoseUnit === 'mmol/L'

  const fmtGlucose = (v: number): string =>
    isMmol ? (v / 18.0).toFixed(1) : Math.round(v).toString()

  const unit = isMmol ? 'mmol/L' : 'mg/dL'

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        <thead>
          <tr style={{ background: 'var(--surface)', borderBottom: '2px solid var(--border)' }}>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'left' }}>
              {t('report.profile.startTime')}
            </th>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'right' }}>
              {t('report.profile.targetLow')} ({unit})
            </th>
            <th style={{ padding: '0.35rem 0.5rem', textAlign: 'right' }}>
              {t('report.profile.targetHigh')} ({unit})
            </th>
          </tr>
        </thead>
        <tbody>
          {segments.map((seg) => (
            <tr key={seg.startTime} style={{ borderBottom: '1px solid var(--border)' }}>
              <td style={{ padding: '0.3rem 0.5rem', fontFamily: 'monospace' }}>
                {formatTime(seg.startTime)}
              </td>
              <td style={{ padding: '0.3rem 0.5rem', textAlign: 'right' }}>
                {fmtGlucose(seg.low)}
              </td>
              <td style={{ padding: '0.3rem 0.5rem', textAlign: 'right' }}>
                {fmtGlucose(seg.high)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ---- Helpers ----

/**
 * Format a time string like "08:00:00" or "08:00" to "08:00".
 */
function formatTime(timeStr: string): string {
  return timeStr.slice(0, 5)
}

interface HourlyBasalPoint {
  hour: number
  rate: number
}

/**
 * Expand basal profile segments to per-hour data points for the bar chart.
 * Each segment runs from startTime until the next segment's startTime (or 24:00).
 */
function buildHourlyBasalData(segments: ProfileSegment[]): HourlyBasalPoint[] {
  if (segments.length === 0) return []

  // Sort by start time
  const sorted = [...segments].sort((a, b) => a.startTime.localeCompare(b.startTime))

  const result: HourlyBasalPoint[] = []

  for (let i = 0; i < sorted.length; i++) {
    const seg = sorted[i]
    if (seg == null) continue
    const nextSeg = sorted[i + 1]

    const startHour = parseHour(seg.startTime)
    const endHour = nextSeg != null ? parseHour(nextSeg.startTime) : 24

    for (let h = startHour; h < endHour; h++) {
      result.push({ hour: h, rate: seg.value })
    }
  }

  return result
}

function parseHour(timeStr: string): number {
  const parts = timeStr.split(':')
  return parseInt(parts[0] ?? '0', 10)
}
