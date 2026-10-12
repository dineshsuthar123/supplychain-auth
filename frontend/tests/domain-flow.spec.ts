import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
type Accepted = { id: string; sequence: number; invitationPath?: string };
type Graph = { lots: { id: string; code: string }[]; sequence: number };
const password = process.env.DEMO_PASSWORD;
test.beforeEach(() => {
  if (!password) throw new Error('Set DEMO_PASSWORD explicitly for isolated E2E accounts');
});
async function post<T>(
  request: APIRequestContext,
  url: string,
  body: unknown,
  token?: string,
): Promise<T> {
  const response = await request.post(`/api/v1${url}`, {
    headers: {
      Origin: process.env.APP_URL ?? 'http://localhost:4173',
      'Idempotency-Key': randomUUID(),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    data: body,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}
async function login(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Trace', exact: true })).toBeVisible();
}
async function commission(page: Page, code: string, quantity: string) {
  await page.goto('/app/trace');
  await page.getByRole('button', { name: '+ Commission lot', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('External lot code').fill(code);
  await dialog.getByLabel(/Exact quantity/).fill(quantity);
  await dialog.getByRole('button', { name: 'Commission lot', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.locator('.dock-code')).toHaveText(code);
  return new URL(page.url()).searchParams.get('lot')!;
}
async function split(page: Page, lot: string, quantity: string, outputs: [string, string][]) {
  await page.goto('/app/trace?lot=' + lot);
  await page.getByRole('button', { name: 'Split / merge / repackage' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Input quantity 1').fill(quantity);
  for (let i = 0; i < outputs.length; i++) {
    await dialog.getByLabel(`Output code ${i + 1}`).fill(outputs[i][0]);
    await dialog.getByLabel(`Output quantity ${i + 1}`).fill(outputs[i][1]);
  }
  await expect(dialog.getByText('✓ Balanced')).toBeVisible();
  await dialog.getByRole('button', { name: 'Consume inputs & create output lots' }).click();
  await expect(dialog).not.toBeVisible();
}
async function prepare(
  page: Page,
  lot: string,
  quantity: string,
  destination: string,
  reference: string,
) {
  await page.goto('/app/trace?lot=' + lot);
  await page.getByRole('button', { name: 'Create handoff ↗', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Receiving partner / site').selectOption(destination);
  await dialog.getByLabel(/Exact quantity/).fill(quantity);
  await dialog.getByLabel('Shipment reference').fill(reference);
  await dialog.getByRole('button', { name: 'Create handoff draft', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole('button', { name: 'Confirm dispatch', exact: true })).toBeVisible();
  const id = page.url().split('/').at(-1)!;
  await page.getByRole('button', { name: 'Confirm dispatch', exact: true }).click();
  await expect(
    page.getByText('Awaiting the destination organization’s receipt.', { exact: false }),
  ).toBeVisible();
  return id;
}
test('real coffee workflow: genealogy, independent short receipt, recall, blocked dispatch, recovery, loss, closure and evidence', async ({
  page,
  browser,
  request,
}, info) => {
  const email = `e2e-${randomUUID()}@example.test`;
  const owner = await post<{
    accessToken: string;
    memberships: { organization_id: string; workspace_id: string }[];
  }>(request, '/auth/register', {
    email,
    password,
    name: 'Mira Vale',
    workspaceName: 'E2E coffee network',
    organizationName: 'Meridian Coffee Works',
  });
  const token = owner.accessToken,
    org = owner.memberships[0].organization_id;
  const source = (
    await post<Accepted>(
      request,
      '/workspace/sites',
      { organizationId: org, name: 'Packing floor' },
      token,
    )
  ).id;
  await post(
    request,
    '/products',
    { name: 'Coffee 250 g · packaged units', baseUnit: 'unit' },
    token,
  );
  const partners = [];
  for (let i = 0; i < 4; i++) {
    const organization = (
      await post<Accepted>(
        request,
        '/workspace/organizations',
        { name: `Partner ${i + 1} with an intentionally long organization name` },
        token,
      )
    ).id;
    const site = (
      await post<Accepted>(
        request,
        '/workspace/sites',
        { organizationId: organization, name: `Receiving site ${i + 1}` },
        token,
      )
    ).id;
    const email = `partner-${randomUUID()}@example.test`;
    const inv = await post<Accepted>(
      request,
      '/workspace/invitations',
      {
        organizationId: organization,
        email,
        role: 'PARTNER_OPERATOR',
        recallManager: false,
        siteIds: [site],
      },
      token,
    );
    await post(request, '/auth/accept-invitation', {
      token: inv.invitationPath!.split('token=')[1],
      email,
      password,
      name: `Receiver ${i + 1}`,
    });
    const context = await browser.newContext();
    partners.push({ context, page: await context.newPage(), email, site });
  }
  const uncaught: string[] = [];
  page.on('pageerror', (e) => uncaught.push(e.message));
  const read = async () => {
    const response = await request.get('/api/v1/trace', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.ok()).toBeTruthy();
    return response.json() as Promise<Graph>;
  };
  await login(page, email);
  const root = await commission(page, 'CF-2401', '1200');
  await split(page, root, '1200', [
    ['CF-2401-A', '700'],
    ['CF-2401-B', '500'],
  ]);
  let graph = await read();
  const a = graph.lots.find((l) => l.code === 'CF-2401-A')!.id,
    b = graph.lots.find((l) => l.code === 'CF-2401-B')!.id;
  await split(page, a, '700', [
    ['CF-2401-A1', '350'],
    ['CF-2401-A2', '350'],
  ]);
  graph = await read();
  const a1 = graph.lots.find((l) => l.code === 'CF-2401-A1')!.id,
    a2 = graph.lots.find((l) => l.code === 'CF-2401-A2')!.id;
  const unrelated = await commission(
    page,
    'CF-2402-UNRELATED-LONG-CODE-KEEP-THIS-READABLE-2026',
    '200',
  );
  const lots = [a1, a2, b, unrelated],
    sent = ['350', '350', '500', '200'],
    received = ['350', '350', '480', '200'],
    transfers: string[] = [];
  for (let i = 0; i < 4; i++) {
    const t = await prepare(page, lots[i], sent[i], partners[i].site, `Packing slip ${i + 1}`);
    transfers.push(t);
    const p = partners[i].page;
    await login(p, partners[i].email);
    await p.goto('/app/handoffs/' + t);
    await expect(p.getByRole('heading', { name: 'Count what arrived.' })).toBeVisible();
    await p.getByLabel(/Received quantity/).fill(received[i]);
    if (i === 2)
      await p.getByLabel(/Exception reason/).fill('20 sealed packages absent from receiving count');
    if (i === 2) {
      const dir = path.resolve('../docs/screenshots');
      mkdirSync(dir, { recursive: true });
      for (const width of [360, 768, 1024, 1440]) {
        await p.setViewportSize({ width, height: 1000 });
        await p.screenshot({ path: path.join(dir, `receipt-${width}.png`), fullPage: true });
        expect(
          await p.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
        ).toBeTruthy();
        expect(
          (await new AxeBuilder({ page: p }).analyze()).violations.filter((v) =>
            ['critical', 'serious'].includes(v.impact ?? ''),
          ),
        ).toEqual([]);
      }
    }
    await p.getByRole('button', { name: 'Accept actual received stock' }).click();
    await expect(p.locator('.receipt-fact')).toContainText(`${received[i]} accepted`);
    if (i === 2) {
      await expect(p.locator('.claim-table')).toContainText('480');
      await expect(p.locator('.claim-table')).toContainText('20');
    }
  }
  await page.goto('/app/incidents?root=' + root);
  await page.getByLabel('Recall reason', { exact: true }).fill('Supplier packaging seal defect');
  await page.getByRole('button', { name: 'Preview affected scope →' }).click();
  await expect(page.locator('.scope-accounting')).toContainText('1180');
  await expect(page.locator('.scope-accounting')).toContainText('20');
  await expect(page.locator('.scope-accounting')).toContainText('200');
  await page.getByRole('checkbox', { name: /I reviewed this scope/ }).check();
  await page.getByRole('button', { name: 'Activate recall & hold stock' }).click();
  await expect(page.locator('.incident-workbench')).toContainText('Closure stays blocked');
  for (const width of [360, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 1000 });
    await page.screenshot({
      path: path.resolve(`../docs/screenshots/incident-${width}.png`),
      fullPage: true,
    });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
    ).toBeTruthy();
    expect(
      (await new AxeBuilder({ page }).analyze()).violations.filter((v) =>
        ['critical', 'serious'].includes(v.impact ?? ''),
      ),
    ).toEqual([]);
  }
  const recall = page.url().split('/').at(-1)!;
  const blockedPage = partners[2].page;
  await blockedPage.goto('/app/trace?lot=' + b);
  await blockedPage.getByRole('button', { name: 'Create handoff ↗', exact: true }).click();
  const blocked = blockedPage.getByRole('dialog');
  await blocked.getByLabel('Receiving partner / site').selectOption(source);
  await blocked.getByLabel(/Exact quantity/).fill('1');
  await blocked.getByLabel('Shipment reference').fill('Blocked dispatch under recall');
  await blocked.getByRole('button', { name: 'Create handoff draft' }).click();
  await expect(blocked.getByRole('alert')).toContainText('held by incident');
  await blocked.getByRole('button', { name: 'Close dialog' }).click();
  await prepare(partners[3].page, unrelated, '1', source, 'Unaffected stock remains movable');
  for (let i = 0; i < 3; i++) {
    const p = partners[i].page;
    await p.goto('/app/incidents/' + recall);
    await p
      .getByLabel('Acknowledgment note')
      .fill('Notice received independently; stock recovery follows');
    await p.getByRole('button', { name: 'Acknowledge notice for my organization' }).click();
    await expect(p.locator('.incident-workbench table')).toContainText('Acknowledged');
    const code = ['CF-2401-A1', 'CF-2401-A2', 'CF-2401-B'][i];
    await p.getByText(`Record physical recovery · ${code}`, { exact: true }).click();
    await p.getByLabel(`Recovery quantity · ${code}`).fill(received[i]);
    await p.getByLabel(`Recovery reason · ${code}`).fill('Physically counted and segregated');
    await p.getByLabel(`Recovery evidence · ${code}`).fill(`Recovery register ${i + 1}`);
    await p.getByRole('button', { name: 'Record recovery', exact: true }).click();
    await expect(
      p
        .locator('.incident-workbench tbody tr')
        .filter({ has: p.locator('td:nth-child(2)', { hasText: received[i] + ' unit' }) })
        .first()
        .locator('td')
        .nth(3),
    ).toHaveText(received[i]);
  }
  await page.goto('/app/incidents/' + recall);
  await page
    .getByLabel('Closure justification')
    .fill('All received stock recovered; record closure');
  await page.getByRole('button', { name: 'Record authorized closure' }).click();
  await expect(page.getByRole('alert')).toContainText('Responses and exact quantity accounting');
  await page.goto('/app/handoffs/' + transfers[2]);
  await page
    .getByRole('combobox', { name: 'Disposition', exact: true })
    .selectOption('ACCOUNTABLE_LOSS');
  await page.getByLabel('Resolution quantity').fill('20');
  await page
    .getByLabel('Resolution reason')
    .fill('Manager accepts missing sealed packages after recorded investigation');
  await page.getByLabel('Resolution evidence reference').fill('Investigation report 82');
  await page.getByRole('button', { name: 'Record accountable resolution' }).click();
  await expect(page.locator('.accepted-resolution')).toContainText('accountable loss');
  await page.goto('/app/incidents/' + recall);
  await expect(page.locator('.closure-state')).toContainText('satisfy the closure policy');
  await page
    .getByLabel('Closure justification')
    .fill('1180 recovered; 20 accountable loss accepted; partner responses recorded');
  await page.getByRole('button', { name: 'Record authorized closure' }).click();
  await expect(page.locator('.incident-workbench .status-closed')).toBeVisible();
  const downloading = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export evidence ↗', exact: true }).click();
  const download = await downloading;
  const output = info.outputPath('lotline-evidence.zip');
  await download.saveAs(output);
  expect(
    execFileSync(
      process.platform === 'win32' ? 'python' : 'python3',
      [path.resolve('../scripts/verify-evidence.py'), output],
      { encoding: 'utf8' },
    ),
  ).toContain('Verified 7 files');
  graph = await read();
  await page.goto(`/app/trace?lot=${root}&at=${graph.sequence - 1}`);
  await expect(page.getByText(/Historical view · sequence/)).toBeVisible();
  await expect(page.getByRole('button', { name: '+ Commission lot', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Create handoff ↗', exact: true })).toBeDisabled();
  await page.reload();
  await expect(page.getByText(/Historical view · sequence/)).toBeVisible();
  await expect(page.locator('.dock-code')).toHaveText('CF-2401');
  expect(
    await page.evaluate(() =>
      Object.keys(localStorage).filter((k) => /token|session|user/i.test(k)),
    ),
  ).toEqual([]);
  const failures = (await new AxeBuilder({ page }).analyze()).violations.filter((v) =>
    ['critical', 'serious'].includes(v.impact ?? ''),
  );
  expect(failures.map((v) => ({ id: v.id, targets: v.nodes.map((n) => n.target) }))).toEqual([]);
  expect(uncaught).toEqual([]);
  await page.screenshot({ path: info.outputPath('real-historical-trace.png'), fullPage: true });
  for (const partner of partners) await partner.context.close();
});
test('owner onboarding with keyboard, empty state and cookie refresh', async ({ page }) => {
  await page.goto('/create');
  await page.getByLabel('Your name').fill('New owner');
  await page.getByLabel('Work email').fill(`onboard-${randomUUID()}@example.test`);
  await page.getByLabel('Password', { exact: true }).fill(password!);
  await page.getByLabel('Workspace name').fill('Fresh trace network');
  await page.getByLabel('Your organization', { exact: true }).fill('Fresh manufacturer');
  await page.getByRole('button', { name: 'Create workspace →' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByText('Your first lot starts the thread.')).toBeVisible();
  await page.reload();
  await expect(page.getByText('Your first lot starts the thread.')).toBeVisible();
  expect(
    (await page.context().cookies()).some(
      (c) => c.name === 'lotline-refresh' && c.httpOnly && c.path === '/',
    ),
  ).toBeTruthy();
});
