/**
 * Returns `raw` only when it is a path inside this app, otherwise `fallback`.
 * Rejects `//evil.com`, `/\evil.com` (browsers read `\` as `/`), absolute URLs, `javascript:` and
 * control characters. Vue Router falls back to a full page load for a cross-origin path, so an
 * unchecked ?redirect= or ?next= would send people to another site after they sign in.
 */
export function safeInternalPath(raw, fallback = '') {
  const p = String(raw == null ? '' : raw).trim()
  if (!p.startsWith('/') || p.startsWith('//')) return fallback
  if (p.includes('\\')) return fallback
  for (let i = 0; i < p.length; i++) {
    const c = p.charCodeAt(i)
    if (c < 0x20 || c === 0x7f) return fallback
  }
  try {
    const base = 'https://app.invalid'
    if (new URL(p, base).origin !== base) return fallback
  } catch {
    return fallback
  }
  return p
}
