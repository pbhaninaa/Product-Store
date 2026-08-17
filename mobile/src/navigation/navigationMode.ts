/** Single source of truth: which mobile shell nav is shown (never both). */

export type MobileNavMode = 'bottom' | 'hamburger' | 'none';

const AUTH_PATHS = new Set([
  '/signup',
  '/forgot-password',
  '/reset-password',
]);

export function normalizeNavRole(role: string | null | undefined): string {
  return (role || '').toUpperCase().trim();
}

export function isGuestShopperPath(path?: string): boolean {
  const p = (path || '/').split('?')[0] || '/';
  if (!p.startsWith('/m/')) return false;
  return !p.includes('/admin');
}

export function isHamburgerNavRole(role: string | null | undefined): boolean {
  const r = normalizeNavRole(role);
  return (
    r === 'MERCHANT_OWNER' ||
    r === 'MERCHANT_STAFF' ||
    r === 'SUPPORT_USER' ||
    r === 'PLATFORM_ADMIN' ||
    r.includes('MERCHANT') ||
    r.includes('SUPPORT') ||
    r.includes('PLATFORM')
  );
}

/**
 * bottom    → guest storefront tabs under /m/:slug
 * hamburger → merchant admin / support web chrome
 * none      → auth / onboarding
 */
export function resolveMobileNavMode(
  role: string | null | undefined,
  loggedIn: boolean,
  path?: string,
): MobileNavMode {
  const p = (path || '/').split('?')[0] || '/';
  if (AUTH_PATHS.has(p)) return 'none';
  if (p.startsWith('/support')) return 'hamburger';
  if (p.includes('/admin')) return 'hamburger';
  if (loggedIn && isHamburgerNavRole(role)) return 'hamburger';
  if (isGuestShopperPath(p)) return 'bottom';
  return 'none';
}

export function shouldShowBottomNav(
  path: string,
  loggedIn: boolean,
  role: string | null | undefined,
): boolean {
  return resolveMobileNavMode(role, loggedIn, path) === 'bottom';
}

export function merchantSlugFromPath(path: string, fallback: string): string {
  const m = (path || '').match(/^\/m\/([^/]+)/);
  return m?.[1] || fallback;
}
