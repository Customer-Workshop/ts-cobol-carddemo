// Menu routing and role gating: REQ-F-343/345/346 (option selection),
// REQ-F-347 (admin gating).

import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRole } from '@carddemo/shared';
import { describe, expect, it } from 'vitest';
import { renderApp, signInAs } from './utils';

describe('Menus (COMEN01C / COADM01C)', () => {
  it('navigates to a screen by option number', async () => {
    signInAs(UserRole.USER);
    renderApp('/menu');
    await userEvent.type(screen.getByLabelText('Option'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));
    expect(await screen.findByRole('heading', { name: 'List Credit Cards' })).toBeInTheDocument();
  });

  it('rejects an out-of-range option number', async () => {
    signInAs(UserRole.USER);
    renderApp('/menu');
    await userEvent.type(screen.getByLabelText('Option'), '99');
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));
    expect(await screen.findByText('Please enter a valid option number...')).toBeInTheDocument();
  });

  it('hides the admin menu option from regular users', () => {
    signInAs(UserRole.USER);
    renderApp('/menu');
    expect(screen.queryByText(/Admin Menu/)).not.toBeInTheDocument();
  });

  it('shows the admin menu option to admins', () => {
    signInAs(UserRole.ADMIN);
    renderApp('/menu');
    expect(screen.getByText(/Admin Menu/)).toBeInTheDocument();
  });

  it('blocks regular users from admin routes', () => {
    signInAs(UserRole.USER);
    renderApp('/admin/users');
    expect(screen.getByRole('heading', { name: 'Main Menu' })).toBeInTheDocument();
  });
});
