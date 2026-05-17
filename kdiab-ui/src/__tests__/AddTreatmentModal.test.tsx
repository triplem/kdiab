import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { AddTreatmentModal } from '../features/treatments/AddTreatmentModal'
import '../i18n'

// Mock carbsApi used by CarbsForm
vi.mock('../api/carbsApi', () => ({
  carbsApi: {
    listFoods: vi.fn().mockResolvedValue({ data: { items: [] } }),
  },
}))

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

const baseProps = {
  isOpen: true,
  onClose: vi.fn(),
  onSave: vi.fn(),
  onSaveMeal: vi.fn(),
  glucoseUnit: 'mg/dL',
}

describe('AddTreatmentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders nothing when isOpen is false', () => {
    render(<AddTreatmentModal {...baseProps} isOpen={false} />, { wrapper })
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  test('renders modal dialog when isOpen is true', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    expect(screen.getByRole('dialog')).toBeDefined()
  })

  test('renders type selector with MEAL as default', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    expect((select as HTMLSelectElement).value).toBe('MEAL')
  })

  test('type selector is disabled in edit mode', () => {
    render(
      <AddTreatmentModal
        {...baseProps}
        editMode={{
          id: '1',
          type: 'BOLUS',
          treatedAt: '2024-01-01T10:00:00Z',
          data: { insulin: 2 },
        }}
      />,
      { wrapper },
    )
    const select = screen.getByRole('combobox')
    expect((select as HTMLSelectElement).disabled).toBe(true)
  })

  test('switching to BOLUS type shows insulin units input', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'BOLUS' } })
    // BolusForm renders an insulin units number input
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('switching to CARBS type shows carbs input', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'CARBS' } })
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('switching to EXERCISE type shows exercise form', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'EXERCISE' } })
    // ExerciseForm renders at least one input
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('switching to NOTE type shows note form', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'NOTE' } })
    // NoteForm renders a text area or text input
    const textInputs = screen.getAllByRole('textbox')
    expect(textInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('switching to TEMP_BASAL type shows temp basal form', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'TEMP_BASAL' } })
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('submitting empty BOLUS form shows validation error', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'BOLUS' } })
    const form = screen.getByRole('dialog').querySelector('form')!
    fireEvent.submit(form)
    expect(screen.getByRole('alert')).toBeDefined()
    expect(baseProps.onSave).not.toHaveBeenCalled()
  })

  test('submitting valid BOLUS calls onSave with BOLUS type', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'BOLUS' } })

    // Fill in insulin units
    const numberInputs = screen.getAllByRole('spinbutton')
    fireEvent.change(numberInputs[0], { target: { value: '2.5' } })

    const form = screen.getByRole('dialog').querySelector('form')!
    fireEvent.submit(form)
    expect(baseProps.onSave).toHaveBeenCalledOnce()
    const call = baseProps.onSave.mock.calls[0][0] as { type: string }
    expect(call.type).toBe('BOLUS')
  })

  test('clicking Cancel calls onClose', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    fireEvent.click(screen.getByText(/cancel/i))
    expect(baseProps.onClose).toHaveBeenCalledTimes(1)
  })

  test('clicking overlay calls onClose', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    fireEvent.click(screen.getByRole('presentation'))
    expect(baseProps.onClose).toHaveBeenCalledTimes(1)
  })

  test('pressing Escape calls onClose', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(baseProps.onClose).toHaveBeenCalledTimes(1)
  })

  test('shows external error message when error prop is set', () => {
    render(<AddTreatmentModal {...baseProps} error="Save failed" />, { wrapper })
    expect(screen.getByText('Save failed')).toBeDefined()
  })

  test('save button is disabled when isSaving is true', () => {
    render(<AddTreatmentModal {...baseProps} isSaving={true} />, { wrapper })
    const submitBtn = screen.getByRole('button', { name: /saving/i })
    expect((submitBtn as HTMLButtonElement).disabled).toBe(true)
  })

  test('notes field is shown for non-MEAL types', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'BOLUS' } })
    // Notes input should appear for BOLUS
    const textInputs = screen.getAllByRole('textbox')
    expect(textInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('notes field is not shown for MEAL type', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    // Default is MEAL — no notes field
    // The date field is a textbox but notes should not appear
    // Verify by checking there's no notes placeholder
    expect(screen.queryByPlaceholderText(/optional note/i)).toBeNull()
  })

  test('CORRECTION_BOLUS type also uses BolusForm', () => {
    render(<AddTreatmentModal {...baseProps} />, { wrapper })
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'CORRECTION_BOLUS' } })
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('edit mode sets modal title to Edit Treatment', () => {
    render(
      <AddTreatmentModal
        {...baseProps}
        editMode={{
          id: '1',
          type: 'BOLUS',
          treatedAt: '2024-01-01T10:00:00Z',
          data: { insulin: 2 },
        }}
      />,
      { wrapper },
    )
    expect(screen.getByText(/edit treatment/i)).toBeDefined()
  })
})
