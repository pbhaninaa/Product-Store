import { apiFetch, apiFetchMultipart } from '@/services/api'
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

export async function uploadSubscriptionPaymentProof(route, file) {
  const slug = slugFromRoute(route)
  const fd = new FormData()
  fd.append('file', file)
  return await apiFetchMultipart(`/api/m/${encodeURIComponent(slug)}/admin/subscription/payment-proof`, {
    method: 'POST',
    formData: fd,
    auth: true
  })
}

export async function fetchPlatformBanking(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/subscription/platform-banking`, {
    auth: true
  })
}
