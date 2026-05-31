import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../../api/usersApi'
import type { UserResponse } from '../../api/usersApi'

export function AdminDoctorPatients() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selectedDoctor, setSelectedDoctor] = useState<UserResponse | null>(null)
  const [showAssign, setShowAssign] = useState(false)
  const [removePatient, setRemovePatient] = useState<{ patientId: string; displayName: string } | null>(null)
  const [patientSearch, setPatientSearch] = useState('')
  const [toast, setToast] = useState<{ kind: 'success' | 'error'; msg: string } | null>(null)

  const showToast = (kind: 'success' | 'error', msg: string) => {
    setToast({ kind, msg })
    setTimeout(() => setToast(null), 4000)
  }

  const { data: allUsers = [], isLoading: loadingUsers } = useQuery({
    queryKey: ['admin', 'users', '', 0],
    queryFn: () => usersApi.listUsers({ size: 100 }).then((r) => r.data),
  })

  const doctors = allUsers.filter((u) => u.roles.includes('DOCTOR'))
  const patients = allUsers.filter((u) => u.roles.includes('PATIENT'))

  const { data: assignedRelations = [], isLoading: loadingPatients } = useQuery({
    queryKey: ['admin', 'doctor-patients', selectedDoctor?.userId],
    queryFn: () =>
      selectedDoctor
        ? usersApi.getPatients(selectedDoctor.userId, { size: 100 }).then((r) => r.data)
        : Promise.resolve([]),
    enabled: selectedDoctor != null,
  })

  const assignedPatientIds = new Set(assignedRelations.map((r) => r.patientId))

  const assignMutation = useMutation({
    mutationFn: ({ doctorId, patientId }: { doctorId: string; patientId: string }) =>
      usersApi.assignPatient(doctorId, patientId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'doctor-patients'] })
      setShowAssign(false)
      setPatientSearch('')
      showToast('success', t('doctorPatients.assignSuccess'))
    },
    onError: () => showToast('error', t('doctorPatients.assignError')),
  })

  const removeMutation = useMutation({
    mutationFn: ({ doctorId, patientId }: { doctorId: string; patientId: string }) =>
      usersApi.removePatient(doctorId, patientId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'doctor-patients'] })
      setRemovePatient(null)
      showToast('success', t('doctorPatients.removeSuccess'))
    },
    onError: () => showToast('error', t('doctorPatients.removeError')),
  })

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr', gap: '1.5rem', alignItems: 'start' }}>
      {/* Doctor list sidebar */}
      <div>
        <h3 style={{ marginTop: 0 }}>{t('doctorPatients.doctorsTitle')}</h3>
        {loadingUsers ? (
          <p>{t('common.loading')}</p>
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {doctors.map((d) => (
              <li key={d.userId}>
                <button
                  className={selectedDoctor?.userId === d.userId ? 'active-tab' : 'btn outline'}
                  style={{ width: '100%', textAlign: 'left', marginBottom: '0.3rem' }}
                  onClick={() => setSelectedDoctor(d)}
                >
                  {d.displayName}
                </button>
              </li>
            ))}
            {doctors.length === 0 && (
              <li style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>{t('doctorPatients.noDoctors')}</li>
            )}
          </ul>
        )}
      </div>

      {/* Patient list for selected doctor */}
      <div>
        {toast && (
          <div className={`banner ${toast.kind}`} role="status" style={{ marginBottom: '1rem' }}>
            {toast.msg}
          </div>
        )}

        {selectedDoctor ? (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h3 style={{ margin: 0 }}>
                {t('doctorPatients.patientsOf', { name: selectedDoctor.displayName })}
              </h3>
              <button className="primary" onClick={() => setShowAssign(true)}>
                + {t('doctorPatients.assignButton')}
              </button>
            </div>

            <p className="hint" style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', margin: '0 0 1rem' }}>
              {t('doctorPatients.nextLoginNote')}
            </p>

            {loadingPatients ? (
              <p>{t('common.loading')}</p>
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('doctorPatients.colPatient')}</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('doctorPatients.colEmail')}</th>
                    <th style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {assignedRelations.map((rel) => {
                    const patient = allUsers.find((u) => u.userId === rel.patientId)
                    return (
                      <tr key={rel.patientId}>
                        <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                          {patient?.displayName ?? rel.patientId}
                        </td>
                        <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)' }}>
                          {patient?.email ?? ''}
                        </td>
                        <td style={{ padding: '0.5rem', borderBottom: '1px solid var(--border)', textAlign: 'right' }}>
                          <button
                            className="btn danger"
                            onClick={() => setRemovePatient({ patientId: rel.patientId, displayName: patient?.displayName ?? rel.patientId })}
                          >
                            {t('doctorPatients.removeButton')}
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                  {assignedRelations.length === 0 && (
                    <tr>
                      <td colSpan={3} style={{ padding: '1rem', textAlign: 'center', color: 'var(--text-secondary)' }}>
                        {t('doctorPatients.noPatientsAssigned')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </>
        ) : (
          <p style={{ color: 'var(--text-secondary)' }}>{t('doctorPatients.selectDoctor')}</p>
        )}
      </div>

      {/* Assign patient modal */}
      {showAssign && selectedDoctor && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={t('doctorPatients.assignTitle')}>
          <div className="modal-box" style={{ maxWidth: 420 }}>
            <h3>{t('doctorPatients.assignTitle')}</h3>
            <input
              type="search"
              placeholder={t('doctorPatients.searchPatient')}
              value={patientSearch}
              onChange={(e) => setPatientSearch(e.target.value)}
              style={{ width: '100%', marginBottom: '0.75rem' }}
              autoFocus
            />
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, maxHeight: 240, overflowY: 'auto' }}>
              {patients
                .filter((p) => !assignedPatientIds.has(p.userId))
                .filter((p) => p.displayName.toLowerCase().includes(patientSearch.toLowerCase()) || p.email.toLowerCase().includes(patientSearch.toLowerCase()))
                .map((p) => (
                  <li key={p.userId} style={{ marginBottom: '0.3rem' }}>
                    <button
                      className="btn outline"
                      style={{ width: '100%', textAlign: 'left' }}
                      onClick={() => assignMutation.mutate({ doctorId: selectedDoctor.userId, patientId: p.userId })}
                      disabled={assignMutation.isPending}
                    >
                      {p.displayName} <span style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>({p.email})</span>
                    </button>
                  </li>
                ))}
            </ul>
            <div style={{ textAlign: 'right', marginTop: '0.75rem' }}>
              <button className="btn outline" onClick={() => { setShowAssign(false); setPatientSearch('') }}>
                {t('common.cancel')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Remove confirmation */}
      {removePatient && selectedDoctor && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label={t('doctorPatients.removeConfirmTitle')}>
          <div className="modal-box" style={{ maxWidth: 380 }}>
            <h3>{t('doctorPatients.removeConfirmTitle')}</h3>
            <p>{t('doctorPatients.removeConfirmBody', { patient: removePatient.displayName, doctor: selectedDoctor.displayName })}</p>
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
              <button className="btn outline" onClick={() => setRemovePatient(null)}>{t('common.cancel')}</button>
              <button
                className="btn danger"
                disabled={removeMutation.isPending}
                onClick={() => removeMutation.mutate({ doctorId: selectedDoctor.userId, patientId: removePatient.patientId })}
              >
                {removeMutation.isPending ? t('common.removing') : t('doctorPatients.removeButton')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
