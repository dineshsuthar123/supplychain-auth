import { test, expect } from '@playwright/test';
import { writeFileSync, readFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
test('measure public entry vitals under documented desktop throttling', async ({
  page,
  context,
}) => {
  const metrics: Record<string, number> = {};
  await page.exposeFunction('lotlineMetric', (m: { name: string; value: number }) => {
    metrics[m.name] = m.value;
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  const protocol = await context.newCDPSession(page);
  await protocol.send('Network.enable');
  await protocol.send('Network.emulateNetworkConditions', {
    offline: false,
    latency: 150,
    downloadThroughput: 1_600_000 / 8,
    uploadThroughput: 750_000 / 8,
  });
  await protocol.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await page.goto('/');
  await page.addScriptTag({
    content: readFileSync(path.resolve('node_modules/web-vitals/dist/web-vitals.iife.js'), 'utf8'),
  });
  await page.evaluate(() => {
    const w = window as unknown as {
      webVitals: {
        onLCP: (callback: (metric: unknown) => void, options: unknown) => void;
        onINP: (callback: (metric: unknown) => void, options: unknown) => void;
        onCLS: (callback: (metric: unknown) => void, options: unknown) => void;
      };
      lotlineMetric: (m: unknown) => void;
    };
    for (const name of ['onLCP', 'onINP', 'onCLS'] as const)
      w.webVitals[name]((m) => w.lotlineMetric(m), { reportAllChanges: true });
  });
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await page.getByRole('button', { name: 'Select defective CF-2401 ↗', exact: true }).click();
  await page.getByRole('button', { name: 'Activate synthetic hold', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('1,180');
  await page.waitForTimeout(500);
  await page.goto('about:blank');
  const report = {
    conditions: {
      browser: 'Playwright Chromium 1.58.2',
      viewport: '1440×1000',
      cpuSlowdown: 4,
      latencyMs: 150,
      downloadMbps: 1.6,
      uploadMbps: 0.75,
      cache: 'fresh browser context',
      page: 'gzip-compressed production public entry on loopback',
      samples: 1,
    },
    metrics: { LCP_ms: metrics.LCP ?? null, INP_ms: metrics.INP ?? null, CLS: metrics.CLS ?? null },
    limits:
      'One lab sample with two real synthetic-walkthrough interactions. Not a field percentile or a deployed Vercel/Render measurement.',
  };
  mkdirSync(path.resolve('../artifacts'), { recursive: true });
  writeFileSync(
    path.resolve('../artifacts/web-vitals.json'),
    JSON.stringify(report, null, 2) + '\n',
  );
  expect(metrics.LCP).toBeGreaterThan(0);
  expect(metrics.INP).toBeGreaterThanOrEqual(0);
});
