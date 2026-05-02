import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileList } from '../../components/ProfileList';
import { api, customApi } from '../../api/client';
import { Profile, ProfileStatusEnum } from '../../api/generated';
import { vi, test, expect, Mock, afterEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TimeFormatProvider } from '../../context/TimeFormatContext';

vi.mock('../../api/client', () => ({
  api: {
    listProfiles: vi.fn(),
    activateProfile: vi.fn(),
  },
  customApi: {
    acceptProposedProfile: vi.fn(),
    rejectProposedProfile: vi.fn(),
  },
}));

afterEach(() => {
  vi.clearAllMocks();
});

const mockProfiles: Profile[] = [
  {
    id: '1',
    userId: 'user-1',
    name: 'Morning Profile',
    insulinType: 'Humalog',
    durationOfAction: 360,
    status: ProfileStatusEnum.Active,
  },
  {
    id: '2',
    userId: 'user-1',
    name: 'Weekend Profile',
    insulinType: 'Humalog',
    durationOfAction: 360,
    status: ProfileStatusEnum.Draft,
  },
];

const proposedProfile: Profile = {
  id: '3',
  userId: 'user-1',
  name: 'Doctor Proposal',
  insulinType: 'Fiasp',
  durationOfAction: 240,
  status: 'PROPOSED' as ProfileStatusEnum,
  createdAt: '2026-04-01T10:00:00Z',
};

function renderList(profiles: Profile[]) {
  (api.listProfiles as Mock).mockResolvedValue({ data: profiles });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileList userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );
}

// ── Basic rendering ────────────────────────────────────────────────────────────

test('renders profile list', async () => {
  renderList(mockProfiles);

  expect(screen.getByText(/loading/i)).toBeInTheDocument();

  await waitFor(() => {
    expect(screen.getByText(/Morning Profile/i)).toBeInTheDocument();
    expect(screen.getByText(/Weekend Profile/i)).toBeInTheDocument();
  });
});

test('handles fetch error', async () => {
  (api.listProfiles as Mock).mockRejectedValue(new Error('Network error'));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileList userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );

  await waitFor(() => {
    expect(screen.getByText(/Network error/i)).toBeInTheDocument();
  });
});

// ── Activate with confirm dialog ───────────────────────────────────────────────

test('activates a draft profile after confirm', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  (api.activateProfile as Mock).mockResolvedValue({ data: {} });
  renderList(mockProfiles);

  await waitFor(() => expect(screen.getByText(/Weekend Profile/i)).toBeInTheDocument());

  await user.click(screen.getByRole('button', { name: /activate profile weekend profile/i }));

  expect(window.confirm).toHaveBeenCalled();
  await waitFor(() => expect(api.activateProfile).toHaveBeenCalledWith('user-1', '2'));
});

test('does NOT activate when user cancels the confirm dialog', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  renderList(mockProfiles);

  await waitFor(() => expect(screen.getByText(/Weekend Profile/i)).toBeInTheDocument());

  await user.click(screen.getByRole('button', { name: /activate profile weekend profile/i }));

  expect(window.confirm).toHaveBeenCalled();
  expect(api.activateProfile).not.toHaveBeenCalled();
});

// ── Proposed profile actions ───────────────────────────────────────────────────

test('proposed profile shows Accept and Reject buttons', async () => {
  renderList([proposedProfile]);

  await waitFor(() => {
    expect(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reject proposed profile doctor proposal/i })).toBeInTheDocument();
  });
});

test('accepting a proposed profile calls acceptProposedProfile after confirm', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  (customApi.acceptProposedProfile as Mock).mockResolvedValue({ data: {} });
  renderList([proposedProfile]);

  await waitFor(() =>
    expect(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i })).toBeInTheDocument()
  );
  await user.click(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i }));

  expect(window.confirm).toHaveBeenCalled();
  await waitFor(() => expect(customApi.acceptProposedProfile).toHaveBeenCalledWith('user-1', '3'));
});

test('rejecting a proposed profile requires confirmation', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  (customApi.rejectProposedProfile as Mock).mockResolvedValue({ data: {} });
  renderList([proposedProfile]);

  await waitFor(() =>
    expect(screen.getByRole('button', { name: /reject proposed profile doctor proposal/i })).toBeInTheDocument()
  );
  await user.click(screen.getByRole('button', { name: /reject proposed profile doctor proposal/i }));

  expect(window.confirm).toHaveBeenCalled();
  await waitFor(() => expect(customApi.rejectProposedProfile).toHaveBeenCalledWith('user-1', '3'));
});

test('reject does NOT call API when user cancels confirm', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  renderList([proposedProfile]);

  await waitFor(() =>
    expect(screen.getByRole('button', { name: /reject proposed profile doctor proposal/i })).toBeInTheDocument()
  );
  await user.click(screen.getByRole('button', { name: /reject proposed profile doctor proposal/i }));

  expect(window.confirm).toHaveBeenCalled();
  expect(customApi.rejectProposedProfile).not.toHaveBeenCalled();
});

// ── Proposed section visibility ────────────────────────────────────────────────

test('proposed section is visible when proposals exist', async () => {
  renderList([...mockProfiles, proposedProfile]);

  await waitFor(() => {
    expect(screen.getByText(/Pending Doctor Recommendations/i)).toBeInTheDocument();
    expect(screen.getByText(/Doctor Proposal/i)).toBeInTheDocument();
  });
});

test('proposed section is hidden when no proposals exist', async () => {
  renderList(mockProfiles);

  await waitFor(() => {
    expect(screen.queryByText(/Pending Doctor Recommendations/i)).not.toBeInTheDocument();
  });
});

test('accept proposed profile requires confirmation', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  (customApi.acceptProposedProfile as Mock).mockResolvedValue({ data: {} });
  renderList([proposedProfile]);

  await waitFor(() =>
    expect(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i })).toBeInTheDocument()
  );
  await user.click(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i }));

  expect(window.confirm).toHaveBeenCalled();
  await waitFor(() => expect(customApi.acceptProposedProfile).toHaveBeenCalledWith('user-1', '3'));
});

test('accept does NOT call API when user cancels confirm', async () => {
  const user = userEvent.setup();
  vi.spyOn(window, 'confirm').mockReturnValue(false);
  renderList([proposedProfile]);

  await waitFor(() =>
    expect(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i })).toBeInTheDocument()
  );
  await user.click(screen.getByRole('button', { name: /accept proposed profile doctor proposal/i }));

  expect(window.confirm).toHaveBeenCalled();
  expect(customApi.acceptProposedProfile).not.toHaveBeenCalled();
});
