// Account update: REQ-F-019/020 (change detection), REQ-F-043/044 (FICO
// validation), REQ-F-057 (confirm-before-save), success flow.

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRole } from '@carddemo/shared';
import { describe, expect, it } from 'vitest';
import { renderApp, signInAs } from './utils';

async function loadAccount() {
  signInAs(UserRole.USER);
  renderApp('/accounts/update');
  await userEvent.type(screen.getByLabelText('Account Number'), '00000000001');
  await userEvent.click(screen.getByRole('button', { name: 'Search' }));
  await screen.findByText('Account 00000000001');
}

describe('Account Update (COACTUPC)', () => {
  it('warns when no changes were made', async () => {
    await loadAccount();
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    expect(await screen.findByText('Please modify to update ...')).toBeInTheDocument();
  });

  it('rejects an out-of-range FICO score', async () => {
    await loadAccount();
    const fico = screen.getByLabelText('FICO Score');
    await userEvent.clear(fico);
    await userEvent.type(fico, '900');
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    expect(await screen.findByText('FICO score should be between 300 and 850')).toBeInTheDocument();
  });

  it('rejects an invalid state/zip combination', async () => {
    await loadAccount();
    const state = screen.getByLabelText('State');
    await userEvent.clear(state);
    await userEvent.type(state, 'CA'); // CA with NY zip 10001
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    expect(await screen.findByText('Zip code is invalid for the state')).toBeInTheDocument();
  });

  it('requires confirmation then saves valid changes', async () => {
    await loadAccount();
    const fico = screen.getByLabelText('FICO Score');
    await userEvent.clear(fico);
    await userEvent.type(fico, '750');
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    const confirmBtn = await screen.findByRole('button', { name: 'F5=Confirm Save' });
    await userEvent.click(confirmBtn);
    await waitFor(() => expect(screen.getByText('Account has been updated ...')).toBeInTheDocument());
    expect(screen.getByLabelText('FICO Score')).toHaveValue('750');
  });
});
