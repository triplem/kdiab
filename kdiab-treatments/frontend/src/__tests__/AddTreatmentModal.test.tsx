import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AddTreatmentModal } from '../features/treatments/AddTreatmentModal'
import { I18nextProvider } from 'react-i18next'
import i18n from '../i18n'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import React from 'react'

function renderModal(props: Partial<Parameters<typeof AddTreatmentModal>[0]> = {}) {
  const defaults = {
    isOpen: true,
    onClose: vi.fn(),
    onSave: vi.fn(),
    isSaving: false,
    error: null as string | null,
  }
  return render(
    <I18nextProvider i18n={i18n}>
      <TimeFormatProvider>
        <AddTreatmentModal {...defaults} {...props} />
      </TimeFormatProvider>
    </I18nextProvider>
  )
}

describe('AddTreatmentModal', () => {
  it('shows error message when error prop is set', () => {
    renderModal({ error: 'Network error' })
    expect(screen.getByRole('alert')).toHaveTextContent('Network error')
  })

  it('does not show error div when error is null', () => {
    renderModal({ error: null })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('does not call onClose when Save is clicked — parent controls lifecycle', () => {
    const onClose = vi.fn()
    const onSave = vi.fn()
    renderModal({ onClose, onSave })
    // BOLUS type is pre-selected; fill in required insulin units field
    const input = screen.getByRole('spinbutton')
    fireEvent.change(input, { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: /save/i }))
    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
  })

  it('calls onClose when Cancel is clicked', () => {
    const onClose = vi.fn()
    renderModal({ onClose })
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('does not render when isOpen is false', () => {
    const { container } = renderModal({ isOpen: false })
    expect(container).toBeEmptyDOMElement()
  })
})
