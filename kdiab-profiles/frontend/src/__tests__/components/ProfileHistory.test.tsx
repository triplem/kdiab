import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { ProfileHistory } from '../../components/ProfileHistory';
import { api } from '../../api/client';
import type { Profile } from '../../api/generated';
import { ProfileStatusEnum } from '../../api/generated';
import { vi, test, expect } from 'vitest';
import type { Mock } from 'vitest';

// Mock the API client
vi.mock('../../api/client', () => ({
  api: {
    getProfileHistory: vi.fn(),
  },
}));

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TimeFormatProvider } from '../../context/TimeFormatContext';

test('renders profile history', async () => {
  const mockHistory: Profile[] = [
    {
      id: 'h1',
      userId: 'user-1',
      name: 'Old Profile',
      insulinType: 'Humalog',
      durationOfAction: 360,
      status: ProfileStatusEnum.Archived,
      createdAt: new Date().toISOString(),
    }
  ];

  (api.getProfileHistory as Mock).mockResolvedValue({ data: mockHistory });

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileHistory userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );

  expect(screen.getByText(/loading history/i)).toBeInTheDocument();

  await waitFor(() => {
    expect(screen.getByText(/old profile/i)).toBeInTheDocument();
    expect(screen.getByText(/archived/i)).toBeInTheDocument();
  });
});

test('refetches history when dates change', async () => {
  (api.getProfileHistory as Mock).mockResolvedValue({ data: [] });

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileHistory userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );

  // Wait for initial load
  await waitFor(() => {
    expect(api.getProfileHistory).toHaveBeenCalled();
  });

  // Change start date
  const startDateInput = screen.getByLabelText(/from/i);
  fireEvent.change(startDateInput, { target: { value: '2023-01-01' } });

  // Should trigger new fetch
  await waitFor(() => {
    expect(api.getProfileHistory).toHaveBeenCalledWith(
      'user-1',
      expect.any(String), // from date
      expect.any(String)  // to date
    );
    // Ensure we have at least 2 calls (initial + update)
    expect((api.getProfileHistory as Mock).mock.calls.length).toBeGreaterThanOrEqual(2);
  });
});
