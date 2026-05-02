import { describe, it, expect } from 'vitest'

/** Mirrors merchant rules: paid product orders are protected. */
function orderMayDeletePermanently(status, paymentConfirmed) {
  const st = String(status || '').toLowerCase()
  if (st === 'paid') return false
  if (paymentConfirmed) return false
  return true
}

/** Mirrors merchant rules: confirmed salon slots are protected. */
function salonBookingMayDeletePermanently(status) {
  return String(status || '').toLowerCase() !== 'confirmed'
}

describe('deletePolicy (unit)', () => {
  it('blocks paid orders', () => {
    expect(orderMayDeletePermanently('paid', false)).toBe(false)
  })

  it('allows pending_payment', () => {
    expect(orderMayDeletePermanently('pending_payment', false)).toBe(true)
  })

  it('allows cancelled orders', () => {
    expect(orderMayDeletePermanently('cancelled', false)).toBe(true)
  })

  it('blocks confirmed salon bookings', () => {
    expect(salonBookingMayDeletePermanently('confirmed')).toBe(false)
  })

  it('allows pending salon bookings', () => {
    expect(salonBookingMayDeletePermanently('pending')).toBe(true)
  })
})
