/**
 * Frontend merchant storefront / admin path helpers (no /m/ prefix).
 * API routes remain under /api/m/... and /api/public/m/...
 */

export function merchantBasePath(slug) {
  const s = String(slug || '').trim()
  return s ? `/${encodeURIComponent(s)}` : '/'
}

export function merchantAdminPath(slug, tail = '') {
  const base = `${merchantBasePath(slug)}/admin`
  const t = String(tail || '')
  if (!t) return base
  return t.startsWith('/') ? `${base}${t}` : `${base}/${t}`
}

export function merchantCheckoutPath(slug) {
  return `${merchantBasePath(slug)}/checkout`
}

export function merchantContactPath(slug) {
  return `${merchantBasePath(slug)}/contact`
}

export function merchantSalonServicesPath(slug) {
  return `${merchantBasePath(slug)}/salon/services`
}

/** Absolute URL merchants share with customers. */
export function merchantStorefrontUrl(slug, origin) {
  const o = String(origin || (typeof window !== 'undefined' ? window.location.origin : '')).replace(/\/$/, '')
  return `${o}${merchantBasePath(slug)}`
}
