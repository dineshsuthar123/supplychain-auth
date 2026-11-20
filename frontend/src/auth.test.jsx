import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { api, AuthProvider, useAuth } from './auth';

const operator = { id: 'operator-1', tenantId: 'tenant-1' };

function token(secondsRemaining) {
  const expiresAt = Math.floor(Date.now() / 1000) + secondsRemaining;
  return 'header.' + btoa(JSON.stringify({ exp: expiresAt })) + '.signature';
}

function SessionControl() {
  const { user, logout } = useAuth();
  return user ? <button type="button" onClick={logout}>Sign out</button> : <p>Signed out</p>;
}

let container;
let root;
let post;
let previousActEnvironment;

beforeEach(() => {
  previousActEnvironment = global.IS_REACT_ACT_ENVIRONMENT;
  global.IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
  post = jest.spyOn(api, 'post');
  localStorage.setItem('sp_user', JSON.stringify(operator));
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  post.mockRestore();
  localStorage.clear();
  global.IS_REACT_ACT_ENVIRONMENT = previousActEnvironment;
});

async function signOut(accessToken) {
  localStorage.setItem('sp_token', accessToken);
  await act(async () => root.render(<AuthProvider><SessionControl /></AuthProvider>));
  await act(async () => container.querySelector('button').click());
  expect(container.textContent).toBe('Signed out');
  expect(localStorage.getItem('sp_token')).toBeNull();
}

test('sign-out revokes an expired session using a refreshed access token', async () => {
  post.mockResolvedValueOnce({ data: { accessToken: 'refreshed', user: operator } })
    .mockResolvedValueOnce({});
  await signOut(token(-60));
  expect(post.mock.calls).toEqual([
    ['/auth/refresh'],
    ['/auth/logout', null, { headers: { Authorization: 'Bearer refreshed' } }]
  ]);
});

test('sign-out does not revoke a different user’s session after a cookie change', async () => {
  post.mockResolvedValueOnce({ data: {
    accessToken: 'different-session',
    user: { id: 'operator-2', tenantId: 'tenant-2' }
  } });
  await signOut(token(-60));
  expect(post.mock.calls).toEqual([['/auth/refresh']]);
});

test('sign-out uses the active access token when it is current', async () => {
  const currentToken = token(900);
  post.mockResolvedValueOnce({});
  await signOut(currentToken);
  expect(post.mock.calls).toEqual([
    ['/auth/logout', null, { headers: { Authorization: 'Bearer ' + currentToken } }]
  ]);
});
