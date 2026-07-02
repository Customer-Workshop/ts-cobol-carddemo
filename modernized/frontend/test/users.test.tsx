// Admin user CRUD: REQ-F-506 (list), REQ-F-557/559 (add), REQ-F-532 (update
// change detection), REQ-F-541/605 (confirmed delete).

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserRole } from '@carddemo/shared';
import { describe, expect, it } from 'vitest';
import { renderApp, signInAs } from './utils';

describe('User admin (COUSR00C-COUSR03C)', () => {
  it('lists users', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users');
    expect(await screen.findByText('ADMIN001')).toBeInTheDocument();
    expect(screen.getByText('USER0001')).toBeInTheDocument();
  });

  it('validates required fields when adding a user', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users/add');
    await userEvent.click(screen.getByRole('button', { name: 'Add User' }));
    expect((await screen.findAllByText('First Name can NOT be empty...')).length).toBeGreaterThan(0);
  });

  it('adds a user', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users/add');
    await userEvent.type(screen.getByLabelText('First Name'), 'NEW');
    await userEvent.type(screen.getByLabelText('Last Name'), 'PERSON');
    await userEvent.type(screen.getByLabelText('User ID'), 'NEWUSER1');
    await userEvent.type(screen.getByLabelText('Password'), 'SECRET12');
    await userEvent.selectOptions(screen.getByLabelText('User Type'), 'USER');
    await userEvent.click(screen.getByRole('button', { name: 'Add User' }));
    expect(await screen.findByText('User NEWUSER1 has been added ...')).toBeInTheDocument();
  });

  it('rejects a duplicate user id', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users/add');
    await userEvent.type(screen.getByLabelText('First Name'), 'DUP');
    await userEvent.type(screen.getByLabelText('Last Name'), 'USER');
    await userEvent.type(screen.getByLabelText('User ID'), 'USER0001');
    await userEvent.type(screen.getByLabelText('Password'), 'SECRET12');
    await userEvent.selectOptions(screen.getByLabelText('User Type'), 'USER');
    await userEvent.click(screen.getByRole('button', { name: 'Add User' }));
    expect(await screen.findByText('User ID already exist...')).toBeInTheDocument();
  });

  it('warns when updating a user without changes', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users/update?userId=USER0001');
    await screen.findByText('User USER0001');
    await userEvent.click(screen.getByRole('button', { name: 'F5=Save' }));
    expect(await screen.findByText('Please modify to update ...')).toBeInTheDocument();
  });

  it('updates a user', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users/update?userId=USER0001');
    await screen.findByText('User USER0001');
    const firstName = screen.getByLabelText('First Name');
    await userEvent.clear(firstName);
    await userEvent.type(firstName, 'UPDATED');
    await userEvent.click(screen.getByRole('button', { name: 'F5=Save' }));
    expect(await screen.findByText('User USER0001 has been updated ...')).toBeInTheDocument();
  });

  it('deletes a user after review', async () => {
    signInAs(UserRole.ADMIN);
    renderApp('/admin/users/delete?userId=USER0001');
    expect(await screen.findByText('Press Delete (F5) key to delete this user ...')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'F5=Delete' }));
    await waitFor(() => expect(screen.getByText('User USER0001 has been deleted ...')).toBeInTheDocument());
  });
});
