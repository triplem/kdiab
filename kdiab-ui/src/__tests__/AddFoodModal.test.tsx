import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AddFoodModal } from '../features/carbs/AddFoodModal'
import '../i18n'

const baseProps = {
  isOpen: true,
  onSave: vi.fn(),
  onCancel: vi.fn(),
}

describe('AddFoodModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders nothing when isOpen is false', () => {
    render(<AddFoodModal {...baseProps} isOpen={false} />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  test('renders dialog when isOpen is true', () => {
    render(<AddFoodModal {...baseProps} />)
    expect(screen.getByRole('dialog')).toBeDefined()
  })

  test('shows "Add Food" title when no initialFood provided', () => {
    render(<AddFoodModal {...baseProps} />)
    expect(screen.getByText('Add Food')).toBeDefined()
  })

  test('shows "Edit Food" title when initialFood is provided', () => {
    render(
      <AddFoodModal
        {...baseProps}
        initialFood={{ id: '1', name: 'Rice', portionGrams: 100, carbsPer100g: 28 }}
      />,
    )
    expect(screen.getByText('Edit Food')).toBeDefined()
  })

  test('pre-fills name field when in edit mode', () => {
    render(
      <AddFoodModal
        {...baseProps}
        initialFood={{ id: '1', name: 'Rice', portionGrams: 100, carbsPer100g: 28 }}
      />,
    )
    const nameInput = screen.getByDisplayValue('Rice')
    expect(nameInput).toBeDefined()
  })

  test('pre-fills portionGrams field when in edit mode', () => {
    render(
      <AddFoodModal
        {...baseProps}
        initialFood={{ id: '1', name: 'Rice', portionGrams: 100, carbsPer100g: 28 }}
      />,
    )
    const portionInput = screen.getByDisplayValue('100')
    expect(portionInput).toBeDefined()
  })

  test('pre-fills carbsPer100g field when in edit mode', () => {
    render(
      <AddFoodModal
        {...baseProps}
        initialFood={{ id: '1', name: 'Rice', portionGrams: 100, carbsPer100g: 28 }}
      />,
    )
    const carbsInput = screen.getByDisplayValue('28')
    expect(carbsInput).toBeDefined()
  })

  test('calls onSave with correct data on valid submit', () => {
    const onSave = vi.fn()
    render(<AddFoodModal {...baseProps} onSave={onSave} />)

    const inputs = screen.getAllByRole('spinbutton')
    const nameInput = screen.getByPlaceholderText('e.g. White rice')

    fireEvent.change(nameInput, { target: { value: 'Pasta' } })
    fireEvent.change(inputs[0], { target: { value: '80' } })
    fireEvent.change(inputs[1], { target: { value: '32' } })

    fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)

    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onSave).toHaveBeenCalledWith({ name: 'Pasta', portionGrams: 80, carbsPer100g: 32 })
  })

  test('calls onCancel on Escape key', () => {
    const onCancel = vi.fn()
    render(<AddFoodModal {...baseProps} onCancel={onCancel} />)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  test('calls onCancel when overlay is clicked', () => {
    const onCancel = vi.fn()
    render(<AddFoodModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(screen.getByRole('presentation'))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  test('does not call onSave when name is empty', () => {
    const onSave = vi.fn()
    render(<AddFoodModal {...baseProps} onSave={onSave} />)

    const inputs = screen.getAllByRole('spinbutton')
    fireEvent.change(inputs[0], { target: { value: '100' } })
    fireEvent.change(inputs[1], { target: { value: '25' } })
    // name field left empty

    fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)

    expect(onSave).not.toHaveBeenCalled()
  })

  test('shows error message when name is empty and form is submitted', () => {
    render(<AddFoodModal {...baseProps} />)

    const inputs = screen.getAllByRole('spinbutton')
    fireEvent.change(inputs[0], { target: { value: '100' } })
    fireEvent.change(inputs[1], { target: { value: '25' } })

    fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)

    expect(screen.getAllByRole('alert').length).toBeGreaterThanOrEqual(1)
  })

  test('does not call onSave when portionGrams is zero', () => {
    const onSave = vi.fn()
    render(<AddFoodModal {...baseProps} onSave={onSave} />)

    const nameInput = screen.getByPlaceholderText('e.g. White rice')
    const inputs = screen.getAllByRole('spinbutton')

    fireEvent.change(nameInput, { target: { value: 'Bread' } })
    fireEvent.change(inputs[0], { target: { value: '0' } })
    fireEvent.change(inputs[1], { target: { value: '50' } })

    fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)

    expect(onSave).not.toHaveBeenCalled()
  })

  test('does not call onSave when carbsPer100g is zero', () => {
    const onSave = vi.fn()
    render(<AddFoodModal {...baseProps} onSave={onSave} />)

    const nameInput = screen.getByPlaceholderText('e.g. White rice')
    const inputs = screen.getAllByRole('spinbutton')

    fireEvent.change(nameInput, { target: { value: 'Chicken' } })
    fireEvent.change(inputs[0], { target: { value: '150' } })
    fireEvent.change(inputs[1], { target: { value: '0' } })

    fireEvent.submit(screen.getByRole('dialog').querySelector('form')!)

    expect(onSave).not.toHaveBeenCalled()
  })

  test('dialog has aria-modal and aria-labelledby attributes', () => {
    render(<AddFoodModal {...baseProps} />)
    const dialog = screen.getByRole('dialog')
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(dialog.getAttribute('aria-labelledby')).toBe('add-food-modal-title')
  })

  test('save button is disabled while isSaving is true', () => {
    render(<AddFoodModal {...baseProps} isSaving={true} />)
    const saveButton = screen.getByText('Saving...')
    expect((saveButton as HTMLButtonElement).disabled).toBe(true)
  })
})
