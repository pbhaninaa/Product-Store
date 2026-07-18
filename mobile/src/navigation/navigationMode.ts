/** Single source of truth: which mobile shell nav is shown (never both). */

export type MobileNavMode = 'bottom' | 'hamburger' | 'none';

const AUTH_PATHS = new Set([
  '/signup',
  '/forgot-password',
  '/reset-password',
]);

const RESERVED_ROOTS = new Set([
  'signup',
  'forgot-password',
  'reset-password',
  'support',
]);

export function normalizeNavRole(role: string | null | undefined): string {
  return (role || '').toUpperCase().trim();
}

export function isGuestShopperPath(path?: string): boolean {
  const p = (path || '/').split('?')[0] || '/';
  if (p.includes('/admin')) return false;
  if (AUTH_PATHS.has(p) || p.startsWith('/support')) return false;
  // Legacy /m/:slug storefront
  if (p.startsWith('/m/')) return true;
  const first = p.split('/').filter(Boolean)[0];
  if (!first || RESERVED_ROOTS.has(first)) return false;
  return true;
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
 * bottom    → guest storefront tabs under /:slug
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
  const p = path || '';
  const legacy = p.match(/^\/m\/([^/]+)/);
  if (legacy?.[1]) return legacy[1];
  const parts = p.split('/').filter(Boolean);
  if (parts[0] && !RESERVED_ROOTS.has(parts[0]) && parts[0] !== 'm') {
    return parts[0];
  }
  return fallback;
}
