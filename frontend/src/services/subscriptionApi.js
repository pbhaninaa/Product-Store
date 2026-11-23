import { apiFetch } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'
import { paymentStatusQuery } from '@/utils/paymentReturn'

function slugFromRoute(route) {
  return requireMerchantSlugForApi(route)
}

export async function fetchSubscriptionStatus(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/me`, { auth: true })
}

/** Same as {@link fetchSubscriptionStatus} for a known store slug (e.g. the payment return page). */
export async function fetchSubscriptionStatusForSlug(merchantSlug) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) throw new Error('Missing store reference.')
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/me`, { auth: true })
}

/**
 * Status of one online subscription payment: `{ paymentId, status, paid, cancelled }`. The server confirms a
 * still pending payment with Paystack, so this also settles a payment whose webhook is late.
 * `reference` is the Paystack reference from the return URL, when present.
 */
export async function fetchSubscriptionPaymentStatus(merchantSlug, paymentId, reference) {
  const slug = String(merchantSlug || '').trim()
  const id = String(paymentId || '').trim()
  if (!slug || !id) throw new Error('Missing payment reference.')
  return await apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/subscription/online-payments/${encodeURIComponent(id)}/status${paymentStatusQuery({ reference })}`,
    { auth: true }
  )
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

/** Starts online payment for the store subscription: `{ paymentId, paymentRedirectUrl }`. */
export async function startSubscriptionOnlinePayment(route, onlinePaymentChannel) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/online-checkout`, {
    method: 'POST',
    json: { onlinePaymentChannel },
    auth: true
  })
}
