import { render, screen, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi } from 'vitest'
import React from 'react'
import '../i18n'

import { ReportPageSelector } from '../features/report/ReportPageSelector'
import { REPORT_PAGE_IDS } from '../features/report/reportPages'
import type { ReportPageId } from '../features/report/reportPages'

const ALL: readonly ReportPageId[] = REPORT_PAGE_IDS

describe('ReportPageSelector', () => {
  test('renders a checkbox for every report page', () => {
    render(
      <ReportPageSelector
        selectedPages={ALL}
        onToggle={vi.fn()}
        onSelectAll={vi.fn()}
        onDeselectAll={vi.fn()}
      />,
    )
    // There should be exactly as many checkboxes as report pages
    const checkboxes = screen.getAllByRole('checkbox')
    expect(checkboxes).toHaveLength(REPORT_PAGE_IDS.length)
  })

  test('SUMMARY checkbox is disabled and always checked', () => {
    render(
      <ReportPageSelector
        selectedPages={['SUMMARY']}
        onToggle={vi.fn()}
        onSelectAll={vi.fn()}
        onDeselectAll={vi.fn()}
      />,
    )
    const summaryCheckbox = screen.getByRole('checkbox', { name: /Summary/i })
    expect(summaryCheckbox).toBeDisabled()
    expect(summaryCheckbox).toBeChecked()
  })

  test('clicking a toggleable page calls onToggle with that page id', () => {
    const onToggle = vi.fn()
    render(
      <ReportPageSelector
        selectedPages={ALL}
        onToggle={onToggle}
        onSelectAll={vi.fn()}
        onDeselectAll={vi.fn()}
      />,
    )
    // The AGP checkbox has aria-label "AGP Percentile Chart" (translated)
    const agpCheckbox = screen.getByRole('checkbox', { name: /AGP Percentile Chart/i })
    fireEvent.click(agpCheckbox)
    expect(onToggle).toHaveBeenCalledWith('AGP')
  })

  test('clicking SUMMARY checkbox does NOT call onToggle', () => {
    const onToggle = vi.fn()
    render(
      <ReportPageSelector
        selectedPages={ALL}
        onToggle={onToggle}
        onSelectAll={vi.fn()}
        onDeselectAll={vi.fn()}
      />,
    )
    const summaryCheckbox = screen.getByRole('checkbox', { name: /Summary/i })
    fireEvent.click(summaryCheckbox)
    expect(onToggle).not.toHaveBeenCalled()
  })

  test('Select all button calls onSelectAll', () => {
    const onSelectAll = vi.fn()
    render(
      <ReportPageSelector
        selectedPages={['SUMMARY']}
        onToggle={vi.fn()}
        onSelectAll={onSelectAll}
        onDeselectAll={vi.fn()}
      />,
    )
    // Use exact text to avoid matching "Deselect all"
    fireEvent.click(screen.getByRole('button', { name: 'Select all' }))
    expect(onSelectAll).toHaveBeenCalledOnce()
  })

  test('Deselect all button calls onDeselectAll', () => {
    const onDeselectAll = vi.fn()
    render(
      <ReportPageSelector
        selectedPages={ALL}
        onToggle={vi.fn()}
        onSelectAll={vi.fn()}
        onDeselectAll={onDeselectAll}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Deselect all' }))
    expect(onDeselectAll).toHaveBeenCalledOnce()
  })

  test('deselected pages show unchecked checkboxes', () => {
    render(
      <ReportPageSelector
        selectedPages={['SUMMARY']}
        onToggle={vi.fn()}
        onSelectAll={vi.fn()}
        onDeselectAll={vi.fn()}
      />,
    )
    // AGP Percentile Chart checkbox should be unchecked
    const agpCheckbox = screen.getByRole('checkbox', { name: /AGP Percentile Chart/i })
    expect(agpCheckbox).not.toBeChecked()
  })
})
