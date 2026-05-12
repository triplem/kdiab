import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, test, expect, vi } from 'vitest'
import React from 'react'
import { TimeframePicker, defaultTimeframe } from '../features/timeframe/TimeframePicker'
import { TimeFormatProvider } from '../context/TimeFormatContext'
import '../i18n'

function wrap(ui: React.ReactElement) {
  return render(<TimeFormatProvider>{ui}</TimeFormatProvider>)
}

describe('TimeframePicker', () => {
  test('renders all preset buttons', () => {
    const onChange = vi.fn()
    wrap(<TimeframePicker value={defaultTimeframe()} onChange={onChange} />)
    expect(screen.getByText('1 Day')).toBeInTheDocument()
    expect(screen.getByText('7 Days')).toBeInTheDocument()
    expect(screen.getByText('14 Days')).toBeInTheDocument()
    expect(screen.getByText('30 Days')).toBeInTheDocument()
  })

  test('calls onChange with 1d timeframe', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    wrap(<TimeframePicker value={defaultTimeframe()} onChange={onChange} />)
    await user.click(screen.getByText('1 Day'))
    expect(onChange).toHaveBeenCalledOnce()
    const [tf] = onChange.mock.calls[0] as [{ from: string; to: string }]
    expect(new Date(tf.to).getTime() - new Date(tf.from).getTime()).toBeLessThanOrEqual(24 * 60 * 60 * 1000 + 1000)
  })
})
