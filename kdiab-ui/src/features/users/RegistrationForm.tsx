import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { usersApi } from '../../api/usersApi'

function isSelfRegistrationEnabled(): boolean {
  return import.meta.env['VITE_SELF_REGISTRATION_ENABLED'] === 'true'
}

const schema = z
  .object({
    displayName: z.string().min(1),
    email: z.string().email(),
    password: z.string().min(8),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: 'registration.passwordMismatch',
    path: ['confirmPassword'],
  })

type FormData = z.infer<typeof schema>

interface Props {
  onBack: () => void
}

export function RegistrationForm({ onBack }: Props) {
  const { t } = useTranslation()
  const [submitted, setSubmitted] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  if (!isSelfRegistrationEnabled()) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <p>{t('registration.disabled')}</p>
        <button className="btn outline" onClick={onBack}>{t('registration.backToLogin')}</button>
      </div>
    )
  }

  if (submitted) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', maxWidth: 480, margin: '0 auto' }}>
        <h2>{t('registration.pendingTitle')}</h2>
        <p>{t('registration.pendingMessage')}</p>
        <button className="btn outline" onClick={onBack}>{t('registration.backToLogin')}</button>
      </div>
    )
  }

  const onSubmit = async (values: FormData) => {
    setApiError(null)
    try {
      await usersApi.register({ displayName: values.displayName, email: values.email, password: values.password })
      setSubmitted(true)
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } } }
      if (e.response?.status === 404) {
        setApiError(t('registration.disabled'))
      } else if (e.response?.status === 409) {
        setApiError(t('registration.emailTaken'))
      } else {
        setApiError(t('common.unknownError'))
      }
    }
  }

  return (
    <div style={{ padding: '2rem', maxWidth: 440, margin: '0 auto' }}>
      <h2>{t('registration.title')}</h2>

      {apiError && (
        <div className="banner error" role="alert" style={{ marginBottom: '1rem' }}>
          {apiError}
        </div>
      )}

      <form onSubmit={(e) => { void handleSubmit(onSubmit)(e) }} noValidate>
        <div className="form-group">
          <label htmlFor="reg-name">{t('registration.fieldName')}</label>
          <input id="reg-name" {...register('displayName')} autoComplete="name" />
          {errors.displayName && <p className="field-error">{t('common.required')}</p>}
        </div>

        <div className="form-group">
          <label htmlFor="reg-email">{t('registration.fieldEmail')}</label>
          <input id="reg-email" type="email" {...register('email')} autoComplete="email" />
          {errors.email && <p className="field-error">{t('common.invalidEmail')}</p>}
        </div>

        <div className="form-group">
          <label htmlFor="reg-pw">{t('registration.fieldPassword')}</label>
          <input id="reg-pw" type="password" {...register('password')} autoComplete="new-password" />
          {errors.password && <p className="field-error">{t('adminUsers.passwordMin')}</p>}
        </div>

        <div className="form-group">
          <label htmlFor="reg-cpw">{t('registration.fieldConfirmPassword')}</label>
          <input id="reg-cpw" type="password" {...register('confirmPassword')} autoComplete="new-password" />
          {errors.confirmPassword && <p className="field-error">{t('registration.passwordMismatch')}</p>}
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem' }}>
          <button type="submit" className="primary" disabled={isSubmitting}>
            {isSubmitting ? t('common.saving') : t('registration.submit')}
          </button>
          <button type="button" className="btn outline" onClick={onBack}>
            {t('registration.backToLogin')}
          </button>
        </div>
      </form>
    </div>
  )
}
