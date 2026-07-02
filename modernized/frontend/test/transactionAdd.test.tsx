// Add transaction: REQ-F-487/488 (validation), REQ-F-490 (confirmation),
// REQ-F-492 (success with new transaction id).

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRole } from '@carddemo/shared';
import { describe, expect, it } from 'vitest';
import { renderApp, signInAs } from './utils';

describe('Add Transaction (COTRN02C)', () => {
  it('requires account or card number', async () => {
    signInAs(UserRole.USER);
    renderApp('/transactions/add');
    await userEvent.click(screen.getByRole('button', { name: 'Validate' }));
    expect(await screen.findByText('Account or Card Number must be entered...')).toBeInTheDocument();
  });

  it('validates amount format', async () => {
    signInAs(UserRole.USER);
    renderApp('/transactions/add');
    await userEvent.type(screen.getByLabelText('Card Number'), '4000000000000001');
    await userEvent.type(screen.getByLabelText('Amount (-99999999.99)'), 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Validate' }));
    expect(await screen.findByText('Amount must be in format -99999999.99')).toBeInTheDocument();
  });

  it('adds a transaction after confirmation', async () => {
    signInAs(UserRole.USER);
    renderApp('/transactions/add');
    await userEvent.type(screen.getByLabelText('Card Number'), '4000000000000001');
    await userEvent.type(screen.getByLabelText('Type Code'), '01');
    await userEvent.type(screen.getByLabelText('Category Code'), '1');
    await userEvent.type(screen.getByLabelText('Source'), 'POS TERM');
    await userEvent.type(screen.getByLabelText('Amount (-99999999.99)'), '42.50');
    await userEvent.type(screen.getByLabelText('Orig Date (YYYY-MM-DD)'), '2026-06-15');
    await userEvent.type(screen.getByLabelText('Merchant ID'), '111222333');
    await userEvent.type(screen.getByLabelText('Merchant Name'), 'TEST MERCHANT');
    await userEvent.type(screen.getByLabelText('Merchant City'), 'NEW YORK');
    await userEvent.type(screen.getByLabelText('Merchant Zip'), '10001');
    await userEvent.type(screen.getByLabelText('Description'), 'TEST PURCHASE');
    await userEvent.click(screen.getByRole('button', { name: 'Validate' }));
    const confirmBtn = await screen.findByRole('button', { name: 'Confirm Add (Y)' });
    await userEvent.click(confirmBtn);
    await waitFor(() =>
      expect(screen.getByText(/Transaction added successfully\. Your Tran ID is \d{16}\./)).toBeInTheDocument(),
    );
  });
});
