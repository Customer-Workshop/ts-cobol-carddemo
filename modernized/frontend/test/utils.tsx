import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { UserRole, type User } from '@carddemo/shared';
import { App } from '../src/App';
import { storeSession } from '../src/api/client';

export function renderApp(initialPath = '/signon') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App />
    </MemoryRouter>,
  );
}

export function signInAs(role: UserRole) {
  const user: User =
    role === UserRole.ADMIN
      ? { id: 'ADMIN001', firstName: 'ALICE', lastName: 'ADMIN', role }
      : { id: 'USER0001', firstName: 'UNA', lastName: 'USER', role };
  storeSession(`mock-jwt-${user.id}`, user);
  return user;
}
