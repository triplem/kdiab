import { render, screen } from '@testing-library/react'
import { describe, test, expect } from 'vitest'
import { PrintHeader } from '../features/analytics/PrintHeader'

describe('PrintHeader', () => {
  test('renders patient name', () => {
    render(<PrintHeader patientName="sarah" from="2026-05-01T00:00:00Z" to="2026-05-25T23:59:59Z" />)
    expect(screen.getByText(/sarah/)).toBeInTheDocument()
  })

  test('renders formatted date range in de-DE format', () => {
    render(<PrintHeader patientName="sarah" from="2026-05-01T00:00:00Z" to="2026-05-25T23:59:59Z" />)
    const content = document.body.textContent ?? ''
    expect(content).toContain('01.05.2026')
    expect(content).toContain('25.05.2026')
  })

  test('renders generated timestamp', () => {
    render(<PrintHeader patientName="mike" from="2026-05-01T00:00:00Z" to="2026-05-25T23:59:59Z" />)
    expect(screen.getByText(/Generated:/)).toBeInTheDocument()
  })

  test('renders report heading', () => {
    render(<PrintHeader patientName="sarah" from="2026-05-01T00:00:00Z" to="2026-05-25T23:59:59Z" />)
    expect(screen.getByRole('heading', { name: /kdiab Analytics Report/, hidden: true })).toBeInTheDocument()
  })
})
