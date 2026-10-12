import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import path from 'node:path';

for (const width of [360, 1440]) {
  test(`cinematic opening, replay, camera and synthetic case at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto('/');
    await expect(page.getByRole('button', { name: /Skip opening/ })).toBeVisible();
    // Sample an actual composed animation frame for reproducible visual review.
    await page.locator('.opening-film').evaluate((element) => {
      for (const animation of element.getAnimations({ subtree: true })) {
        animation.pause();
        animation.currentTime = 1850;
      }
    });
    await page.screenshot({ path: path.resolve(`../docs/screenshots/opening-${width}.png`) });
    await page.keyboard.press('Escape');
    await expect(page.locator('.opening-film')).toHaveCount(0);
    await page.reload();
    await expect(page.locator('.opening-film')).toHaveCount(0);
    await page.getByRole('button', { name: 'Replay opening film' }).click();
    await expect(page.getByRole('button', { name: /Skip opening/ })).toBeVisible();
    await page.getByRole('button', { name: /Skip opening/ }).click();
    await expect(page.locator('.opening-film')).toHaveCount(0);
    const scene = page.locator('.cinema-scene');
    await page.getByRole('button', { name: 'Reconcile scene', exact: true }).click();
    await expect(scene).toHaveClass(/scene-chapter-1/);
    await expect(scene.locator('.tag-receipt')).toContainText('480 / 500');
    await page.getByRole('button', { name: 'Contain scene', exact: true }).click();
    await expect(scene.locator('.tag-receipt')).toContainText('DEMO HOLD');
    await expect(scene).toHaveAttribute('data-awake', 'true');
    await page.getByRole('button', { name: 'Change camera angle' }).click();
    await expect(scene.locator('.scene-space')).toHaveAttribute('style', /rotateY\(-16deg\)/);
    await page.screenshot({ path: path.resolve(`../docs/screenshots/cinema-scene-${width}.png`) });
    await page.getByRole('button', { name: 'Select defective CF-2401 ↗', exact: true }).click();
    await page.getByRole('button', { name: 'Activate synthetic hold', exact: true }).click();
    await expect(scene).toHaveAttribute('data-awake', 'false');
    await expect(page.getByRole('status')).toContainText('1,180');
    await page.getByRole('button', { name: 'Reset the case' }).click();
    await expect(page.getByRole('status')).toHaveCount(0);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
    ).toBeTruthy();
    const serious = (await new AxeBuilder({ page }).analyze()).violations.filter((v) =>
      ['critical', 'serious'].includes(v.impact ?? ''),
    );
    expect(serious.map((v) => ({ id: v.id, targets: v.nodes.map((n) => n.target) }))).toEqual([]);
    expect(errors).toEqual([]);
  });
}

test('motion preference persists, pauses the cast, and protects password input', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Reduce motion', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-motion', 'reduced');
  await page.locator('.cinema-scene').scrollIntoViewIfNeeded();
  await expect(page.locator('.cinema-scene')).toHaveAttribute('data-awake', 'false');
  await page.reload();
  await expect(page.locator('.opening-film')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Use device motion preference' })).toBeVisible();
  await page.getByRole('link', { name: 'Sign in', exact: true }).click();
  await page.getByLabel('Password', { exact: true }).focus();
  await expect(page.locator('.auth-character .lot-character')).toHaveClass(/expression-secret/);
  await page.getByLabel('Work email').focus();
  await expect(page.locator('.auth-character .lot-character')).toHaveClass(/expression-awake/);
  expect(
    await page.locator('.thread-transition').evaluate((element) => element.getAnimations().length),
  ).toBe(0);
});

test('opening completes automatically and the next page is immediately usable', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/');
  await expect(page.locator('.opening-film')).toHaveCount(0, { timeout: 5000 });
  await page.getByRole('link', { name: /Start a workspace/ }).click();
  await expect(page.getByLabel('Work email')).toBeVisible();
  await page.getByLabel('Work email').fill('cinema-entry@example.test');
  await expect(page.getByLabel('Work email')).toHaveValue('cinema-entry@example.test');
  await page.goBack();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.locator('.opening-film')).toHaveCount(0);
});

test('page transitions run on navigation and keep the real workbench responsive', async ({
  page,
}) => {
  if (!process.env.DEMO_PASSWORD)
    throw new Error('Set DEMO_PASSWORD for the real seeded workspace');
  await page.addInitScript(() => {
    const actual = Element.prototype.animate;
    const target = window as unknown as { lotlineTransitionCount: number };
    target.lotlineTransitionCount = 0;
    Element.prototype.animate = function (keyframes, options) {
      if (this.classList.contains('thread-transition')) target.lotlineTransitionCount++;
      return actual.call(this, keyframes, options);
    };
  });
  await page.goto('/login');
  await page.getByLabel('Work email').fill('lotline-demo+owner@example.test');
  await page.getByLabel('Password', { exact: true }).fill(process.env.DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect(page.locator('.canvas-caption')).toBeVisible();
  const transitions = () =>
    page.evaluate(
      () => (window as unknown as { lotlineTransitionCount: number }).lotlineTransitionCount,
    );
  const initial = await transitions();
  await page.getByRole('link', { name: 'Handoffs', exact: true }).click();
  await expect(page.locator('.shipment-card').first()).toBeVisible();
  expect(await transitions()).toBeGreaterThan(initial);
  await page.getByRole('link', { name: 'Trace', exact: true }).click();
  await expect(page.locator('.canvas-caption')).toBeVisible();
  const beforeSelection = await transitions();
  await page.locator('.lot-node').last().click();
  await expect(page).toHaveURL(/lot=/);
  expect(await transitions()).toBe(beforeSelection);
  await page.getByRole('button', { name: 'Operator profile and workspace settings' }).click();
  await page.getByRole('button', { name: 'Motion settings · reduce animation' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-motion', 'reduced');
  await page.keyboard.press('Control+k');
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByLabel('Search lots, products and workbench destinations').fill('CF-2401-B');
  await expect(page.locator('.palette-lots')).toContainText('CF-2401-B');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
});
