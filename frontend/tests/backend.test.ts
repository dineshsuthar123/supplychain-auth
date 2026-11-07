import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

beforeEach(() => {
  vi.resetModules();
  vi.useFakeTimers();
  vi.stubGlobal('navigator', { onLine: true });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const up = () => Response.json({ status: 'UP', product: 'Lotline' });

describe('authentication waits for a sleeping backend without replaying commands', () => {
  it('waits beyond the old 25-second limit, then sends signup exactly once with its key', async () => {
    const started = Date.now();
    const fetch = vi.fn(async (url: string) => {
      if (url.endsWith('/health'))
        return Date.now() - started < 40_000 ? new Response('Starting', { status: 503 }) : up();
      return Response.json({ accepted: true });
    });
    vi.stubGlobal('fetch', fetch);
    const { api } = await import('../src/lib/api');
    const pending = api.post('/auth/register', { email: 'test@example.test' }, 'unchanged-key');
    await vi.advanceTimersByTimeAsync(30_000);
    expect(fetch.mock.calls.every(([url]) => url.endsWith('/health'))).toBe(true);
    await vi.advanceTimersByTimeAsync(12_000);
    await expect(pending).resolves.toEqual({ accepted: true });
    const commands = fetch.mock.calls.filter(([url]) => url.endsWith('/register'));
    expect(commands).toHaveLength(1);
    expect(commands[0]).toEqual([
      '/api/v1/auth/register',
      expect.objectContaining({
        headers: expect.objectContaining({ 'Idempotency-Key': 'unchanged-key' }),
      }),
    ]);
  });

  it('shares concurrent startup probes and rechecks after its short readiness cache expires', async () => {
    const fetch = vi.fn(async () => up());
    vi.stubGlobal('fetch', fetch);
    const { waitForBackend } = await import('../src/lib/backend');
    await Promise.all([waitForBackend(), waitForBackend()]);
    await waitForBackend();
    expect(fetch).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(31_000);
    await waitForBackend();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('rejects a startup HTML page and waits for actual API readiness', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response('<html>Starting</html>'))
      .mockImplementation(async () => up());
    vi.stubGlobal('fetch', fetch);
    const { waitForBackend } = await import('../src/lib/backend');
    const pending = waitForBackend();
    await vi.advanceTimersByTimeAsync(2_000);
    await pending;
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('stops after two minutes without sending the signup command', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>(
      async () => new Response('Starting', { status: 503 }),
    );
    vi.stubGlobal('fetch', fetch);
    const { api } = await import('../src/lib/api');
    const pending = expect(api.post('/auth/register', {}, 'same-key')).rejects.toMatchObject({
      code: 'BACKEND_WARMING',
      message: expect.stringContaining('has not been sent'),
    });
    await vi.advanceTimersByTimeAsync(120_000);
    await pending;
    expect(fetch.mock.calls.every((args) => args[0] === '/api/v1/health')).toBe(true);
  });

  it('bounds hanging probes and never submits credentials while the server is unreachable', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>(
      async (_url, options) =>
        new Promise<Response>((_resolve, reject) => {
          options?.signal?.addEventListener('abort', () => reject(new Error('Aborted')));
        }),
    );
    vi.stubGlobal('fetch', fetch);
    const { api } = await import('../src/lib/api');
    const pending = expect(api.post('/auth/login', {})).rejects.toMatchObject({
      code: 'BACKEND_WARMING',
    });
    await vi.advanceTimersByTimeAsync(120_000);
    await pending;
    expect(fetch.mock.calls.every(([url]) => url === '/api/v1/health')).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not replay an authentication command after losing its response', async () => {
    const fetch = vi.fn(async (url: string) => {
      if (url.endsWith('/health')) return up();
      throw new Error('Connection lost after sending');
    });
    vi.stubGlobal('fetch', fetch);
    const { api } = await import('../src/lib/api');
    await expect(api.post('/auth/register', {}, 'preserved-key')).rejects.toMatchObject({
      message: expect.stringContaining('may have been accepted'),
    });
    expect(fetch.mock.calls.filter(([url]) => url.endsWith('/register'))).toHaveLength(1);
  });

  it('fails immediately when offline, even when readiness was recently cached', async () => {
    const fetch = vi.fn(async () => up());
    vi.stubGlobal('fetch', fetch);
    const { waitForBackend } = await import('../src/lib/backend');
    await waitForBackend();
    vi.stubGlobal('navigator', { onLine: false });
    const { api } = await import('../src/lib/api');
    await expect(api.post('/auth/login', {})).rejects.toMatchObject({ code: 'OFFLINE' });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
