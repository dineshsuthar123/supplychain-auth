import { test, expect } from '@playwright/test';
test('deployed HTTPS proxy forwards, rotates and deletes host-only refresh cookie', async ({
  page,
}) => {
  test.skip(
    !process.env.APP_URL?.startsWith('https://') ||
      !process.env.SMOKE_EMAIL ||
      !process.env.SMOKE_PASSWORD,
    'Requires a deployed HTTPS frontend and isolated smoke account credentials.',
  );
  await page.goto('/login');
  await page.getByLabel('Work email').fill(process.env.SMOKE_EMAIL!);
  await page.getByLabel('Password', { exact: true }).fill(process.env.SMOKE_PASSWORD!);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Trace', exact: true })).toBeVisible();
  const first = (await page.context().cookies()).find((c) => c.name === '__Host-lotline-refresh');
  expect(first?.secure).toBeTruthy();
  expect(first?.httpOnly).toBeTruthy();
  expect(first?.path).toBe('/');
  expect(first?.domain.startsWith('.')).toBeFalsy();
  await page.reload();
  await expect(page.getByRole('link', { name: 'Trace', exact: true })).toBeVisible();
  const rotated = (await page.context().cookies()).find((c) => c.name === '__Host-lotline-refresh');
  expect(rotated?.value).not.toBe(first?.value);
  const headers = await page.request.get('/api/v1/health');
  expect(headers.headers()['cache-control']).toContain('no-store');
  await page.locator('.workspace-button').click();
  await page.getByRole('button', { name: /Sign out ·/ }).click();
  await expect(page.getByRole('button', { name: 'Sign in →', exact: true })).toBeVisible();
  expect(
    (await page.context().cookies()).some((c) => c.name === '__Host-lotline-refresh'),
  ).toBeFalsy();
});
