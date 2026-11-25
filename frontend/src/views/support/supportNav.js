/**
 * Whether a console tab shows for the signed-in console user. `adminOnly` tabs (payouts, commission)
 * show to platform admins only: support staff hold permissions, but never these pages. UX only, the
 * API answers 403 to anyone else.
 */
export function isTabVisible(tab, { platformAdmin, can }) {
  if (tab && tab.adminOnly) return Boolean(platformAdmin)
  return Boolean(can(tab && tab.perm))
}

/** True when the session belongs to a platform admin (roles from the signed-in user). */
export function isPlatformAdminUser(user) {
  return Boolean(user && Array.isArray(user.roles) && user.roles.includes('PLATFORM_ADMIN'))
}
