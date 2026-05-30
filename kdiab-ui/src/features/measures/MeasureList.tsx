import React, { useState, useEffect } from 'react'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { measuresApi } from '../../api/measuresApi'
import { AddMeasureModal } from './AddMeasureModal'
import type { MeasureEditMode } from './AddMeasureModal'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { ConfirmModal } from '../../components/ConfirmModal'
import { renderDataSummary } from './measureHelpers'
import type { MeasureResponse } from '../../api/measuresApi'
import type { PagedMeasureResponse } from '../../api/generated/measures'

const PAGE_SIZES = [5, 20, 50, 100] as const

interface MeasureListProps {
  userId: string
  glucoseUnit?: string
  weightUnit?: string
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

function getGlucoseZoneForMeasure(m: MeasureResponse): GlucoseZone {
  if (m.type !== 'CGM' && m.type !== 'BGM' && m.type !== 'BG_CHECK') return ''
  const d = m.data as unknown as Record<string, unknown>
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
  weightUnit = 'kg',
  canArchive,
  canDelete,
}) => {
  const queryClient = useQueryClient()
  const { formatDate } = useTimeFormat()
  const { t } = useTranslation()
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(50)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const [editTarget, setEditTarget] = useState<MeasureEditMode | null>(null)
  const [editError, setEditError] = useState<string | null>(null)
  const [isSavingEdit, setIsSavingEdit] = useState(false)
  const [showArchived, setShowArchived] = useState(false)

  const {
    data,
    isLoading,
    isError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['measures', userId, pageSize, showArchived],
    queryFn: async ({ pageParam }: { pageParam: number }) => {
      const status = showArchived ? 'ARCHIVED' : 'ACTIVE'
      const res = await measuresApi.listMeasures(userId, pageParam, pageSize, status)
      return res.data as PagedMeasureResponse
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage: PagedMeasureResponse, _allPages: PagedMeasureResponse[], lastPageParam: number) => {
      const loaded = (lastPageParam + 1) * pageSize
      return loaded < (lastPage.totalCount ?? 0) ? lastPageParam + 1 : undefined
    },
    enabled: !!userId,
  })

  const measures = data?.pages.flatMap((p) => p.items ?? []) ?? []
  const totalCount = data?.pages[0]?.totalCount ?? 0

  useEffect(() => {
    setSelectedIds(new Set())
    setExpandedIds(new Set())
  }, [userId])

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
    setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'))
  }

  const resetAndRefetch = () => {
    setMutationError(null)
    setSelectedIds(new Set())
    void queryClient.invalidateQueries({ queryKey: ['measures', userId] })
  }

  const toggleShowArchived = () => {
    setShowArchived((prev) => !prev)
    setSelectedIds(new Set())
    // showArchived is in the query key so React Query starts fresh automatically
  }

  const handlePageSizeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const next = Number(e.target.value) as (typeof PAGE_SIZES)[number]
    setPageSize(next)
    // pageSize is in the query key so React Query starts fresh automatically
  }

  const archiveMutation = useMutation({
    mutationFn: (ids: string[]) => measuresApi.archiveMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  })

  const unarchiveMutation = useMutation({
    mutationFn: (ids: string[]) => measuresApi.unarchiveMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  })

  const deleteMutation = useMutation({
    mutationFn: (ids: string[]) => measuresApi.deleteMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  })

  const handleEditSave = async (measure: { type: string; measuredAt: string; source: string; data: Record<string, unknown> }) => {
    if (!editTarget) return
    setIsSavingEdit(true)
    setEditError(null)
    try {
      await measuresApi.updateMeasure(userId, editTarget.id, {
        measuredAt: measure.measuredAt,
        data: measure.data,
      })
      setEditTarget(null)
      resetAndRefetch()
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setEditError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'))
    } finally {
      setIsSavingEdit(false)
    }
  }

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

  if (isLoading) {
    return <div style={{ padding: '2rem' }}>{t('list.loading')}</div>
  }
  if (isError) {
    return <div style={{ padding: '2rem', color: 'var(--accent-danger)' }}>{t('list.error')}</div>
  }

  const showAlert = hasAlertInPage(measures)

  return (
    <div className="measure-list-container">
      {editTarget && (
        <AddMeasureModal
          isOpen={true}
          onClose={() => { setEditTarget(null); setEditError(null) }}
          onSave={(m) => { void handleEditSave(m) }}
          glucoseUnit={glucoseUnit}
          weightUnit={weightUnit}
          isSaving={isSavingEdit}
          error={editError}
          editMode={editTarget}
        />
      )}

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
          <button
            className={`btn outline${showArchived ? ' active-tab' : ''}`}
            style={{ padding: '0.4rem 0.8rem', fontSize: '0.875rem' }}
            onClick={toggleShowArchived}
          >
            {showArchived ? t('list.showActive', { defaultValue: 'Show Active' }) : t('list.showArchived', { defaultValue: 'Show Archived' })}
          </button>
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
          {showArchived ? (
            <button
              disabled={selectedIds.size === 0 || unarchiveMutation.isPending}
              onClick={() => unarchiveMutation.mutate(Array.from(selectedIds))}
              className="btn outline"
              style={{ padding: '0.4rem 0.8rem' }}
            >
              {t('list.unarchiveSelected', { defaultValue: 'Unarchive Selected ({{count}})', count: selectedIds.size })}
            </button>
          ) : (
            canArchive && (
              <button
                disabled={selectedIds.size === 0 || archiveMutation.isPending}
                onClick={handleBulkArchive}
                className="btn outline"
                style={{ padding: '0.4rem 0.8rem' }}
              >
                {t('list.archiveSelected', { count: selectedIds.size })}
              </button>
            )
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
            <th scope="col" style={{ padding: '12px 8px' }}>
              <input
                type="checkbox"
                aria-label={t('list.selectAll')}
                checked={measures.length > 0 && selectedIds.size === measures.length}
                onChange={toggleSelectAll}
              />
            </th>
            <th scope="col" style={{ padding: '12px 8px' }}>{t('list.summary')}</th>
            <th scope="col" style={{ padding: '12px 8px' }}>{t('list.typeSource')}</th>
            <th scope="col" style={{ padding: '12px 8px' }}>{t('list.actions')}</th>
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
                  tabIndex={0}
                  role="button"
                  aria-expanded={isExpanded}
                  aria-label={`${formatDate(m.measuredAt)} ${renderDataSummary(m)}`}
                  onClick={() => toggleExpand(m.id)}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleExpand(m.id) } }}
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
                      {t(`measureSource.${m.source}`, { defaultValue: m.source })}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px' }} onClick={(e) => e.stopPropagation()}>
                    {m.type !== 'CGM' && (
                      <button
                        className="btn outline"
                        style={{ marginRight: '5px', padding: '2px 8px', fontSize: '0.8rem' }}
                        onClick={() => setEditTarget({ id: m.id, type: m.type, measuredAt: m.measuredAt, data: m.data as unknown as Record<string, unknown> })}
                      >
                        {t('list.edit', { defaultValue: 'Edit' })}
                      </button>
                    )}
                    {showArchived ? (
                      <button
                        className="btn outline"
                        style={{ marginRight: '5px', padding: '2px 8px', fontSize: '0.8rem' }}
                        disabled={unarchiveMutation.isPending}
                        onClick={() => unarchiveMutation.mutate([m.id])}
                      >
                        {t('list.unarchive', { defaultValue: 'Unarchive' })}
                      </button>
                    ) : (
                      canArchive && (
                        <button
                          className="btn outline"
                          style={{ marginRight: '5px', padding: '2px 8px', fontSize: '0.8rem' }}
                          disabled={archiveMutation.isPending}
                          onClick={() => archiveMutation.mutate([m.id])}
                        >
                          {t('list.archive')}
                        </button>
                      )
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

      {hasNextPage && (
        <div style={{ textAlign: 'center', marginTop: '1rem' }}>
          <button
            className="btn outline"
            disabled={isFetchingNextPage}
            onClick={() => void fetchNextPage()}
            style={{ padding: '0.5rem 1.5rem' }}
          >
            {isFetchingNextPage
              ? t('list.loading')
              : t('list.loadMore', { loaded: measures.length, total: totalCount })}
          </button>
        </div>
      )}
    </div>
  )
}
