import { ONLINE_PAYMENT_METHOD } from '@/utils/onlinePayment'

/** Online payment (hosted checkout) is the only in app payment method. */
export function isInAppPaymentMethod(raw) {
  return String(raw || '').trim().toLowerCase() === ONLINE_PAYMENT_METHOD
}

export function matchesPaymentMethodFilter(orderMethod, filter) {
  const m = String(orderMethod || '').trim().toLowerCase()
  const f = String(filter || '').trim().toLowerCase()
  if (!f) return true
  return m === f
}

/** "Online · Card", "Online · Instant EFT" or "Online" (provider neutral); null for methods not paid online. */
export function inAppPaymentLabel(method, channel) {
  if (!isInAppPaymentMethod(method)) return null
  const c = String(channel || '').trim().toUpperCase()
  if (c === 'CARD') return 'Online · Card'
  if (c === 'EFT') return 'Online · Instant EFT'
  return 'Online'
}
