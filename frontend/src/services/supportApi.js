import { apiFetch } from '@/services/api'

const API_BASE = (process.env.VUE_APP_API_BASE || 'http://localhost:8080').replace(/\/+$/, '')

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

export function fetchPendingSubscriptionProofs() {
  return apiFetch('/api/support/subscriptions/pending-proofs', { auth: true })
}

export function approveSubscriptionProof(tenantId) {
  return apiFetch(`/api/support/subscriptions/${encodeURIComponent(tenantId)}/approve-proof`, {
    method: 'POST',
    auth: true
  })
}

export function rejectSubscriptionProof(tenantId, note) {
  return apiFetch(`/api/support/subscriptions/${encodeURIComponent(tenantId)}/reject-proof`, {
    method: 'POST',
    json: { note: note || '' },
    auth: true
  })
}

export function fetchSupportPlatformBanking() {
  return apiFetch('/api/support/subscriptions/platform-banking', { auth: true })
}

export function updateSupportPlatformBanking(body) {
  return apiFetch('/api/support/subscriptions/platform-banking', {
    method: 'PUT',
    json: body,
    auth: true
  })
}

export function subscriptionProofFileUrl(tenantId) {
  return `${API_BASE}/api/support/subscriptions/${encodeURIComponent(tenantId)}/proof-file`
}
