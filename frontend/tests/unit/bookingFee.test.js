import { describe, expect, it } from 'vitest'
import {
  amountDueLabel,
  bookingFeePercentError,
  bookingFeePreview,
  bookingFeeStatusLabel,
  cancelConfirmText,
  cancellationOutcomeMessage,
  feeRefundableIfCancelledAt,
  noShowConfirmText,
  nonRefundableWarning,
  readBookingFee,
  receiptLineLabel,
  receiptLines
} from '@/utils/bookingFee'
import { checkOrderOrBookingReturn } from '@/utils/paymentReturn'
import { friendlyError } from '@/utils/errorMessages'
import { formatZar } from '@/utils/price'

describe('bookingFeePercentError (US-1 AC-2)', () => {
  it('accepts whole percents from 0 to 50', () => {
    for (const ok of ['0', '1', '20', '50', ' 35 ', 20]) expect(bookingFeePercentError(ok)).toBe('')
  })

  it('rejects 60, negatives, fractions and blanks with the allowed range', () => {
    for (const bad of ['60', '51', '-1', '12.5', '', null, 'abc']) {
      expect(bookingFeePercentError(bad)).toBe('The booking fee must be a whole number from 0% to 50%.')
    }
  })

  it('matches the server error message', () => {
    expect(friendlyError('booking_fee_percent_out_of_range')).toBe(
      'The booking fee must be a whole number from 0% to 50%.'
    )
  })
})

describe('bookingFeePreview (US-2 AC-1, NFR-1)', () => {
  it('R1 000 at 20% is R200 online and R800 at the shop', () => {
    expect(bookingFeePreview('1000.00', 20)).toEqual({ feeZar: '200.00', remainderZar: '800.00', totalZar: '1000.00' })
  })

  it('rounds half up to the cent and the parts add up to the total', () => {
    expect(bookingFeePreview('999.99', 50)).toEqual({ feeZar: '500.00', remainderZar: '499.99', totalZar: '999.99' })
    expect(bookingFeePreview('10.05', 10)).toEqual({ feeZar: '1.01', remainderZar: '9.04', totalZar: '10.05' })
  })

  it('shows no fee for 0%, out of range percents or a fee below the Paystack minimum (edge case 6)', () => {
    expect(bookingFeePreview('1000', 0)).toBeNull()
    expect(bookingFeePreview('1000', 60)).toBeNull()
    expect(bookingFeePreview('4.00', 20)).toBeNull() // R0.80 is below R1.00
    expect(bookingFeePreview('5.00', 20, '1.00')).toEqual({ feeZar: '1.00', remainderZar: '4.00', totalZar: '5.00' })
    expect(bookingFeePreview('', 20)).toBeNull()
  })
})

describe('24 hour refund boundary (BR-4, BR-5, edge cases 1 and 2)', () => {
  const start = '2026-12-01T08:00:00.000Z'

  it('is refundable at exactly 24 hours and earlier', () => {
    expect(feeRefundableIfCancelledAt(start, '2026-11-30T08:00:00.000Z')).toBe(true)
    expect(feeRefundableIfCancelledAt(start, '2026-11-30T07:00:00.000Z')).toBe(true)
  })

  it('is not refundable within 24 hours', () => {
    expect(feeRefundableIfCancelledAt(start, '2026-11-30T08:00:01.000Z')).toBe(false)
    expect(feeRefundableIfCancelledAt(start, '2026-12-01T07:00:00.000Z')).toBe(false)
    expect(feeRefundableIfCancelledAt('not a date', start)).toBe(false)
  })

  it('warns before paying when the booking starts within 24 hours', () => {
    expect(nonRefundableWarning(start, '2026-12-01T03:00:00.000Z')).toMatch(/not refundable once paid/)
    expect(nonRefundableWarning(start, '2026-11-28T03:00:00.000Z')).toMatch(/at least 24 hours/)
  })
})

