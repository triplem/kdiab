import React from 'react'
import { useTranslation } from 'react-i18next'

interface PatientBannerProps {
  patientId: string
  onReturnToOwn: () => void
}

export const PatientBanner: React.FC<PatientBannerProps> = ({ patientId, onReturnToOwn }) => {
  const { t } = useTranslation()

  return (
    <div className="patient-banner" role="status" aria-live="polite">
      <span>
        {t('app.viewingPatient')} <strong>{patientId.slice(0, 8)}…</strong> &mdash;{' '}
        {t('patientBanner.viewingMessage')}
      </span>
      <button
        type="button"
        onClick={onReturnToOwn}
        className="btn outline"
        style={{
          fontSize: '0.85rem',
          padding: '0.3rem 0.75rem',
          borderColor: '#fef3c7',
          color: '#fef3c7',
        }}
      >
        {t('patientBanner.returnButton')}
      </button>
    </div>
  )
}
