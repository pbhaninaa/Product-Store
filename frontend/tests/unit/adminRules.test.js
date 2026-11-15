import { describe, expect, it } from 'vitest'
import {
  bookingAwaitingInAppPayment,
  canMerchantConfirmBooking,
  canOpenAdminRoute,
  forwardFulfilmentOptions,
  isOwnerOnlyAdminRoute,
  storefrontContactPath,
  storefrontShopPath,
  teamHubTabsFor
} from '@/views/admin/adminRules'

const owner = { roles: ['MERCHANT_OWNER'] }
const staff = { roles: ['MERCHANT_STAFF'] }
const ownerAndStaff = { roles: ['MERCHANT_OWNER', 'MERCHANT_STAFF'] }

describe('admin access rules', () => {
  it('marks owner-only admin pages', () => {
    for (const name of [
      'merchant-admin-products',
      'merchant-admin-store',
      'merchant-admin-salon',
      'merchant-admin-salon-staff',
      'merchant-admin-help'
    ]) {
      expect(isOwnerOnlyAdminRoute(name)).toBe(true)
    }
    for (const name of [
      'merchant-admin',
      'merchant-admin-orders',
      'merchant-admin-salon-bookings',
      'merchant-admin-notifications',
      'merchant-admin-promotions',
      'merchant-admin-team'
    ]) {
      expect(isOwnerOnlyAdminRoute(name)).toBe(false)
    }
  })

  it('blocks staff from owner-only pages but not owners', () => {
    expect(canOpenAdminRoute(staff, 'merchant-admin-products')).toBe(false)
    expect(canOpenAdminRoute(staff, 'merchant-admin-help')).toBe(false)
    expect(canOpenAdminRoute(staff, 'merchant-admin-orders')).toBe(true)
    expect(canOpenAdminRoute(owner, 'merchant-admin-products')).toBe(true)
    expect(canOpenAdminRoute(ownerAndStaff, 'merchant-admin-store')).toBe(true)
  })

  it('gives staff only the My income tab in Team & Payroll', () => {
    expect(teamHubTabsFor(staff)).toEqual(['income'])
    expect(teamHubTabsFor(owner)).toEqual(['team', 'payroll', 'income'])
  })
})

describe('forwardFulfilmentOptions', () => {
  const values = (status) => forwardFulfilmentOptions(status, false).map((o) => o.value)

  it('offers only forward steps', () => {
    expect(values('processing')).toEqual(['processing', 'ready', 'completed'])
    expect(values('ready')).toEqual(['ready', 'completed'])
    expect(values('completed')).toEqual(['completed'])
  })

  it('never offers Processing once an order is Ready', () => {
    expect(values('ready')).not.toContain('processing')
  })

  it('labels the ready step by delivery type', () => {
    expect(forwardFulfilmentOptions('processing', true)[1].text).toBe('Out for delivery')
    expect(forwardFulfilmentOptions('processing', false)[1].text).toBe('Ready for pickup')
  })
})

describe('canMerchantConfirmBooking', () => {
  const pending = (extra) => ({ status: 'pending', paymentVerificationState: 'not_applicable', ...extra })

  it('hides Confirm booking for unpaid PayFast / Peach bookings', () => {
    expect(canMerchantConfirmBooking(pending({ clientPaymentMethod: 'payfast' }))).toBe(false)
    expect(canMerchantConfirmBooking(pending({ clientPaymentMethod: 'PEACH' }))).toBe(false)
    expect(bookingAwaitingInAppPayment(pending({ clientPaymentMethod: 'payfast' }))).toBe(true)
  })

  it('hides it for cash and EFT that still need payment checks', () => {
    expect(canMerchantConfirmBooking(pending({ clientPaymentMethod: 'cash_store' }))).toBe(false)
    expect(
      canMerchantConfirmBooking(pending({ clientPaymentMethod: 'eft', paymentVerificationState: 'manual_pending' }))
    ).toBe(false)
    expect(
      canMerchantConfirmBooking(pending({ clientPaymentMethod: 'eft', paymentVerificationState: 'awaiting_proof' }))
    ).toBe(false)
  })

  it('allows it for EFT that has been verified, and never for non-pending rows', () => {
    expect(
      canMerchantConfirmBooking(pending({ clientPaymentMethod: 'eft', paymentVerificationState: 'manual_approved' }))
    ).toBe(true)
    expect(canMerchantConfirmBooking({ status: 'confirmed', clientPaymentMethod: 'payfast' })).toBe(false)
    expect(bookingAwaitingInAppPayment({ status: 'confirmed', clientPaymentMethod: 'payfast' })).toBe(false)
  })
})

describe('storefront links', () => {
  it('points at the merchant storefront, not the platform root', () => {
    expect(storefrontShopPath('my-shop')).toBe('/m/my-shop')
    expect(storefrontContactPath('my-shop')).toBe('/m/my-shop/contact')
  })
})
