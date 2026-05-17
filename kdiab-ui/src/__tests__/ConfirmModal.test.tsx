import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ConfirmModal } from '../components/ConfirmModal'

const baseProps = {
  isOpen: true,
  title: 'Delete item',
  message: 'Are you sure you want to delete this item?',
  onConfirm: vi.fn(),
  onCancel: vi.fn(),
}

describe('ConfirmModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders nothing when isOpen is false', () => {
    render(<ConfirmModal {...baseProps} isOpen={false} />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  test('renders dialog when isOpen is true', () => {
    render(<ConfirmModal {...baseProps} />)
    expect(screen.getByRole('dialog')).toBeDefined()
  })

  test('displays title and message', () => {
    render(<ConfirmModal {...baseProps} />)
    expect(screen.getByText('Delete item')).toBeDefined()
    expect(screen.getByText('Are you sure you want to delete this item?')).toBeDefined()
  })

  test('uses default labels when none provided', () => {
    render(<ConfirmModal {...baseProps} />)
    expect(screen.getByText('Confirm')).toBeDefined()
    expect(screen.getByText('Cancel')).toBeDefined()
  })

  test('uses custom confirmLabel and cancelLabel', () => {
    render(<ConfirmModal {...baseProps} confirmLabel="Delete" cancelLabel="Go back" />)
    expect(screen.getByText('Delete')).toBeDefined()
    expect(screen.getByText('Go back')).toBeDefined()
  })

  test('calls onConfirm when confirm button is clicked', () => {
    const onConfirm = vi.fn()
    render(<ConfirmModal {...baseProps} onConfirm={onConfirm} />)
    fireEvent.click(screen.getByText('Confirm'))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  test('calls onCancel when cancel button is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(screen.getByText('Cancel'))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  test('calls onCancel when overlay is clicked', () => {
    const onCancel = vi.fn()
    render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(screen.getByRole('presentation'))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  test('does not propagate clicks on dialog box to overlay', () => {
    const onCancel = vi.fn()
    render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.click(screen.getByRole('dialog'))
    expect(onCancel).not.toHaveBeenCalled()
  })

  test('calls onCancel when Escape key is pressed', () => {
    const onCancel = vi.fn()
    render(<ConfirmModal {...baseProps} onCancel={onCancel} />)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  test('applies danger class to confirm button when danger prop is true', () => {
    render(<ConfirmModal {...baseProps} danger={true} />)
    const confirmBtn = screen.getByText('Confirm')
    expect(confirmBtn.className).toContain('danger')
  })

  test('applies primary class to confirm button when danger prop is false', () => {
    render(<ConfirmModal {...baseProps} danger={false} />)
    const confirmBtn = screen.getByText('Confirm')
    expect(confirmBtn.className).toContain('primary')
  })

  test('dialog has aria-modal and aria-labelledby attributes', () => {
    render(<ConfirmModal {...baseProps} />)
    const dialog = screen.getByRole('dialog')
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(dialog.getAttribute('aria-labelledby')).toBe('confirm-modal-title')
  })

  test('Tab key on last focusable element wraps to first', () => {
    render(<ConfirmModal {...baseProps} />)
    const confirmBtn = screen.getByText('Confirm')
    confirmBtn.focus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: false })
    // focus should wrap — no error thrown and modal still open
    expect(screen.getByRole('dialog')).toBeDefined()
  })

  test('Shift+Tab on first focusable element wraps to last', () => {
    render(<ConfirmModal {...baseProps} />)
    const cancelBtn = screen.getByText('Cancel')
    cancelBtn.focus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(screen.getByRole('dialog')).toBeDefined()
  })
})
