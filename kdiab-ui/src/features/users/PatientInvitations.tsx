import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { usersApi } from '../../api/usersApi'
import type { InvitationResponse } from '../../api/usersApi'

interface Props {
  patientId: string
}

interface ConfirmState {
  invitationId: string
  doctorName: string
  action: 'ACCEPT' | 'DECLINE'
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function PatientInvitations({ patientId }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null)

  const { data: invitations = [], isLoading } = useQuery({
    queryKey: ['incoming-invitations', patientId],
    queryFn: () => usersApi.listIncomingInvitations(patientId).then((r) => r.data),
    enabled: !!patientId,
  })

  const respondMutation = useMutation({
    mutationFn: (vars: { invitationId: string; action: 'ACCEPT' | 'DECLINE'; doctorName: string }) =>
      usersApi.respondToInvitation(patientId, vars.invitationId, { action: vars.action }).then((r) => r.data),
    onSuccess: (_data, vars) => {
      void queryClient.invalidateQueries({ queryKey: ['incoming-invitations', patientId] })
      if (vars.action === 'ACCEPT') {
        toast.success(t('invitations.acceptSuccess', { doctorName: vars.doctorName }))
      } else {
        toast.success(t('invitations.declineSuccess'))
      }
      setConfirmState(null)
    },
    onError: (_err, vars) => {
      if (vars.action === 'ACCEPT') {
        toast.error(t('invitations.acceptError'))
      } else {
        toast.error(t('invitations.declineError'))
      }
    },
  })

  const pendingInvitations = invitations.filter(
    (inv: InvitationResponse) => inv.status === 'PENDING',
  )

  const handleAcceptClick = (inv: InvitationResponse) => {
    const doctorName = inv.doctorId
    setConfirmState({ invitationId: inv.id, doctorName, action: 'ACCEPT' })
  }

  const handleDeclineClick = (inv: InvitationResponse) => {
    const doctorName = inv.doctorId
    setConfirmState({ invitationId: inv.id, doctorName, action: 'DECLINE' })
  }

  const handleConfirm = () => {
    if (!confirmState) return
    respondMutation.mutate({
      invitationId: confirmState.invitationId,
      action: confirmState.action,
      doctorName: confirmState.doctorName,
    })
  }

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>{t('invitations.incomingTitle')}</h2>

      {isLoading ? (
        <p>{t('common.loading')}</p>
      ) : pendingInvitations.length === 0 ? (
        <p style={{ color: 'var(--text-secondary)' }}>{t('invitations.noIncomingInvitations')}</p>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {pendingInvitations.map((inv: InvitationResponse) => (
            <li
              key={inv.id}
              style={{
                border: '1px solid var(--border)',
                borderRadius: '0.5rem',
                padding: '1rem',
                marginBottom: '0.75rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
            >
              <div style={{ fontWeight: 600 }}>
                {t('invitations.fromDoctor', { doctorName: inv.doctorId })}
              </div>
              {inv.message && (
                <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
                  {inv.message}
                </div>
              )}
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                {t('invitations.sentAt', { date: formatDate(inv.createdAt) })}
                {' · '}
                {t('invitations.expiresAt', { date: formatDate(inv.expiresAt) })}
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.25rem' }}>
                <button
                  className="primary"
                  onClick={() => handleAcceptClick(inv)}
                  disabled={respondMutation.isPending}
                >
                  {t('invitations.acceptButton')}
                </button>
                <button
                  className="btn outline"
                  onClick={() => handleDeclineClick(inv)}
                  disabled={respondMutation.isPending}
                >
                  {t('invitations.declineButton')}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* Confirmation dialog */}
      {confirmState && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-label={
            confirmState.action === 'ACCEPT'
              ? t('invitations.acceptConfirmTitle')
              : t('invitations.declineConfirmTitle')
          }
        >
          <div className="modal-box" style={{ maxWidth: 420 }}>
            <h3>
              {confirmState.action === 'ACCEPT'
                ? t('invitations.acceptConfirmTitle')
                : t('invitations.declineConfirmTitle')}
            </h3>
            <p>
              {confirmState.action === 'ACCEPT'
                ? t('invitations.acceptConfirmBody', { doctorName: confirmState.doctorName })
                : t('invitations.declineConfirmBody', { doctorName: confirmState.doctorName })}
            </p>
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
              <button
                className="btn outline"
                onClick={() => setConfirmState(null)}
                disabled={respondMutation.isPending}
              >
                {t('common.cancel')}
              </button>
              <button
                className={confirmState.action === 'ACCEPT' ? 'primary' : 'btn danger'}
                onClick={handleConfirm}
                disabled={respondMutation.isPending}
              >
                {respondMutation.isPending
                  ? t('common.saving')
                  : confirmState.action === 'ACCEPT'
                    ? t('invitations.acceptButton')
                    : t('invitations.declineButton')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
