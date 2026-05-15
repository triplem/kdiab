import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../../api/usersApi'
import type { UserResponse } from '../../api/usersApi'

const createSchema = z.object({
  displayName: z.string().min(1),
  email: z.string().email(),
  password: z.string().min(8),
  role: z.enum(['PATIENT', 'DOCTOR', 'ADMIN']),
})

const editSchema = z.object({
  displayName: z.string().min(1),
  role: z.enum(['PATIENT', 'DOCTOR', 'ADMIN']),
})

type CreateForm = z.infer<typeof createSchema>
type EditForm = z.infer<typeof editSchema>

const PAGE_SIZE = 20

export function AdminUserList() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [editUser, setEditUser] = useState<UserResponse | null>(null)
  const [deleteUser, setDeleteUser] = useState<UserResponse | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState('')
  const [toast, setToast] = useState<{ kind: 'success' | 'error'; msg: string } | null>(null)

  const showToast = (kind: 'success' | 'error', msg: string) => {
    setToast({ kind, msg })
    setTimeout(() => setToast(null), 4000)
  }

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin', 'users', search, page],
    queryFn: () =>
      usersApi.listUsers({ search: search || undefined, page, size: PAGE_SIZE }).then((r) => r.data),
  })

  const createMutation = useMutation({
    mutationFn: usersApi.createUser,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      setShowCreate(false)
      showToast('success', t('adminUsers.createSuccess'))
    },
    onError: () => showToast('error', t('adminUsers.createError')),
  })

  const editMutation = useMutation({
    mutationFn: ({ userId, body }: { userId: string; body: EditForm }) =>
      usersApi.updateUser(userId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      setEditUser(null)
      showToast('success', t('adminUsers.editSuccess'))
    },
    onError: () => showToast('error', t('adminUsers.editError')),
  })

  const deleteMutation = useMutation({
    mutationFn: (userId: string) => usersApi.deleteUser(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      setDeleteUser(null)
      setDeleteConfirm('')
      showToast('success', t('adminUsers.deleteSuccess'))
    },
    onError: () => showToast('error', t('adminUsers.deleteError')),
  })

  const createForm = useForm<CreateForm>({ resolver: zodResolver(createSchema) })
  const editForm = useForm<EditForm>({
    resolver: zodResolver(editSchema),
    values: editUser
      ? { displayName: editUser.displayName, role: (editUser.roles[0] ?? 'PATIENT') as 'PATIENT' | 'DOCTOR' | 'ADMIN' }
      : undefined,
  })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ margin: 0 }}>{t('adminUsers.title')}</h2>
        <button className="primary" onClick={() => setShowCreate(true)}>
          + {t('adminUsers.createButton')}
        </button>
      </div>

      {toast && (
        <div className={`banner ${toast.kind}`} role="status" style={{ marginBottom: '1rem' }}>
          {toast.msg}
        </div>
      )}

      <div style={{ marginBottom: '1rem' }}>
        <input
          type="search"
          placeholder={t('adminUsers.searchPlaceholder')}
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          style={{ width: '100%', maxWidth: 320 }}
        />
      </div>

      {isLoading ? (
        <p>{t('common.loading')}</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('adminUsers.colName')}</th>
              <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('adminUsers.colEmail')}</th>
              <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('adminUsers.colRole')}</th>
              <th style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('adminUsers.colActions')}</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.userId}>
                <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{u.displayName}</td>
                <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{u.email}</td>
                <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{u.roles.join(', ')}</td>
                <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)', textAlign: 'right' }}>
                  <button className="btn outline" style={{ marginRight: '0.4rem' }} onClick={() => setEditUser(u)}>
                    {t('common.edit')}
                  </button>
                  <button className="btn danger" onClick={() => setDeleteUser(u)}>
                    {t('common.delete')}
                  </button>
                </td>
              </tr>
            ))}
            {users.length === 0 && (
              <tr>
                <td colSpan={4} style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                  {t('adminUsers.empty')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', justifyContent: 'flex-end' }}>
        <button className="btn outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          {t('common.prev')}
        </button>
        <span style={{ alignSelf: 'center', fontSize: '0.85rem' }}>{t('common.page', { page: page + 1 })}</span>
        <button className="btn outline" disabled={users.length < PAGE_SIZE} onClick={() => setPage((p) => p + 1)}>
          {t('common.next')}
        </button>
      </div>

      {/* Create user modal */}
      {showCreate && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={t('adminUsers.createTitle')}>
          <div className="modal-content" style={{ maxWidth: 420 }}>
            <h3>{t('adminUsers.createTitle')}</h3>
            <form onSubmit={(e) => { void createForm.handleSubmit((v) => createMutation.mutate(v))(e) }} noValidate>
              <div className="form-group">
                <label htmlFor="cu-name">{t('adminUsers.fieldName')}</label>
                <input id="cu-name" {...createForm.register('displayName')} />
                {createForm.formState.errors.displayName && <p className="field-error">{t('common.required')}</p>}
              </div>
              <div className="form-group">
                <label htmlFor="cu-email">{t('adminUsers.fieldEmail')}</label>
                <input id="cu-email" type="email" {...createForm.register('email')} />
                {createForm.formState.errors.email && <p className="field-error">{t('common.invalidEmail')}</p>}
              </div>
              <div className="form-group">
                <label htmlFor="cu-pw">{t('adminUsers.fieldPassword')}</label>
                <input id="cu-pw" type="password" {...createForm.register('password')} />
                {createForm.formState.errors.password && <p className="field-error">{t('adminUsers.passwordMin')}</p>}
              </div>
              <div className="form-group">
                <label htmlFor="cu-role">{t('adminUsers.fieldRole')}</label>
                <select id="cu-role" {...createForm.register('role')}>
                  <option value="PATIENT">PATIENT</option>
                  <option value="DOCTOR">DOCTOR</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                <button type="button" className="btn outline" onClick={() => { setShowCreate(false); createForm.reset() }}>
                  {t('common.cancel')}
                </button>
                <button type="submit" className="primary" disabled={createMutation.isPending}>
                  {createMutation.isPending ? t('common.saving') : t('common.create')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit user drawer */}
      {editUser && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={t('adminUsers.editTitle')}>
          <div className="modal-content" style={{ maxWidth: 420 }}>
            <h3>{t('adminUsers.editTitle')}</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', margin: '0 0 0.75rem' }}>
              {editUser.email} · {editUser.userId}
            </p>
            <form onSubmit={(e) => { void editForm.handleSubmit((v) => editMutation.mutate({ userId: editUser.userId, body: v }))(e) }} noValidate>
              <div className="form-group">
                <label htmlFor="eu-name">{t('adminUsers.fieldName')}</label>
                <input id="eu-name" {...editForm.register('displayName')} />
              </div>
              <div className="form-group">
                <label htmlFor="eu-role">{t('adminUsers.fieldRole')}</label>
                <select id="eu-role" {...editForm.register('role')}>
                  <option value="PATIENT">PATIENT</option>
                  <option value="DOCTOR">DOCTOR</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                <button type="button" className="btn outline" onClick={() => setEditUser(null)}>
                  {t('common.cancel')}
                </button>
                <button type="submit" className="primary" disabled={editMutation.isPending}>
                  {editMutation.isPending ? t('common.saving') : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete confirmation dialog */}
      {deleteUser && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={t('adminUsers.deleteTitle')}>
          <div className="modal-content" style={{ maxWidth: 420 }}>
            <h3>{t('adminUsers.deleteTitle')}</h3>
            <p>{t('adminUsers.deleteConfirmPrompt', { email: deleteUser.email })}</p>
            <input
              type="text"
              placeholder={deleteUser.email}
              value={deleteConfirm}
              onChange={(e) => setDeleteConfirm(e.target.value)}
              style={{ width: '100%', marginBottom: '1rem' }}
            />
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
              <button className="btn outline" onClick={() => { setDeleteUser(null); setDeleteConfirm('') }}>
                {t('common.cancel')}
              </button>
              <button
                className="btn danger"
                disabled={deleteConfirm !== deleteUser.email || deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleteUser.userId)}
              >
                {deleteMutation.isPending ? t('common.deleting') : t('common.delete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
