import { describe, it, expect, vi, afterEach } from 'vitest'
import { isSubscriptionPaid } from '@/services/subscriptionApi'
import { paymentReturnPolling, MAX_ATTEMPTS, POLL_INTERVAL_MS } from '@/utils/paymentReturnPolling'

describe('isSubscriptionPaid', () => {
  it('is false while the subscription is still on trial or awaiting payment', () => {
    expect(isSubscriptionPaid(null)).toBe(false)
    expect(isSubscriptionPaid({ active: true, valid: true, onTrial: true, needsPayment: false })).toBe(false)
    expect(isSubscriptionPaid({ active: true, valid: true, onTrial: false, needsPayment: true })).toBe(false)
    expect(isSubscriptionPaid({ active: false, valid: false, onTrial: false, needsPayment: true })).toBe(false)
  })

  it('is true once the paid period is active', () => {
    expect(isSubscriptionPaid({ active: true, valid: true, onTrial: false, needsPayment: false })).toBe(true)
  })
})

/** Minimal stand-in for a component using the mixin. */
function makeVm(checkPaid) {
  const vm = { ...paymentReturnPolling.data(), checkPaid }
  for (const [k, fn] of Object.entries(paymentReturnPolling.methods)) vm[k] = fn.bind(vm)
  return vm
}

describe('paymentReturnPolling', () => {
  afterEach(() => vi.useRealTimers())

  it('stops and flags a timeout when payment is never confirmed', async () => {
    vi.useFakeTimers()
    const vm = makeVm(vi.fn().mockResolvedValue(false))
    vm.startPolling()
    expect(vm.polling).toBe(true)
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * (MAX_ATTEMPTS + 1))
    expect(vm.polling).toBe(false)
    expect(vm.timedOut).toBe(true)
    expect(vm.paid).toBe(false)
  })

  it('marks paid and stops as soon as the server confirms', async () => {
    vi.useFakeTimers()
    const check = vi.fn().mockResolvedValueOnce(false).mockResolvedValue(true)
    const vm = makeVm(check)
    vm.startPolling()
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2)
    expect(vm.paid).toBe(true)
    expect(vm.polling).toBe(false)
    expect(vm.timedOut).toBe(false)
  })
})
