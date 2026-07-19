/** Ensures Railway/API origin is absolute (avoids Vercel treating host as a path). */
function normalizeApiBase(raw) {
  let b = String(raw || '').trim().replace(/\/+$/, '')
  if (!b) return 'http://localhost:8080'
  if (!/^https?:\/\//i.test(b)) {
    b = 'https://' + b.replace(/^\/+/, '')
  }
  return b.replace(/\/+$/, '')
}

/**
 * Prefer the native WebView bridge (mobile shell) so UAT/PROD APKs hit Railway
 * even when the hosted SPA was built with a different VUE_APP_API_BASE.
 */
function resolveApiBase() {
  if (typeof window !== 'undefined') {
    const injected =
      window.__productStoreApiBaseUrl ||
      window.__wheelHubApiBaseUrl ||
      ''
    if (String(injected).trim()) {
      return normalizeApiBase(injected)
    }
  }
  return normalizeApiBase(process.env.VUE_APP_API_BASE || 'http://localhost:8080')
}

/** Public backend origin used by the SPA (normalized absolute URL). */
export function getApiBase() {
  return resolveApiBase()
}

/** Turn relative API asset paths (images) into absolute Railway URLs for the Vercel SPA. */
export function resolveMediaUrl(raw) {
  const API_BASE = resolveApiBase()
  const u = String(raw == null ? '' : raw).trim()
  if (!u) return ''
  if (/^(https?:|data:|blob:)/i.test(u)) return u
  if (u.startsWith('//')) return (typeof window !== 'undefined' && window.location.protocol === 'http:' ? 'http:' : 'https:') + u
  if (u.startsWith('/')) return `${API_BASE}${u}`
  return `${API_BASE}/${u.replace(/^\/+/, '')}`
}

function buildUrl(path) {
  const API_BASE = resolveApiBase()
  const p = String(path || '')
  if (!p.startsWith('/')) return `${API_BASE}/${p}`
  return `${API_BASE}${p}`
}

const AUTH_CHANGED = 'ps-auth-change'

/**
 * Broadcasts auth-related localStorage updates to the active tab ({@code storage}
 * fires only across tabs).
 */
export function notifyAuthChanged() {
  try {
    window.dispatchEvent(new Event(AUTH_CHANGED))
  } catch {
    // ignore SSR / restrictive environments
  }
}

export { AUTH_CHANGED as AUTH_CHANGE_EVENT }

function getToken() {
  try {
    return localStorage.getItem('ps_token') || ''
  } catch {
    return ''
  }
}

export function setToken(token) {
  const t = String(token || '').trim()
  try {
    if (!t) localStorage.removeItem('ps_token')
    else localStorage.setItem('ps_token', t)
  } catch {
    // ignore
  }
  notifyAuthChanged()
}

async function parseError(res) {
  let body = null
  try {
    body = await res.json()
  } catch {
    // ignore
  }
  const msg =
    (body && (body.error || body.message)) ||
    `${res.status} ${res.statusText || 'Request failed'}`
  const e = new Error(String(msg))
  e.status = res.status
  e.body = body
  throw e
}

/** Multipart uploads (omit Content-Type — browser sets boundary). */
export async function apiFetchMultipart(path, { method = 'POST', formData, auth = true } = {}) {
  const headers = { Accept: 'application/json' }
  if (auth) {
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }
  const res = await fetch(buildUrl(path), { method, headers, body: formData })
  if (!res.ok) await parseError(res)
  if (res.status === 204) return null
  return await res.json()
}

export async function apiFetch(path, { method = 'GET', json, auth = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (json !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) {
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  let res
  try {
    res = await fetch(buildUrl(path), {
      method,
      headers,
      body: json !== undefined ? JSON.stringify(json) : undefined
    })
  } catch {
    const e = new Error(
      'Cannot reach the API right now. Check your connection, or wait for the backend to finish deploying.'
    )
    e.status = 0
    throw e
  }
  if (!res.ok) await parseError(res)
  if (res.status === 204) return null
  return await res.json()
}

