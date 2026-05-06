import React, { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measuresApi } from '../../api/measuresApi'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { ConfirmModal } from '../../components/ConfirmModal'

const PAGE_SIZES = [5, 20, 50, 100] as const

interface MeasureResponse {
  id: string
  userId: string
  measuredAt: string
  createdAt: string
  type: string
  source: string
  status: string
  data: Record<string, unknown>
}

interface PagedMeasures {
  items: MeasureResponse[]
  page: number
  size: number
  totalCount: number
}

interface MeasureListProps {
  userId: string
  glucoseUnit?: string
  canArchive: boolean
  canDelete: boolean
}

type GlucoseZone = 'glucose-low' | 'glucose-target' | 'glucose-high' | 'glucose-very-high' | ''

function getGlucoseZone(valueMgDl: number): GlucoseZone {
  if (valueMgDl < 70) return 'glucose-low'
  if (valueMgDl <= 180) return 'glucose-target'
  if (valueMgDl <= 250) return 'glucose-high'
  return 'glucose-very-high'
}

function getZoneLabel(zone: GlucoseZone, t: TFunction): string {
  switch (zone) {
    case 'glucose-low': return t('list.zoneLow')
    case 'glucose-high': return t('list.zoneHigh')
    case 'glucose-very-high': return t('list.zoneVeryHigh')
    default: return t('list.zoneTarget')
  }
}

function toMgDl(value: number, unit: string): number {
  return unit === 'mmol/L' ? value * 18.0 : value
}

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

function getGlucoseZoneForMeasure(m: MeasureResponse): GlucoseZone {
  if (m.type !== 'CGM' && m.type !== 'BGM' && m.type !== 'BG_CHECK') return ''
  const d = m.data as Record<string, unknown>
  const raw = d['value']
  const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN
  if (isNaN(val)) return ''
  const unit = typeof d['unit'] === 'string' ? d['unit'] : 'mg/dL'
  return getGlucoseZone(toMgDl(val, unit))
}

function hasAlertInPage(measures: MeasureResponse[]): boolean {
  return measures.some((m) => {
    const zone = getGlucoseZoneForMeasure(m)
    return zone === 'glucose-low' || zone === 'glucose-very-high'
  })
}

interface ConfirmAction {
  title: string
  message: string
  danger: boolean
  action: () => void
}

