import { Navigate, Route, Routes } from 'react-router-dom';
import { UserRole } from '@carddemo/shared';
import { AuthProvider, RequireAuth } from './auth/AuthContext';
import { SignOn } from './screens/SignOn';
import { MainMenu } from './screens/MainMenu';
import { AdminMenu } from './screens/AdminMenu';
import { AccountView } from './screens/AccountView';
import { AccountUpdate } from './screens/AccountUpdate';
import { CardList } from './screens/CardList';
import { CardView } from './screens/CardView';
import { CardUpdate } from './screens/CardUpdate';
import { TransactionList } from './screens/TransactionList';
import { TransactionView } from './screens/TransactionView';
import { TransactionAdd } from './screens/TransactionAdd';
import { BillPay } from './screens/BillPay';
import { Reports } from './screens/Reports';
import { UserList } from './screens/UserList';
import { UserAdd } from './screens/UserAdd';
import { UserUpdate } from './screens/UserUpdate';
import { UserDelete } from './screens/UserDelete';

function guarded(element: React.ReactNode, role?: UserRole) {
  return <RequireAuth role={role}>{element}</RequireAuth>;
}

export function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Navigate to="/signon" replace />} />
        <Route path="/signon" element={<SignOn />} />
        <Route path="/menu" element={guarded(<MainMenu />)} />
        <Route path="/admin" element={guarded(<AdminMenu />, UserRole.ADMIN)} />
        <Route path="/accounts/view" element={guarded(<AccountView />)} />
        <Route path="/accounts/update" element={guarded(<AccountUpdate />)} />
        <Route path="/cards" element={guarded(<CardList />)} />
        <Route path="/cards/view" element={guarded(<CardView />)} />
        <Route path="/cards/update" element={guarded(<CardUpdate />)} />
        <Route path="/transactions" element={guarded(<TransactionList />)} />
        <Route path="/transactions/view" element={guarded(<TransactionView />)} />
        <Route path="/transactions/add" element={guarded(<TransactionAdd />)} />
        <Route path="/billpay" element={guarded(<BillPay />)} />
        <Route path="/reports" element={guarded(<Reports />)} />
        <Route path="/admin/users" element={guarded(<UserList />, UserRole.ADMIN)} />
        <Route path="/admin/users/add" element={guarded(<UserAdd />, UserRole.ADMIN)} />
        <Route path="/admin/users/update" element={guarded(<UserUpdate />, UserRole.ADMIN)} />
        <Route path="/admin/users/delete" element={guarded(<UserDelete />, UserRole.ADMIN)} />
        <Route path="*" element={<Navigate to="/signon" replace />} />
      </Routes>
    </AuthProvider>
  );
}
