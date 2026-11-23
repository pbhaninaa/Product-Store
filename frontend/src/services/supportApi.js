import { apiFetch, getApiBase } from '@/services/api'

export function fetchSupportOverview() {
  return apiFetch('/api/support/overview', { auth: true })
}

export function fetchSupportMe() {
  return apiFetch('/api/support/me', { auth: true })
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

export function resetMerchantOwnerPassword(slug, password) {
  return apiFetch(`/api/support/merchants/${encodeURIComponent(slug)}/reset-owner-password`, {
    method: 'POST',
    json: { password },
    auth: true
  })
}

export function fetchPendingSubscriptionProofs() {
  return apiFetch('/api/support/subscriptions/pending-proofs', { auth: true })
}

export function fetchSupportPlans() {
  return apiFetch('/api/support/subscriptions/plans', { auth: true })
}

export function updateSupportPlan(tier, body) {
  return apiFetch(`/api/support/subscriptions/plans/${encodeURIComponent(tier)}`, {
    method: 'PUT',
    json: body,
    auth: true
  })
}

export function fetchMerchantSubscriptions() {
  return apiFetch('/api/support/subscriptions', { auth: true })
}

/** @deprecated Manual subscription activation is retired (API returns 410). */
export function forceActivateSubscription(tenantId, tier) {
  return apiFetch(`/api/support/subscriptions/${encodeURIComponent(tenantId)}/activate`, {
    method: 'POST',
    json: tier ? { tier } : {},
    auth: true
  })
}


export function createPlatformSupportUser({ email, password }) {
  return apiFetch('/api/platform-admin/support-users', {
    method: 'POST',
    auth: true,
    json: { email, password }
  })
}

export function subscriptionProofFileUrl(tenantId) {
  return `${getApiBase()}/api/support/subscriptions/${encodeURIComponent(tenantId)}/proof-file`
}

export function fetchShadowMerchants(q) {
  const qs = q && String(q).trim() ? `?q=${encodeURIComponent(String(q).trim())}` : ''
  return apiFetch(`/api/support/shadow/merchants${qs}`, { auth: true })
}

export function mintShadowToken(slug) {
  return apiFetch('/api/support/shadow/token', { method: 'POST', json: { slug }, auth: true })
}

export function fetchSupportTickets(status) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiFetch(`/api/support/tickets${qs}`, { auth: true })
}

export function resolveSupportTicket(id, note) {
  return apiFetch(`/api/support/tickets/${encodeURIComponent(id)}/resolve`, {
    method: 'POST',
    json: { note: note || '' },
    auth: true
  })
}

export function fetchSupportNotifications() {
  return apiFetch('/api/support/notifications', { auth: true })
}

export function markSupportNotificationRead(id) {
  return apiFetch(`/api/support/notifications/${encodeURIComponent(id)}/read`, {
    method: 'POST',
    auth: true
  })
}

export function markAllSupportNotificationsRead() {
  return apiFetch('/api/support/notifications/read-all', { method: 'POST', auth: true })
}

export function fetchPlatformFeatures() {
  return apiFetch('/api/support/platform-features', { auth: true })
}

export function setPlatformFeature(key, enabled) {
  return apiFetch(`/api/support/platform-features/${encodeURIComponent(key)}`, {
    method: 'PUT',
    json: { enabled },
    auth: true
  })
}

export function fetchSupportAudit() {
  return apiFetch('/api/support/audit', { auth: true })
}

export function fetchSupportStaff() {
  return apiFetch('/api/support/staff', { auth: true })
}

export function createSupportStaff(body) {
  return apiFetch('/api/support/staff', { method: 'POST', json: body, auth: true })
}

export function suspendSupportStaff(userId) {
  return apiFetch(`/api/support/staff/${encodeURIComponent(userId)}/suspend`, {
    method: 'POST',
    auth: true
  })
}

export function activateSupportStaff(userId) {
  return apiFetch(`/api/support/staff/${encodeURIComponent(userId)}/activate`, {
    method: 'POST',
    auth: true
  })
}

export function resetSupportStaffPassword(userId, password) {
  return apiFetch(`/api/support/staff/${encodeURIComponent(userId)}/reset-password`, {
    method: 'POST',
    json: { password },
    auth: true
  })
}

export function updateSupportStaffPermissions(userId, permissions) {
  return apiFetch(`/api/support/staff/${encodeURIComponent(userId)}/permissions`, {
    method: 'PUT',
    json: { permissions },
    auth: true
  })
}

/** Platform-wide user directory. `page` is zero-based; empty filters are left out. */
export function fetchSupportUsers({ q, role, store, status, page, size } = {}) {
  const params = new URLSearchParams()
  const add = (key, value) => {
    const v = value == null ? '' : String(value).trim()
    if (v) params.set(key, v)
  }
  add('q', q)
  add('role', role)
  add('store', store)
  add('status', status)
  add('page', page)
  add('size', size)
  const qs = params.toString()
  return apiFetch(`/api/support/users${qs ? `?${qs}` : ''}`, { auth: true })
}

export function fetchSupportUserStores() {
  return apiFetch('/api/support/users/stores', { auth: true })
}

export function suspendSupportUser(userId) {
  return apiFetch(`/api/support/users/${encodeURIComponent(userId)}/suspend`, { method: 'POST', auth: true })
}

export function reactivateSupportUser(userId) {
  return apiFetch(`/api/support/users/${encodeURIComponent(userId)}/reactivate`, { method: 'POST', auth: true })
}

export function sendSupportUserPasswordReset(userId) {
  return apiFetch(`/api/support/users/${encodeURIComponent(userId)}/send-password-reset`, {
    method: 'POST',
    auth: true
  })
}

export function fetchSupportHelpContact() {
  return apiFetch('/api/support/help-contact', { auth: true })
}

export function updateSupportHelpContact(body) {
  return apiFetch('/api/support/help-contact', { method: 'PUT', json: body, auth: true })
}

export function fetchSupportOrders() {
  return apiFetch('/api/support/orders', { auth: true })
}

export function fetchSupportBookings() {
  return apiFetch('/api/support/bookings', { auth: true })
}

export function fetchSupportDangerStatus() {
  return apiFetch('/api/support/danger', { auth: true })
}

export function wipeSupportMerchants(confirm) {
  return apiFetch('/api/support/danger/wipe-merchants', {
    method: 'POST',
    json: { confirm },
    auth: true
  })
}