export const MeasureList: React.FC<MeasureListProps> = ({
  userId,
  glucoseUnit = 'mg/dL',
  canArchive,
  canDelete,
}) => {
  const queryClient = useQueryClient()
  const { formatDate } = useTimeFormat()
  const { t } = useTranslation()
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(50)
  const [measures, setMeasures] = useState<MeasureResponse[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)

  const { isLoading, isError } = useQuery({
    queryKey: ['measures', userId, page, pageSize],
    queryFn: async () => {
      const res = await measuresApi.listMeasures(userId, page, pageSize)
      const paged = res.data as PagedMeasures
      if (page === 0) {
        setMeasures(paged.items ?? [])
      } else {
        setMeasures((prev) => [...prev, ...(paged.items ?? [])])
      }
      setTotalCount(paged.totalCount ?? 0)
      return paged
    },
    enabled: !!userId,
  })

  useEffect(() => {
    setPage(0)
    setMeasures([])
  }, [userId])

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
    setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'))
  }

  const resetAndRefetch = () => {
    setMutationError(null)
    setSelectedIds(new Set())
    setMeasures([])
    setPage(0)
    void queryClient.invalidateQueries({ queryKey: ['measures', userId] })
  }

  const handlePageSizeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const next = Number(e.target.value) as (typeof PAGE_SIZES)[number]
    setPageSize(next)
    setMeasures([])
    setPage(0)
  }

  const archiveMutation = useMutation({
    mutationFn: (ids: string[]) => measuresApi.archiveMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  })

  const deleteMutation = useMutation({
    mutationFn: (ids: string[]) => measuresApi.deleteMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  })

  const toggleSelect = (id: string) => {
    const next = new Set(selectedIds)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setSelectedIds(next)
  }

  const toggleSelectAll = () => {
    if (selectedIds.size === measures.length && measures.length > 0) setSelectedIds(new Set())
    else setSelectedIds(new Set(measures.map((m) => m.id)))
  }

  const toggleExpand = (id: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation()
    const next = new Set(expandedIds)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setExpandedIds(next)
  }

  const handleBulkArchive = () => {
    archiveMutation.mutate(Array.from(selectedIds))
  }

  const handleBulkDelete = () => {
    setConfirmAction({
      title: t('confirm.deleteTitle'),
      message: t('list.confirmBulkDelete'),
      danger: true,
      action: () => deleteMutation.mutate(Array.from(selectedIds)),
    })
  }

  const handleSingleDelete = (id: string) => {
    setConfirmAction({
      title: t('confirm.deleteTitle'),
      message: t('list.confirmDelete'),
      danger: true,
      action: () => deleteMutation.mutate([id]),
    })
  }

  if (isLoading && measures.length === 0) {
    return <div style={{ padding: '2rem' }}>{t('list.loading')}</div>
  }
  if (isError) {
    return <div style={{ padding: '2rem', color: 'var(--accent-danger)' }}>{t('list.error')}</div>
  }

  const showAlert = hasAlertInPage(measures)

  return (
    <div className="measure-list-container">
      {confirmAction && (
        <ConfirmModal
          isOpen={true}
          title={confirmAction.title}
          message={confirmAction.message}
          danger={confirmAction.danger}
          confirmLabel={t('confirm.confirmLabel')}
          cancelLabel={t('confirm.cancelLabel')}
          onConfirm={() => {
            confirmAction.action()
            setConfirmAction(null)
          }}
          onCancel={() => setConfirmAction(null)}
        />
      )}

      {showAlert && (
        <div className="glucose-alert-banner" role="alert">
          {t('list.glucoseAlertBanner', {
            low: glucoseUnit === 'mmol/L' ? '3.9' : '70',
            high: glucoseUnit === 'mmol/L' ? '13.9' : '250',
            unit: glucoseUnit,
          })}
        </div>
      )}

      {mutationError && (
        <div role="alert" className="error" style={{ padding: '0.75rem 1rem', marginBottom: '1rem' }}>
          {mutationError}
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2>{t('list.title')}</h2>
        <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.875rem', color: 'var(--color-text-muted)' }}>
            {t('list.pageSize')}
            <select
              value={pageSize}
              onChange={handlePageSizeChange}
              style={{ fontSize: '0.875rem', padding: '0.2rem 0.4rem', borderRadius: '4px', border: '1px solid var(--input-border)', background: 'var(--input-bg)', color: 'var(--color-text)' }}
            >
              {PAGE_SIZES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </label>
          <div className="bulk-actions" style={{ display: 'flex', gap: '10px' }}>
          {canArchive && (
            <button
              disabled={selectedIds.size === 0 || archiveMutation.isPending}
              onClick={handleBulkArchive}
              className="btn outline"
              style={{ padding: '0.4rem 0.8rem' }}
            >
              {t('list.archiveSelected', { count: selectedIds.size })}
            </button>
          )}
          {canDelete && (
            <button
              disabled={selectedIds.size === 0 || deleteMutation.isPending}
              onClick={handleBulkDelete}
              className="btn danger"
              style={{ padding: '0.4rem 0.8rem' }}
            >
              {t('list.deleteSelected', { count: selectedIds.size })}
            </button>
          )}
          </div>
        </div>
      </div>

      <table
        className="measure-table"
        style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}
      >
        <thead>
          <tr style={{ borderBottom: '2px solid var(--table-border-header)' }}>
            <th style={{ padding: '12px 8px' }}>
              <input
                type="checkbox"
                checked={measures.length > 0 && selectedIds.size === measures.length}
                onChange={toggleSelectAll}
              />
            </th>
            <th style={{ padding: '12px 8px' }}>{t('list.summary')}</th>
            <th style={{ padding: '12px 8px' }}>{t('list.typeSource')}</th>
            <th style={{ padding: '12px 8px' }}>{t('list.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {measures.map((m) => {
            const isExpanded = expandedIds.has(m.id)
            const glucoseZone = getGlucoseZoneForMeasure(m)

            return (
              <React.Fragment key={m.id}>
                <tr
                  style={{
                    borderBottom: '1px solid var(--table-border)',
                    cursor: 'pointer',
                    background: selectedIds.has(m.id) ? 'var(--table-row-selected)' : 'transparent',
                  }}
                  onClick={() => toggleExpand(m.id)}
                >
                  <td style={{ padding: '12px 8px' }} onClick={(e) => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      checked={selectedIds.has(m.id)}
                      onChange={() => toggleSelect(m.id)}
                    />
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <strong>{formatDate(m.measuredAt)}</strong>
                    <span
                      className={glucoseZone}
                      style={{ marginLeft: '12px' }}
                    >
                      {renderDataSummary(m)}
                      {glucoseZone !== '' && (
                        <>
                          <span className="sr-only">{getZoneLabel(glucoseZone, t)}</span>
                          {glucoseZone !== 'glucose-target' && (
                            <span className="glucose-zone-label">({getZoneLabel(glucoseZone, t)})</span>
                          )}
                        </>
                      )}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <span
                      style={{
                        padding: '2px 6px',
                        background: 'var(--badge-bg)',
                        borderRadius: '4px',
                        fontSize: '0.8rem',
                        color: 'var(--text-primary)',
                      }}
                    >
                      {m.type}
                    </span>
                    <span style={{ marginLeft: '10px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                      {m.source}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px' }} onClick={(e) => e.stopPropagation()}>
                    {canArchive && (
                      <button
                        className="btn outline"
                        style={{ marginRight: '5px', padding: '2px 8px', fontSize: '0.8rem' }}
                        disabled={archiveMutation.isPending}
                        onClick={() => archiveMutation.mutate([m.id])}
                      >
                        {t('list.archive')}
                      </button>
                    )}
                    {canDelete && (
                      <button
                        className="btn danger"
                        style={{ padding: '2px 8px', fontSize: '0.8rem' }}
                        disabled={deleteMutation.isPending}
                        onClick={() => handleSingleDelete(m.id)}
                      >
                        {t('list.delete')}
                      </button>
                    )}
                  </td>
                </tr>
                {isExpanded && (
                  <tr style={{ background: 'var(--table-row-alt)' }}>
                    <td colSpan={4} style={{ padding: '16px', borderBottom: '1px solid var(--table-border)' }}>
                      <h4>{t('list.details')}</h4>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginTop: '10px' }}>
                        <div>
                          <p>
                            <strong>{t('list.systemId')}:</strong>{' '}
                            <span style={{ fontFamily: 'monospace' }}>{m.id}</span>
                          </p>
                          <p>
                            <strong>{t('list.userId')}:</strong>{' '}
                            <span style={{ fontFamily: 'monospace' }}>{m.userId}</span>
                          </p>
                          <p>
                            <strong>{t('list.recordedAt')}:</strong> {formatDate(m.createdAt)}
                          </p>
                        </div>
                        <div>
                          <p>
                            <strong>{t('list.dataPayload')}:</strong>
                          </p>
                          <pre
                            style={{
                              background: 'var(--code-bg)',
                              padding: '8px',
                              borderRadius: '4px',
                              fontSize: '0.9rem',
                              overflowX: 'auto',
                            }}
                          >
                            {JSON.stringify(m.data, null, 2)}
                          </pre>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            )
          })}
          {measures.length === 0 && (
            <tr>
              <td colSpan={4} style={{ textAlign: 'center', padding: '2rem' }}>
                {t('list.empty')}
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {measures.length < totalCount && (
        <div style={{ textAlign: 'center', marginTop: '1rem' }}>
          <button
            className="btn outline"
            disabled={isLoading}
            onClick={() => setPage((p) => p + 1)}
            style={{ padding: '0.5rem 1.5rem' }}
          >
            {isLoading
              ? t('list.loading')
              : t('list.loadMore', { loaded: measures.length, total: totalCount })}
          </button>
        </div>
      )}
    </div>
  )
}
