import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../../api/usersApi'
import type { InvitationStatus } from '../../api/usersApi'
import { isAxiosError } from 'axios'

const STATUS_I18N_KEYS: Record<InvitationStatus, string> = {
  PENDING: 'invitations.statusPending',
  ACCEPTED: 'invitations.statusAccepted',
  DECLINED: 'invitations.statusDeclined',
  CANCELLED: 'invitations.statusCancelled',
  EXPIRED: 'invitations.statusExpired',
}

interface Props {
  doctorId: string
}

function statusBadgeStyle(status: InvitationStatus): React.CSSProperties {
  const base: React.CSSProperties = {
    display: 'inline-block',
    padding: '0.15rem 0.5rem',
    borderRadius: '0.25rem',
    fontSize: '0.8rem',
    fontWeight: 600,
  }
  switch (status) {
    case 'PENDING':
      return { ...base, background: '#fff3cd', color: '#856404' }
    case 'ACCEPTED':
      return { ...base, background: '#d1e7dd', color: '#0f5132' }
    case 'DECLINED':
      return { ...base, background: '#f8d7da', color: '#842029' }
    case 'CANCELLED':
      return { ...base, background: '#e2e3e5', color: '#41464b' }
    case 'EXPIRED':
      return { ...base, background: '#f0f0f0', color: '#6c757d' }
    default:
      return base
  }
}

export function DoctorInvitations({ doctorId }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [patientIdentifier, setPatientIdentifier] = useState('')
  const [message, setMessage] = useState('')
  const [sendError, setSendError] = useState<string | null>(null)
  const [cancelConfirmId, setCancelConfirmId] = useState<string | null>(null)

  const [toast, setToast] = useState<{ kind: 'success' | 'error'; msg: string } | null>(null)

  const showToast = (kind: 'success' | 'error', msg: string) => {
    setToast({ kind, msg })
    setTimeout(() => setToast(null), 4000)
  }

  const { data, isLoading } = useQuery({
    queryKey: ['doctor-invitations', doctorId],
    queryFn: () => usersApi.listDoctorInvitations(doctorId).then((r) => r.data),
  })

  const sendMutation = useMutation({
    mutationFn: () => usersApi.sendInvitation(doctorId, { patientIdentifier: patientIdentifier.trim(), message: message.trim() || null }),
    onSuccess: () => {
      setSendError(null)
      setPatientIdentifier('')
      setMessage('')
      void queryClient.invalidateQueries({ queryKey: ['doctor-invitations', doctorId] })
      showToast('success', t('invitations.sendSuccess'))
    },
    onError: (err: unknown) => {
      if (isAxiosError(err)) {
        const status = err.response?.status
        if (status === 409) {
          setSendError(t('invitations.sendErrorDuplicate'))
        } else if (status === 400) {
          setSendError(t('invitations.sendErrorNotFound'))
        } else {
          setSendError(t('invitations.sendError'))
        }
      } else {
        setSendError(t('invitations.sendError'))
      }
    },
  })

  const cancelMutation = useMutation({
    mutationFn: (invitationId: string) => usersApi.cancelInvitation(doctorId, invitationId),
    onSuccess: () => {
      setCancelConfirmId(null)
      void queryClient.invalidateQueries({ queryKey: ['doctor-invitations', doctorId] })
      showToast('success', t('invitations.cancelSuccess'))
    },
    onError: () => {
      setCancelConfirmId(null)
      showToast('error', t('invitations.cancelError'))
    },
  })

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault()
    setSendError(null)
    sendMutation.mutate()
  }

  const invitations = data?.content ?? []

  const cancelTarget = cancelConfirmId !== null
    ? invitations.find((inv) => inv.id === cancelConfirmId)
    : null

  return (
    <div>
      {toast && (
        <div className={`banner ${toast.kind}`} role="status" style={{ marginBottom: '1rem' }}>
          {toast.msg}
        </div>
      )}

      {/* Send invitation form */}
      <section style={{ marginBottom: '2rem', maxWidth: 480 }}>
        <h3 style={{ marginTop: 0 }}>{t('invitations.inviteTitle')}</h3>
        <form onSubmit={handleSend}>
          <div style={{ marginBottom: '0.75rem' }}>
            <label htmlFor="patient-identifier" style={{ display: 'block', marginBottom: '0.25rem', fontWeight: 500 }}>
              {t('invitations.patientIdentifierLabel')}
            </label>
            <input
              id="patient-identifier"
              type="text"
              value={patientIdentifier}
              onChange={(e) => setPatientIdentifier(e.target.value)}
              placeholder={t('invitations.patientIdentifierPlaceholder')}
              style={{ width: '100%' }}
              required
              disabled={sendMutation.isPending}
            />
          </div>
          <div style={{ marginBottom: '0.75rem' }}>
            <label htmlFor="invitation-message" style={{ display: 'block', marginBottom: '0.25rem', fontWeight: 500 }}>
              {t('invitations.messageLabel')}
            </label>
            <textarea
              id="invitation-message"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={3}
              style={{ width: '100%', resize: 'vertical' }}
              disabled={sendMutation.isPending}
            />
          </div>
          {sendError && (
            <div role="alert" style={{ color: 'var(--color-error, #c0392b)', marginBottom: '0.75rem', fontSize: '0.875rem' }}>
              {sendError}
            </div>
          )}
          <button type="submit" className="primary" disabled={sendMutation.isPending || !patientIdentifier.trim()}>
            {sendMutation.isPending ? t('common.saving') : t('invitations.sendButton')}
          </button>
        </form>
      </section>

      {/* Invitation list */}
      <section>
        <h3 style={{ marginTop: 0 }}>{t('invitations.doctorTabTitle')}</h3>
        {isLoading ? (
          <p>{t('common.loading')}</p>
        ) : invitations.length === 0 ? (
          <p style={{ color: 'var(--text-secondary)' }}>{t('invitations.noPendingInvitations')}</p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                  {t('invitations.colPatientIdentifier')}
                </th>
                <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                  {t('invitations.colStatus')}
                </th>
                <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                  {t('invitations.colExpires')}
                </th>
                <th style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody>
              {invitations.map((inv) => (
                <tr key={inv.id}>
                  <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                    {inv.patientIdentifier}
                  </td>
                  <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                    <span style={statusBadgeStyle(inv.status)}>
                      {t(STATUS_I18N_KEYS[inv.status] as Parameters<typeof t>[0])}
                    </span>
                  </td>
                  <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)', fontSize: '0.875rem' }}>
                    {new Date(inv.expiresAt).toLocaleDateString()}
                  </td>
                  <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)', textAlign: 'right' }}>
                    {inv.status === 'PENDING' && (
                      <button
                        className="btn danger"
                        onClick={() => setCancelConfirmId(inv.id)}
                        disabled={cancelMutation.isPending}
                      >
                        {t('invitations.cancelButton')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* Cancel confirmation */}
      {cancelTarget && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={t('invitations.cancelConfirmTitle')}>
          <div className="modal-box" style={{ maxWidth: 380 }}>
            <h3>{t('invitations.cancelConfirmTitle')}</h3>
            <p>{t('invitations.cancelConfirmBody', { identifier: cancelTarget.patientIdentifier })}</p>
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
              <button className="btn outline" onClick={() => setCancelConfirmId(null)}>
                {t('common.cancel')}
              </button>
              <button
                className="btn danger"
                disabled={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate(cancelTarget.id)}
              >
                {cancelMutation.isPending ? t('common.removing') : t('invitations.cancelButton')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
