import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';

test('signup survives a cold start beyond 25 seconds and submits once when the real API is ready', async ({
  page,
}) => {
  const password = process.env.DEMO_PASSWORD;
  if (!password) throw new Error('Set DEMO_PASSWORD for the isolated startup-check account.');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.clock.install();
  let warming = true;
  let signupRequests = 0;
  await page.route('**/api/v1/health', async (route) => {
    if (warming)
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: '{"status":"STARTING"}',
      });
    else await route.continue();
  });
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/api/v1/auth/register'))
      signupRequests++;
  });
  await page.goto('/create');
  const email = `startup-browser-${randomUUID()}@example.test`;
  await page.getByLabel('Your name', { exact: true }).fill('Startup verification');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByLabel('Workspace name').fill('Cold start · SYNTHETIC');
  await page.getByLabel('Your organization').fill('Synthetic startup check');
  await page.getByRole('button', { name: 'Create workspace →', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('up to two minutes');
  await page.clock.runFor(30_000);
  await expect(page.getByLabel('Work email')).toHaveValue(email);
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue(password);
  await expect(page.getByRole('button', { name: 'Connecting…', exact: true })).toBeDisabled();
  expect(signupRequests).toBe(0);
  warming = false;
  await page.clock.resume();
  await expect(page.getByRole('link', { name: 'Trace', exact: true })).toBeVisible();
  expect(signupRequests).toBe(1);
  await expect(page.getByRole('alert')).toHaveCount(0);
});
