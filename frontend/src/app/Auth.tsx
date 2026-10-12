import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react';
import { api, onSession, refresh, setSession, ApiError } from '../lib/api';
import type { Session, Membership } from '../lib/types';
interface AuthContextValue {
  session: Session | null;
  member: Membership | null;
  status: 'loading' | 'ready' | 'unavailable';
  error: string;
  retry: () => void;
  login: (path: string, body: unknown, key?: string) => Promise<void>;
  logout: () => Promise<void>;
  switchWorkspace: (id: string) => Promise<void>;
}
const Context = createContext<AuthContextValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, updateSession] = useState<Session | null>(null);
  const [status, setStatus] = useState<AuthContextValue['status']>('loading');
  const [error, setError] = useState('');
  const restore = useCallback(
    () =>
      refresh()
        .then(() => setStatus('ready'))
        .catch((e) => {
          if (e instanceof ApiError && e.status === 401) {
            setSession(null);
            setStatus('ready');
          } else {
            setError(e.message);
            setStatus('unavailable');
          }
        }),
    [],
  );
  const boot = () => {
    setStatus('loading');
    setError('');
    void restore();
  };
  useEffect(() => {
    const off = onSession(updateSession);
    void restore();
    return off;
  }, [restore]);
  const login = async (path: string, body: unknown, key?: string) => {
    const s = await api.post<Session>(path, body, key);
    setSession(s);
    setStatus('ready');
  };
  const logout = async () => {
    await api.post('/auth/logout', undefined);
    setSession(null);
  };
  const switchWorkspace = async (id: string) =>
    setSession(await api.post<Session>('/auth/workspace', { workspaceId: id }));
  return (
    <Context.Provider
      value={{
        session,
        member: session?.memberships.find((m) => m.active) ?? null,
        status,
        error,
        retry: boot,
        login,
        logout,
        switchWorkspace,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export function useAuth() {
  const value = useContext(Context);
  if (!value) throw new Error('Auth provider required');
  return value;
}
