import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AddMeasureModal } from '../features/measures/AddMeasureModal'
import '../i18n'

const baseProps = {
  isOpen: true,
  onClose: vi.fn(),
  onSave: vi.fn(),
  glucoseUnit: 'mg/dL',
  weightUnit: 'kg',
}

describe('AddMeasureModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders nothing when isOpen is false', () => {
    render(<AddMeasureModal {...baseProps} isOpen={false} />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  test('renders modal dialog when isOpen is true', () => {
    render(<AddMeasureModal {...baseProps} />)
    expect(screen.getByRole('dialog')).toBeDefined()
  })

  test('renders type selector with measure types', () => {
    render(<AddMeasureModal {...baseProps} />)
    const select = screen.getByRole('combobox')
    expect(select).toBeDefined()
    // BGM is the default type
    expect((select as HTMLSelectElement).value).toBe('BGM')
  })

  test('type selector is disabled in edit mode', () => {
    render(
      <AddMeasureModal
        {...baseProps}
        editMode={{ id: '1', type: 'BGM', measuredAt: '2024-01-01T10:00:00Z', data: { value: 120 } }}
      />,
    )
    const select = screen.getByRole('combobox')
    expect((select as HTMLSelectElement).disabled).toBe(true)
  })

  test('changing type from BGM to BLOOD_PRESSURE shows systolic and diastolic inputs', () => {
    render(<AddMeasureModal {...baseProps} />)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'BLOOD_PRESSURE' } })
    // Both systolic and diastolic fields should appear
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(2)
  })

  test('changing type to WEIGHT shows weight input', () => {
    render(<AddMeasureModal {...baseProps} />)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'WEIGHT' } })
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('changing type to PULSE shows pulse input', () => {
    render(<AddMeasureModal {...baseProps} />)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'PULSE' } })
    const numberInputs = screen.getAllByRole('spinbutton')
    expect(numberInputs.length).toBeGreaterThanOrEqual(1)
  })

  test('submitting with empty BGM value shows validation error', () => {
    render(<AddMeasureModal {...baseProps} />)
    const form = screen.getByRole('dialog').querySelector('form')!
    fireEvent.submit(form)
    expect(screen.getByRole('alert')).toBeDefined()
    expect(baseProps.onSave).not.toHaveBeenCalled()
  })

  test('submitting with valid BGM value calls onSave', () => {
    render(<AddMeasureModal {...baseProps} />)
    const numberInput = screen.getByRole('spinbutton')
    fireEvent.change(numberInput, { target: { value: '120' } })
    const form = screen.getByRole('dialog').querySelector('form')!
    fireEvent.submit(form)
    expect(baseProps.onSave).toHaveBeenCalledOnce()
    const call = baseProps.onSave.mock.calls[0][0] as { type: string; data: Record<string, unknown> }
    expect(call.type).toBe('BGM')
    expect(call.data.value).toBe(120)
  })

  test('clicking Cancel calls onClose', () => {
    render(<AddMeasureModal {...baseProps} />)
    fireEvent.click(screen.getByText(/cancel/i))
    expect(baseProps.onClose).toHaveBeenCalledTimes(1)
  })

  test('clicking overlay calls onClose', () => {
    render(<AddMeasureModal {...baseProps} />)
    fireEvent.click(screen.getByRole('presentation'))
    expect(baseProps.onClose).toHaveBeenCalledTimes(1)
  })

  test('clicking dialog box does not close modal', () => {
    render(<AddMeasureModal {...baseProps} />)
    fireEvent.click(screen.getByRole('dialog'))
    expect(baseProps.onClose).not.toHaveBeenCalled()
  })

  test('pressing Escape calls onClose', () => {
    render(<AddMeasureModal {...baseProps} />)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(baseProps.onClose).toHaveBeenCalledTimes(1)
  })

  test('shows external error message when error prop is set', () => {
    render(<AddMeasureModal {...baseProps} error="Server error" />)
    expect(screen.getByText('Server error')).toBeDefined()
  })

  test('save button is disabled when isSaving is true', () => {
    render(<AddMeasureModal {...baseProps} isSaving={true} />)
    const submitBtn = screen.getByRole('button', { name: /saving/i })
    expect((submitBtn as HTMLButtonElement).disabled).toBe(true)
  })

  test('KETONE_CHECK type renders ketone and method inputs', () => {
    render(<AddMeasureModal {...baseProps} />)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'KETONE_CHECK' } })
    // Should have a number input for ketones + a method select
    const selects = screen.getAllByRole('combobox')
    expect(selects.length).toBeGreaterThanOrEqual(2)
  })

  test('edit mode pre-fills type from editMode', () => {
    render(
      <AddMeasureModal
        {...baseProps}
        editMode={{ id: '1', type: 'WEIGHT', measuredAt: '2024-01-01T10:00:00Z', data: { value: 75 } }}
      />,
    )
    const select = screen.getByRole('combobox')
    expect((select as HTMLSelectElement).value).toBe('WEIGHT')
  })

  test('modal title shows Edit Measure in edit mode', () => {
    render(
      <AddMeasureModal
        {...baseProps}
        editMode={{ id: '1', type: 'BGM', measuredAt: '2024-01-01T10:00:00Z', data: { value: 120 } }}
      />,
    )
    expect(screen.getByText(/edit measure/i)).toBeDefined()
  })

  test('time input uses type="time" so the CSS ampm-field rule applies', () => {
    render(<AddMeasureModal {...baseProps} />)
    const timeInput = screen.getByRole('dialog').querySelector('input[type="time"]')
    expect(timeInput).not.toBeNull()
  })

  test('time input value is in HH:mm format', () => {
    render(<AddMeasureModal {...baseProps} />)
    const timeInput = screen.getByRole('dialog').querySelector('input[type="time"]') as HTMLInputElement
    expect(timeInput).not.toBeNull()
    // Value must match HH:mm — 24h format, no am/pm component
    expect(timeInput.value).toMatch(/^\d{2}:\d{2}$/)
  })

  test('time input in edit mode pre-fills with local HH:mm from ISO timestamp', () => {
    render(
      <AddMeasureModal
        {...baseProps}
        editMode={{ id: '1', type: 'BGM', measuredAt: '2024-01-01T14:30:00Z', data: { value: 120 } }}
      />,
    )
    const timeInput = screen.getByRole('dialog').querySelector('input[type="time"]') as HTMLInputElement
    expect(timeInput).not.toBeNull()
    expect(timeInput.value).toMatch(/^\d{2}:\d{2}$/)
  })
})
