import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { profilesApi } from '../../api/profilesApi'
import type { Insulin } from '../../api/profilesApi'
import { ConfirmModal } from '../../components/ConfirmModal'

export function AdminInsulinManager() {
  const queryClient = useQueryClient()
  const { t } = useTranslation()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState<string>('')
  const [newName, setNewName] = useState<string>('')
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Insulin | null>(null)

  const onMutationError = (err: unknown) => {
    const apiErr = err as { response?: { data?: { message?: string } }; message?: string }
    setMutationError(apiErr?.response?.data?.message ?? apiErr?.message ?? t('admin.error'))
  }

  const { data: insulins = [], isLoading, error } = useQuery<Insulin[]>({
    queryKey: ['insulins-admin'],
    queryFn: () => profilesApi.getInsulins().then(res => res.data),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['insulins-admin'] })
    void queryClient.invalidateQueries({ queryKey: ['insulins'] })
  }

  const createMutation = useMutation({
    mutationFn: (name: string) => profilesApi.createInsulin({ name }),
    onSuccess: () => { setMutationError(null); invalidate(); setNewName('') },
    onError: onMutationError,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => profilesApi.updateInsulin(id, { name }),
    onSuccess: () => { setMutationError(null); invalidate(); setEditingId(null); setEditName('') },
    onError: onMutationError,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => profilesApi.deleteInsulin(id),
    onSuccess: () => { setMutationError(null); invalidate() },
    onError: onMutationError,
  })

  if (isLoading) return <div>{t('admin.loading')}</div>
  if (error) return <div>{t('admin.error')} {(error as Error).message}</div>

  return (
    <div className="card" style={{ maxWidth: '600px' }}>
      <h2>{t('admin.title')}</h2>
      {mutationError && (
        <div role="alert" className="error" style={{ marginBottom: '1rem' }}>
          {mutationError}
        </div>
      )}
      <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '1.25rem' }}>
        {t('admin.description')}
      </p>

      <ul style={{ listStyle: 'none', padding: 0 }}>
        {insulins.map((insulin) => (
          <li key={insulin.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.625rem 0', borderBottom: '1px solid var(--border-color)' }}>
            {editingId === insulin.id ? (
              <div style={{ display: 'flex', gap: '0.5rem', flex: 1, marginRight: '1rem' }}>
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
                <button onClick={() => { setEditingId(null); setEditName('') }} className="btn">{t('admin.cancel')}</button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flex: 1 }}>
                <span>{insulin.name}</span>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button
                    onClick={() => { setEditingId(insulin.id); setEditName(insulin.name) }}
                    className="btn"
                    style={{ fontSize: '0.8rem', padding: '2px 8px' }}
                  >{t('admin.rename')}</button>
                  <button
                    onClick={() => setConfirmDelete(insulin)}
                    className="btn danger"
                    style={{ fontSize: '0.8rem', padding: '2px 8px' }}
                  >{t('admin.delete')}</button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>

      <div style={{ marginTop: '1.25rem', display: 'flex', gap: '0.5rem' }}>
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

      {confirmDelete && (
        <ConfirmModal
          isOpen={true}
          title={t('admin.delete')}
          message={t('admin.confirmDelete', { name: confirmDelete.name })}
          danger
          onConfirm={() => { deleteMutation.mutate(confirmDelete.id); setConfirmDelete(null) }}
          onCancel={() => setConfirmDelete(null)}
        />
      )}
    </div>
  )
}
