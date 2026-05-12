import React, { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { treatmentsApi } from '../../api/treatmentsApi'
import type { TreatmentResponse, PagedTreatments } from '../../api/treatmentsApi'
import { AddTreatmentModal } from './AddTreatmentModal'
import type { TreatmentEditMode } from './AddTreatmentModal'
import { useTimeFormat } from '../../context/TimeFormatContext'
import { useTranslation } from 'react-i18next'
import { ConfirmModal } from '../../components/ConfirmModal'

interface TreatmentListProps {
  userId: string
  canDelete: boolean
  canArchive: boolean
}

const FIELD_LABELS: Record<string, string> = {
  insulin: 'Insulin (U)',
  insulinType: 'Insulin Type',
  carbs: 'Carbohydrates (g)',
  absorptionTime: 'Absorption Time (h)',
  rate: 'Rate (U/h)',
  duration: 'Duration (min)',
  intensity: 'Intensity',
  name: 'Activity',
  text: 'Note',
  location: 'Location',
  sensor: 'Sensor',
  reason: 'Reason',
  immediatePercent: 'Immediate (%)',
}

function renderPayload(data: Record<string, unknown>): React.ReactNode {
  const entries = Object.entries(data)
  if (entries.length === 0) return <span style={{ color: 'var(--text-secondary)' }}>—</span>
  return (
    <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '2px 12px' }}>
      {entries.map(([key, val]) => (
        <React.Fragment key={key}>
          <dt style={{ color: 'var(--text-secondary)', fontWeight: 500, fontSize: '0.85rem' }}>
            {FIELD_LABELS[key] ?? key}:
          </dt>
          <dd style={{ margin: 0, fontSize: '0.85rem' }}>{String(val)}</dd>
        </React.Fragment>
      ))}
    </dl>
  )
}

