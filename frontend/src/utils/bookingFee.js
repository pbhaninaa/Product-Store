/**
 * Booking fee for pay in store salon bookings (docs/engineering/requirements/booking-fee.md).
 * The server decides the fee, refunds and kept fees; these helpers only validate the settings form,
 * preview the fee before booking and label what the server returns.
 */
import { formatZar } from '@/utils/price'

export const BOOKING_FEE_MIN_PERCENT = 0
export const BOOKING_FEE_MAX_PERCENT = 50
export const FREE_CANCELLATION_HOURS = 24

const RANGE_MESSAGE = 'The booking fee must be a whole number from 0% to 50%.'

/** '' when the typed percent is a whole number from 0 to 50, else the message to show (AC-2). */
export function bookingFeePercentError(input) {
  const s = String(input == null ? '' : input).trim()
  if (!/^\d+$/.test(s)) return RANGE_MESSAGE
  const n = Number(s)
  return n >= BOOKING_FEE_MIN_PERCENT && n <= BOOKING_FEE_MAX_PERCENT ? '' : RANGE_MESSAGE
}

/** A typed rand price ("1000", "999.99") as integer cents, or null. */
function toCents(price) {
  const m = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(String(price == null ? '' : price).trim())
  if (!m) return null
  return Number(m[1]) * 100 + Number((m[2] || '').padEnd(2, '0'))
}

function centsToAmount(cents) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`
}

/**
 * Preview of the fee for a price and percent, computed like the server (half up to the cent, remainder = price
 * minus fee). Null when there is no fee, or when it is below the Paystack minimum (no online step then).
 */
export function bookingFeePreview(price, percent, minimumZar = '1.00') {
  const cents = toCents(price)
  const p = Number(percent)
  if (cents == null || !Number.isInteger(p) || p <= 0 || p > BOOKING_FEE_MAX_PERCENT) return null
  const feeCents = Math.round((cents * p) / 100)
  const minCents = toCents(minimumZar) || 100
  if (feeCents <= 0 || feeCents < minCents) return null
  return { feeZar: centsToAmount(feeCents), remainderZar: centsToAmount(cents - feeCents), totalZar: centsToAmount(cents) }
}

/** True when a customer cancelling at `now` would still get the fee back: at least 24 hours before the start. */
export function feeRefundableIfCancelledAt(startAt, now = new Date()) {
  const start = new Date(startAt).getTime()
  const at = new Date(now).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(at)) return false
  return start - at >= FREE_CANCELLATION_HOURS * 60 * 60 * 1000
}

/** The booking fee block of an API booking, or null when the booking has none. */
export function readBookingFee(res) {
  const f = res && res.bookingFee
  if (!f || typeof f !== 'object') return null
  return {
    percent: f.percent != null ? Number(f.percent) : null,
    feeZar: f.feeZar != null ? String(f.feeZar) : '',
    remainderZar: f.remainderZar != null ? String(f.remainderZar) : '',
    totalZar: f.totalZar != null ? String(f.totalZar) : '',
    status: String(f.status || ''),
    reason: f.reason ? String(f.reason) : '',
    refundableIfCancelledNow: Boolean(f.refundableIfCancelledNow)
  }
}

const LINE_LABELS = {
  booking_fee_online: 'Booking fee (paid online)',
  booking_fee_due_online: 'Booking fee (to pay online now)',
  paid_at_shop: 'Paid at the shop (cash)',
  due_at_shop: 'Due at the shop (cash)',
  paid_online: 'Paid online',
  due_online: 'To pay online',
  fee_refunded: 'Booking fee refunded',
  fee_refund_in_progress: 'Booking fee refund in progress',
  fee_kept: 'Booking fee kept by the shop',
  total: 'Total'
}

/** Label of one receipt line kind from the server (FR-7). */
export function receiptLineLabel(kind) {
  return LINE_LABELS[String(kind || '')] || 'Amount'
}

/** Receipt lines ready to show: `{ kind, label, amount }` with the amount formatted as rand. */
export function receiptLines(res) {
  const raw = res && Array.isArray(res.receiptLines) ? res.receiptLines : []
  return raw.map((l) => ({
    kind: String(l.kind || ''),
    label: receiptLineLabel(l.kind),
    amount: formatZar(l.amountZar)
  }))
}

const STATUS_LABELS = {
  awaiting_payment: 'Booking fee not paid yet',
  paid: 'Booking fee paid',
  refund_pending: 'Booking fee refund in progress',
  refunded: 'Booking fee refunded',
  kept: 'Booking fee kept',
  not_paid: 'Booking fee not paid'
}

const REASON_LABELS = {
  customer_cancellation: 'customer cancelled at least 24 hours ahead',
  late_cancellation: 'customer cancelled less than 24 hours ahead',
  no_show: 'customer did not show up',
  provider_cancellation: 'cancelled by the shop',
  payment_after_cancellation: 'paid after the booking was cancelled'
}

/** "Booking fee kept (customer did not show up)" and similar; '' for bookings without a fee. */
export function bookingFeeStatusLabel(status, reason) {
  const base = STATUS_LABELS[String(status || '')]
  if (!base) return ''
  const why = REASON_LABELS[String(reason || '')]
  return why ? `${base} (${why})` : base
}

/** What staff still collect, e.g. "R 800,00 due"; '' when nothing is due. */
export function amountDueLabel(amountDueZar) {
  const n = Number(amountDueZar)
  if (!Number.isFinite(n) || n <= 0) return ''
  return `${formatZar(n)} due`
}

/** Message shown to the customer after cancelling, from the server's `bookingFeeOutcome`. */
export function cancellationOutcomeMessage(outcome, feeZar) {
  const fee = feeZar ? formatZar(feeZar) : 'The booking fee'
  switch (String(outcome || '')) {
    case 'refunded':
      return `Booking cancelled. ${fee} booking fee is being refunded to your card or account. It can take up to 10 business days to show.`
    case 'refund_pending':
      return `Booking cancelled. Your ${fee} booking fee refund is in progress.`
    case 'kept':
      return `Booking cancelled. The ${fee} booking fee is not refunded because you cancelled less than 24 hours before the start.`
    default:
      return 'Booking cancelled.'
  }
}

/** Warning shown before paying when the booking starts within 24 hours (edge case 1). */
export function nonRefundableWarning(startAt, now = new Date()) {
  if (feeRefundableIfCancelledAt(startAt, now)) {
    return 'Cancel at least 24 hours before the start to get the booking fee back. Later cancellations and no shows are not refunded.'
  }
  return 'This booking starts within 24 hours, so the booking fee is not refundable once paid.'
}

/** Staff confirmation before cancelling a booking: a provider cancellation always refunds a paid fee (BR-6). */
export function cancelConfirmText(booking) {
  const b = booking || {}
  if (b.bookingFeeStatus === 'paid') {
    return `The customer's ${formatZar(b.bookingFeeZar)} booking fee will be refunded, and the time is freed.`
  }
  return 'The time is freed for other customers. The customer is told the booking was cancelled.'
}

/** Staff confirmation before marking a no show: a paid booking fee is kept (BR-5). */
export function noShowConfirmText(booking) {
  const b = booking || {}
  if (b.bookingFeeStatus === 'paid') {
    return `The booking is cancelled and you keep the ${formatZar(b.bookingFeeZar)} booking fee. This cannot be undone.`
  }
  return 'The booking is cancelled as a no show. This cannot be undone.'
}
