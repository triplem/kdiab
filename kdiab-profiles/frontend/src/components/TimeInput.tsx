import React, { forwardRef } from 'react';
import { useTimeFormat } from '../context/TimeFormatContext';

interface TimeChangeEvent {
  target: { name?: string; value: string };
}

interface TimeInputProps {
  value?: string;
  onChange?: (e: TimeChangeEvent) => void;
  onBlur?: React.FocusEventHandler<HTMLSelectElement>;
  name?: string;
  disabled?: boolean;
}

export const TimeInput = forwardRef<HTMLDivElement, TimeInputProps>(
  ({ value, onChange, onBlur, name, disabled }, ref) => {
    const { is24Hour } = useTimeFormat();

    // Default to 00:00
    const timeVal = typeof value === 'string' && value ? value : '00:00';
    const [hStr, mStr] = timeVal.split(':');
    let h = parseInt(hStr, 10);
    if (isNaN(h)) h = 0;
    const m = (mStr || '00').padStart(2, '0');

    const isPM = h >= 12;
    const displayH = is24Hour ? h : (h % 12 || 12);

    const triggerChange = (newHH: string, newMM: string) => {
      if (onChange) {
        onChange({
          target: {
            name,
            value: `${newHH}:${newMM}`
          }
        });
      }
    };

    const handleHourChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
      let newH = parseInt(e.target.value, 10);
      if (!is24Hour) {
        if (isPM && newH !== 12) newH += 12;
        if (!isPM && newH === 12) newH = 0;
      }
      const hh = String(newH).padStart(2, '0');
      triggerChange(hh, m);
    };

    const handleMinuteChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
      const hh = String(h).padStart(2, '0');
      triggerChange(hh, e.target.value);
    };

    const handleAmPmChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
      const newIsPM = e.target.value === 'PM';
      let newH = h;
      if (newIsPM && !isPM) newH = h === 0 ? 12 : h + 12;
      if (!newIsPM && isPM) newH = h === 12 ? 0 : h - 12;
      const hh = String(newH).padStart(2, '0');
      triggerChange(hh, m);
    };

    const hours = Array.from({ length: is24Hour ? 24 : 12 }, (_, i) => {
      return is24Hour ? i : (i === 0 ? 12 : i);
    });

    const minutes = ['00', '05', '10', '15', '20', '25', '30', '35', '40', '45', '50', '55'];

    return (
      <div ref={ref} className="time-input-group" style={{ display: 'inline-flex', gap: '4px', alignItems: 'center' }}>
        <select
          value={displayH}
          onChange={handleHourChange}
          onBlur={onBlur}
          disabled={disabled}
          aria-label="Hour"
          style={{ padding: '4px' }}
        >
          {hours.map(hr => (
            <option key={hr} value={hr}>
              {String(hr).padStart(2, '0')}
            </option>
          ))}
        </select>
        <span>:</span>
        <select
          value={m}
          onChange={handleMinuteChange}
          onBlur={onBlur}
          disabled={disabled}
          aria-label="Minute"
          style={{ padding: '4px' }}
        >
          {minutes.map(min => (
            <option key={min} value={min}>
              {min}
            </option>
          ))}
        </select>
        {!is24Hour && (
          <select
            value={isPM ? 'PM' : 'AM'}
            onChange={handleAmPmChange}
            onBlur={onBlur}
            disabled={disabled}
            aria-label="AM/PM"
            style={{ padding: '4px', marginLeft: '4px' }}
          >
            <option value="AM">AM</option>
            <option value="PM">PM</option>
          </select>
        )}
      </div>
    );
  }
);
TimeInput.displayName = 'TimeInput';
