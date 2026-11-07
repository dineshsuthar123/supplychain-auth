const STARTUP_BUDGET_MS = 120_000;
const PROBE_TIMEOUT_MS = 10_000;
const RETRY_DELAY_MS = 2_000;
const READY_CACHE_MS = 30_000;

let readyUntil = 0;
let startup: Promise<void> | null = null;

export async function waitForBackend(): Promise<void> {
  if (navigator.onLine === false) throw new Error('Offline');
  if (Date.now() < readyUntil) return;
  if (!startup) startup = probe().finally(() => (startup = null));
  return startup;
}

async function probe(): Promise<void> {
  const deadline = Date.now() + STARTUP_BUDGET_MS;
  while (Date.now() < deadline) {
    if (navigator.onLine === false) throw new Error('Offline');
    const controller = new AbortController();
    const timeout = setTimeout(
      () => controller.abort(),
      Math.min(PROBE_TIMEOUT_MS, deadline - Date.now()),
    );
    try {
      const response = await fetch('/api/v1/health', {
        credentials: 'omit',
        cache: 'no-store',
        signal: controller.signal,
      });
      if (response.ok && (await response.json()).status === 'UP') {
        readyUntil = Date.now() + READY_CACHE_MS;
        return;
      }
      if (response.status >= 400 && response.status < 500 && ![408, 429].includes(response.status))
        break;
    } catch {
      // A startup page, gateway error or interrupted probe is safe to retry as a GET.
    } finally {
      clearTimeout(timeout);
    }
    const remaining = deadline - Date.now();
    if (remaining > 0)
      await new Promise((resolve) => setTimeout(resolve, Math.min(RETRY_DELAY_MS, remaining)));
  }
  throw new Error('The server did not become ready within two minutes.');
}
