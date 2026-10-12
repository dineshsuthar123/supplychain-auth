import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
test('stale visible recall preview requires renewed confirmation', async ({ page, request }) => {
  if (!process.env.DEMO_PASSWORD) throw new Error('Set DEMO_PASSWORD');
  const email = `stale-${randomUUID()}@example.test`;
  let access = '';
  const command = async (path: string, body: unknown) => {
    const response = await request.post('/api/v1' + path, {
      headers: {
        Origin: process.env.APP_URL ?? 'http://localhost:4173',
        'Idempotency-Key': randomUUID(),
        ...(access ? { Authorization: 'Bearer ' + access } : {}),
      },
      data: body,
    });
    expect(response.ok(), await response.text()).toBeTruthy();
    return response.json();
  };
  const session = await command('/auth/register', {
    email,
    password: process.env.DEMO_PASSWORD,
    name: 'Scope reviewer',
    workspaceName: 'Stale scope synthetic network',
    organizationName: 'Coffee manufacturer',
  });
  access = session.accessToken;
  const site = (
    await command('/workspace/sites', {
      organizationId: session.memberships[0].organization_id,
      name: 'Packing floor',
    })
  ).id;
  const product = (await command('/products', { name: 'Coffee unit', baseUnit: 'unit' })).id;
  const root = (
    await command('/inventory/commission', {
      siteId: site,
      productId: product,
      code: 'ROOT',
      quantity: '10',
      issuedOn: '2026-10-01',
    })
  ).id;
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(process.env.DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Trace', exact: true })).toBeVisible();
  await page.goto('/app/incidents?root=' + root);
  await page.getByLabel('Recall reason').fill('Defect scope review');
  await page.getByRole('button', { name: 'Preview affected scope →' }).click();
  await expect(page.locator('.scope-accounting')).toContainText('10');
  await command('/inventory/commission', {
    siteId: site,
    productId: product,
    code: 'UNRELATED',
    quantity: '2',
    issuedOn: '2026-10-01',
  });
  await page.getByRole('checkbox', { name: /I reviewed this scope/ }).check();
  await page.getByRole('button', { name: 'Activate recall & hold stock' }).click();
  await expect(page.getByRole('alert')).toContainText('workspace changed');
  await expect(page.getByRole('checkbox', { name: /I reviewed this scope/ })).not.toBeChecked();
  await expect(page.getByRole('button', { name: 'Activate recall & hold stock' })).toBeDisabled();
  await expect(page.locator('.scope-accounting')).toContainText('2');
  await page.getByRole('checkbox', { name: /I reviewed this scope/ }).check();
  await page.getByRole('button', { name: 'Activate recall & hold stock' }).click();
  await expect(page.locator('.incident-workbench .status-active')).toBeVisible();
});
