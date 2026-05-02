import { render, screen, waitFor } from '@testing-library/react';
import { ProfileEditor } from '../../components/ProfileEditor';
import { api } from '../../api/client';
import { vi, test, expect, Mock } from 'vitest';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TimeFormatProvider } from '../../context/TimeFormatContext';

// Mock the API client
vi.mock('../../api/client', () => ({
  api: {
    createProfile: vi.fn(),
    getInsulins: vi.fn().mockResolvedValue({ data: [{ id: 'Fiasp', name: 'Fiasp' }] }),
    createInsulin: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

test('renders profile editor form', () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileEditor userId="user-1" />
      </TimeFormatProvider>
    </QueryClientProvider>
  );
  expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/insulin type/i)).toBeInTheDocument();
});

test('validates form and submits', async () => {
  const user = userEvent.setup();
  (api.createProfile as Mock).mockResolvedValue({ data: {} });
  const onSaved = vi.fn();

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileEditor userId="user-1" onProfileSaved={onSaved} />
      </TimeFormatProvider>
    </QueryClientProvider>
  );

  // Fill form
  const nameInput = screen.getByLabelText(/name/i);
  await user.clear(nameInput);
  await user.type(nameInput, 'Test Profile');

  // Wait for insulins to load
  await waitFor(() => {
    expect(screen.getByRole('option', { name: 'Fiasp' })).toBeInTheDocument();
  });

  const insulinInput = screen.getByLabelText(/insulin type/i);
  await user.selectOptions(insulinInput, 'Fiasp');
  
  // Basal is pre-filled with one segment in defaultValues, just check it exists
  expect(screen.getByDisplayValue('0.5')).toBeInTheDocument();

  // Submit
  await user.click(screen.getByRole('button', { name: /create profile/i }));

  await waitFor(() => {
    expect(api.createProfile).toHaveBeenCalledWith('user-1', expect.objectContaining({
      name: 'Test Profile',
      insulinType: 'Fiasp',
      durationOfAction: 300,
      basal: expect.arrayContaining([
        expect.objectContaining({ startTime: '00:00', value: 0.5 })
      ])
    }));
    expect(onSaved).toHaveBeenCalled();
  });
});

function renderEditor(props: Parameters<typeof ProfileEditor>[0] = { userId: 'user-1' }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeFormatProvider>
        <ProfileEditor {...props} />
      </TimeFormatProvider>
    </QueryClientProvider>
  );
}

test('shows validation error for negative duration', async () => {
  const user = userEvent.setup();
  renderEditor();

  const durationInput = screen.getByLabelText(/duration/i);
  await user.clear(durationInput);
  await user.type(durationInput, '-10');

  await user.click(screen.getByRole('button', { name: /create profile/i }));

  await waitFor(() => {
    expect(screen.getByText(/duration must be positive/i)).toBeInTheDocument();
  });
});

test('Targets tab is rendered and can be switched to', async () => {
  const user = userEvent.setup();
  renderEditor();

  const targetsTab = screen.getByRole('button', { name: /^targets$/i });
  expect(targetsTab).toBeInTheDocument();

  await user.click(targetsTab);

  // The Blood Glucose Targets heading should appear after switching
  expect(screen.getByText(/blood glucose targets/i)).toBeInTheDocument();
});

test('can add a target segment with low and high values', async () => {
  const user = userEvent.setup();
  renderEditor();

  // Switch to Targets tab
  await user.click(screen.getByRole('button', { name: /^targets$/i }));

  // Initially no target rows
  expect(screen.queryByPlaceholderText(/low/i)).not.toBeInTheDocument();

  // Add a target segment
  await user.click(screen.getByRole('button', { name: /add target segment/i }));

  // Low and High inputs should appear
  const lowInput = await screen.findByPlaceholderText(/low/i);
  const highInput = await screen.findByPlaceholderText(/high/i);
  expect(lowInput).toBeInTheDocument();
  expect(highInput).toBeInTheDocument();
});

test('target segment is included in form submission', async () => {
  const user = userEvent.setup();
  (api.createProfile as Mock).mockResolvedValue({ data: {} });
  const onSaved = vi.fn();
  renderEditor({ userId: 'user-1', onProfileSaved: onSaved });

  // Fill required fields
  await user.clear(screen.getByLabelText(/name/i));
  await user.type(screen.getByLabelText(/name/i), 'Target Test');

  await waitFor(() => expect(screen.getByRole('option', { name: 'Fiasp' })).toBeInTheDocument());
  await user.selectOptions(screen.getByLabelText(/insulin type/i), 'Fiasp');

  // Switch to Targets tab and add a segment
  await user.click(screen.getByRole('button', { name: /^targets$/i }));
  await user.click(screen.getByRole('button', { name: /add target segment/i }));

  const lowInput = await screen.findByPlaceholderText(/low/i);
  const highInput = await screen.findByPlaceholderText(/high/i);
  await user.clear(lowInput);
  await user.type(lowInput, '80');
  await user.clear(highInput);
  await user.type(highInput, '120');

  await user.click(screen.getByRole('button', { name: /create profile/i }));

  await waitFor(() => {
    expect(api.createProfile).toHaveBeenCalledWith('user-1', expect.objectContaining({
      targets: expect.arrayContaining([
        expect.objectContaining({ startTime: '00:00', low: 80, high: 120 })
      ])
    }));
    expect(onSaved).toHaveBeenCalled();
  });
});

test('beforeunload listener is added when form becomes dirty', async () => {
  const addEventListenerSpy = vi.spyOn(window, 'addEventListener');
  const user = userEvent.setup();
  renderEditor();

  // Make the form dirty
  const nameInput = screen.getByLabelText(/name/i);
  await user.type(nameInput, 'x');

  const beforeunloadCall = addEventListenerSpy.mock.calls.find(
    ([event]) => event === 'beforeunload'
  );
  expect(beforeunloadCall).toBeTruthy();
  addEventListenerSpy.mockRestore();
});
