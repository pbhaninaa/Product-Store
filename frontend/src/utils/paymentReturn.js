import { readHandoverCode } from '@/utils/handoverCode'
import { readBookingFee } from '@/utils/bookingFee'

/**
 * Reads the online payment return URL (`/payment/return` or `/m/:merchantSlug/payment/return`).
 *
 * The backend builds the callback URL with `kind=order|booking|subscription` plus `id` (orders, bookings) or
 * `paymentId` and `merchant` (subscriptions). Paystack appends `reference` and `trxref` (both the transaction
 * reference), which the status endpoints accept as proof of the checkout. Older order and booking links also
 * carry `email`; it is still sent when present. A cancelled checkout may come back with `cancelled=1`.
 */
export const PAYMENT_RETURN_KINDS = Object.freeze(['order', 'booking', 'subscription'])

/** vue-router gives an array when a key repeats; the first value wins. */
function text(value) {
  const v = Array.isArray(value) ? value[0] : value
  return String(v == null ? '' : v).trim()
}

export function readPaymentReturn(params, query) {
  const p = params || {}
  const q = query || {}
  const rawKind = text(q.kind).toLowerCase()
  const kind = PAYMENT_RETURN_KINDS.includes(rawKind) ? rawKind : 'order'
  return {
    kind,
    merchantSlug: text(p.merchantSlug) || text(q.merchant),
    entityId: kind === 'subscription' ? text(q.paymentId) : text(q.id),
    customerEmail: text(q.email),
    reference: text(q.reference) || text(q.trxref),
    cancelled: text(q.cancelled) === '1' || text(q.status).toLowerCase() === 'cancelled'
  }
}

/**
 * Query string for the payment status endpoints: `?reference=...&customerEmail=...` with only the values that
 * are present, or '' when there are none.
 */
export function paymentStatusQuery(lookup) {
  const l = lookup || {}
  const params = new URLSearchParams()
  const reference = text(l.reference)
  const customerEmail = text(l.customerEmail)
  if (reference) params.set('reference', reference)
  if (customerEmail) params.set('customerEmail', customerEmail)
  const q = params.toString()
  return q ? `?${q}` : ''
}

/** True when an order or booking return has something the public status endpoint accepts (reference or email). */
export function hasStatusLookup(ret) {
  return Boolean(ret && (ret.reference || ret.customerEmail))
}

/**
 * Reads `{ paid, cancelled }` from an order, booking or subscription payment status response. The server
 * reports `cancelled` when Paystack says the checkout was abandoned or failed; a paid record is never cancelled.
 */
export function readPaymentStatus(res) {
  const paid = Boolean(res && res.paid)
  return { paid, cancelled: !paid && Boolean(res && res.cancelled) }
}

/**
 * Checks a subscription return. Uses the payment status endpoint for `paymentId`; only when that answers
 * 404 (or there is no paymentId) it falls back to the plan status (`/admin/subscription/me`), which cannot
 * tell a cancelled checkout apart. Other errors are thrown so the page can show them.
 *
 * @param {{ merchantSlug: string, entityId: string }} ret from {@link readPaymentReturn}
 * @param {{ fetchPaymentStatus: Function, fetchPlanStatus: Function, isPlanPaid: Function }} api
 * @returns {Promise<{ paid: boolean, cancelled: boolean }>}
 */
export async function checkSubscriptionReturn(ret, api) {
  if (ret && ret.entityId) {
    try {
      return readPaymentStatus(await api.fetchPaymentStatus(ret.merchantSlug, ret.entityId, ret.reference || ''))
    } catch (e) {
      if (!e || e.status !== 404) throw e
    }
  }
  return { paid: Boolean(api.isPlanPaid(await api.fetchPlanStatus(ret.merchantSlug))), cancelled: false }
}

/**
 * Checks an order or booking return with the Paystack reference (and the email, only when an older link has it).
 * Without either the public status endpoint cannot answer, so it keeps waiting (not paid, not cancelled).
 *
 * @param {{ kind: string, merchantSlug: string, entityId: string, reference: string, customerEmail: string }} ret
 * @param {{ fetchOrderStatus: Function, fetchBookingStatus: Function }} api
 * @returns {Promise<{ paid: boolean, cancelled: boolean }>}
 */
export async function checkOrderOrBookingReturn(ret, api) {
  if (!hasStatusLookup(ret)) return { paid: false, cancelled: false }
  const lookup = { reference: ret.reference, customerEmail: ret.customerEmail }
  const fetchStatus = ret.kind === 'booking' ? api.fetchBookingStatus : api.fetchOrderStatus
  const res = await fetchStatus(ret.merchantSlug, ret.entityId, lookup)
  const outcome = readPaymentStatus(res)
  // Paid: the server adds the handover code the customer shows at collection, delivery or service start.
  const handoverCode = outcome.paid ? readHandoverCode(res) : ''
  const out = handoverCode ? { ...outcome, handoverCode } : outcome
  // A cash booking's booking fee: `paid` means the fee was paid; the rest is due at the shop.
  const bookingFee = res && res.paymentPurpose === 'booking_fee' ? readBookingFee(res) : null
  return bookingFee ? { ...out, bookingFee } : out
}

/** True when the return URL lacks what the status check needs (store, and the order or booking id). */
export function isPaymentReturnIncomplete(ret) {
  if (!ret || !ret.merchantSlug) return true
  return ret.kind !== 'subscription' && !ret.entityId
}
