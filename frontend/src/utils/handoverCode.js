/**
 * Handover code (CTO decision 2026-11): every order and booking gets a 6 digit code, whatever the payment method.
 * Staff enter the customer's code to hand an order over (collection or delivery) and to start a salon service; the
 * server refuses the change without it. For cash payments the same entry records the payment.
 */

/** Label used on every staff prompt and customer screen. */
export const HANDOVER_CODE_LABEL = 'Handover code'

/** The code from an API payload: `handoverCode`, or the older `cashPaymentCode` from servers before the change. */
export function readHandoverCode(res) {
  if (!res) return ''
  const raw = res.handoverCode != null && String(res.handoverCode).trim() ? res.handoverCode : res.cashPaymentCode
  return raw != null ? String(raw).trim() : ''
}

/** What staff typed, without spaces (customers often read the code out in groups). */
export function normalizeHandoverCode(input) {
  return String(input == null ? '' : input).replace(/\s+/g, '')
}

const lower = (v) => String(v || '').trim().toLowerCase()

function orderPaymentMethod(order) {
  return lower(order && (order.paymentMethod || order.payment_method))
}

function orderIsCancelled(order) {
  return Boolean(order && (lower(order.status) === 'cancelled' || order.cancelledAt || order.cancelled_at))
}

/** Moving an order to `completed` is the handover and needs the code; other steps do not. */
export function orderStatusNeedsHandoverCode(currentStatus, nextStatus) {
  return lower(nextStatus) === 'completed' && lower(currentStatus) !== 'completed'
}

/** Unpaid cash order: staff can hand it over with the code, which also marks it paid. */
export function canHandOverUnpaidCashOrder(order) {
  if (!order || orderIsCancelled(order)) return false
  return lower(order.status) === 'pending_payment' && orderPaymentMethod(order) === 'cash_store'
}

/**
 * Starting the service needs the code: moving to `in_progress`, or straight from `confirmed` to `completed`.
 * Completing a service that already started does not.
 */
export function bookingStatusNeedsHandoverCode(currentStatus, nextStatus) {
  const from = lower(currentStatus)
  const to = lower(nextStatus)
  if (from === to) return false
  if (to === 'in_progress') return true
  return to === 'completed' && from === 'confirmed'
}

/** Unpaid cash booking: staff can start it with the code, which also marks it paid. */
export function canStartUnpaidCashBooking(booking) {
  if (!booking) return false
  return lower(booking.status) === 'pending' && lower(booking.clientPaymentMethod) === 'cash_store'
}

/** JSON body for the order fulfilment endpoint. */
export function orderFulfillmentBody(status, handoverCode) {
  const body = { status: lower(status) }
  const code = normalizeHandoverCode(handoverCode)
  if (code) body.handoverCode = code
  return body
}

/** JSON body for the booking status endpoint. */
export function bookingStatusBody(status, handoverCode) {
  const body = { status }
  const code = normalizeHandoverCode(handoverCode)
  if (code) body.code = code
  return body
}

/**
 * One sentence telling the customer when to show the code. `kind` is 'order' or 'booking'; for an unpaid cash
 * order or booking it adds that the same code records the cash payment.
 */
export function handoverCodeHint({ kind, paymentMethod, status } = {}) {
  const unpaidCash = lower(paymentMethod) === 'cash_store' && ['pending_payment', 'pending'].includes(lower(status))
  if (lower(kind) === 'booking') {
    return unpaidCash
      ? 'Show it when you arrive. Staff enter it to start your service and to record your cash payment.'
      : 'Show it when you arrive. Staff need it to start your service.'
  }
  return unpaidCash
    ? 'Show it when you collect or when your delivery arrives. Staff enter it to hand over your order and to record your cash payment.'
    : 'Show it when you collect or when your delivery arrives. Staff need it to hand over your order.'
}
