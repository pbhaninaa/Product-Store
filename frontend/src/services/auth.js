import { apiFetch, setToken, notifyAuthChanged, AUTH_CHANGE_EVENT, clearAuthStorage } from '@/services/api'
import { normalizeShopType } from '@/services/shopType'

const TENANT_CTX_KEY = 'ps_merchant_tenant_context'

function decodeJwtPayload(token) {
  const parts = String(token || '').split('.')
  if (parts.length !== 3) return null
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = atob(b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '='))
    return JSON.parse(json)
  } catch {
    return null
  }
}

function isMerchantRoles(roles) {
  if (!Array.isArray(roles)) return false
  return roles.some((r) => r === 'MERCHANT_OWNER' || r === 'MERCHANT_STAFF')
}

/**
 * Platform admin / support users without a merchant role must not use merchant-scoped
 * `/api/m/:slug/admin/...` APIs or URL slugs — they use `/support` and `/api/support/...`.
 */
export function isSupportOrPlatformOnlyUser(u) {
  if (!u || !Array.isArray(u.roles)) return false
  const merchant = u.roles.includes('MERCHANT_OWNER') || u.roles.includes('MERCHANT_STAFF')
  const elevated = u.roles.includes('SUPPORT_USER') || u.roles.includes('PLATFORM_ADMIN')
  return elevated && !merchant
}

/** Normalized tenant snapshot persisted after login/register (merchant only). */
export function getMerchantTenantContext() {
  try {
    const raw = localStorage.getItem(TENANT_CTX_KEY)
    if (!raw) return null
    const o = JSON.parse(raw)
    if (!o || !o.slug || typeof o.slug !== 'string') return null
    return {
      id: String(o.id || ''),
      slug: String(o.slug).trim(),
      name: String(o.name || ''),
      shopType: normalizeShopType(o.shopType)
    }
  } catch {
    return null
  }
}

export function clearMerchantTenantContext() {
  try {
    localStorage.removeItem(TENANT_CTX_KEY)
  } catch {
    // ignore
  }
  notifyAuthChanged()
}

function isJwtExpired(payload) {
  if (!payload || payload.exp == null) return false
  const expMs = Number(payload.exp) * 1000
  if (!Number.isFinite(expMs)) return false
  // Small skew so we clear slightly before the server rejects.
  return expMs <= Date.now() + 5_000
}

function wipeExpiredSession() {
  clearAuthStorage()
  notifyAuthChanged()
}

function persistMerchantTenantFromLoginResponse(res, jwtPayload) {
  const roles = Array.isArray(res && res.roles)
    ? res.roles
    : Array.isArray(jwtPayload && jwtPayload.roles)
      ? jwtPayload.roles
      : []
  const t = res && res.tenant
  if (!isMerchantRoles(roles) || !t || typeof t !== 'object') {
    clearMerchantTenantContext()
    return
  }
  const slug = String(t.slug || '').trim()
  if (!slug) {
    clearMerchantTenantContext()
    return
  }
  const payload = {
    id: String(t.id || ''),
    slug,
    name: String(t.name || ''),
    shopType: normalizeShopType(t.shopType)
  }
  try {
    localStorage.setItem(TENANT_CTX_KEY, JSON.stringify(payload))
    notifyAuthChanged()
  } catch {
    // ignore
  }
}

export function getSessionUser() {
  let token = ''
  try {
    token = localStorage.getItem('ps_token') || ''
  } catch {
    token = ''
  }
  if (!token) return null
  const payload = decodeJwtPayload(token)
  if (!payload || !payload.sub) {
    wipeExpiredSession()
    return null
  }
  if (isJwtExpired(payload)) {
    wipeExpiredSession()
    return null
  }
  const roles = Array.isArray(payload.roles) ? payload.roles : []
  const jwtTenantSlug = String(payload.tenant || '').trim()
  const jwtTenantId = String(payload.tenantId || '').trim()

  let ctx = getMerchantTenantContext()
  if (isMerchantRoles(roles) && jwtTenantSlug && ctx && ctx.slug !== jwtTenantSlug) {
    clearMerchantTenantContext()
    ctx = null
  }
  if (!isMerchantRoles(roles)) {
    ctx = null
  }

  let tenantSlug = jwtTenantSlug
  let tenantDetail = null
  if (isMerchantRoles(roles)) {
    if (jwtTenantSlug) {
      if (ctx && ctx.slug === jwtTenantSlug) {
        tenantDetail = {
          ...ctx,
          id: jwtTenantId || ctx.id
        }
      } else {
        tenantDetail = {
          id: jwtTenantId,
          slug: jwtTenantSlug,
          name: '',
          shopType: normalizeShopType(null)
        }
      }
    } else if (ctx?.slug) {
      tenantSlug = ctx.slug
      tenantDetail = { ...ctx, id: jwtTenantId || ctx.id }
    }
  }

  return {
    id: payload.sub,
    email: payload.email || '',
    roles,
    tenant: tenantSlug,
    tenantId: jwtTenantId || (tenantDetail && tenantDetail.id) || '',
    tenantDetail,
    shadowSupport: Boolean(payload.shadowSupport)
  }
}

const SUPPORT_TOKEN_STASH = 'ps_support_token_stash'

