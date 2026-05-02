import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { MeasureResponse } from '../../api/generated';
import { useTimeFormat } from '../../context/TimeFormatContext';
import { useTranslation } from 'react-i18next';

const PAGE_SIZE = 50;

interface MeasureListProps {
  userId: string;
  canArchive: boolean;
  canDelete: boolean;
}

type MeasureType = MeasureResponse['type'];

const renderDataSummary = (m: MeasureResponse): string => {
  const d = m.data as Record<string, unknown>;
  switch (m.type as MeasureType) {
    case 'BGM':
    case 'CGM': {
      const trend = d.trend ? ` (${d.trend})` : '';
      const unit = typeof d.unit === 'string' ? d.unit : 'mg/dL';
      return d.value != null ? `${d.value} ${unit}${trend}` : JSON.stringify(d);
    }
    case 'BLOOD_PRESSURE': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mmHg';
      return d.systolic != null && d.diastolic != null
        ? `${d.systolic}/${d.diastolic} ${unit}`
        : JSON.stringify(d);
    }
    case 'WEIGHT': {
      const unit = typeof d.unit === 'string' ? d.unit : 'kg';
      return d.value != null ? `${d.value} ${unit}` : JSON.stringify(d);
    }
    case 'PULSE': {
      const unit = typeof d.unit === 'string' ? d.unit : 'bpm';
      return d.value != null ? `${d.value} ${unit}` : JSON.stringify(d);
    }
    case 'BG_CHECK': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mg/dL';
      return d.value != null ? `${d.value} ${unit}` : JSON.stringify(d);
    }
    case 'KETONE_CHECK': {
      const unit = typeof d.unit === 'string' ? d.unit : 'mmol/L';
      const method = typeof d.method === 'string' ? ` (${d.method})` : '';
      return d.value != null ? `${d.value} ${unit}${method}` : JSON.stringify(d);
    }
    default:
      return JSON.stringify(d);
  }
};

