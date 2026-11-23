import { beforeEach, describe, expect, it, vi } from 'vitest'

// The router module eagerly imports HomeView; stub it so the guard can be tested without compiling SFCs.
vi.mock('@/views/HomeView.vue', () => ({ default: { name: 'HomeView', render: () => null } }))

const session = { user: null }
vi.mock('@/services/auth', () => {
  const roles = (u) => (u && u.roles) || []
  return {
    getSessionUser: () => session.user,
    isClientUser: (u) => roles(u).includes('CLIENT'),
    isSupportOrPlatformOnlyUser: (u) => {
      const r = roles(u)
      const merchant = r.includes('MERCHANT_OWNER') || r.includes('MERCHANT_STAFF')
      return (r.includes('SUPPORT_USER') || r.includes('PLATFORM_ADMIN')) && !merchant
    },
    isShadowSession: () => Boolean(session.user && session.user.shadowSupport),
    logout: vi.fn()
  }
})

const { default: router } = await import('@/router/index.js')

function runGuard(path) {
  const to = router.resolve(path).route
  let result = 'not-called'
  router.beforeHooks[0](to, router.currentRoute, (arg) => {
    result = arg === undefined ? 'allowed' : arg
  })
  return result
}

describe('router guard for support users opening merchant admin', () => {
  beforeEach(() => {
    session.user = null
  })

  it('bounces a support user without a shadow session back to the console', () => {
    session.user = { roles: ['SUPPORT_USER'], tenant: '', shadowSupport: false }
    expect(runGuard('/m/acme/admin/orders')).toEqual({ name: 'support-dashboard', replace: true })
  })

  it('lets a shadow session (merchant-owner token minted for support) into that store admin', () => {
    session.user = { roles: ['MERCHANT_OWNER'], tenant: 'acme', shadowSupport: true }
    expect(runGuard('/m/acme/admin/orders')).toBe('allowed')
    expect(runGuard('/m/acme/admin/salon/bookings')).toBe('allowed')
  })

  it('keeps the support console closed to signed-out visitors', () => {
    const res = runGuard('/support/orders')
    expect(res).toEqual({ path: '/m/platform/admin', query: { support: '1' } })
  })

  it('resolves the admin pages the support console opens', () => {
    for (const name of ['merchant-admin', 'merchant-admin-orders', 'merchant-admin-salon-bookings']) {
      const { route } = router.resolve({ name, params: { merchantSlug: 'acme' } })
      expect(route.name).toBe(name)
      expect(route.path.startsWith('/m/acme/admin')).toBe(true)
    }
  })

  it('gives Team & Payroll its own lead rather than the staff-management copy', () => {
    const lead = (name) => router.resolve({ name, params: { merchantSlug: 'acme' } }).route.meta.adminLead
    expect(lead('merchant-admin-team')).toBeTruthy()
    expect(lead('merchant-admin-team')).not.toBe(lead('merchant-admin-salon-staff'))
    expect(lead('merchant-admin-team')).toMatch(/payroll/i)
    expect(lead('merchant-admin-promotions')).toMatch(/applied at checkout/)
  })
})

describe('router guard for platform admin only payout pages', () => {
  beforeEach(() => {
    session.user = null
  })

  it('sends support staff back to the console home and lets platform admins in (AC-5)', () => {
    session.user = { roles: ['SUPPORT_USER'], tenant: '' }
    for (const path of ['/support/payouts', '/support/payouts/t1/release', '/support/payout-history']) {
      expect(runGuard(path), path).toEqual({ name: 'support-dashboard', replace: true })
    }
    session.user = { roles: ['PLATFORM_ADMIN'], tenant: '' }
    for (const path of ['/support/payouts', '/support/payouts/t1/release', '/support/payout-history']) {
      expect(runGuard(path), path).toBe('allowed')
    }
    expect(router.resolve('/support/payouts/t1/release').route.name).toBe('support-payout-release')
    expect(router.resolve('/support/payouts/t1/release').route.params.tenantId).toBe('t1')
  })
})
