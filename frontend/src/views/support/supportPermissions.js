/**
 * Support permissions the API accepts (backend enum `SupportPermission`), in its declared order.
 * PLATFORM_ADMIN always has all of them; only platform admins may change a support user's set.
 */
export const SUPPORT_PERMISSIONS = Object.freeze([
  { value: 'MANAGE_SUBSCRIPTIONS', label: 'Billing & subscriptions', hint: 'Billing tab: subscriptions and legacy proofs' },
  { value: 'MANAGE_PLANS', label: 'Edit plans', hint: 'Change plan fees, limits and features' },
  { value: 'MANAGE_MERCHANTS', label: 'Manage merchants', hint: 'Create, edit and remove merchant workspaces' },
  { value: 'USE_SHADOW', label: 'Use shadow help', hint: "Enter a store's admin as its owner (audited)" },
  { value: 'MANAGE_TICKETS', label: 'Help tickets', hint: 'View and resolve merchant tickets' },
  { value: 'MANAGE_FEATURES', label: 'Feature flags', hint: 'Turn platform features on or off' },
  { value: 'VIEW_AUDIT', label: 'Audit log', hint: 'Read the support audit trail' },
  { value: 'MANAGE_STAFF', label: 'Staff roster', hint: 'See support staff and add support users' },
  { value: 'VIEW_OPS', label: 'Orders, bookings & referrals', hint: 'Platform wide orders, bookings and referrals' },
  { value: 'VIEW_USERS', label: 'View users', hint: 'Search every user account on the platform' },
  {
    value: 'MANAGE_USERS',
    label: 'Manage users',
    hint: 'Suspend, reactivate and send password resets (support and admin accounts stay platform admin only)'
  }
])

const LABELS = Object.fromEntries(SUPPORT_PERMISSIONS.map((p) => [p.value, p.label]))

export function permissionLabel(value) {
  return LABELS[value] || String(value || '')
}

/** Keeps only values the API knows, in the canonical order. */
export function normalizePermissions(values) {
  const set = new Set((values || []).map((v) => String(v).toUpperCase()))
  return SUPPORT_PERMISSIONS.map((p) => p.value).filter((v) => set.has(v))
}