export const MeasureList: React.FC<MeasureListProps> = ({ userId, canArchive, canDelete }) => {
  const queryClient = useQueryClient();
  const { formatDate } = useTimeFormat();
  const { t } = useTranslation();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [measures, setMeasures] = useState<MeasureResponse[]>([]);
  const [totalCount, setTotalCount] = useState(0);

  const { isLoading, isError } = useQuery({
    queryKey: ['measures', userId, page],
    queryFn: async () => {
      const res = await api.listMeasures(userId, page, PAGE_SIZE);
      const paged = res.data as { items: MeasureResponse[]; page: number; size: number; totalCount: number };
      if (page === 0) {
        setMeasures(paged.items ?? []);
      } else {
        setMeasures(prev => [...prev, ...(paged.items ?? [])]);
      }
      setTotalCount(paged.totalCount ?? 0);
      return paged;
    },
    enabled: !!userId,
  });

  useEffect(() => {
    setPage(0);
    setMeasures([]);
  }, [userId]);

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string };
    setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('list.mutationError'));
  };

  const resetAndRefetch = () => {
    setMutationError(null);
    setSelectedIds(new Set());
    setMeasures([]);
    setPage(0);
    void queryClient.invalidateQueries({ queryKey: ['measures', userId] });
  };

  const archiveMutation = useMutation({
    mutationFn: (ids: string[]) => api.archiveMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  });

  const deleteMutation = useMutation({
    mutationFn: (ids: string[]) => api.deleteMeasures(userId, { measureIds: ids }),
    onSuccess: resetAndRefetch,
    onError: onMutationError,
  });

  const toggleSelect = (id: string) => {
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedIds(next);
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === measures.length && measures.length > 0) setSelectedIds(new Set());
    else setSelectedIds(new Set(measures.map(m => m.id)));
  };

  const toggleExpand = (id: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    const next = new Set(expandedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setExpandedIds(next);
  };

  const handleBulkArchive = () => {
    archiveMutation.mutate(Array.from(selectedIds));
  };

  const handleBulkDelete = () => {
    if (window.confirm(t('list.confirmBulkDelete'))) {
      deleteMutation.mutate(Array.from(selectedIds));
    }
  };

  if (isLoading) return <div style={{ padding: '2rem' }}>{t('list.loading')}</div>;
  if (isError) return <div style={{ padding: '2rem', color: 'red' }}>{t('list.error')}</div>;

  return (
    <div className="measure-list-container">
      {mutationError && (
        <div role="alert" style={{ padding: '0.75rem 1rem', marginBottom: '1rem', background: '#fee', border: '1px solid #fcc', borderRadius: '4px', color: '#c00' }}>
          {mutationError}
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2>{t('list.title')}</h2>
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

      <table className="measure-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid #ccc' }}>
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
            const isExpanded = expandedIds.has(m.id);

            return (
              <React.Fragment key={m.id}>
                <tr
                  style={{ borderBottom: '1px solid #eee', cursor: 'pointer', background: selectedIds.has(m.id) ? '#f0f8ff' : 'transparent' }}
                  onClick={() => toggleExpand(m.id)}
                >
                  <td style={{ padding: '12px 8px' }} onClick={e => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      checked={selectedIds.has(m.id)}
                      onChange={() => toggleSelect(m.id)}
                    />
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <strong>{formatDate(m.measuredAt)}</strong>
                    <span style={{ marginLeft: '12px', color: '#444' }}>{renderDataSummary(m)}</span>
                  </td>
                  <td style={{ padding: '12px 8px' }}>
                    <span style={{ padding: '2px 6px', background: '#e0e0e0', borderRadius: '4px', fontSize: '0.8rem' }}>{m.type}</span>
                    <span style={{ marginLeft: '10px', fontSize: '0.9rem', color: '#666' }}>{m.source}</span>
                  </td>
                  <td style={{ padding: '12px 8px' }} onClick={e => e.stopPropagation()}>
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
                        onClick={() => window.confirm(t('list.confirmDelete')) && deleteMutation.mutate([m.id])}
                      >
                        {t('list.delete')}
                      </button>
                    )}
                  </td>
                </tr>
                {isExpanded && (
                  <tr style={{ background: '#fafafa' }}>
                    <td colSpan={4} style={{ padding: '16px', borderBottom: '1px solid #ddd' }}>
                      <h4>{t('list.details')}</h4>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginTop: '10px' }}>
                        <div>
                          <p><strong>{t('list.systemId')}:</strong> <span style={{ fontFamily: 'monospace' }}>{m.id}</span></p>
                          <p><strong>{t('list.userId')}:</strong> <span style={{ fontFamily: 'monospace' }}>{m.userId}</span></p>
                          <p><strong>{t('list.recordedAt')}:</strong> {formatDate(m.createdAt)}</p>
                        </div>
                        <div>
                          <p><strong>{t('list.dataPayload')}:</strong></p>
                          <pre style={{ background: '#eef2f5', padding: '8px', borderRadius: '4px', fontSize: '0.9rem', overflowX: 'auto' }}>
                            {JSON.stringify(m.data, null, 2)}
                          </pre>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            );
          })}
          {measures.length === 0 && (
            <tr>
              <td colSpan={4} style={{ textAlign: 'center', padding: '2rem' }}>{t('list.empty')}</td>
            </tr>
          )}
        </tbody>
      </table>

      {measures.length < totalCount && (
        <div style={{ textAlign: 'center', marginTop: '1rem' }}>
          <button
            className="btn outline"
            disabled={isLoading}
            onClick={() => setPage(p => p + 1)}
            style={{ padding: '0.5rem 1.5rem' }}
          >
            {isLoading ? t('list.loading') : t('list.loadMore', { loaded: measures.length, total: totalCount })}
          </button>
        </div>
      )}
    </div>
  );
};