export function isShadowSession() {
  const u = getSessionUser()
  return Boolean(u && u.shadowSupport)
}

export function enterShadowSession(token, tenant) {
  let current = ''
  try {
    current = localStorage.getItem('ps_token') || ''
  } catch {
    current = ''
  }
  try {
    if (current) localStorage.setItem(SUPPORT_TOKEN_STASH, current)
  } catch {
    // ignore
  }
  setToken(token)
  if (tenant && tenant.slug) {
    try {
      localStorage.setItem(
        TENANT_CTX_KEY,
        JSON.stringify({
          id: String(tenant.id || ''),
          slug: String(tenant.slug).trim(),
          name: String(tenant.name || ''),
          shopType: normalizeShopType(tenant.shopType)
        })
      )
      notifyAuthChanged()
    } catch {
      // ignore
    }
  }
}

export function exitShadowSession() {
  let stashed = ''
  try {
    stashed = localStorage.getItem(SUPPORT_TOKEN_STASH) || ''
    localStorage.removeItem(SUPPORT_TOKEN_STASH)
  } catch {
    stashed = ''
  }
  clearMerchantTenantContext()
  if (stashed) {
    setToken(stashed)
    return true
  }
  setToken('')
  return false
}

/**
 * Slug for `/api/m/:slug/admin/...` requests. Merchants always use the login/JWT tenant slug so
 * browsing `/m/demo/admin` does not call APIs for a missing or wrong tenant.
 *
 * Support / platform-only users never get a slug here (they must not impersonate merchants via
 * the URL); they should use the Support console instead.
 */
export function resolveMerchantSlugForApi(route) {
  const u = getSessionUser()
  if (isSupportOrPlatformOnlyUser(u)) {
    return ''
  }
  const sessionSlug = u && typeof u.tenant === 'string' ? u.tenant.trim() : ''
  if (u && isMerchantRoles(u.roles) && sessionSlug) {
    return sessionSlug
  }
  const fromRoute = String((route && route.params && route.params.merchantSlug) || '').trim()
  if (fromRoute) return fromRoute
  const ctx = getMerchantTenantContext()
  return ctx && ctx.slug ? String(ctx.slug).trim() : ''
}

/** Same as resolveMerchantSlugForApi but throws if no merchant context (e.g. support-only user). */
export function requireMerchantSlugForApi(route) {
  const slug = resolveMerchantSlugForApi(route)
  if (!slug) {
    throw new Error(
      'Merchant workspace is not available for this account. Platform and support staff should use the Support console, not merchant admin links.'
    )
  }
  return slug
}

export function subscribeToAuth(callback) {
  callback(getSessionUser())
  const handler = () => callback(getSessionUser())
  window.addEventListener('storage', handler)
  window.addEventListener(AUTH_CHANGE_EVENT, handler)
  // Re-check expiry while the tab stays open (JWT TTL).
  const tick = window.setInterval(() => {
    callback(getSessionUser())
  }, 60_000)
  return () => {
    window.removeEventListener('storage', handler)
    window.removeEventListener(AUTH_CHANGE_EVENT, handler)
    window.clearInterval(tick)
  }
}

export async function loginWithEmailPassword(email, password) {
  const e = String(email || '').trim()
  const p = String(password || '')
  if (!e || !p) throw new Error('Email and password are required.')
  const res = await apiFetch('/api/auth/login', { method: 'POST', json: { email: e, password: p } })
  if (!res || !res.token) throw new Error('Login failed.')
  setToken(res.token)
  persistMerchantTenantFromLoginResponse(res, decodeJwtPayload(res.token))
  return res
}

export async function changePassword(currentPassword, newPassword) {
  const cur = String(currentPassword || '')
  const next = String(newPassword || '')
  if (!cur || !next) throw new Error('Current and new password are required.')
  if (next.length < 8) throw new Error('New password must be at least 8 characters.')
  return apiFetch('/api/auth/change-password', {
    method: 'POST',
    auth: true,
    json: { currentPassword: cur, newPassword: next }
  })
}

export async function requestPasswordReset(email) {
  const e = String(email || '').trim()
  if (!e) throw new Error('Email is required.')
  return apiFetch('/api/auth/forgot-password', {
    method: 'POST',
    json: { email: e }
  })
}

export async function resetPassword(token, newPassword) {
  const t = String(token || '').trim()
  const next = String(newPassword || '')
  if (!t) throw new Error('Reset token is missing.')
  if (next.length < 8) throw new Error('New password must be at least 8 characters.')
  return apiFetch('/api/auth/reset-password', {
    method: 'POST',
    json: { token: t, newPassword: next }
  })
}

export async function registerMerchant({ merchantName, merchantSlug, ownerEmail, ownerPassword }) {
  const body = { merchantName, ownerEmail, ownerPassword }
  if (merchantSlug != null && String(merchantSlug).trim()) {
    body.merchantSlug = String(merchantSlug).trim()
  }
  const res = await apiFetch('/api/auth/register-merchant', {
    method: 'POST',
    json: body
  })
  if (!res || !res.token) throw new Error('Registration failed.')
  setToken(res.token)
  persistMerchantTenantFromLoginResponse(res, decodeJwtPayload(res.token))
  return res
}

export async function logout() {
  clearAuthStorage()
  notifyAuthChanged()
}
