import { CONFIG } from '../config';

export type ApiError = Error & { status?: number; body?: unknown };

function normalizeApiBase(raw: string): string {
  let b = String(raw || '')
    .trim()
    .replace(/\/+$/, '');
  if (!b) return 'http://localhost:8080';
  if (!/^https?:\/\//i.test(b)) {
    b = 'https://' + b.replace(/^\/+/, '');
  }
  return b.replace(/\/+$/, '');
}

export function getApiBase(): string {
  return normalizeApiBase(CONFIG.API_BASE_URL);
}

export function resolveMediaUrl(raw: unknown): string {
  const API_BASE = getApiBase();
  const u = String(raw == null ? '' : raw).trim();
  if (!u) return '';
  if (/^(https?:|data:|blob:)/i.test(u)) return u;
  if (u.startsWith('//')) return `https:${u}`;
  if (u.startsWith('/')) return `${API_BASE}${u}`;
  return `${API_BASE}/${u.replace(/^\/+/, '')}`;
}

function buildUrl(path: string): string {
  const API_BASE = getApiBase();
  const p = String(path || '');
  if (!p.startsWith('/')) return `${API_BASE}/${p}`;
  return `${API_BASE}${p}`;
}

type TokenGetter = () => Promise<string>;
type OnUnauthorized = () => void | Promise<void>;

let getTokenAsync: TokenGetter = async () => '';
let onUnauthorized: OnUnauthorized = () => undefined;

export function configureApiClient(opts: {
  getToken: TokenGetter;
  onUnauthorized?: OnUnauthorized;
}) {
  getTokenAsync = opts.getToken;
  if (opts.onUnauthorized) onUnauthorized = opts.onUnauthorized;
}

async function parseError(res: Response): Promise<never> {
  let body: any = null;
  try {
    body = await res.json();
  } catch {
    // ignore
  }
  const msg =
    (body && (body.error || body.message)) ||
    `${res.status} ${res.statusText || 'Request failed'}`;
  const e = new Error(String(msg)) as ApiError;
  e.status = res.status;
  e.body = body;
  throw e;
}

export async function apiFetch<T = any>(
  path: string,
  {
    method = 'GET',
    json,
    auth = false,
  }: { method?: string; json?: unknown; auth?: boolean } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (json !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = await getTokenAsync();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetch(buildUrl(path), {
      method,
      headers,
      body: json !== undefined ? JSON.stringify(json) : undefined,
    });
  } catch {
    const e = new Error(
      'Cannot reach the API right now. Check your connection, or wait for the backend to finish deploying.',
    ) as ApiError;
    e.status = 0;
    throw e;
  }

  if (!res.ok) {
    if (auth && res.status === 401) {
      await onUnauthorized();
    }
    await parseError(res);
  }
  if (res.status === 204) return null as T;
  return (await res.json()) as T;
}

export async function apiFetchMultipart<T = any>(
  path: string,
  {
    method = 'POST',
    formData,
    auth = true,
  }: { method?: string; formData: FormData; auth?: boolean },
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (auth) {
    const token = await getTokenAsync();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(buildUrl(path), { method, headers, body: formData });
  if (!res.ok) {
    if (auth && res.status === 401) {
      await onUnauthorized();
    }
    await parseError(res);
  }
  if (res.status === 204) return null as T;
  return (await res.json()) as T;
}
