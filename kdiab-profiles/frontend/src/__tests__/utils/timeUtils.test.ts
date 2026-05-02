import { describe, it, expect } from 'vitest';
import { getNextSegment } from '../../components/ProfileEditor';

describe('getNextSegment', () => {
  it('should return 00:00 when fields are empty', () => {
    const result = getNextSegment([], 0.5);
    expect(result).toEqual({ startTime: '00:00', value: 0.5 });
  });

  it('should increment by 60 minutes from the last segment', () => {
    const fields = [{ startTime: '08:00', value: 1.0 }];
    const result = getNextSegment(fields, 0.5);
    expect(result.startTime).toBe('09:00');
    expect(result.value).toBe(1.0); // Should copy last value
  });

  it('should sort segments to find the true last one', () => {
    const fields = [
      { startTime: '10:00', value: 1.2 },
      { startTime: '08:00', value: 1.0 },
    ];
    const result = getNextSegment(fields, 0.5);
    expect(result.startTime).toBe('11:00');
  });

  it('should cap at 23:45 to prevent rolling over to 00:00', () => {
    const fields = [{ startTime: '23:30', value: 1.0 }];
    const result = getNextSegment(fields, 0.5);
    expect(result.startTime).toBe('23:45');
  });

  it('should stay at 23:45 if already there', () => {
    const fields = [{ startTime: '23:45', value: 1.0 }];
    const result = getNextSegment(fields, 0.5);
    expect(result.startTime).toBe('23:45');
  });
});
