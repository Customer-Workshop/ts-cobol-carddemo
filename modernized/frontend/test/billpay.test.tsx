// Bill pay: REQ-F-100 (account required), REQ-F-103/104 (confirm full-balance
// payment), REQ-F-108 (nothing to pay).

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRole } from '@carddemo/shared';
import { describe, expect, it } from 'vitest';
import { renderApp, signInAs } from './utils';

describe('Bill Pay (COBIL00C)', () => {
  it('requires an account id', async () => {
    signInAs(UserRole.USER);
    renderApp('/billpay');
    await userEvent.click(screen.getByRole('button', { name: 'Get Balance' }));
    expect(await screen.findByText('Acct ID can NOT be empty...')).toBeInTheDocument();
  });

  it('shows nothing-to-pay for a zero balance', async () => {
    signInAs(UserRole.USER);
    renderApp('/billpay');
    await userEvent.type(screen.getByLabelText('Account ID'), '00000000002');
    await userEvent.click(screen.getByRole('button', { name: 'Get Balance' }));
    expect(await screen.findByText('You have nothing to pay...')).toBeInTheDocument();
  });

  it('pays the full balance after confirmation', async () => {
    signInAs(UserRole.USER);
    renderApp('/billpay');
    await userEvent.type(screen.getByLabelText('Account ID'), '00000000001');
    await userEvent.click(screen.getByRole('button', { name: 'Get Balance' }));
    expect(await screen.findByText('1250.75')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm Payment (Y)' }));
    await waitFor(() =>
      expect(screen.getByText(/Payment successful\. Your Transaction ID is \d{16}\./)).toBeInTheDocument(),
    );
    expect(screen.getByText('0.00')).toBeInTheDocument();
  });
});
