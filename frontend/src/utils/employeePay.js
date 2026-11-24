import { orderPayMethodLabel, payMethodLabel } from '@/utils/displayLabels'
import { formatZar } from '@/utils/price'
import { rules } from '@/utils/validation'

/**
 * Employee pay settings: service pay (bookings) and order pay (orders), as sent by
 * GET …/admin/team, …/payment-calculations and …/my-expected-income. Mirrors EmployeePayRules.java.
 */

/** Service pay options, in the order the owner sees them. */
export const SERVICE_PAY_METHODS = Object.freeze([
  'PER_SERVICE',
  'PERCENT_OF_SERVICE',
  'PER_HOUR',
  'PER_DAY',
  'WEEKLY',
  'MONTHLY'
])

/** Order pay options. NONE is the default for every employee. */
export const ORDER_PAY_METHODS = Object.freeze(['NONE', 'FIXED_PER_ORDER', 'PERCENT_OF_ORDER'])

const PERCENT_METHODS = new Set(['PERCENT_OF_SERVICE', 'PERCENT_OF_ORDER'])

const upper = (v) => String(v == null ? '' : v).trim().toUpperCase()

export const isPercentMethod = (method) => PERCENT_METHODS.has(upper(method))

/** Unset order pay (employees created before the setting existed) reads as NONE. */
export const normalizeOrderPayMethod = (method) => (ORDER_PAY_METHODS.includes(upper(method)) ? upper(method) : 'NONE')

export const servicePayItems = () => SERVICE_PAY_METHODS.map((value) => ({ value, text: payMethodLabel(value) }))
export const orderPayItems = () => ORDER_PAY_METHODS.map((value) => ({ value, text: orderPayMethodLabel(value) }))

/** Field label for the rate: rand or percentage, depending on the method. */
export function rateFieldLabel(method, prefix = 'Pay rate') {
  return isPercentMethod(method) ? `${prefix} (%) *` : `${prefix} (ZAR) *`
}

/** Vuetify rules for a rate: rand amount >= 0, or a percentage 0 to 100 with at most 2 decimals. */
export function rateRules(method, label = 'Pay rate') {
  if (upper(method) === 'NONE') return []
  return isPercentMethod(method)
    ? [rules.required(label), rules.percentRate()]
    : [rules.required(label), rules.money({ min: 0 })]
}

function percentText(rate) {
  const n = Number(rate || 0)
  return `${Number.isInteger(n) ? n : n.toFixed(2).replace(/0$/, '')}%`
}

/** "Per service, R 100,00" / "40% of service price". */
export function describeServicePay(method, rate) {
  const m = upper(method) || 'PER_SERVICE'
  if (m === 'PERCENT_OF_SERVICE') return `${percentText(rate)} of service price`
  return `${payMethodLabel(m)}, ${formatZar(Number(rate || 0))}`
}

/** "No order pay" / "R 20,00 per order" / "5% of order total". */
export function describeOrderPay(method, rate) {
  const m = normalizeOrderPayMethod(method)
  if (m === 'FIXED_PER_ORDER') return `${formatZar(Number(rate || 0))} per order`
  if (m === 'PERCENT_OF_ORDER') return `${percentText(rate)} of order total`
  return orderPayMethodLabel('NONE')
}

/**
 * Service / order / total for one My income group ('owed', 'upcoming', 'paidOut'). Older servers sent only
 * `<group>Total`; then all of it is shown as service pay.
 */
export function incomeSplit(bundle, group) {
  const b = bundle || {}
  const total = Number(b[`${group}Total`] || 0)
  const hasSplit = b[`${group}ServiceTotal`] != null || b[`${group}OrderTotal`] != null
  if (!hasSplit) return { service: total, order: 0, total }
  return {
    service: Number(b[`${group}ServiceTotal`] || 0),
    order: Number(b[`${group}OrderTotal`] || 0),
    total
  }
}

/** Rows for the My income pay summary: service pay, order pay and total, each for owed / upcoming / paid out. */
export function incomeSummaryRows(bundle) {
  const owed = incomeSplit(bundle, 'owed')
  const upcoming = incomeSplit(bundle, 'upcoming')
  const paid = incomeSplit(bundle, 'paidOut')
  return [
    { key: 'service', label: 'Service pay', owed: owed.service, upcoming: upcoming.service, paid: paid.service },
    { key: 'order', label: 'Order pay', owed: owed.order, upcoming: upcoming.order, paid: paid.order },
    { key: 'total', label: 'Total', owed: owed.total, upcoming: upcoming.total, paid: paid.total, isTotal: true }
  ]
}

/** Service / order / total still to pay for one payroll row (older servers: all service). */
export function payrollSplit(row) {
  const r = row || {}
  const total = Number(r.pendingExpected || 0)
  if (r.servicePending == null && r.orderPending == null) return { service: total, order: 0, total }
  return { service: Number(r.servicePending || 0), order: Number(r.orderPending || 0), total }
}

/** "Order pay" for an order line, "Service pay" for a booking line. */
export const payKindLabel = (jobType) => (upper(jobType) === 'ORDER' ? 'Order pay' : 'Service pay')
