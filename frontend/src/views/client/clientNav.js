/**
 * Client (customer) workspace navigation: which pages open inside the client shell, and the sidebar it shows.
 * Same shape as the provider admin and platform console sidebars (WorkspaceSidebar items).
 */

/** Pages a signed-in customer sees inside the workspace (sidebar + page header). Guests get the plain top bar. */
export const CLIENT_SHELL_ROUTES = Object.freeze(['client-hub', 'client-discover', 'provider-directory', 'client-history'])

/** True when this route should be wrapped in the client shell for this visitor. */
export function usesClientShell(routeName, signedInClient) {
  return Boolean(signedInClient) && CLIENT_SHELL_ROUTES.includes(routeName)
}

/** Sidebar pages, grouped into sections in display order. */
export function clientSidebarItems() {
  return [
    { key: 'client-hub', label: 'Hub', icon: 'home', to: { name: 'client-hub' }, exact: true, group: 'Explore' },
    {
      key: 'client-discover-shop',
      label: 'Items near me',
      icon: 'storefront',
      to: { name: 'client-discover', query: { kind: 'shop' } },
      group: 'Explore'
    },
    {
      key: 'client-discover-salon',
      label: 'Services near me',
      icon: 'event_available',
      to: { name: 'client-discover', query: { kind: 'salon' } },
      group: 'Explore'
    },
    { key: 'provider-directory', label: 'All providers', icon: 'store', to: { name: 'provider-directory' }, group: 'Explore' },
    {
      key: 'client-history',
      label: 'Orders & bookings',
      icon: 'receipt_long',
      to: { name: 'client-history' },
      group: 'My account'
    }
  ]
}

/** Pinned sidebar actions; `logout` has no route, the shell handles it. */
export function clientSidebarFooter() {
  return [{ key: 'logout', label: 'Sign out', icon: 'logout' }]
}

/** Sidebar heading: the customer's name when we know it. */
export function clientWorkspaceTitle(displayName) {
  const name = String(displayName || '').trim()
  return name || 'My account'
}
