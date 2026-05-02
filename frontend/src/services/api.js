const API_BASE = (process.env.VUE_APP_API_BASE || 'http://localhost:8080').replace(/\/+$/, '')

function buildUrl(path) {
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

  const res = await fetch(buildUrl(path), {
    method,
    headers,
    body: json !== undefined ? JSON.stringify(json) : undefined
  })
  if (!res.ok) await parseError(res)
  if (res.status === 204) return null
  return await res.json()
}

