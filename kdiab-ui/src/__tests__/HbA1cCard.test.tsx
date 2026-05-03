import { render, screen } from '@testing-library/react'
import { describe, test, expect } from 'vitest'
import { HbA1cCard } from '../features/analytics/HbA1cCard'
import '../i18n'

const baseTir = { belowCount: 10, inRangeCount: 70, aboveCount: 15, highCount: 5, totalCount: 100 }

describe('HbA1cCard', () => {
  test('shows hba1c value with % in mg/dL mode', () => {
    render(<HbA1cCard hba1c={7.2} meanGlucose={162} tir={baseTir} glucoseUnit="mg/dL" />)
    expect(screen.getByText('7.2%')).toBeInTheDocument()
    expect(screen.getByText(/162 mg\/dL/)).toBeInTheDocument()
  })

  test('shows dash when hba1c is null', () => {
    render(<HbA1cCard hba1c={null} meanGlucose={140} tir={baseTir} glucoseUnit="mg/dL" />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  test('converts mean glucose to mmol/L', () => {
    render(<HbA1cCard hba1c={6.5} meanGlucose={162} tir={baseTir} glucoseUnit="mmol/L" />)
    expect(screen.getByText(/9\.0 mmol\/L/)).toBeInTheDocument()
  })

  test('shows reading count', () => {
    render(<HbA1cCard hba1c={7.0} meanGlucose={154} tir={baseTir} glucoseUnit="mg/dL" />)
    expect(screen.getByText(/100 CGM readings/)).toBeInTheDocument()
  })
})
