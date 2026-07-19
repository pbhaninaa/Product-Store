/** Merchant role helpers for admin UI gating. */

export function rolesOf(user) {
  return (user && Array.isArray(user.roles) && user.roles) || []
}

export function isMerchantOwner(user) {
  return rolesOf(user).includes('MERCHANT_OWNER')
}

/** Staff member who is not also an owner. */
export function isMerchantStaffOnly(user) {
  const roles = rolesOf(user)
  return roles.includes('MERCHANT_STAFF') && !roles.includes('MERCHANT_OWNER')
}

export function isMerchantUser(user) {
  const roles = rolesOf(user)
  return roles.includes('MERCHANT_OWNER') || roles.includes('MERCHANT_STAFF')
}

/** Admin routes staff may open (day-to-day ops). */
export const STAFF_ALLOWED_ADMIN_ROUTE_NAMES = new Set([
  'merchant-admin',
  'merchant-admin-orders',
  'merchant-admin-salon-bookings',
  'merchant-admin-notifications',
  'merchant-admin-my-income',
  'merchant-admin-order-invoice'
])

export function isStaffAllowedAdminRoute(routeName) {
  return STAFF_ALLOWED_ADMIN_ROUTE_NAMES.has(routeName)
}
