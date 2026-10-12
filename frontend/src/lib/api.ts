import type { Session } from './types';
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details: unknown = {},
  ) {
    super(message);
  }
}
let access: string | null = null;
let commandActor = 'anonymous';
let refreshFlight: Promise<Session> | null = null;
let sessionListener: (session: Session | null) => void = () => {};
export function onSession(listener: (session: Session | null) => void) {
  sessionListener = listener;
  return () => {
    sessionListener = () => {};
  };
}
export function setSession(session: Session | null) {
  access = session?.accessToken ?? null;
  commandActor = session
    ? `${session.user.id}:${session.memberships.find((m) => m.active)?.workspace_id ?? ''}`
    : 'anonymous';
  sessionListener(session);
}
export async function refresh(): Promise<Session> {
  if (!refreshFlight) {
    const rotate = () => request<Session>('/auth/refresh', 'POST', undefined, undefined, false);
    refreshFlight = (async () => {
      const s = await (navigator.locks
        ? navigator.locks.request('lotline-refresh', rotate)
        : rotate());
      setSession(s);
      return s;
    })().finally(() => {
      refreshFlight = null;
    });
  }
  return refreshFlight;
}
async function request<T>(
  path: string,
  method = 'GET',
  body?: unknown,
  key?: string,
  retry = true,
): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 25000);
  try {
    const response = await fetch(`/api/v1${path}`, {
      method,
      credentials: 'include',
      cache: 'no-store',
      signal: controller.signal,
      headers: {
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(access ? { Authorization: `Bearer ${access}` } : {}),
        ...(key ? { 'Idempotency-Key': key } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    if (response.status === 401 && retry && !path.startsWith('/auth/')) {
      await refresh();
      return request<T>(path, method, body, key, false);
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      throw new ApiError(
        response.status,
        problem.code ?? (response.status >= 500 ? 'BACKEND_WARMING' : 'REQUEST_FAILED'),
        problem.message ??
          (response.status >= 500
            ? 'The backend is starting or unavailable. Retry when it is ready.'
            : 'This request could not be accepted.'),
        problem.details,
      );
    }
    if (response.status === 204) return undefined as T;
    if (response.headers.get('Content-Type')?.includes('application/zip'))
      return (await response.blob()) as T;
    return (await response.json()) as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(
      0,
      navigator.onLine ? 'BACKEND_WARMING' : 'OFFLINE',
      navigator.onLine
        ? 'The backend did not respond in time. Your command may have been accepted; retrying the same fields reuses its command key.'
        : 'You are offline. Reconnect before submitting a command.',
    );
  } finally {
    clearTimeout(timeout);
  }
}
async function post<T>(path: string, body: unknown, key?: string): Promise<T> {
  if (path.startsWith('/auth/') || path === '/recalls/preview')
    return request<T>(path, 'POST', body, key);
  const digest = Array.from(
    new Uint8Array(
      await crypto.subtle.digest('SHA-256', new TextEncoder().encode(path + JSON.stringify(body))),
    ),
  )
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
  const storage = `lotline-pending-command:${commandActor}:${digest}`;
  const persisted = sessionStorage.getItem(storage) ?? key ?? crypto.randomUUID();
  sessionStorage.setItem(storage, persisted);
  try {
    const result = await request<T>(path, 'POST', body, persisted);
    sessionStorage.removeItem(storage);
    return result;
  } catch (e) {
    if (
      e instanceof ApiError &&
      e.status > 0 &&
      e.status < 500 &&
      e.status !== 408 &&
      e.status !== 429
    )
      sessionStorage.removeItem(storage);
    throw e;
  }
}
export const api = { get: <T>(path: string) => request<T>(path), post };
export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
