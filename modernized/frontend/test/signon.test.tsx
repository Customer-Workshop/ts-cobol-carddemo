// Sign-on flow: REQ-F-379/380 (blank field messages), REQ-F-382/383 (auth
// errors), REQ-F-386/387 (role-based routing), and route guarding.

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderApp } from './utils';

describe('Sign-on (COSGN00C)', () => {
  it('shows an error when user id is blank', async () => {
    renderApp();
    await userEvent.click(screen.getByRole('button', { name: 'Sign On' }));
    expect(await screen.findByText('Please enter User ID ...')).toBeInTheDocument();
  });

  it('shows an error when password is blank', async () => {
    renderApp();
    await userEvent.type(screen.getByLabelText('User ID'), 'ADMIN001');
    await userEvent.click(screen.getByRole('button', { name: 'Sign On' }));
    expect(await screen.findByText('Please enter Password ...')).toBeInTheDocument();
  });

  it('shows user-not-found message', async () => {
    renderApp();
    await userEvent.type(screen.getByLabelText('User ID'), 'NOBODY');
    await userEvent.type(screen.getByLabelText('Password'), 'PASSWORD');
    await userEvent.click(screen.getByRole('button', { name: 'Sign On' }));
    expect(await screen.findByText('User not found. Try again ...')).toBeInTheDocument();
  });

  it('shows wrong-password message', async () => {
    renderApp();
    await userEvent.type(screen.getByLabelText('User ID'), 'USER0001');
    await userEvent.type(screen.getByLabelText('Password'), 'WRONG');
    await userEvent.click(screen.getByRole('button', { name: 'Sign On' }));
    expect(await screen.findByText('Wrong Password. Try again ...')).toBeInTheDocument();
  });

  it('routes ADMIN to the admin menu', async () => {
    renderApp();
    await userEvent.type(screen.getByLabelText('User ID'), 'admin001');
    await userEvent.type(screen.getByLabelText('Password'), 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign On' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Admin Menu' })).toBeInTheDocument());
  });

  it('routes USER to the main menu', async () => {
    renderApp();
    await userEvent.type(screen.getByLabelText('User ID'), 'USER0001');
    await userEvent.type(screen.getByLabelText('Password'), 'PASSWORD');
    await userEvent.click(screen.getByRole('button', { name: 'Sign On' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Main Menu' })).toBeInTheDocument());
  });

  it('redirects unauthenticated access to the sign-on screen', () => {
    renderApp('/accounts/view');
    expect(screen.getByRole('heading', { name: 'Sign On' })).toBeInTheDocument();
  });
});
