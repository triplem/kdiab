import { render, screen, waitFor } from '@testing-library/react';
import { AdminInsulinManager } from '../../components/AdminInsulinManager';
import { api } from '../../api/client';
import { vi, test, expect, Mock } from 'vitest';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../../api/client', () => ({
  api: {
    getInsulins: vi.fn(),
    createInsulin: vi.fn(),
    updateInsulin: vi.fn(),
    deleteInsulin: vi.fn(),
  },
}));

test('renders insulin list and permits creation', async () => {
  const user = userEvent.setup();
  (api.getInsulins as Mock).mockResolvedValue({
    data: [{ id: '1', name: 'Humalog' }, { id: '2', name: 'Fiasp' }]
  });
  (api.createInsulin as Mock).mockResolvedValue({ data: { id: '3', name: 'Liumjev' } });

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <AdminInsulinManager />
    </QueryClientProvider>
  );

  await waitFor(() => {
    expect(screen.getByText('Humalog')).toBeInTheDocument();
    expect(screen.getByText('Fiasp')).toBeInTheDocument();
  });

  const nameInput = screen.getByPlaceholderText(/new insulin name/i);
  await user.type(nameInput, 'Liumjev');
  
  await user.click(screen.getByText(/add insulin/i));

  await waitFor(() => {
    expect(api.createInsulin).toHaveBeenCalledWith({ name: 'Liumjev' });
  });
});
