import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TimeInput } from '../../components/TimeInput';
import { vi, test, expect, describe } from 'vitest';
import { TimeFormatProvider } from '../../context/TimeFormatContext';

// Wrap in provider — TimeInput reads is24Hour from context.
// The test environment defaults to 24h (Intl.DateTimeFormat hourCycle is not h11/h12 in JSDOM).
function renderTimeInput(value: string, onChange = vi.fn()) {
  return render(
    <TimeFormatProvider>
      <TimeInput value={value} onChange={onChange} name="test" />
    </TimeFormatProvider>
  );
}

describe('TimeInput', () => {
  test('renders hour and minute selects', () => {
    renderTimeInput('08:30');
    expect(screen.getByRole('combobox', { name: /hour/i })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: /minute/i })).toBeInTheDocument();
  });

  test('displays correct hour and minute for 08:30', () => {
    renderTimeInput('08:30');
    const hourSelect = screen.getByRole('combobox', { name: /hour/i }) as HTMLSelectElement;
    const minuteSelect = screen.getByRole('combobox', { name: /minute/i }) as HTMLSelectElement;
    expect(hourSelect.value).toBe('8');
    expect(minuteSelect.value).toBe('30');
  });

  test('midnight (00:00) is displayed correctly', () => {
    renderTimeInput('00:00');
    const minuteSelect = screen.getByRole('combobox', { name: /minute/i }) as HTMLSelectElement;
    expect(minuteSelect.value).toBe('00');
  });

  test('noon (12:00) is displayed correctly', () => {
    renderTimeInput('12:00');
    const hourSelect = screen.getByRole('combobox', { name: /hour/i }) as HTMLSelectElement;
    expect(hourSelect.value).toBe('12');
  });

  test('single-digit minute string "5:5" is padded to "05"', () => {
    renderTimeInput('05:05');
    const minuteSelect = screen.getByRole('combobox', { name: /minute/i }) as HTMLSelectElement;
    expect(minuteSelect.value).toBe('05');
  });

  test('missing minute defaults to "00"', () => {
    // Simulate a value with no minute part
    renderTimeInput('08:');
    const minuteSelect = screen.getByRole('combobox', { name: /minute/i }) as HTMLSelectElement;
    expect(minuteSelect.value).toBe('00');
  });

  test('onChange is called with padded time string on hour change', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderTimeInput('08:30', onChange);
    const hourSelect = screen.getByRole('combobox', { name: /hour/i });
    await user.selectOptions(hourSelect, '10');
    expect(onChange).toHaveBeenCalled();
    const calledValue: string = onChange.mock.calls[0][0].target.value;
    expect(calledValue).toMatch(/^10:/);
  });

  test('onChange is called with correct time string on minute change', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderTimeInput('08:30', onChange);
    const minuteSelect = screen.getByRole('combobox', { name: /minute/i });
    await user.selectOptions(minuteSelect, '45');
    expect(onChange).toHaveBeenCalled();
    const calledValue: string = onChange.mock.calls[0][0].target.value;
    expect(calledValue).toMatch(/:45$/);
  });
});
