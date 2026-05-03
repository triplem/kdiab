import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { TreatmentResponse } from '../../api/generated';
import { useTimeFormat } from '../../context/TimeFormatContext';
import { useTranslation } from 'react-i18next';

interface TreatmentListProps {
  userId: string;
  canDelete: boolean;
}

/** Format a duration stored in minutes into a human-readable string. */
function formatDuration(minutes: number): string {
  if (minutes >= 60 && minutes % 60 === 0) return `${minutes / 60}h`;
  if (minutes >= 60) return `${Math.floor(minutes / 60)}h ${minutes % 60}min`;
  return `${minutes} min`;
}

const renderDataSummary = (tr: TreatmentResponse): string => {
  const d = tr.data as Record<string, unknown>;
  switch (tr.type) {
    case 'BOLUS':
    case 'CORRECTION_BOLUS':
      return d.insulin != null ? `${d.insulin} U${typeof d.insulinType === 'string' ? ` (${d.insulinType})` : ''}` : JSON.stringify(d);
    case 'COMBO_BOLUS':
      return d.insulin != null ? `${d.insulin} U combo` : JSON.stringify(d);
    case 'BASAL':
      return d.insulin != null ? `${d.insulin} U${typeof d.insulinType === 'string' ? ` (${d.insulinType})` : ''}${typeof d.duration === 'number' ? ` / ${formatDuration(d.duration)}` : ''}` : JSON.stringify(d);
    case 'CARBS':
      return d.carbs != null ? `${d.carbs} g${typeof d.absorptionTime === 'number' ? ` (${d.absorptionTime}h abs.)` : ''}` : JSON.stringify(d);
    case 'TEMP_BASAL':
      return d.rate != null ? `${d.rate} U/h${typeof d.duration === 'number' ? ` for ${formatDuration(d.duration)}` : ''}` : JSON.stringify(d);
    case 'EXERCISE':
      return typeof d.duration === 'number' ? `${formatDuration(d.duration)}${typeof d.intensity === 'string' ? ` (${d.intensity})` : ''}` : JSON.stringify(d);
    case 'NOTE':
      return d.text ? String(d.text).slice(0, 50) : JSON.stringify(d);
    case 'PUMP_SUSPEND':
      return typeof d.duration === 'number' ? `Suspended ${formatDuration(d.duration)}` : 'Pump suspended';
    case 'SITE_CHANGE':
      return d.location ? `Site: ${d.location}` : 'Site change';
    case 'SENSOR_INSERT':
      return d.sensor ? String(d.sensor) : 'Sensor insert';
    case 'INSULIN_CHANGE':
      return d.insulinType ? String(d.insulinType) : 'Insulin change';
    default:
      return JSON.stringify(d);
  }
};

