import { apiFetch, setToken, notifyAuthChanged, AUTH_CHANGE_EVENT } from '@/services/api'
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
  if (!payload || !payload.sub) return null
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
    tenantDetail
  }
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
  return () => {
    window.removeEventListener('storage', handler)
    window.removeEventListener(AUTH_CHANGE_EVENT, handler)
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

export async function registerMerchant({ merchantName, merchantSlug, ownerEmail, ownerPassword }) {
  const res = await apiFetch('/api/auth/register-merchant', {
    method: 'POST',
    json: { merchantName, merchantSlug, ownerEmail, ownerPassword }
  })
  if (!res || !res.token) throw new Error('Registration failed.')
  setToken(res.token)
  persistMerchantTenantFromLoginResponse(res, decodeJwtPayload(res.token))
  return res
}

export async function logout() {
  clearMerchantTenantContext()
  setToken('')
}
