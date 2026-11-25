import { describe, expect, it, vi } from 'vitest'
import {
  bookingStatusBody,
  bookingStatusNeedsHandoverCode,
  canHandOverUnpaidCashOrder,
  canStartUnpaidCashBooking,
  handoverCodeHint,
  normalizeHandoverCode,
  orderFulfillmentBody,
  orderStatusNeedsHandoverCode,
  readHandoverCode
} from '@/utils/handoverCode'
import { checkOrderOrBookingReturn } from '@/utils/paymentReturn'
import { friendlyError } from '@/utils/errorMessages'

describe('readHandoverCode', () => {
  it('reads the handover code for every payment method', () => {
    expect(readHandoverCode({ handoverCode: '123456', paymentMethod: 'online' })).toBe('123456')
    expect(readHandoverCode({ handoverCode: 654321 })).toBe('654321')
  })

  it('falls back to the cash code from servers before the change', () => {
    expect(readHandoverCode({ cashPaymentCode: '111222' })).toBe('111222')
    expect(readHandoverCode({ handoverCode: '  ', cashPaymentCode: '111222' })).toBe('111222')
  })

  it('is empty when there is no code', () => {
    expect(readHandoverCode({})).toBe('')
    expect(readHandoverCode(null)).toBe('')
    expect(readHandoverCode({ handoverCode: null })).toBe('')
  })
})

describe('normalizeHandoverCode', () => {
  it('drops every space the customer read out', () => {
    expect(normalizeHandoverCode(' 123 456 ')).toBe('123456')
    expect(normalizeHandoverCode('123\t456')).toBe('123456')
    expect(normalizeHandoverCode(null)).toBe('')
  })
})

describe('when staff must enter the code', () => {
  it('asks for it only when an order is handed over (completed)', () => {
    expect(orderStatusNeedsHandoverCode('processing', 'completed')).toBe(true)
    expect(orderStatusNeedsHandoverCode('ready', 'COMPLETED')).toBe(true)
    expect(orderStatusNeedsHandoverCode('processing', 'ready')).toBe(false)
    expect(orderStatusNeedsHandoverCode('completed', 'completed')).toBe(false)
  })

  it('asks for it when a service starts, including completing straight from paid', () => {
    expect(bookingStatusNeedsHandoverCode('confirmed', 'in_progress')).toBe(true)
    expect(bookingStatusNeedsHandoverCode('pending', 'in_progress')).toBe(true)
    expect(bookingStatusNeedsHandoverCode('confirmed', 'completed')).toBe(true)
    expect(bookingStatusNeedsHandoverCode('in_progress', 'completed')).toBe(false)
    expect(bookingStatusNeedsHandoverCode('in_progress', 'in_progress')).toBe(false)
    expect(bookingStatusNeedsHandoverCode('pending', 'cancelled')).toBe(false)
    expect(bookingStatusNeedsHandoverCode('pending', 'confirmed')).toBe(false)
  })

  it('offers the one step cash handover only for unpaid cash orders', () => {
    expect(canHandOverUnpaidCashOrder({ status: 'pending_payment', payment_method: 'cash_store' })).toBe(true)
    expect(canHandOverUnpaidCashOrder({ status: 'pending_payment', paymentMethod: 'cash_store' })).toBe(true)
    expect(canHandOverUnpaidCashOrder({ status: 'pending_payment', paymentMethod: 'online' })).toBe(false)
    expect(canHandOverUnpaidCashOrder({ status: 'paid', paymentMethod: 'cash_store' })).toBe(false)
    expect(
      canHandOverUnpaidCashOrder({ status: 'pending_payment', paymentMethod: 'cash_store', cancelledAt: '2026-11-01' })
    ).toBe(false)
    expect(canHandOverUnpaidCashOrder(null)).toBe(false)
  })

  it('offers the one step cash start only for pending cash bookings', () => {
    expect(canStartUnpaidCashBooking({ status: 'pending', clientPaymentMethod: 'cash_store' })).toBe(true)
    expect(canStartUnpaidCashBooking({ status: 'pending', clientPaymentMethod: 'online' })).toBe(false)
    expect(canStartUnpaidCashBooking({ status: 'confirmed', clientPaymentMethod: 'cash_store' })).toBe(false)
    expect(canStartUnpaidCashBooking(undefined)).toBe(false)
  })
})

describe('request bodies', () => {
  it('sends the code with the order handover and omits it otherwise', () => {
    expect(orderFulfillmentBody('Completed', ' 123 456 ')).toEqual({ status: 'completed', handoverCode: '123456' })
    expect(orderFulfillmentBody('ready')).toEqual({ status: 'ready' })
    expect(orderFulfillmentBody('ready', '')).toEqual({ status: 'ready' })
  })

  it('sends the code with the booking status change and omits it otherwise', () => {
    expect(bookingStatusBody('in_progress', '654321')).toEqual({ status: 'in_progress', code: '654321' })
    expect(bookingStatusBody('cancelled')).toEqual({ status: 'cancelled' })
  })
})

describe('customer hint', () => {
  it('tells the customer when to show the code', () => {
    expect(handoverCodeHint({ kind: 'order', paymentMethod: 'online', status: 'paid' })).toMatch(/hand over your order/)
    expect(handoverCodeHint({ kind: 'order', paymentMethod: 'online', status: 'paid' })).not.toMatch(/cash/)
    expect(handoverCodeHint({ kind: 'booking', paymentMethod: 'online', status: 'confirmed' })).toMatch(
      /start your service/
    )
  })

  it('mentions the cash payment only while a cash order or booking is unpaid', () => {
    expect(handoverCodeHint({ kind: 'order', paymentMethod: 'cash_store', status: 'pending_payment' })).toMatch(
      /cash payment/
    )
    expect(handoverCodeHint({ kind: 'order', paymentMethod: 'cash_store', status: 'paid' })).not.toMatch(/cash/)
    expect(handoverCodeHint({ kind: 'booking', paymentMethod: 'cash_store', status: 'pending' })).toMatch(
      /cash payment/
    )
  })
})

describe('error messages', () => {
  it('explains a missing or wrong code in words', () => {
    expect(friendlyError('handover_code_required')).toMatch(/handover code/)
    expect(friendlyError('invalid_handover_code')).toMatch(/doesn't match/)
    expect(friendlyError('rate_limited')).toMatch(/Too many attempts/)
  })
})

describe('payment return page', () => {
  const ret = { kind: 'order', merchantSlug: 'shop', entityId: 'o1', reference: 'ref_1' }

  it('passes on the handover code once the online payment is paid', async () => {
    const api = {
      fetchOrderStatus: vi.fn().mockResolvedValue({ paid: true, cancelled: false, handoverCode: '246810' }),
      fetchBookingStatus: vi.fn()
    }
    await expect(checkOrderOrBookingReturn(ret, api)).resolves.toEqual({
      paid: true,
      cancelled: false,
      handoverCode: '246810'
    })
  })

  it('shows no code while the payment is still pending', async () => {
    const api = {
      fetchOrderStatus: vi.fn().mockResolvedValue({ paid: false, cancelled: false, handoverCode: '246810' }),
      fetchBookingStatus: vi.fn()
    }
    await expect(checkOrderOrBookingReturn(ret, api)).resolves.toEqual({ paid: false, cancelled: false })
  })
})
