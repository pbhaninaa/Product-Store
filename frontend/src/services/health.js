import { apiFetch, getApiBase } from '@/services/api'

/**
 * Lightweight backend connectivity check (public GET /api/health).
 * Resolves { ok: true, ... } on success; throws on network / non-OK response.
 */
export async function checkApiHealth() {
  const res = await apiFetch('/api/health', { method: 'GET', auth: false })
  if (!res || res.ok !== true) {
    throw new Error('API health check failed.')
  }
  return res
}

export function apiBaseLabel() {
  return getApiBase()
}
