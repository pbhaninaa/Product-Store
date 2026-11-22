/**
 * Everything a person reads must make sense to them: no UUIDs, no enum names like PENDING_PAYMENT.
 * Views turn raw API values into words with these helpers.
 */

/**
 * Short reference for an order or booking: first 8 hex characters of its id, upper case.
 * Same rule as the backend (RefCodes.shortRef), so emails, bank references and the UI agree.
 */
export function shortRef(id) {
  const compact = String(id || '').replace(/-/g, '')
  return compact ? compact.slice(0, 8).toUpperCase() : ''
}

/** "A to z words" from SOME_ENUM_VALUE / some_enum_value, as a last resort for values not mapped below. */
export function humanize(value) {
  const s = String(value == null ? '' : value).trim()
  if (!s) return 'None'
  const words = s.replace(/[_.-]+/g, ' ').toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

function lookup(map, value) {
  const key = String(value == null ? '' : value).trim().toLowerCase()
  if (!key) return 'None'
  return map[key] || humanize(value)
}

const ORDER_STATUS = { pending_payment: 'Awaiting payment', paid: 'Paid', cancelled: 'Cancelled' }
const FULFILLMENT = { processing: 'Processing', ready: 'Ready', completed: 'Completed' }
const BOOKING_STATUS = {
  pending: 'Awaiting payment',
  confirmed: 'Confirmed',
  in_progress: 'In progress',
  completed: 'Completed',
  cancelled: 'Cancelled'
}
const PAYMENT_METHOD = {
  eft: 'Bank transfer (EFT)',
  cash_store: 'Cash in store',
  payfast: 'PayFast',
  peach: 'Card / instant EFT',
  card: 'Card',
  paybybank: 'Instant EFT'
}
const VERIFICATION = {
  not_applicable: 'Not needed',
  awaiting_proof: 'Waiting for proof of payment',
  auto_verified: 'Verified automatically',
  manual_pending: 'Needs your review',
  manual_approved: 'Approved',
  manual_rejected: 'Rejected'
}
const TICKET_STATUS = { open: 'Open', resolved: 'Resolved' }
const PLAN = { starter: 'Starter', standard: 'Standard', premium: 'Premium' }
const PROOF_STATUS = { none: 'None', pending: 'Waiting for review', approved: 'Approved', rejected: 'Rejected' }
const PAY_METHOD = {
  per_service: 'Per service',
  per_hour: 'Per hour',
  per_day: 'Per day',
  weekly: 'Weekly',
  monthly: 'Monthly'
}
const ROLE = {
  platform_admin: 'System admin',
  support_user: 'Support',
  merchant_owner: 'Store owner',
  merchant_staff: 'Store staff',
  client: 'Client'
}
const AUDIT_ACTION = {
  feature_toggle: 'Turned a feature on or off',
  help_contact_update: 'Updated help contact details',
  plan_update: 'Updated plan pricing',
  merchant_delete: 'Deleted a store',
  merchant_reset_owner_password: "Reset a store owner's password",
  staff_create: 'Added a support user',
  staff_reset_password: "Reset a support user's password",
  staff_permissions: "Changed a support user's permissions",
  ticket_resolve: 'Resolved a help ticket'
}
const AUDIT_ENTITY = {
  feature: 'Feature',
  platform: 'Platform',
  plan: 'Plan',
  tenant: 'Store',
  user: 'User',
  ticket: 'Help ticket'
}

export const orderStatusLabel = (v) => lookup(ORDER_STATUS, v)
export const fulfillmentLabel = (v) => lookup(FULFILLMENT, v)
export const bookingStatusLabel = (v) => lookup(BOOKING_STATUS, v)
export const paymentMethodLabel = (v) => lookup(PAYMENT_METHOD, v)
export const verificationLabel = (v) => lookup(VERIFICATION, v)
export const ticketStatusLabel = (v) => lookup(TICKET_STATUS, v)
export const planLabel = (v) => lookup(PLAN, v)
export const proofStatusLabel = (v) => lookup(PROOF_STATUS, v)
export const payMethodLabel = (v) => lookup(PAY_METHOD, v)
export const roleLabel = (v) => lookup(ROLE, v)
export const auditActionLabel = (v) => lookup(AUDIT_ACTION, v)
export const auditEntityLabel = (v) => lookup(AUDIT_ENTITY, v)

/** "16 Nov 2026, 14:05" in the viewer's locale; 'Not set' when empty or unparseable. */
export function formatDateTime(iso) {
  if (!iso) return 'Not set'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return 'Not set'
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

/**
 * "16 Nov 2026" in the viewer's locale; 'Not set' when empty or unparseable.
 * A plain "2026-11-16" (LocalDate) is read as that calendar day, not as UTC midnight.
 */
export function formatDate(iso) {
  if (!iso) return 'Not set'
  const ymd = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(iso).trim())
  const d = ymd ? new Date(Number(ymd[1]), Number(ymd[2]) - 1, Number(ymd[3])) : new Date(iso)
  if (Number.isNaN(d.getTime())) return 'Not set'
  return d.toLocaleDateString(undefined, { dateStyle: 'medium' })
}

const JOB_TYPE = { order: 'Order', salon_booking: 'Booking', booking: 'Booking' }
/** Team payroll lines: ORDER / SALON_BOOKING. */
export const jobTypeLabel = (v) => lookup(JOB_TYPE, v)

Object.assign(AUDIT_ACTION, {
  shadow_enter: 'Opened a store admin as shadow help',
  danger_reset_database: 'Reset the test database',
  staff_suspend: 'Suspended a support user',
  staff_activate: 'Reactivated a support user'
})

/** One readable sentence for an audit row's detail (no ids, no key=value dumps). */
export function auditDetail(entry) {
  const e = entry || {}
  const action = String(e.action || '').toUpperCase()
  const detail = String(e.detail == null ? '' : e.detail).trim()
  if (action === 'FEATURE_TOGGLE') {
    return `${humanize(e.entityId)} turned ${detail === 'true' ? 'on' : 'off'}`
  }
  if (action === 'PLAN_UPDATE') return `${planLabel(e.entityId)} plan`
  if (action === 'HELP_CONTACT_UPDATE') return 'Help contact details'
  if (action === 'SHADOW_ENTER') {
    const m = detail.match(/^shadow into (\S+) as (\S+)$/)
    return m ? `Store /${m[1]} (as ${m[2]})` : detail || 'No details'
  }
  if (action === 'DANGER_RESET_DATABASE') {
    return (
      detail
        .split(';')
        .map((part) => {
          const [k, v] = part.split('=')
          return v == null ? '' : `${humanize(k.replace(/([a-z])([A-Z])/g, '$1 $2'))}: ${v}`
        })
        .filter(Boolean)
        .join(', ') || 'No details'
    )
  }
  return detail || 'No details'
}
