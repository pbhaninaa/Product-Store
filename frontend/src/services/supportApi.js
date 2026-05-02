import { apiFetch } from '@/services/api'

export function fetchSupportOverview() {
  return apiFetch('/api/support/overview', { auth: true })
}

export function fetchSupportMerchants(q) {
  const qs = q && String(q).trim() ? `?q=${encodeURIComponent(String(q).trim())}` : ''
  return apiFetch(`/api/support/merchants${qs}`, { auth: true })
}

export function fetchSupportMerchantDetail(slug) {
  const s = String(slug || '').trim()
  if (!s) return Promise.reject(new Error('slug_required'))
  return apiFetch(`/api/support/merchants/${encodeURIComponent(s)}`, { auth: true })
}

export function createSupportMerchant(body) {
  return apiFetch('/api/support/merchants', { method: 'POST', json: body, auth: true })
}

export function updateSupportMerchant(slug, body) {
  const s = String(slug || '').trim()
  if (!s) return Promise.reject(new Error('slug_required'))
  return apiFetch(`/api/support/merchants/${encodeURIComponent(s)}`, { method: 'PUT', json: body, auth: true })
}

export function deleteSupportMerchant(slug) {
  const s = String(slug || '').trim()
  if (!s) return Promise.reject(new Error('slug_required'))
  return apiFetch(`/api/support/merchants/${encodeURIComponent(s)}`, { method: 'DELETE', auth: true })
}
