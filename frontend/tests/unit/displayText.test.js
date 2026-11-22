import { describe, expect, it } from 'vitest'
import {
  auditDetail,
  bookingStatusLabel,
  humanize,
  orderStatusLabel,
  payMethodLabel,
  planLabel,
  shortRef
} from '@/utils/displayLabels'
import { friendlyError, isErrorCode } from '@/utils/errorMessages'

describe('shortRef', () => {
  it('shows the first 8 hex characters in upper case, never the full id', () => {
    expect(shortRef('3f9a12bc-1d2e-4f50-8a9b-0c1d2e3f4a5b')).toBe('3F9A12BC')
  })
  it('is empty for a missing id', () => {
    expect(shortRef(null)).toBe('')
    expect(shortRef('')).toBe('')
  })
})

describe('status and enum labels', () => {
  it('turns API enum values into words', () => {
    expect(orderStatusLabel('pending_payment')).toBe('Awaiting payment')
    expect(bookingStatusLabel('in_progress')).toBe('In progress')
    expect(planLabel('STANDARD')).toBe('Standard')
    expect(payMethodLabel('PER_SERVICE')).toBe('Per service')
  })
  it('humanizes values it does not know instead of showing SHOUTY_CODES', () => {
    expect(orderStatusLabel('SOMETHING_NEW')).toBe('Something new')
    expect(humanize('')).toBe('None')
  })
  it('summarises audit details without ids', () => {
    expect(auditDetail({ action: 'FEATURE_TOGGLE', entityId: 'MERCHANT_SIGNUP', detail: 'false' })).toBe(
      'Merchant signup turned off'
    )
    expect(auditDetail({ action: 'PLAN_UPDATE', entityId: 'PREMIUM', detail: '{monthlyFee=1}' })).toBe('Premium plan')
  })
})

describe('friendlyError', () => {
  it('maps API error codes to sentences', () => {
    expect(friendlyError('email_taken')).toBe('An account with this email already exists. Sign in instead.')
  })
  it('never shows an unknown code; uses the fallback instead', () => {
    expect(friendlyError('some_new_code', 'Fallback.')).toBe('Fallback.')
  })
  it('passes real sentences through unchanged', () => {
    expect(friendlyError('Cannot reach the API right now.')).toBe('Cannot reach the API right now.')
  })
  it('recognises codes', () => {
    expect(isErrorCode('setup_required')).toBe(true)
    expect(isErrorCode('Please try again.')).toBe(false)
  })
})
