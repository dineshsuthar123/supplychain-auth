import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
for (const width of [360, 768, 1024, 1440])
  test(`responsive evidence and public story at ${width}px`, async ({ page }, info) => {
    if (!process.env.DEMO_PASSWORD)
      throw new Error('Set DEMO_PASSWORD and run scripts/seed-demo.py --prefix lotline-demo first');
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const dir = path.resolve('../docs/screenshots');
    mkdirSync(dir, { recursive: true });
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    for (const route of ['/', '/login']) {
      await page.goto(route);
      await page.evaluate(() => document.fonts.ready);
      await expect(page.locator('h1')).toBeVisible();
      await page.screenshot({
        path: path.join(dir, `${route === '/' ? 'story' : 'login'}-${width}.png`),
        fullPage: true,
      });
      const failures = (await new AxeBuilder({ page }).analyze()).violations.filter((v) =>
        ['critical', 'serious'].includes(v.impact ?? ''),
      );
      expect(failures).toEqual([]);
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
      ).toBeTruthy();
    }
    await page.getByLabel('Work email').fill('lotline-demo+owner@example.test');
    await page.getByLabel('Password', { exact: true }).fill(process.env.DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
    await expect(page.locator('.canvas-caption')).toBeVisible();
    for (const [name, route] of [
      ['trace', '/app/trace'],
      ['handoffs', '/app/handoffs'],
      ['workspace', '/app/workspace'],
      ['evidence', '/app/evidence'],
    ]) {
      await page.goto(route);
      await expect(page.locator('h1')).toBeVisible();
      await expect(page.getByRole('status')).toHaveCount(0);
      await page.screenshot({ path: path.join(dir, `${name}-${width}.png`), fullPage: true });
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
      ).toBeTruthy();
      const failures = (await new AxeBuilder({ page }).analyze()).violations.filter((v) =>
        ['critical', 'serious'].includes(v.impact ?? ''),
      );
      expect(
        failures,
        JSON.stringify(failures.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) }))),
      ).toEqual([]);
    }
    await page.goto('/app/trace');
    const contributions = page.getByRole('button', { name: /consumed into.*, operation/ });
    if (await contributions.count()) {
      await contributions.first().focus();
      await page.keyboard.press('Enter');
      await expect(page.locator('.selected-contribution')).toBeVisible();
      await page.reload();
      await expect(page.locator('.selected-contribution')).toBeVisible();
    }
    await page.getByRole('button', { name: '+ Commission lot', exact: true }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Tab');
    const focused = await page.evaluate(() => document.activeElement?.closest('dialog') !== null);
    expect(focused).toBeTruthy();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).not.toBeVisible();
    expect(errors).toEqual([]);
    await info.attach('visual-width', { body: String(width), contentType: 'text/plain' });
  });
test('offline and backend warming keep explicit recoverable states', async ({ page }) => {
  await page.route('**/api/v1/auth/refresh', (route) => route.abort('failed'));
  await page.goto('/app/trace');
  await expect(page.getByRole('alert')).toContainText('backend');
  await expect(page.getByRole('button', { name: 'Try again' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Return to the story' })).toBeVisible();
  await page.context().setOffline(true);
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('alert')).toContainText('offline');
  await page.context().setOffline(false);
});
