import { describe, expect, it } from 'vitest'
import {
  inAppPaymentLabel,
  isInAppPaymentMethod,
  matchesPaymentMethodFilter
} from '@/utils/inAppPayment'

describe('inAppPayment', () => {
  it('treats peach and payfast as the same in-app rail', () => {
    expect(isInAppPaymentMethod('peach')).toBe(true)
    expect(isInAppPaymentMethod('payfast')).toBe(true)
    expect(isInAppPaymentMethod('eft')).toBe(false)
  })

  it('matches PayFast filter against leftover peach rows', () => {
    expect(matchesPaymentMethodFilter('payfast', 'peach')).toBe(true)
    expect(matchesPaymentMethodFilter('peach', 'payfast')).toBe(true)
    expect(matchesPaymentMethodFilter('eft', 'peach')).toBe(false)
  })

  it('labels card and Instant EFT as PayFast', () => {
    expect(inAppPaymentLabel('payfast', 'CARD')).toBe('PAYFAST · CARD')
    expect(inAppPaymentLabel('peach', 'EFT')).toBe('PAYFAST · INSTANT EFT')
  })
})
