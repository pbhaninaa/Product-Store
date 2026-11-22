import { fetchJson } from '../fetchJson';

type Outcome = { status?: number; body?: string; event?: 'load' | 'error' | 'timeout' };

function mockXhr(outcome: Outcome) {
  const headers: Record<string, string> = {};
  class FakeXhr {
    status = 0;
    responseText = '';
    timeout = 0;
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
    ontimeout: (() => void) | null = null;
    open() {}
    setRequestHeader(name: string, value: string) {
      headers[name] = value;
    }
    send() {
      this.status = outcome.status ?? 0;
      this.responseText = outcome.body ?? '';
      const event = outcome.event ?? 'load';
      if (event === 'load') this.onload?.();
      if (event === 'error') this.onerror?.();
      if (event === 'timeout') this.ontimeout?.();
    }
  }
  (globalThis as any).XMLHttpRequest = FakeXhr;
  return headers;
}

describe('fetchJson', () => {
  it('resolves parsed JSON on 200 and sends the headers', async () => {
    const sent = mockXhr({ status: 200, body: '{"ok":true}' });
    await expect(fetchJson('https://x.test', { 'User-Agent': 'UA' })).resolves.toEqual({ ok: true });
    expect(sent['User-Agent']).toBe('UA');
  });

  // Nominatim answers blocked clients with HTTP 629, which crashed the app through fetch().
  it.each([629, 0, 403, 500])('rejects (does not throw) on status %s', async (status) => {
    mockXhr({ status, body: 'blocked' });
    await expect(fetchJson('https://x.test')).rejects.toThrow(`HTTP ${status}`);
  });

  it('rejects on invalid JSON', async () => {
    mockXhr({ status: 200, body: '<html>' });
    await expect(fetchJson('https://x.test')).rejects.toThrow('Invalid JSON');
  });

  it.each(['error', 'timeout'] as const)('rejects on network %s', async (event) => {
    mockXhr({ event });
    await expect(fetchJson('https://x.test')).rejects.toThrow();
  });
});
