import { createContext, useCallback, useContext, useRef, useState } from 'react';
import axios from 'axios';

export const api = axios.create({
  baseURL: process.env.REACT_APP_API_BASE || '',
  withCredentials: true,
  timeout: 30000
});

const Auth = createContext(null);
export const useAuth = () => useContext(Auth);

function storedSession() {
  try {
    const user = JSON.parse(localStorage.getItem('sp_user'));
    const accessToken = localStorage.getItem('sp_token');
    return user && accessToken ? { user, accessToken } : null;
  } catch {
    return null;
  }
}

export function expiresSoon(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)).exp * 1000 <= Date.now() + 15000;
  } catch {
    return false;
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(storedSession);
  const [expired, setExpired] = useState(false);
  const current = useRef(session);
  const version = useRef(0);
  const pendingRefresh = useRef(null);
  const pendingLogout = useRef(null);

  const save = useCallback((next, sessionExpired = false) => {
    current.current = next;
    setSession(next);
    setExpired(sessionExpired);
    if (next) {
      localStorage.setItem('sp_user', JSON.stringify(next.user));
      localStorage.setItem('sp_token', next.accessToken);
    } else {
      localStorage.removeItem('sp_user');
      localStorage.removeItem('sp_token');
    }
  }, []);

  const refresh = useCallback(async (generation, previous) => {
    if (generation !== version.current || !current.current) throw new axios.CanceledError();
    if (current.current.accessToken !== previous) return current.current.accessToken;
    if (pendingRefresh.current?.generation === generation) return pendingRefresh.current.promise;

    const promise = api.post('/auth/refresh').then(({ data }) => {
      if (generation !== version.current) throw new axios.CanceledError();
      if (data.user.id !== current.current.user.id || data.user.tenantId !== current.current.user.tenantId) {
        version.current++;
        save(null, true);
        throw new axios.CanceledError();
      }
      save(data);
      return data.accessToken;
    }).catch(error => {
      if (generation === version.current && [401, 403].includes(error.response?.status)) {
        version.current++;
        save(null, true);
      }
      throw error;
    }).finally(() => {
      if (pendingRefresh.current?.promise === promise) pendingRefresh.current = null;
    });
    pendingRefresh.current = { generation, promise };
    return promise;
  }, [save]);

  const request = useCallback(async (config) => {
    const generation = version.current;
    let token = current.current?.accessToken;
    if (!token) throw new axios.CanceledError();
    const send = () => {
      if (generation !== version.current) throw new axios.CanceledError();
      return api.request({ ...config, headers: { Authorization: 'Bearer ' + token } });
    };
    if (expiresSoon(token)) token = await refresh(generation, token);
    try {
      const { data } = await send();
      if (generation !== version.current) throw new axios.CanceledError();
      return data;
    } catch (error) {
      if (error.response?.status !== 401 && !(error.response?.status === 403 && expiresSoon(token))) throw error;
      token = await refresh(generation, token);
      const { data } = await send();
      if (generation !== version.current) throw new axios.CanceledError();
      return data;
    }
  }, [refresh]);

  const signIn = async (mode, payload) => {
    await pendingLogout.current;
    const { data } = await api.post('/auth/' + mode, payload);
    version.current++;
    save(data);
  };

  const logout = () => {
    const token = current.current?.accessToken;
    if (!token) return;
    const refreshing = pendingRefresh.current?.promise;
    version.current++;
    save(null);
    pendingLogout.current = Promise.resolve(refreshing).catch(() => {}).then(() =>
      api.post('/auth/logout', null, { headers: { Authorization: 'Bearer ' + token } })
    ).catch(() => {
      // The local session has already ended.
    }).finally(() => {
      pendingLogout.current = null;
    });
  };

  return <Auth.Provider value={{ user: session?.user, expired, signIn, logout, request }}>{children}</Auth.Provider>;
}
