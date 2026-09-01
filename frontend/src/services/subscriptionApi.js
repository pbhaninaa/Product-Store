import { apiFetch } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'

function slugFromRoute(route) {
  return requireMerchantSlugForApi(route)
}

export async function fetchSubscriptionStatus(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/me`, { auth: true })
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
