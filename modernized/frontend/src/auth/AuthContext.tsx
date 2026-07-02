// Session/auth state replacing the legacy CICS COMMAREA + RACF sign-on flow.
// REQ-F-385: successful authentication stores the session context (JWT + user).

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { UserRole, type User } from '@carddemo/shared';
import { api, clearSession, getStoredUser, getToken, storeSession } from '../api/client';

interface AuthContextValue {
  user: User | null;
  signIn: (userId: string, password: string) => Promise<User>;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => (getToken() ? getStoredUser() : null));

  const signIn = useCallback(async (userId: string, password: string) => {
    const res = await api.signIn(userId, password);
    storeSession(res.token, res.user);
    setUser(res.user);
    return res.user;
  }, []);

  const signOut = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, signIn, signOut }), [user, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

// REQ-F-352: users without a session are always routed to the sign-on screen.
export function RequireAuth({ children, role }: { children: ReactNode; role?: UserRole }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/signon" state={{ from: location }} replace />;
  // REQ-F-347: admin-only functions are denied to standard users.
  if (role && user.role !== role) return <Navigate to="/menu" replace />;
  return <>{children}</>;
}
