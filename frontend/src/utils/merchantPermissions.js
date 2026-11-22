/**
 * Per-staff merchant permissions (mirrors backend MerchantPermission). Pure helpers, no Vue / API imports.
 *
 * The server enforces every permission; these helpers only decide what the admin UI shows.
 */
import { isMerchantOwner, isMerchantUser } from '@/utils/merchantRoles'

export const AREA_PERMISSIONS = Object.freeze([
  { key: 'ORDERS', label: 'Orders', hint: 'See orders, update fulfilment, cancel unpaid orders.' },
  { key: 'BOOKINGS', label: 'Bookings', hint: 'See bookings and change their status.' },
  { key: 'PAYMENTS', label: 'Payments', hint: 'See the payments overview.' },
  { key: 'PRODUCTS', label: 'Products', hint: 'Add and edit products.' },
  { key: 'SERVICES', label: 'Services & schedules', hint: 'Edit services, staff names and working hours.' },
  { key: 'PROMOTIONS', label: 'Promotions', hint: 'Create and edit promotions.' },
  { key: 'TEAM_PAYROLL', label: 'Payroll', hint: 'See payroll, mark work paid, credit work to staff.' },
  { key: 'STORE_SETTINGS', label: 'Store settings', hint: 'Contact details, delivery, branding, opening hours.' },
  { key: 'INSIGHTS', label: 'Insights', hint: 'See sales statistics.' }
])

export const RISKY_PERMISSIONS = Object.freeze([
  { key: 'CONFIRM_PAYMENTS', label: 'Confirm payments', hint: 'Confirm order payments, booking cash codes and EFT proof.' },
  { key: 'DELETE_RECORDS', label: 'Delete records', hint: 'Permanently delete orders, bookings and products.' },
  { key: 'CANCEL_BOOKINGS', label: 'Cancel bookings', hint: 'Cancel a customer’s booking.' }
])

export const ALL_PERMISSIONS = Object.freeze([...AREA_PERMISSIONS, ...RISKY_PERMISSIONS].map((p) => p.key))

/** What staff could do before permissions existed; the server uses the same set when none are saved. */
export const DEFAULT_STAFF_PERMISSIONS = Object.freeze([
  'ORDERS',
  'BOOKINGS',
  'PAYMENTS',
  'PROMOTIONS',
  'INSIGHTS',
  'CONFIRM_PAYMENTS',
  'DELETE_RECORDS',
  'CANCEL_BOOKINGS'
])

/** Never grantable to staff (shown next to the editor). */
export const OWNER_ONLY_ACTIONS = Object.freeze([
  'Subscription & billing',
  'Store banking details',
  'Adding, editing and removing staff',
  'Changing anyone’s permissions'
])

/**
 * Admin pages and the permissions that open them (any one is enough). Pages not listed are open to every
 * member: dashboard, alerts, help, plan status (read-only for staff), and Team & Payroll (staff see their income).
 */
export const ROUTE_PERMISSIONS = Object.freeze({
  'merchant-admin-orders': ['ORDERS'],
  'merchant-admin-order-invoice': ['ORDERS'],
  'merchant-admin-salon-bookings': ['BOOKINGS'],
  'merchant-admin-salon-payments': ['PAYMENTS'],
  'merchant-admin-products': ['PRODUCTS'],
  'merchant-admin-salon': ['SERVICES'],
  'merchant-admin-salon-staff': ['SERVICES'],
  'merchant-admin-promotions': ['PROMOTIONS'],
  'merchant-admin-insights': ['INSIGHTS'],
  'merchant-admin-store': ['STORE_SETTINGS']
})

/**
 * Normalises the `/admin/access/me` response. Without one yet, owners get everything and staff get the
 * default staff access, so the UI never shows more than the server would allow by default.
 */
export function effectiveAccess(user, accessResponse) {
  if (accessResponse && Array.isArray(accessResponse.permissions)) {
    const isOwner = Boolean(accessResponse.isOwner)
    return {
      isOwner,
      permissions: isOwner ? [...ALL_PERMISSIONS] : accessResponse.permissions.filter((p) => ALL_PERMISSIONS.includes(p)),
      loaded: true
    }
  }
  if (isMerchantOwner(user)) return { isOwner: true, permissions: [...ALL_PERMISSIONS], loaded: false }
  if (isMerchantUser(user)) return { isOwner: false, permissions: [...DEFAULT_STAFF_PERMISSIONS], loaded: false }
  return { isOwner: false, permissions: [], loaded: false }
}

export function hasPermission(access, permission) {
  if (!access) return false
  return access.isOwner || (Array.isArray(access.permissions) && access.permissions.includes(permission))
}

export function canOpenRoute(access, routeName) {
  const needed = ROUTE_PERMISSIONS[String(routeName || '')]
  if (!needed) return true
  return needed.some((p) => hasPermission(access, p))
}
