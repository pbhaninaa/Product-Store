import { apiFetch } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'

function slugFromRoute(route) {
  return requireMerchantSlugForApi(route)
}

export async function fetchSubscriptionStatus(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/me`, { auth: true })
}

/** Same as {@link fetchSubscriptionStatus} for a known store slug (e.g. the PayFast return page). */
export async function fetchSubscriptionStatusForSlug(merchantSlug) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) throw new Error('Missing store reference.')
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/me`, { auth: true })
}

/**
 * True once the subscription status shows a paid period: valid, not on a free trial, and no
 * payment still outstanding (covers both first activation and plan upgrades).
 */
export function isSubscriptionPaid(status) {
  if (!status || typeof status !== 'object') return false
  return Boolean(status.valid) && Boolean(status.active) && !status.onTrial && !status.needsPayment
}

export async function fetchSubscriptionPlans(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/plans`, { auth: true })
}

export async function chooseSubscriptionPlan(route, tier) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/plan`, {
    method: 'PUT',
    json: { tier },
    auth: true
  })
}

export async function startSubscriptionPeachCheckout(route, peachPaymentMethod) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/payfast-checkout`, {
    method: 'POST',
    json: { peachPaymentMethod, payFastPaymentMethod: peachPaymentMethod },
    auth: true
  })
}
