import { render, screen, waitFor } from '@testing-library/react';
import App from '../App';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TimeFormatProvider } from '../context/TimeFormatContext';
import { vi, test, expect } from 'vitest';
import { api } from '../api/client';

vi.mock('react-oidc-context', () => ({
  useAuth: vi.fn(),
}));
import { useAuth } from 'react-oidc-context';

// Mock the API client globally for this test file
vi.mock('../api/client', () => ({
  api: {
    listProfiles: vi.fn(),
    createProfile: vi.fn(),
    getProfileHistory: vi.fn(),
  },
  customApi: {
    acceptProposedProfile: vi.fn(),
    rejectProposedProfile: vi.fn(),
  },
  axiosInstance: { interceptors: { request: { handlers: [] }, response: { handlers: [] } } },
}));

// Build a minimal JWT with the given payload (no real signing — just for decoding in the browser)
function buildFakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fakesig`;
}

function renderApp(userOverrides: Record<string, unknown> = {}) {
  vi.mocked(useAuth).mockReturnValue({
    isLoading: false,
    error: undefined,
    isAuthenticated: true,
    user: {
      profile: {
        sub: '11111111-1111-1111-1111-111111111111',
        preferred_username: 'sarah',
      },
      access_token: buildFakeJwt({ roles: [] }),
      ...userOverrides,
    },
    signinRedirect: vi.fn(),
    removeUser: vi.fn(),
    signoutRedirect: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <App />
      </TimeFormatProvider>
    </QueryClientProvider>
  );
}

test('renders main heading', async () => {
  vi.mocked(api.listProfiles).mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.listProfiles>>);
  renderApp();
  expect(screen.getByText(/T1D Profile Manager/i)).toBeInTheDocument();
  expect(screen.getByText(/Create New Profile/i)).toBeInTheDocument();
});

// ── strictArray role validation ────────────────────────────────────────────────

test('admin nav button is shown when roles is an array containing ADMIN', async () => {
  vi.mocked(api.listProfiles).mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.listProfiles>>);
  renderApp({
    access_token: buildFakeJwt({ roles: ['ADMIN'] }),
  });
  await waitFor(() =>
    expect(screen.getByText(/Manage Insulins/i)).toBeInTheDocument()
  );
});

test('admin nav button is NOT shown when roles claim is a plain string "ADMIN"', async () => {
  // A crafted token where roles is a scalar string instead of an array — must be rejected
  vi.mocked(api.listProfiles).mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.listProfiles>>);
  renderApp({
    access_token: buildFakeJwt({ roles: 'ADMIN' }),
  });
  await waitFor(() => screen.getByText(/T1D Profile Manager/i));
  expect(screen.queryByText(/Manage Insulins/i)).not.toBeInTheDocument();
});

test('admin nav button is NOT shown when roles array is empty', async () => {
  vi.mocked(api.listProfiles).mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.listProfiles>>);
  renderApp({
    access_token: buildFakeJwt({ roles: [] }),
  });
  await waitFor(() => screen.getByText(/T1D Profile Manager/i));
  expect(screen.queryByText(/Manage Insulins/i)).not.toBeInTheDocument();
});

test('admin nav button is NOT shown when roles claim is absent', async () => {
  vi.mocked(api.listProfiles).mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.listProfiles>>);
  renderApp({
    access_token: buildFakeJwt({}),
  });
  await waitFor(() => screen.getByText(/T1D Profile Manager/i));
  expect(screen.queryByText(/Manage Insulins/i)).not.toBeInTheDocument();
});

// ── token sync via useEffect ───────────────────────────────────────────────────

test('setAccessToken is called with the access_token from OIDC user', async () => {
  const { setAccessToken } = await import('../api/tokenProvider');
  const spy = vi.spyOn(await import('../api/tokenProvider'), 'setAccessToken');
  vi.mocked(api.listProfiles).mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.listProfiles>>);

  const token = buildFakeJwt({ roles: [] });
  renderApp({ access_token: token });

  await waitFor(() => {
    expect(spy).toHaveBeenCalledWith(token);
  });
  spy.mockRestore();
  void setAccessToken; // silence unused import warning
});
