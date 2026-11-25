import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'

/**
 * Handover code (CTO decision 2026-11): staff are prompted for the customer's code before handing an order over or
 * starting a service, and customers see the code for every payment method. The server enforces it; these checks
 * keep the screens wired to it.
 */
const SRC = path.resolve(__dirname, '../../src')
const read = (p) => fs.readFileSync(path.join(SRC, p), 'utf8')

describe('handover code prompts and display', () => {
  it('asks for the code before an order is marked completed', () => {
    const mixin = read('views/admin/mixins/adminModuleMixin.js')
    expect(mixin).toMatch(/orderStatusNeedsHandoverCode\(/)
    expect(mixin).toMatch(/updateAdminOrderFulfillment\(this\.\$route, o\.id, 'completed', normalizeHandoverCode/)
    const view = read('views/admin/AdminOrdersView.vue')
    expect(view).toMatch(/v-model="orderHandoverDialogOpen"/)
    expect(view).toMatch(/:rules="fieldRules\.handoverCode"/)
  })

  it('asks for the code before a service starts', () => {
    const view = read('views/admin/AdminSalonBookingsView.vue')
    expect(view).toMatch(/bookingStatusNeedsHandoverCode\(item\.status, status\)/)
    expect(view).toMatch(/v-model="bookingStartDialogOpen"/)
    expect(view).toMatch(/normalizeHandoverCode\(this\.bookingStartCodeInput\)/)
  })

  it('sends the code to the server', () => {
    expect(read('services/adminApi.js')).toMatch(/orderFulfillmentBody\(status, handoverCode\)/)
    expect(read('services/salonAdmin.js')).toMatch(/bookingStatusBody\(status, code\)/)
  })

  it('shows customers the handover code, not only the cash code', () => {
    for (const f of ['views/TrackView.vue', 'views/ClientHistoryView.vue']) {
      const s = read(f)
      expect(s, f).toMatch(/readHandoverCode/)
      expect(s, f).not.toMatch(/\.cashPaymentCode/)
    }
    const checkout = read('views/CheckoutView.vue')
    expect(checkout).toMatch(/successHandoverCode = placed\.handoverCode/)
    expect(checkout).not.toMatch(/successCashPaymentCode/)
    expect(read('views/SalonBookView.vue')).toMatch(/v-if="lastBooking\.handoverCode"/)
    expect(read('views/PaymentReturnView.vue')).toMatch(/this\.handoverCode = outcome\.handoverCode/)
  })
})