function formatDuration(minutes: number): string {
  if (minutes >= 60 && minutes % 60 === 0) return `${minutes / 60}h`
  if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${minutes % 60}min`
  return `${minutes} min`
}

const renderDataSummary = (tr: TreatmentResponse): string => {
  const d = tr.data as Record<string, unknown>
  switch (tr.type) {
    case 'BOLUS':
    case 'CORRECTION_BOLUS':
      return d.insulin != null
        ? `${d.insulin as number} U${typeof d.insulinType === 'string' ? ` (${d.insulinType})` : ''}`
        : JSON.stringify(d)
    case 'COMBO_BOLUS':
      return d.insulin != null ? `${d.insulin as number} U combo` : JSON.stringify(d)
    case 'BASAL':
      return d.insulin != null
        ? `${d.insulin as number} U${typeof d.insulinType === 'string' ? ` (${d.insulinType})` : ''}${typeof d.duration === 'number' ? ` / ${formatDuration(d.duration)}` : ''}`
        : JSON.stringify(d)
    case 'CARBS':
      return d.carbs != null
        ? `${d.carbs as number} g${typeof d.absorptionTime === 'number' ? ` (${d.absorptionTime}h abs.)` : ''}`
        : JSON.stringify(d)
    case 'TEMP_BASAL':
      return d.rate != null
        ? `${d.rate as number} U/h${typeof d.duration === 'number' ? ` for ${formatDuration(d.duration)}` : ''}`
        : JSON.stringify(d)
    case 'EXERCISE':
      return typeof d.duration === 'number'
        ? `${formatDuration(d.duration)}${typeof d.intensity === 'string' ? ` (${d.intensity})` : ''}`
        : JSON.stringify(d)
    case 'ACTIVITY':
      return typeof d.duration === 'number'
        ? `${typeof d.name === 'string' ? d.name + ': ' : ''}${formatDuration(d.duration)}${typeof d.intensity === 'string' ? ` (${d.intensity})` : ''}`
        : JSON.stringify(d)
    case 'HYPO_TREATMENT':
      return d.carbs != null
        ? `${d.carbs as number}g carbs${typeof d.reason === 'string' ? ` (${d.reason})` : ''}`
        : JSON.stringify(d)
    case 'NOTE':
      return d.text ? String(d.text).slice(0, 50) : JSON.stringify(d)
    case 'PUMP_SUSPEND':
      return typeof d.duration === 'number' ? `Suspended ${formatDuration(d.duration)}` : 'Pump suspended'
    case 'SITE_CHANGE':
      return d.location ? `Site: ${d.location as string}` : 'Site change'
    case 'SENSOR_INSERT':
      return d.sensor ? String(d.sensor) : 'Sensor insert'
    case 'INSULIN_CHANGE':
      return d.insulinType ? String(d.insulinType) : 'Insulin change'
    default:
      return JSON.stringify(d)
  }
}

interface ConfirmAction {
  title: string
  message: string
  action: () => void
}

const PAGE_SIZE = 25

export const TreatmentList: React.FC<TreatmentListProps> = ({ userId, canDelete, canArchive }) => {
  const queryClient = useQueryClient()
  const { formatDate } = useTimeFormat()
  const { t } = useTranslation()
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const [editTarget, setEditTarget] = useState<TreatmentEditMode | null>(null)
  const [editError, setEditError] = useState<string | null>(null)
  const [isSavingEdit, setIsSavingEdit] = useState(false)
  const [showArchived, setShowArchived] = useState(false)
  const [page, setPage] = useState(0)

  // Reset page when userId changes
  useEffect(() => {
    setPage(0)
  }, [userId])

  const { data: pagedResult, isLoading, isError } = useQuery<PagedTreatments>({
    queryKey: ['treatments', userId, showArchived, page],
    queryFn: async () => {
      const status = showArchived ? 'ARCHIVED' : 'ACTIVE'
      const res = await treatmentsApi.listTreatments(userId, status, page, PAGE_SIZE)
      return res.data as PagedTreatments
    },
    enabled: !!userId,
  })

  const treatments = pagedResult?.items ?? []

  const deleteMutation = useMutation({
    mutationFn: (ids: string[]) => treatmentsApi.deleteTreatments(userId, { treatmentIds: ids }),
    onSuccess: () => {
      setMutationError(null)
      setSelectedIds(new Set())
      void queryClient.invalidateQueries({ queryKey: ['treatments', userId] })
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'))
    },
  })

  const archiveMutation = useMutation({
    mutationFn: (ids: string[]) => treatmentsApi.archiveTreatments(userId, { treatmentIds: ids }),
    onSuccess: () => {
      setMutationError(null)
      setSelectedIds(new Set())
      void queryClient.invalidateQueries({ queryKey: ['treatments', userId] })
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'))
    },
  })

  const unarchiveMutation = useMutation({
    mutationFn: (ids: string[]) => treatmentsApi.unarchiveTreatments(userId, { treatmentIds: ids }),
    onSuccess: () => {
      setMutationError(null)
      setSelectedIds(new Set())
      void queryClient.invalidateQueries({ queryKey: ['treatments', userId] })
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
      setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'))
    },
  })

  const handleEditSave = async (treatment: { type: string; treatedAt: string; data: Record<string, unknown>; notes?: string }) => {
    if (!editTarget) return
    setIsSavingEdit(true)
    setEditError(null)
    try {
      await treatmentsApi.updateTreatment(userId, editTarget.id, {
        treatedAt: treatment.treatedAt,
        data: treatment.data,
        notes: treatment.notes,
      })
      setEditTarget(null)
      void queryClient.invalidateQueries({ queryKey: ['treatments', userId] })
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
    if (selectedIds.size === treatments.length && treatments.length > 0) setSelectedIds(new Set())
    else setSelectedIds(new Set(treatments.map((tr) => tr.id)))
  }

  const toggleExpand = (id: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation()
    const next = new Set(expandedIds)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setExpandedIds(next)
  }

  const handleBulkDelete = () => {
    setConfirmAction({
      title: t('confirm.deleteTitle'),
      message: t('list.confirmBulkDelete'),
      action: () => deleteMutation.mutate(Array.from(selectedIds)),
    })
  }

  const handleSingleDelete = (id: string) => {
    setConfirmAction({
      title: t('confirm.deleteTitle'),
      message: t('list.confirmDelete'),
      action: () => deleteMutation.mutate([id]),
    })
  }

  const handleBulkArchive = () => {
    archiveMutation.mutate(Array.from(selectedIds))
  }

  const handleSingleArchive = (id: string) => {
    archiveMutation.mutate([id])
  }

  const showCheckbox = canArchive || canDelete
  const colSpan = showCheckbox ? 4 : 3

  if (isLoading) return <div style={{ padding: '2rem' }}>{t('list.loading')}</div>
  if (isError) return <div style={{ padding: '2rem', color: 'var(--accent-danger)' }}>{t('list.error')}</div>

  return (
    <div className="measure-list-container">
      {editTarget && (
        <AddTreatmentModal
          isOpen={true}
          onClose={() => { setEditTarget(null); setEditError(null) }}
          onSave={(tr) => { void handleEditSave(tr) }}
          onSaveMeal={() => { /* no-op in edit mode */ }}
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
          danger={true}
          confirmLabel={t('confirm.confirmLabel')}
          cancelLabel={t('confirm.cancelLabel')}
          onConfirm={() => {
            confirmAction.action()
            setConfirmAction(null)
          }}
          onCancel={() => setConfirmAction(null)}
        />
      )}

      {mutationError && (
        <div role="alert" className="error" style={{ padding: '0.75rem 1rem', marginBottom: '1rem' }}>
          {mutationError}
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2>{t('list.title')}</h2>
        <div className="bulk-actions" style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            className={`btn outline${showArchived ? ' active-tab' : ''}`}
            style={{ padding: '0.4rem 0.8rem', fontSize: '0.875rem' }}
            onClick={() => { setShowArchived((p) => !p); setSelectedIds(new Set()); setPage(0) }}
          >
            {showArchived ? t('list.showActive', { defaultValue: 'Show Active' }) : t('list.showArchived', { defaultValue: 'Show Archived' })}
          </button>
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

      <table
        className="measure-table"
        style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}
      >
        <thead>
          <tr style={{ borderBottom: '2px solid var(--table-border-header)' }}>
            {showCheckbox && (
              <th style={{ padding: '12px 8px' }}>
                <input
                  type="checkbox"
                  aria-label={t('list.selectAll', { defaultValue: 'Select all' })}
                  checked={treatments.length > 0 && selectedIds.size === treatments.length}
                  onChange={toggleSelectAll}
                />
              </th>
            )}
            <th style={{ padding: '12px 8px' }}>{t('list.summary')}</th>
            <th style={{ padding: '12px 8px' }}>{t('list.type')}</th>
            <th style={{ padding: '12px 8px' }}>{t('list.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {treatments.map((tr) => {
            const isExpanded = expandedIds.has(tr.id)
            return (
              <React.Fragment key={tr.id}>
                <tr
                  style={{
                    borderBottom: '1px solid var(--table-border)',
                    cursor: 'pointer',
                    background: selectedIds.has(tr.id) ? 'var(--table-row-selected)' : 'transparent',
                  }}
                  onClick={() => toggleExpand(tr.id)}
                >
                  {showCheckbox && (
                    <td style={{ padding: '12px 8px' }} onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={selectedIds.has(tr.id)}
                        onChange={() => toggleSelect(tr.id)}
                      />
                    </td>
                  )}
                  <td style={{ padding: '12px 8px' }}>
                    <strong>{formatDate(tr.treatedAt)}</strong>
                    <span style={{ marginLeft: '12px', color: 'var(--text-primary)' }}>
                      {renderDataSummary(tr)}
                    </span>
                    {tr.notes && (
                      <span
                        style={{
                          marginLeft: '8px',
                          color: 'var(--text-secondary)',
                          fontStyle: 'italic',
                          fontSize: '0.85rem',
                        }}
                      >
                        — {tr.notes}
                      </span>
                    )}
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
                      {t(`treatmentModal.types.${tr.type}`, { defaultValue: tr.type })}
                    </span>
                  </td>
                  <td style={{ padding: '12px 8px' }} onClick={(e) => e.stopPropagation()}>
                    <div style={{ display: 'flex', gap: '0.4rem' }}>
                      <button
                        className="btn outline"
                        style={{ padding: '2px 8px', fontSize: '0.8rem' }}
                        onClick={() => setEditTarget({
                          id: tr.id,
                          type: tr.type,
                          treatedAt: tr.treatedAt,
                          data: tr.data as Record<string, unknown>,
                          notes: tr.notes,
                        })}
                      >
                        {t('list.edit', { defaultValue: 'Edit' })}
                      </button>
                      {showArchived ? (
                        <button
                          className="btn outline"
                          style={{ padding: '2px 8px', fontSize: '0.8rem' }}
                          disabled={unarchiveMutation.isPending}
                          onClick={() => unarchiveMutation.mutate([tr.id])}
                        >
                          {t('list.unarchive', { defaultValue: 'Unarchive' })}
                        </button>
                      ) : (
                        canArchive && (
                          <button
                            className="btn outline"
                            style={{ padding: '2px 8px', fontSize: '0.8rem' }}
                            disabled={archiveMutation.isPending}
                            onClick={() => handleSingleArchive(tr.id)}
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
                          onClick={() => handleSingleDelete(tr.id)}
                        >
                          {t('list.delete')}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
                {isExpanded && (
                  <tr style={{ background: 'var(--table-row-alt)' }}>
                    <td
                      colSpan={colSpan}
                      style={{ padding: '16px', borderBottom: '1px solid var(--table-border)' }}
                    >
                      <h4>{t('list.details')}</h4>
                      <div
                        style={{
                          display: 'grid',
                          gridTemplateColumns: '1fr 1fr',
                          gap: '1rem',
                          marginTop: '10px',
                        }}
                      >
                        <div>
                          <p>
                            <strong>{t('list.systemId')}:</strong>{' '}
                            <span style={{ fontFamily: 'monospace' }}>{tr.id}</span>
                          </p>
                          <p>
                            <strong>{t('list.userId')}:</strong>{' '}
                            <span style={{ fontFamily: 'monospace' }}>{tr.userId}</span>
                          </p>
                          <p>
                            <strong>{t('list.recordedAt')}:</strong> {formatDate(tr.createdAt)}
                          </p>
                          {tr.notes && (
                            <p>
                              <strong>{t('list.notes')}:</strong> {tr.notes}
                            </p>
                          )}
                        </div>
                        <div>
                          <p><strong>{t('list.dataPayload')}:</strong></p>
                          {renderPayload(tr.data as Record<string, unknown>)}
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            )
          })}
          {treatments.length === 0 && (
            <tr>
              <td colSpan={colSpan} style={{ textAlign: 'center', padding: '2rem' }}>
                {t('list.empty')}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
