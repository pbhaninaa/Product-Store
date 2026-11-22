import { describe, expect, it } from 'vitest'
import {
  ALL_PERMISSIONS,
  DEFAULT_STAFF_PERMISSIONS,
  canOpenRoute,
  effectiveAccess,
  hasPermission
} from '@/utils/merchantPermissions'

const owner = { roles: ['MERCHANT_OWNER'] }
const staff = { roles: ['MERCHANT_STAFF'] }

describe('effectiveAccess', () => {
  it('gives owners everything even before the access call returns', () => {
    const a = effectiveAccess(owner, null)
    expect(a.isOwner).toBe(true)
    expect(a.permissions).toEqual(ALL_PERMISSIONS)
    expect(hasPermission(a, 'DELETE_RECORDS')).toBe(true)
  })

  it('gives staff the default staff access until the server answers', () => {
    const a = effectiveAccess(staff, null)
    expect(a.isOwner).toBe(false)
    expect(a.loaded).toBe(false)
    expect(a.permissions).toEqual([...DEFAULT_STAFF_PERMISSIONS])
    expect(hasPermission(a, 'PRODUCTS')).toBe(false)
    expect(hasPermission(a, 'ORDERS')).toBe(true)
  })

  it('uses the server answer and drops unknown names', () => {
    const a = effectiveAccess(staff, { isOwner: false, permissions: ['ORDERS', 'MAKE_COFFEE'] })
    expect(a.loaded).toBe(true)
    expect(a.permissions).toEqual(['ORDERS'])
    expect(hasPermission(a, 'DELETE_RECORDS')).toBe(false)
  })

  it('treats an empty saved list as no optional access', () => {
    const a = effectiveAccess(staff, { isOwner: false, permissions: [] })
    expect(a.permissions).toEqual([])
    expect(canOpenRoute(a, 'merchant-admin-orders')).toBe(false)
    expect(canOpenRoute(a, 'merchant-admin')).toBe(true)
    expect(canOpenRoute(a, 'merchant-admin-notifications')).toBe(true)
  })

  it('trusts isOwner from the server', () => {
    const a = effectiveAccess(staff, { isOwner: true, permissions: [] })
    expect(hasPermission(a, 'STORE_SETTINGS')).toBe(true)
  })

  it('gives signed-out or non-merchant users nothing', () => {
    expect(effectiveAccess(null, null).permissions).toEqual([])
    expect(hasPermission(null, 'ORDERS')).toBe(false)
  })
})

describe('canOpenRoute', () => {
  it('maps each admin page to its permission', () => {
    const only = (p) => ({ isOwner: false, permissions: [p] })
    expect(canOpenRoute(only('ORDERS'), 'merchant-admin-orders')).toBe(true)
    expect(canOpenRoute(only('BOOKINGS'), 'merchant-admin-salon-bookings')).toBe(true)
    expect(canOpenRoute(only('PAYMENTS'), 'merchant-admin-salon-payments')).toBe(true)
    expect(canOpenRoute(only('PRODUCTS'), 'merchant-admin-products')).toBe(true)
    expect(canOpenRoute(only('SERVICES'), 'merchant-admin-salon')).toBe(true)
    expect(canOpenRoute(only('SERVICES'), 'merchant-admin-salon-staff')).toBe(true)
    expect(canOpenRoute(only('PROMOTIONS'), 'merchant-admin-promotions')).toBe(true)
    expect(canOpenRoute(only('INSIGHTS'), 'merchant-admin-insights')).toBe(true)
    expect(canOpenRoute(only('STORE_SETTINGS'), 'merchant-admin-store')).toBe(true)
    expect(canOpenRoute(only('ORDERS'), 'merchant-admin-store')).toBe(false)
  })

  it('keeps everyday pages open to every member', () => {
    const none = { isOwner: false, permissions: [] }
    for (const name of [
      'merchant-admin',
      'merchant-admin-notifications',
      'merchant-admin-help',
      'merchant-admin-subscription',
      'merchant-admin-team'
    ]) {
      expect(canOpenRoute(none, name)).toBe(true)
    }
  })
})
