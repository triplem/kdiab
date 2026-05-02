import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { Insulin } from '../api/generated';

export const AdminInsulinManager: React.FC = () => {
  const queryClient = useQueryClient();
  const { t } = useTranslation();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState<string>('');
  const [newName, setNewName] = useState<string>('');
  const [mutationError, setMutationError] = useState<string | null>(null);

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string };
    const msg = apiErr?.response?.data?.message ?? apiErr?.message ?? t('admin.error');
    setMutationError(msg);
  };

  const { data: insulins = [], isLoading, error } = useQuery<Insulin[]>({
    queryKey: ['insulins-admin'],
    queryFn: () => api.getInsulins().then(res => res.data),
  });

  const createMutation = useMutation({
    mutationFn: (name: string) => api.createInsulin({ name }),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ['insulins-admin'] });
      queryClient.invalidateQueries({ queryKey: ['insulins'] });
      setNewName('');
    },
    onError: onMutationError,
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, name }: { id: string, name: string }) => api.updateInsulin(id, { name }),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ['insulins-admin'] });
      queryClient.invalidateQueries({ queryKey: ['insulins'] });
      setEditingId(null);
      setEditName('');
    },
    onError: onMutationError,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteInsulin(id),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ['insulins-admin'] });
      queryClient.invalidateQueries({ queryKey: ['insulins'] });
    },
    onError: onMutationError,
  });

  if (isLoading) return <div>{t('admin.loading')}</div>;
  if (error) return <div>{t('admin.error')} {(error as Error).message}</div>;

  return (
    <div className="admin-container" style={{ padding: '20px', maxWidth: '600px', margin: '0 auto', border: '1px solid var(--border-color, #444)', borderRadius: '8px' }}>
      <h2>{t('admin.title')}</h2>
      {mutationError && (
        <div role="alert" className="error" style={{ marginBottom: '1rem' }}>
          {mutationError}
        </div>
      )}
      <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
        {t('admin.description')}
      </p>

      <ul style={{ listStyle: 'none', padding: 0 }}>
        {insulins.map((insulin) => (
          <li key={insulin.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid var(--border-color, #444)' }}>
            {editingId === insulin.id ? (
              <div style={{ display: 'flex', gap: '8px', flex: 1, marginRight: '16px' }}>
                <input
                  type="text"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  autoFocus
                  style={{ flex: 1, padding: '4px' }}
                />
                <button
                  onClick={() => updateMutation.mutate({ id: insulin.id, name: editName })}
                  disabled={updateMutation.isPending || !editName.trim()}
                  className="btn primary"
                >{t('admin.save')}</button>
                <button
                  onClick={() => { setEditingId(null); setEditName(''); }}
                  className="btn"
                >{t('admin.cancel')}</button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flex: 1 }}>
                <span>{insulin.name}</span>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    onClick={() => { setEditingId(insulin.id); setEditName(insulin.name); }}
                    className="btn"
                    style={{ fontSize: '0.8rem', padding: '2px 8px' }}
                  >{t('admin.rename')}</button>
                  <button
                    onClick={() => {
                      if (confirm(t('admin.confirmDelete', { name: insulin.name }))) {
                        deleteMutation.mutate(insulin.id);
                      }
                    }}
                    className="btn danger"
                    style={{ fontSize: '0.8rem', padding: '2px 8px' }}
                  >{t('admin.delete')}</button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>

      <div style={{ marginTop: '20px', display: 'flex', gap: '8px' }}>
        <input
          type="text"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder={t('admin.newInsulinPlaceholder')}
          style={{ flex: 1, padding: '8px' }}
        />
        <button
          onClick={() => createMutation.mutate(newName)}
          disabled={createMutation.isPending || !newName.trim()}
          className="btn primary"
        >
          {t('admin.addInsulin')}
        </button>
      </div>
    </div>
  );
};
