// Admin Menu screen (legacy COADM01C / tran CA00).
// REQ-F-066..REQ-F-076: admin option list, selection validation, PF3 to sign-on.

import { useState } from 'react';
import { MenuList, type MenuOption } from '../components/MenuScreen';
import { Screen, type ScreenMessage } from '../components/Screen';

const OPTIONS: MenuOption[] = [
  { label: 'User List (Security)', to: '/admin/users' },
  { label: 'User Add (Security)', to: '/admin/users/add' },
  { label: 'User Update (Security)', to: '/admin/users/update' },
  { label: 'User Delete (Security)', to: '/admin/users/delete' },
];

export function AdminMenu() {
  const [message, setMessage] = useState<ScreenMessage | null>(null);
  return (
    <Screen tranId="CA00" program="COADM01C" title="Admin Menu" message={message} backTo="/signon">
      <MenuList options={OPTIONS} onError={setMessage} />
    </Screen>
  );
}
