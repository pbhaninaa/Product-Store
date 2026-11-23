import { describe, expect, it } from 'vitest'
import {
  inAppPaymentLabel,
  isInAppPaymentMethod,
  matchesPaymentMethodFilter
} from '@/utils/inAppPayment'
import { paymentMethodLabel } from '@/utils/displayLabels'

describe('inAppPayment', () => {
  it('treats online as the only in app payment method', () => {
    expect(isInAppPaymentMethod('online')).toBe(true)
    expect(isInAppPaymentMethod(' ONLINE ')).toBe(true)
    expect(isInAppPaymentMethod('eft')).toBe(false)
    expect(isInAppPaymentMethod('cash_store')).toBe(false)
    expect(isInAppPaymentMethod('')).toBe(false)
    expect(isInAppPaymentMethod(null)).toBe(false)
  })

  it('matches the payment method filter exactly', () => {
    expect(matchesPaymentMethodFilter('online', 'online')).toBe(true)
    expect(matchesPaymentMethodFilter('ONLINE', 'online')).toBe(true)
    expect(matchesPaymentMethodFilter('eft', 'online')).toBe(false)
    expect(matchesPaymentMethodFilter('eft', '')).toBe(true)
  })

  it('labels the online channel without naming the provider', () => {
    expect(inAppPaymentLabel('online', 'CARD')).toBe('Online · Card')
    expect(inAppPaymentLabel('online', 'eft')).toBe('Online · Instant EFT')
    expect(inAppPaymentLabel('online', '')).toBe('Online')
    expect(inAppPaymentLabel('online', null)).toBe('Online')
    expect(inAppPaymentLabel('online', 'CARD')).not.toMatch(/paystack/i)
  })

  it('has no in app label for other methods', () => {
    expect(inAppPaymentLabel('eft', 'CARD')).toBeNull()
    expect(inAppPaymentLabel('cash_store', null)).toBeNull()
  })

  it('names the online payment method in words', () => {
    expect(paymentMethodLabel('online')).toBe('Online (card or Instant EFT)')
    expect(paymentMethodLabel('cash_store')).toBe('Cash in store')
  })
})
