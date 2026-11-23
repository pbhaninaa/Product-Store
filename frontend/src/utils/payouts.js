/**
 * Provider payout rules the support console shows. UX only: the API recomputes and enforces every
 * amount, the commission limits and the release checks.
 */
import { formatCents, parseZarToCents } from '@/utils/price'

export const COMMISSION_MAX_PERCENT = 50
export const COMMISSION_MESSAGE = 'Commission must be between 0% and 50%'
export const REFERENCE_MAX = 100
export const NOTE_MAX = 500

/** Vuetify rule for the commission box: a number from 0 to 50 with at most 2 decimals (AC-4). */
export function commissionRule(value) {
  const s = String(value == null ? '' : value).trim().replace(',', '.')
  if (!/^\d+(?:\.\d{1,2})?$/.test(s)) return COMMISSION_MESSAGE
  return Number(s) <= COMMISSION_MAX_PERCENT || COMMISSION_MESSAGE
}

/** The commission as the API wants it: the trimmed text with a dot decimal ("12.5"). */
export function commissionPayload(value) {
  return { commissionPercent: String(value == null ? '' : value).trim().replace(',', '.') }
}

/** "12.5%" from the API's "12.50"; "0%" when unset. */
export function formatPercent(percent) {
  const n = Number(percent)
  if (!Number.isFinite(n)) return '0%'
  return `${String(n.toFixed(2)).replace(/\.?0+$/, '')}%`
}

const BANK_FIELD_LABELS = Object.freeze({
  bankName: 'bank name',
  accountHolder: 'account holder',
  accountNumber: 'account number',
  branchCode: 'branch code'
})

export const BANK_FIELDS = Object.freeze(Object.keys(BANK_FIELD_LABELS))

/** Missing bank fields: the API's list, or the empty ones in `bankDetails` when the list is absent. */
export function missingBankFields(preview) {
  const p = preview || {}
  if (Array.isArray(p.missingBankFields)) return p.missingBankFields.filter((f) => BANK_FIELD_LABELS[f])
  const bank = p.bankDetails || {}
  return BANK_FIELDS.filter((f) => !String(bank[f] == null ? '' : bank[f]).trim())
}

/** AC-9 warning naming the empty fields ('' when none are missing). */
export function missingBankMessage(fields) {
  const names = (fields || []).map((f) => BANK_FIELD_LABELS[f]).filter(Boolean)
  if (!names.length) return ''
  const list = names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`
  const plural = names.length === 1 ? 'is' : 'are'
  return `The ${list} ${plural} missing in this store's bank details. Check them with the store before paying. You can still mark the payout as released.`
}

/**
 * Net for the release screen: gross minus the fee total typed in minus commission (BR-3), in cents.
 * Null when the fee text is not a valid amount.
 */
export function releaseNetCents(summary, feeText) {
  const fee = parseZarToCents(feeText)
  if (fee == null) return null
  const s = summary || {}
  return Number(s.grossCents || 0) - fee - Number(s.commissionCents || 0)
}

/** Vuetify rule for the fee total box (AC-10). */
export function feeTotalRule(summary) {
  return (value) => {
    if (parseZarToCents(value) == null) return 'Enter the fee total in rand, e.g. 25.00'
    const net = releaseNetCents(summary, value)
    return net > 0 || `The fee total must leave more than ${formatCents(0)} to pay out`
  }
}

/** Vuetify rule for the payment reference (AC-11): required, not only spaces, at most 100 characters. */
export function referenceRule(value) {
  const s = String(value == null ? '' : value).trim()
  if (!s) return 'Payment reference is required'
  return s.length <= REFERENCE_MAX || `Payment reference must be ${REFERENCE_MAX} characters or fewer`
}

/**
 * POST /api/support/payouts body. Covers exactly the payments on screen with their version, so the
 * API can refuse when anything changed (AC-14). The fee total is left out when it equals the
 * captured fees, so the payout records "not adjusted".
 */
export function releasePayload(preview, { feeText, reference, note }) {
  const p = preview || {}
  const summary = p.summary || {}
  const feeCents = parseZarToCents(feeText)
  const body = {
    tenantId: p.merchant && p.merchant.tenantId,
    attemptIds: (p.payments || []).map((x) => x.attemptId),
    payableVersion: p.payableVersion,
    reference: String(reference || '').trim()
  }
  if (feeCents != null && feeCents !== Number(summary.knownFeeCents || 0)) body.feeTotalCents = feeCents
  const n = String(note || '').trim()
  if (n) body.note = n
  return body
}

/** Release answers that mean the screen is out of date: show the message and offer Refresh. */
const STALE_CODES = ['payout_already_released', 'payout_amounts_changed', 'payout_nothing_owed']

export function isStaleReleaseError(error) {
  return Boolean(error && STALE_CODES.includes(error.code))
}

/** "1 payment" / "3 payments". */
export function paymentsLabel(n) {
  const c = Number(n || 0)
  return c === 1 ? '1 payment' : `${c} payments`
}

/** "1 fee unknown" / "2 fees unknown" (AC-6: an unknown fee is flagged, never shown as R0 silently). */
export function unknownFeeLabel(n) {
  const c = Number(n || 0)
  return c === 1 ? '1 fee unknown' : `${c} fees unknown`
}

/**
 * Tiles for the owner's "Owed by the platform" section (AC-17), from the store-earnings `owed` block.
 * Same numbers as the platform's Payouts page: all unreleased payments, current commission.
 */
export function ownerOwedTiles(owed) {
  const o = owed || {}
  const unknown = Number(o.unknownFeeCount || 0)
  return [
    {
      key: 'net',
      label: 'Owed by the platform',
      value: formatCents(o.netOwedCents || 0),
      hint: `${paymentsLabel(o.paymentCount)} not paid out yet, all dates`,
      accent: true
    },
    { key: 'gross', label: 'Gross', value: formatCents(o.grossCents || 0), hint: 'In app payments not paid out yet' },
    {
      key: 'fees',
      label: 'Paystack fees',
      value: formatCents(o.knownFeeCents || 0),
      hint: unknown > 0 ? `${unknownFeeLabel(unknown)}, settled at payout` : 'Paid by the store'
    },
    {
      key: 'commission',
      label: `Commission (${formatPercent(o.commissionPercent)})`,
      value: formatCents(o.commissionCents || 0),
      hint: 'Platform commission on the gross'
    }
  ]
}

/** "Order" / "Booking" for a payment kind. */
export function paymentKindLabel(kind) {
  return String(kind || '').toUpperCase() === 'BOOKING' ? 'Booking' : 'Order'
}

/** Payout history filter: both dates or neither (same rule as the API). True or a message. */
export function dateRangeRule(startDate, endDate) {
  return Boolean(startDate) === Boolean(endDate) || 'Enter both a start and end date, or neither.'
}
