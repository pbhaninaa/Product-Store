import { describe, expect, it } from 'vitest'
import {
  ONLINE_PAYMENT_CHANNELS,
  ONLINE_PAYMENT_METHOD,
  isSafeRedirectUrl,
  readOnlinePaymentStart,
  startOnlinePayment
} from '@/utils/onlinePayment'
import { friendlyError } from '@/utils/errorMessages'

const CHECKOUT_URL = 'https://checkout.paystack.com/abc123'

describe('startOnlinePayment', () => {
  it('redirects to paymentRedirectUrl and returns true', () => {
    const location = { href: 'https://app.example.com/m/shop/checkout' }
    expect(startOnlinePayment({ paymentId: 'p1', paymentRedirectUrl: CHECKOUT_URL }, location)).toBe(true)
    expect(location.href).toBe(CHECKOUT_URL)
  })

  it('returns false and stays on the page without a redirect URL', () => {
    const location = { href: 'https://app.example.com/' }
    expect(startOnlinePayment({ paymentId: 'p1' }, location)).toBe(false)
    expect(startOnlinePayment({ paymentRedirectUrl: '' }, location)).toBe(false)
    expect(startOnlinePayment(null, location)).toBe(false)
    expect(startOnlinePayment(undefined, location)).toBe(false)
    expect(location.href).toBe('https://app.example.com/')
  })

  it('never follows a script, relative or plain http URL', () => {
    const location = { href: 'https://app.example.com/' }
    expect(startOnlinePayment({ paymentRedirectUrl: 'http://checkout.paystack.com/abc' }, location)).toBe(false)
    expect(startOnlinePayment({ paymentRedirectUrl: 'javascript:alert(1)' }, location)).toBe(false)
    expect(startOnlinePayment({ paymentRedirectUrl: '/somewhere' }, location)).toBe(false)
    expect(location.href).toBe('https://app.example.com/')
  })
})

describe('isSafeRedirectUrl', () => {
  it('accepts absolute https URLs only (http only for a local test server)', () => {
    expect(isSafeRedirectUrl(CHECKOUT_URL)).toBe(true)
    expect(isSafeRedirectUrl('http://checkout.paystack.com/abc')).toBe(false)
    expect(isSafeRedirectUrl('http://localhost:8080/fake-checkout')).toBe(true)
    expect(isSafeRedirectUrl('http://127.0.0.1/fake-checkout')).toBe(true)
    expect(isSafeRedirectUrl('ftp://checkout.paystack.com/abc')).toBe(false)
    expect(isSafeRedirectUrl('data:text/html,hi')).toBe(false)
    expect(isSafeRedirectUrl('not a url')).toBe(false)
    expect(isSafeRedirectUrl('')).toBe(false)
  })
})

describe('readOnlinePaymentStart', () => {
  it('reads the start payment fields of the contract', () => {
    expect(
      readOnlinePaymentStart({ needsOnlinePayment: true, paymentId: 42, paymentRedirectUrl: CHECKOUT_URL })
    ).toEqual({ needsOnlinePayment: true, paymentId: '42', paymentRedirectUrl: CHECKOUT_URL })
  })

  it('is empty when the response is not an online payment', () => {
    expect(readOnlinePaymentStart({ orderId: 'o1' })).toEqual({
      needsOnlinePayment: false,
      paymentId: '',
      paymentRedirectUrl: ''
    })
    expect(readOnlinePaymentStart(null).needsOnlinePayment).toBe(false)
  })
})

describe('online payment constants', () => {
  it('match the API contract', () => {
    expect(ONLINE_PAYMENT_METHOD).toBe('online')
    expect(ONLINE_PAYMENT_CHANNELS).toEqual(['CARD', 'EFT'])
  })
})

describe('online payment error codes', () => {
  const codes = [
    'online_payment_not_accepted',
    'online_payment_channel_required',
    'invalid_online_payment_channel',
    'online_payment_channel_not_applicable',
    'not_online_order',
    'not_online_booking',
    'online_payment_unavailable'
  ]

  it.each(codes)('maps %s to a sentence without dashes', (code) => {
    const text = friendlyError(code, 'FALLBACK')
    expect(text).not.toBe('FALLBACK')
    expect(text).not.toBe(code)
    expect(text).not.toMatch(/[-‐-―]/)
    expect(text).toMatch(/\.$/)
  })

  it('asks for the store owner email before paying online', () => {
    expect(friendlyError('owner_email_missing')).toBe(
      "Add a valid email to the store owner's account before paying online."
    )
  })

  it('maps specific codes to the expected wording', () => {
    expect(friendlyError('online_payment_channel_required')).toBe('Choose card or Instant EFT.')
    expect(friendlyError('not_online_order')).toBe('This order was not set up for online payment.')
  })

  it('shares one message for provider amount, currency and reference checks', () => {
    expect(friendlyError('online_payment_amount_mismatch')).toBe(
      'The payment could not be verified. Contact the store for help.'
    )
  })
})
