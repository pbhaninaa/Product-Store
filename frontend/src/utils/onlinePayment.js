/**
 * Online payment (card or Instant EFT) through the hosted checkout of the platform payment provider.
 * The backend starts the payment and returns `{ needsOnlinePayment, paymentId, paymentRedirectUrl }`;
 * the browser then leaves the app for the provider page and comes back on `/payment/return`.
 */

/** Payment method value for orders and bookings paid online. */
export const ONLINE_PAYMENT_METHOD = 'online'

/** Channel values the customer picks before leaving for the hosted checkout. */
export const ONLINE_PAYMENT_CHANNELS = Object.freeze(['CARD', 'EFT'])

const LOCAL_HOSTS = ['localhost', '127.0.0.1']

/**
 * Only absolute https URLs are followed (plain http only for a local test server), so a bad response can never
 * run script via `javascript:` or send the customer to an unencrypted payment page.
 */
export function isSafeRedirectUrl(raw) {
  const s = String(raw || '').trim()
  if (!s) return false
  try {
    const url = new URL(s)
    if (url.protocol === 'https:') return true
    return url.protocol === 'http:' && LOCAL_HOSTS.includes(url.hostname)
  } catch (e) {
    return false
  }
}

/** Normalised start-payment fields from any start-payment response (order, booking, subscription). */
export function readOnlinePaymentStart(res) {
  const r = res && typeof res === 'object' ? res : {}
  const paymentRedirectUrl = r.paymentRedirectUrl != null ? String(r.paymentRedirectUrl) : ''
  return {
    needsOnlinePayment: Boolean(r.needsOnlinePayment) || Boolean(paymentRedirectUrl),
    paymentId: r.paymentId != null ? String(r.paymentId) : '',
    paymentRedirectUrl
  }
}

/**
 * Sends the browser to the hosted checkout. Returns true when it redirected, false when the
 * session has no usable redirect URL (the caller shows an error and keeps the customer on the page).
 */
export function startOnlinePayment(session, location = window.location) {
  const url = session && session.paymentRedirectUrl ? String(session.paymentRedirectUrl).trim() : ''
  if (!isSafeRedirectUrl(url)) return false
  location.href = url
  return true
}
