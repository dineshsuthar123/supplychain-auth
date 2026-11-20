import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { api } from './auth';

const user = { id: 'signup-operator', tenantId: 'signup-workspace', username: 'signup-test', role: 'USER' };
const signupPassword = 'signup-fixture-' + user.id;
let container;
let root;
let previousActEnvironment;

beforeEach(() => {
  previousActEnvironment = global.IS_REACT_ACT_ENVIRONMENT;
  global.IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  jest.restoreAllMocks();
  localStorage.clear();
  global.IS_REACT_ACT_ENVIRONMENT = previousActEnvironment;
});

async function openSignup(roles) {
  jest.spyOn(api, 'get').mockImplementation(url => Promise.resolve({
    data: url === '/auth/registration-options' ? { roles } : { status: 'UP' }
  }));
  await act(async () => root.render(<App />));
  const createAccount = Array.from(container.querySelectorAll('button'))
    .find(button => button.textContent === 'Create an account');
  await act(async () => createAccount.click());
  container.querySelector('[name="username"]').value = user.username;
  container.querySelector('[name="email"]').value = 'signup@example.test';
  container.querySelector('[name="password"]').value = signupPassword;
}

async function submitSignup() {
  await act(async () => container.querySelector('form').dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true })
  ));
}

test('approval-only signup explains the Standard User role and sends no operator role', async () => {
  const post = jest.spyOn(api, 'post').mockResolvedValue({ data: { user, accessToken: 'signed-up' } });
  await openSignup(['USER']);
  expect(container.querySelector('select[name="role"]')).toBeNull();
  expect(container.textContent).toContain('A workspace administrator assigns inspection or audit access.');
  await submitSignup();
  expect(post).toHaveBeenCalledWith('/auth/register', {
    username: user.username, email: 'signup@example.test', password: signupPassword, displayName: user.username
  });
  expect(container.textContent).toContain('Access pending.');
});

test('signup offers only the operator roles enabled by the backend', async () => {
  const post = jest.spyOn(api, 'post').mockResolvedValue({
    data: { user: { ...user, role: 'MANUFACTURER' }, accessToken: 'signed-up' }
  });
  jest.spyOn(api, 'request').mockResolvedValue({ data: { productsAttested: 0 } });
  await openSignup(['USER', 'MANUFACTURER', 'VERIFIER']);
  const roles = container.querySelector('select[name="role"]');
  expect(Array.from(roles.options, option => option.value)).toEqual(['USER', 'MANUFACTURER', 'VERIFIER']);
  roles.value = 'MANUFACTURER';
  await submitSignup();
  expect(post).toHaveBeenCalledWith('/auth/register', expect.objectContaining({ role: 'MANUFACTURER' }));
  expect(container.textContent).toContain('Enroll reference');
});

test('Check access refreshes the signed-in session and opens the workspace after a role grant', async () => {
  localStorage.setItem('sp_user', JSON.stringify(user));
  localStorage.setItem('sp_token', 'prior-access-token');
  jest.spyOn(api, 'get').mockResolvedValue({ data: { status: 'UP' } });
  const post = jest.spyOn(api, 'post').mockResolvedValue({
    data: { user: { ...user, role: 'MANUFACTURER' }, accessToken: 'updated' }
  });
  const request = jest.spyOn(api, 'request').mockResolvedValue({ data: { productsAttested: 0 } });
  await act(async () => root.render(<App />));
  const checkAccess = Array.from(container.querySelectorAll('button'))
    .find(button => button.textContent === 'Check access');
  await act(async () => checkAccess.click());
  expect(post).toHaveBeenCalledWith('/auth/refresh');
  expect(request).toHaveBeenCalledWith(expect.objectContaining({
    url: '/api/dashboard', headers: { Authorization: 'Bearer updated' }
  }));
  expect(container.textContent).toContain('Enroll reference');
  expect(container.textContent).not.toContain('Access pending.');
});