export const TreatmentList: React.FC<TreatmentListProps> = ({ userId, canDelete }) => {
  const queryClient = useQueryClient();
  const { formatDate } = useTimeFormat();
  const { t } = useTranslation();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [mutationError, setMutationError] = useState<string | null>(null);

  const { data: treatments = [], isLoading, isError } = useQuery<TreatmentResponse[]>({
    queryKey: ['treatments', userId],
    queryFn: async () => {
      const res = await api.listTreatments(userId);
      return res.data;
    },
    enabled: !!userId,
  });

  const deleteMutation = useMutation({
    mutationFn: (ids: string[]) => api.deleteTreatments(userId, { treatmentIds: ids }),
    onSuccess: () => {
      setMutationError(null);
      setSelectedIds(new Set());
      void queryClient.invalidateQueries({ queryKey: ['treatments', userId] });
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { message?: string } }; message?: string };
      setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'));
    },
  });

  const toggleSelect = (id: string) => {
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedIds(next);
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === treatments.length) setSelectedIds(new Set());
    else setSelectedIds(new Set(treatments.map(tr => tr.id)));
  };

  const toggleExpand = (id: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    const next = new Set(expandedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpandedIds(next);
  };

  const handleBulkDelete = () => {
    if (window.confirm(t('list.confirmBulkDelete'))) {
      deleteMutation.mutate(Array.from(selectedIds));
    }
  };

  if (isLoading) return <div style={{ padding: '2rem' }}>{t('list.loading')}</div>;
  if (isError) return <div style={{ padding: '2rem', color: 'var(--accent-danger)' }}>{t('list.error')}</div>;

  return (
    <div className="measure-list-container">
      {mutationError && (
        <div role="alert" className="error" style={{ padding: '0.75rem 1rem', marginBottom: '1rem' }}>
          {mutationError}
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2>{t('list.title')}</h2>
        {canDelete && (
          <div className="bulk-actions">
            <button
              disabled={selectedIds.size === 0 || deleteMutation.isPending}
              onClick={handleBulkDelete}
              className="btn danger"
              style={{ padding: '0.4rem 0.8rem' }}
            >
              {t('list.deleteSelected', { count: selectedIds.size })}
            </button>
          </div>
        )}
      </div>

      <table className="measure-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid var(--table-border-header)' }}>
            {canDelete && (
              <th style={{ padding: '12px 8px' }}>
                <input
                  type="checkbox"
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
            const isExpanded = expandedIds.has(tr.id);

            return (
              <React.Fragment key={tr.id}>
                <tr
                  style={{ borderBottom: '1px solid var(--table-border)', cursor: 'pointer', background: selectedIds.has(tr.id) ? 'var(--table-row-selected)' : 'transparent' }}
                  onClick={() => toggleExpand(tr.id)}
                >
                  {canDelete && (
                    <td style={{ padding: '12px 8px' }} onClick={e => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={selectedIds.has(tr.id)}
                        onChange={() => toggleSelect(tr.id)}
                      />
                    </td>
                  )}
                  <td style={{ padding: '12px 8px' }}>
                    <strong>{formatDate(tr.treatedAt)}</strong>
                    <span style={{ marginLeft: '12px', color: 'var(--text-primary)' }}>{renderDataSummary(tr)}</span>
                    {tr.notes && <span style={{ marginLeft: '8px', color: 'var(--text-secondary)', fontStyle: 'italic', fontSize: '0.85rem' }}>— {tr.notes}</span>}
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <span style={{ padding: '2px 6px', background: 'var(--badge-bg)', borderRadius: '4px', fontSize: '0.8rem', color: 'var(--text-primary)' }}>{tr.type}</span>
                  </td>
                  <td style={{ padding: '12px 8px' }} onClick={e => e.stopPropagation()}>
                    {canDelete && (
                      <button
                        className="btn danger"
                        style={{ padding: '2px 8px', fontSize: '0.8rem' }}
                        disabled={deleteMutation.isPending}
                        onClick={() => window.confirm(t('list.confirmDelete')) && deleteMutation.mutate([tr.id])}
                      >
                        {t('list.delete')}
                      </button>
                    )}
                  </td>
                </tr>
                {isExpanded && (
                  <tr style={{ background: 'var(--table-row-alt)' }}>
                    <td colSpan={canDelete ? 4 : 3} style={{ padding: '16px', borderBottom: '1px solid var(--table-border)' }}>
                      <h4>{t('list.details')}</h4>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginTop: '10px' }}>
                        <div>
                          <p><strong>{t('list.systemId')}:</strong> <span style={{ fontFamily: 'monospace' }}>{tr.id}</span></p>
                          <p><strong>{t('list.userId')}:</strong> <span style={{ fontFamily: 'monospace' }}>{tr.userId}</span></p>
                          <p><strong>{t('list.recordedAt')}:</strong> {formatDate(tr.createdAt)}</p>
                          {tr.notes && <p><strong>{t('list.notes')}:</strong> {tr.notes}</p>}
                        </div>
                        <div>
                          <p><strong>{t('list.dataPayload')}:</strong></p>
                          <pre style={{ background: 'var(--code-bg)', padding: '8px', borderRadius: '4px', fontSize: '0.9rem', overflowX: 'auto' }}>
                            {JSON.stringify(tr.data, null, 2)}
                          </pre>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            );
          })}
          {treatments.length === 0 && (
            <tr>
              <td colSpan={canDelete ? 4 : 3} style={{ textAlign: 'center', padding: '2rem' }}>{t('list.empty')}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
};
