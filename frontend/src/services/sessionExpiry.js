/**
 * Expired / revoked JWT handling (Wheel Hub standard): a 401 on an authenticated call
 * clears the session once and sends the user back to sign in, instead of leaving a UI
 * that looks logged in but fails every request.
 */
export const SESSION_EXPIRED_EVENT = 'ps-session-expired'

/** Auth endpoints return 401 for wrong passwords etc. — those are not session expiry. */
const AUTH_ENDPOINT = /^\/?api\/auth\//i

export function isSessionExpiredResponse({ status, sentToken, path }) {
  if (status !== 401 || !sentToken) return false
  return !AUTH_ENDPOINT.test(String(path || ''))
}

/** JWT `exp` is seconds since epoch; tokens without `exp` are treated as unexpired. */
export function isJwtPayloadExpired(payload, nowMs = Date.now()) {
  if (!payload || typeof payload.exp !== 'number') return false
  return payload.exp * 1000 <= nowMs
}

let notified = false

/** Fire at most once per session so parallel failing requests don't stack redirects. */
export function notifySessionExpired() {
  if (notified) return
  notified = true
  try {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
  } catch {
    // ignore SSR / restrictive environments
  }
}

/** Called whenever a fresh token is stored. */
export function resetSessionExpiredNotice() {
  notified = false
}
