import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'
import { paymentMethodLabel } from '@/utils/displayLabels'
import { friendlyError } from '@/utils/errorMessages'

/** Manual EFT was removed (CTO decision 2026-11-23): customers pay online (Paystack) or cash; merchants via Paystack. */
const SRC = path.resolve(__dirname, '../../src')
const read = (p) => fs.readFileSync(path.join(SRC, p), 'utf8')

describe('Manual EFT removal', () => {
  it('offers no Manual EFT, bank details or proof upload at checkout and salon booking', () => {
    for (const f of ['views/CheckoutView.vue', 'views/SalonBookView.vue']) {
      const s = read(f)
      expect(s, f).not.toMatch(/value="eft"|choosePayment\('eft'\)|paymentMethod = 'eft'/)
      expect(s, f).not.toMatch(/v-file-input|EftProof|eftProof|bankAccountNumber|acceptCustomerEft/)
      expect(s, f).not.toMatch(/Manual EFT/)
    }
  })

  it('no longer calls the customer proof upload endpoints or reads bank details from public shop settings', () => {
    for (const f of ['services/publicStore.js', 'services/salonPublic.js']) {
      const s = read(f)
      expect(s, f).not.toMatch(/eft-proof|needsEftProof|acceptCustomerEft|bankAccountNumber|eftBankInstructions/)
    }
  })

  it('keeps the bank fields as payout details in store settings, without the EFT toggle or instructions', () => {
    const view = read('views/admin/AdminStoreSettingsView.vue')
    expect(view).not.toMatch(/acceptCustomerEftDraft|eftNotesDraft|Manual EFT/)
    expect(view).toMatch(/Payout details/)
    for (const field of ['bankNameDraft', 'bankAccountHolderDraft', 'bankAccountNumberDraft', 'bankBranchCodeDraft']) {
      expect(view).toMatch(new RegExp(`v-model="${field}"`))
    }
    const mixin = read('views/admin/mixins/adminModuleMixin.js')
    expect(mixin).not.toMatch(/acceptCustomerEft|eftBankInstructions/)
  })

  it('pays merchant subscriptions with Paystack only', () => {
    const view = read('views/admin/AdminSubscriptionView.vue')
    expect(view).not.toMatch(/v-file-input|payment-proof|paymentProofPendingReview|paymentProofStatus/)
    expect(view).toMatch(/startOnlineCheckout/)
  })

  it('labels historical EFT rows as legacy and explains the new checkout error', () => {
    expect(paymentMethodLabel('eft')).toBe('Manual EFT (legacy)')
    expect(friendlyError('eft_not_accepted')).toBe(
      'Bank transfer is no longer available. Pay online (card or Instant EFT) or in store.'
    )
  })
})
