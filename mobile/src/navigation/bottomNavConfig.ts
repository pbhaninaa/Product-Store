import { shouldShowBottomNav } from './navigationMode';

export type BottomNavTab = {
  id: string;
  label: string;
  icon: string;
  path: string;
  matchPaths?: string[];
  badgeKey?: string;
};

/** Guest storefront tabs for /m/:merchantSlug (mirrors public web routes). */
export function tabsForMerchant(slug: string): BottomNavTab[] {
  const base = `/m/${slug}`;
  return [
    { id: 'home', label: 'Shop', icon: 'storefront-outline', path: base },
    {
      id: 'salon',
      label: 'Salon',
      icon: 'content-cut',
      path: `${base}/salon/services`,
      matchPaths: [`${base}/salon`],
    },
    {
      id: 'cart',
      label: 'Cart',
      icon: 'cart-outline',
      path: `${base}/checkout`,
      matchPaths: [`${base}/checkout`, `${base}/peach/return`],
    },
    {
      id: 'contact',
      label: 'Contact',
      icon: 'email-outline',
      path: `${base}/contact`,
    },
    {
      id: 'account',
      label: 'Admin',
      icon: 'account-cog-outline',
      path: `${base}/admin`,
      matchPaths: [`${base}/admin`, '/signup', '/support'],
    },
  ];
}

export function tabsForRole(_role?: string | null, slug = 'demo'): BottomNavTab[] {
  return tabsForMerchant(slug);
}

export function tabMatchesPath(tab: BottomNavTab, path: string): boolean {
  const p = path.split('?')[0] || '/';
  if (p === tab.path || p.startsWith(`${tab.path}/`)) return true;
  return (tab.matchPaths ?? []).some((m) => p === m || p.startsWith(`${m}/`));
}

export function activeTabId(tabs: BottomNavTab[], path: string): string {
  const match = tabs.find((t) => tabMatchesPath(t, path));
  return match?.id ?? tabs[0]?.id ?? 'home';
}

export { shouldShowBottomNav };
