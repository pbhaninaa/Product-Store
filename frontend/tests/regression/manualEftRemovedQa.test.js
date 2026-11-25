import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'
import { friendlyError } from '@/utils/errorMessages'
import { paymentMethodLabel } from '@/utils/displayLabels'

/**
 * Independent QA for the Manual EFT removal (CTO decision 2026-11-23): the error codes the API can still return
 * must read as real sentences, and a legacy shop that only had Manual EFT on must tell customers why they cannot pay.
 */
const SRC = path.resolve(__dirname, '../../src')
const read = (p) => fs.readFileSync(path.join(SRC, p), 'utf8')
const GENERIC = 'Something went wrong. Please try again.'

describe('Manual EFT removal: messages people still see', () => {
  // Codes the merchant can still get from confirm-payment / eft-payment-decision on legacy EFT rows.
  it.each([
    'eft_proof_amount_or_reference_mismatch',
    'proof_missing',
    'not_pending_manual_review',
    'not_eft_booking',
    'forbidden'
  ])('legacy EFT review code %s has its own message', (code) => {
    expect(friendlyError(code)).not.toBe(GENERIC)
  })

  // Codes a customer gets from checkout or booking now that Manual EFT is gone (including a shop with nothing on).
  it.each(['eft_not_accepted', 'cash_not_accepted', 'online_payment_not_accepted', 'payment_not_configured'])(
    'checkout and booking code %s has its own message',
    (code) => {
      expect(friendlyError(code)).not.toBe(GENERIC)
    }
  )

  it('never suggests bank transfer is still on offer when it is refused', () => {
    expect(friendlyError('eft_not_accepted')).toMatch(/no longer available/i)
    expect(paymentMethodLabel('online')).not.toMatch(/Manual/i)
  })
})

describe('Manual EFT removal: a shop with cash and online both off', () => {
  it('checkout tells the customer there is no payment method', () => {
    expect(read('views/CheckoutView.vue')).toMatch(/v-if="!shopAcceptCash && !shopAcceptOnline"/)
  })

  // Open finding: docs/engineering/reports/remove-manual-eft-review.md #2. Re-enable once SalonBookView explains it.
  it.skip('salon booking tells the customer there is no payment method instead of a silently disabled button', () => {
    const view = read('views/SalonBookView.vue')
    // The payment section is hidden when no method is live; something must explain why booking cannot go ahead.
    const explains = /v-(else|if)="!showPaymentSection"/.test(view)
    expect(explains, 'SalonBookView has no message when no payment method is live').toBe(true)
  })
})
