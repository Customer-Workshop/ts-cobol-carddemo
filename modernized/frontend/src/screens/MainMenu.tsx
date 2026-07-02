// Main Menu screen (legacy COMEN01C / tran CM00).
// REQ-F-335..REQ-F-348: menu display, option selection, admin gating.

import { useState } from 'react';
import { UserRole } from '@carddemo/shared';
import { useAuth } from '../auth/AuthContext';
import { MenuList, type MenuOption } from '../components/MenuScreen';
import { Screen, type ScreenMessage } from '../components/Screen';

export function MainMenu() {
  const { user } = useAuth();
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  const options: MenuOption[] = [
    { label: 'Account View', to: '/accounts/view' },
    { label: 'Account Update', to: '/accounts/update' },
    { label: 'Credit Card List', to: '/cards' },
    { label: 'Credit Card View', to: '/cards/view' },
    { label: 'Credit Card Update', to: '/cards/update' },
    { label: 'Transaction List', to: '/transactions' },
    { label: 'Transaction View', to: '/transactions/view' },
    { label: 'Transaction Add', to: '/transactions/add' },
    { label: 'Bill Payment', to: '/billpay' },
    { label: 'Transaction Reports', to: '/reports' },
  ];
  // REQ-F-347: admin-only entries are hidden from standard users
  if (user?.role === UserRole.ADMIN) {
    options.push({ label: 'Admin Menu', to: '/admin' });
  }

  return (
    <Screen tranId="CM00" program="COMEN01C" title="Main Menu" message={message} backTo="/signon">
      <MenuList options={options} onError={setMessage} />
    </Screen>
  );
}
