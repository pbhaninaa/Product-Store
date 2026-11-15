/**
 * Where "Sign in" should go for each kind of account. Merchants with a known store sign in from that
 * store admin (`/m/<slug>/admin`); support staff from the platform console; shoppers (the default) from
 * the customer account page. There is no dedicated store-agnostic merchant sign-in route yet, so a
 * merchant without a known store uses the account page with `audience=merchant` (the shared login
 * sends merchant accounts on to their own store admin).
 */
const RESET_CONTEXT_KEY = 'productstore_reset_context'

function clean(v) {
  return String(v == null ? '' : v).trim()
}

/** Normalises `{ audience, m }` from a route query: audience is 'client', 'merchant' or 'support'. */
export function signInContextFromQuery(query) {
  const q = query || {}
  const m = clean(q.m)
  let audience = clean(q.audience).toLowerCase()
  if (clean(q.support) === '1') audience = 'support'
  if (!['client', 'merchant', 'support'].includes(audience)) audience = m ? 'merchant' : 'client'
  return audience === 'merchant' && m ? { audience, m } : { audience }
}

/** Router location for the sign-in page matching a context from {@link signInContextFromQuery}. */
export function signInRouteFor(context) {
  const ctx = context || {}
  if (ctx.audience === 'support') return { path: '/m/platform/admin', query: { support: '1' } }
  if (ctx.audience === 'merchant') {
    if (clean(ctx.m)) return { path: `/m/${encodeURIComponent(clean(ctx.m))}/admin` }
    return { name: 'client-login', query: { audience: 'merchant' } }
  }
  return { name: 'client-login' }
}

/** Query to pass to /forgot-password so the reset flow can send the person back to the right sign-in. */
export function forgotPasswordQuery(context, email) {
  const ctx = context || {}
  const query = { audience: ctx.audience || 'client' }
  if (ctx.audience === 'merchant' && clean(ctx.m)) query.m = clean(ctx.m)
  if (clean(email)) query.email = clean(email)
  return query
}

/** Remembers the sign-in context while the emailed reset link is opened (usually in the same browser). */
export function rememberResetContext(context) {
  try {
    localStorage.setItem(RESET_CONTEXT_KEY, JSON.stringify(context || { audience: 'client' }))
  } catch {
    // storage unavailable: the reset page falls back to the customer sign-in
  }
}

export function recallResetContext() {
  try {
    const raw = localStorage.getItem(RESET_CONTEXT_KEY)
    if (!raw) return null
    return signInContextFromQuery(JSON.parse(raw))
  } catch {
    return null
  }
}