describe('server booking fee display (FR-6, FR-7)', () => {
  const booking = {
    bookingFee: {
      percent: 20,
      feeZar: '200.00',
      remainderZar: '800.00',
      totalZar: '1000.00',
      status: 'paid',
      reason: null,
      refundableIfCancelledNow: true
    },
    receiptLines: [
      { kind: 'booking_fee_online', amountZar: '200.00' },
      { kind: 'due_at_shop', amountZar: '800.00' },
      { kind: 'total', amountZar: '1000.00' }
    ]
  }

  it('reads the fee block and is null without one', () => {
    expect(readBookingFee(booking)).toMatchObject({ feeZar: '200.00', remainderZar: '800.00', status: 'paid' })
    expect(readBookingFee({})).toBeNull()
    expect(readBookingFee(null)).toBeNull()
  })

  it('labels the fee, the shop amount and the total as separate lines', () => {
    expect(receiptLines(booking)).toEqual([
      { kind: 'booking_fee_online', label: 'Booking fee (paid online)', amount: formatZar('200.00') },
      { kind: 'due_at_shop', label: 'Due at the shop (cash)', amount: formatZar('800.00') },
      { kind: 'total', label: 'Total', amount: formatZar('1000.00') }
    ])
    expect(receiptLineLabel('fee_refunded')).toBe('Booking fee refunded')
    expect(receiptLineLabel('fee_kept')).toBe('Booking fee kept by the shop')
    expect(receiptLineLabel('fee_refund_in_progress')).toBe('Booking fee refund in progress')
    expect(receiptLineLabel('paid_at_shop')).toBe('Paid at the shop (cash)')
    expect(receiptLines({})).toEqual([])
  })

  it('shows staff the amount due, like "R800 due"', () => {
    expect(amountDueLabel('800.00')).toBe(`${formatZar(800)} due`)
    expect(amountDueLabel('0.00')).toBe('')
    expect(amountDueLabel('')).toBe('')
  })

  it('labels the fee status with its reason', () => {
    expect(bookingFeeStatusLabel('kept', 'no_show')).toBe('Booking fee kept (customer did not show up)')
    expect(bookingFeeStatusLabel('refunded', 'provider_cancellation')).toBe('Booking fee refunded (cancelled by the shop)')
    expect(bookingFeeStatusLabel('awaiting_payment')).toBe('Booking fee not paid yet')
    expect(bookingFeeStatusLabel('')).toBe('')
  })
})

describe('cancellation messages (US-4)', () => {
  it('tells the customer the refund outcome', () => {
    expect(cancellationOutcomeMessage('refunded', '200.00')).toMatch(/being refunded/)
    expect(cancellationOutcomeMessage('refund_pending', '200.00')).toMatch(/refund is in progress/)
    expect(cancellationOutcomeMessage('kept', '200.00')).toMatch(/not refunded/)
    expect(cancellationOutcomeMessage('none')).toBe('Booking cancelled.')
  })

  it('tells staff a cancellation refunds the fee and a no show keeps it', () => {
    expect(cancelConfirmText({ bookingFeeStatus: 'paid', bookingFeeZar: '200.00' })).toMatch(/will be refunded/)
    expect(cancelConfirmText({})).toMatch(/time is freed/)
    expect(noShowConfirmText({ bookingFeeStatus: 'paid', bookingFeeZar: '200.00' })).toMatch(/you keep/)
    expect(noShowConfirmText({})).toMatch(/no show/)
  })

  it('has messages for the new server errors', () => {
    for (const code of ['booking_fee_unpaid', 'booking_fee_paid_cancel_first', 'booking_not_cancellable', 'no_show_too_early']) {
      expect(friendlyError(code)).not.toBe('Something went wrong. Please try again.')
    }
  })
})

describe('payment return for a booking fee', () => {
  it('reports the fee as paid with the remainder due at the shop', async () => {
    const outcome = await checkOrderOrBookingReturn(
      { kind: 'booking', merchantSlug: 'shop', entityId: 'b1', reference: 'BKGX', customerEmail: '' },
      {
        fetchBookingStatus: async () => ({
          paid: true,
          paymentPurpose: 'booking_fee',
          handoverCode: '123456',
          bookingFee: { feeZar: '200.00', remainderZar: '800.00', status: 'paid' }
        }),
        fetchOrderStatus: async () => ({})
      }
    )
    expect(outcome.paid).toBe(true)
    expect(outcome.handoverCode).toBe('123456')
    expect(outcome.bookingFee).toMatchObject({ feeZar: '200.00', remainderZar: '800.00' })
  })

  it('leaves in app booking returns unchanged', async () => {
    const outcome = await checkOrderOrBookingReturn(
      { kind: 'booking', merchantSlug: 'shop', entityId: 'b1', reference: 'BKGX', customerEmail: '' },
      { fetchBookingStatus: async () => ({ paid: true, handoverCode: '123456' }), fetchOrderStatus: async () => ({}) }
    )
    expect(outcome).toEqual({ paid: true, cancelled: false, handoverCode: '123456' })
  })
})
