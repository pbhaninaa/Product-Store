import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'

/**
 * Booking fee (docs/engineering/requirements/booking-fee.md): the server decides fees, refunds and kept fees; these
 * checks keep the screens wired to the API so the provider setting, the booking flow, staff and customers see it.
 */
const SRC = path.resolve(__dirname, '../../src')
const read = (p) => fs.readFileSync(path.join(SRC, p), 'utf8')

describe('booking fee screens', () => {
  it('lets the provider set the fee in the store settings, for those allowed (US-1)', () => {
    const card = read('views/admin/BookingFeeSettingsCard.vue')
    expect(card).toMatch(/updateAdminBookingFee\(this\.\$route/)
    expect(card).toMatch(/can\('STORE_SETTINGS'\)/)
    expect(card).toMatch(/bookingFeePercentError/)
    expect(read('views/admin/AdminStoreSettingsView.vue')).toMatch(/<booking-fee-settings-card/)
    expect(read('services/adminApi.js')).toMatch(/\/admin\/store\/booking-fee/)
  })

  it('shows the fee now, the rest at the shop and the refund warning before paying (US-2, edge case 1)', () => {
    const view = read('views/SalonBookView.vue')
    expect(view).toMatch(/v-if="bookingFeeApplies"/)
    expect(view).toMatch(/bookingFeeQuote\.remainderZar/)
    expect(view).toMatch(/nonRefundableWarning\(/)
    expect(view).toMatch(/this\.paymentMethod === 'online' \|\| this\.bookingFeeApplies \? this\.onlinePaymentChannel : null/)
  })

  it('confirms the fee on the payment return page', () => {
    expect(read('views/PaymentReturnView.vue')).toMatch(/this\.bookingFee = outcome\.bookingFee/)
  })

  it('shows staff the amount due and offers the no show action (US-3, US-4 AC-3)', () => {
    const view = read('views/admin/AdminSalonBookingsView.vue')
    expect(view).toMatch(/amountDueLabel\(item\.amountDueZar\)/)
    expect(view).toMatch(/amountDueLabel\(bookingCashTarget\.amountDueZar\)/)
    expect(view).toMatch(/postAdminSalonBookingNoShow\(this\.\$route, item\.id\)/)
    expect(view).toMatch(/showNoShow\(item\) && can\('CANCEL_BOOKINGS'\)/)
    expect(read('services/salonAdmin.js')).toMatch(/\/no-show`/)
  })

  it('shows receipt lines on the Payments page and to customers, with a cancel that reports the refund (FR-7, FR-8)', () => {
    expect(read('views/admin/AdminSalonPaymentsView.vue')).toMatch(/<booking-payment-summary/)
    for (const f of ['views/TrackView.vue', 'views/ClientHistoryView.vue']) {
      const s = read(f)
      expect(s, f).toMatch(/<booking-payment-summary/)
      expect(s, f).toMatch(/<booking-cancel-button/)
    }
    expect(read('components/BookingCancelButton.vue')).toMatch(/cancellationOutcomeMessage\(/)
    expect(read('services/salonPublic.js')).toMatch(/\/cancel`/)
    expect(read('services/clientNearby.js')).toMatch(/\/cancel`/)
  })
})
