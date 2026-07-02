// Card update: REQ-F-311 (no-change detection), REQ-F-313/316/319 (field
// validation), REQ-F-327 (confirm then save).

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRole } from '@carddemo/shared';
import { describe, expect, it } from 'vitest';
import { renderApp, signInAs } from './utils';

async function loadCard() {
  signInAs(UserRole.USER);
  renderApp('/cards/update?cardNumber=4000000000000001');
  await screen.findByText(/Card 4000000000000001/);
}

describe('Card Update (COCRDUPC)', () => {
  it('detects when no changes were made', async () => {
    await loadCard();
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    expect(await screen.findByText('No change detected with respect to values fetched...')).toBeInTheDocument();
  });

  it('rejects a non-alphabetic embossed name', async () => {
    await loadCard();
    const name = screen.getByLabelText('Name on Card');
    await userEvent.clear(name);
    await userEvent.type(name, 'JOHN DOE 3RD');
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    expect(await screen.findByText('Card name can only contain alphabets and spaces')).toBeInTheDocument();
  });

  it('rejects an invalid expiry month', async () => {
    await loadCard();
    const month = screen.getByLabelText('Expiry Month (1-12)');
    await userEvent.clear(month);
    await userEvent.type(month, '13');
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    expect(await screen.findByText('Card expiry month must be between 1 and 12')).toBeInTheDocument();
  });

  it('saves valid changes after confirmation', async () => {
    await loadCard();
    const name = screen.getByLabelText('Name on Card');
    await userEvent.clear(name);
    await userEvent.type(name, 'JOHN QUINCY DOE');
    await userEvent.click(screen.getByRole('button', { name: 'Validate Changes' }));
    const saveBtn = await screen.findByRole('button', { name: 'F5=Save' });
    await userEvent.click(saveBtn);
    await waitFor(() => expect(screen.getByText('Changes committed to database')).toBeInTheDocument());
  });
});
