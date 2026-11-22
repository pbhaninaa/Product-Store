import { appStateKey } from '../WebAppScreen';

describe('appStateKey', () => {
  const base = { path: '/admin', loggedIn: true, role: 'MERCHANT_ADMIN', merchantSlug: 'bhani-resellers' };

  it('is identical for a repeated heartbeat (so the shell does nothing)', () => {
    expect(appStateKey({ ...base })).toBe(appStateKey({ ...base }));
  });

  it.each([
    ['path', { path: '/admin/orders' }],
    ['sign-in', { loggedIn: false }],
    ['role', { role: 'STAFF' }],
    ['store', { merchantSlug: 'crafts' }],
  ])('changes when the %s changes', (_label, change) => {
    expect(appStateKey({ ...base, ...change })).not.toBe(appStateKey(base));
  });
});
