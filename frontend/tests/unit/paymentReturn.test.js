import { describe, it, expect, vi, afterEach } from 'vitest'
import { isSubscriptionPaid } from '@/services/subscriptionApi'
import { paymentReturnPolling, MAX_ATTEMPTS, POLL_INTERVAL_MS } from '@/utils/paymentReturnPolling'
import {
  checkOrderOrBookingReturn,
  checkSubscriptionReturn,
  hasStatusLookup,
  isPaymentReturnIncomplete,
  paymentStatusQuery,
  readPaymentReturn,
  readPaymentStatus
} from '@/utils/paymentReturn'

describe('readPaymentStatus', () => {
  it('reads paid and cancelled from a status response', () => {
    expect(readPaymentStatus({ paid: true, cancelled: false })).toEqual({ paid: true, cancelled: false })
    expect(readPaymentStatus({ paid: false, cancelled: true })).toEqual({ paid: false, cancelled: true })
    expect(readPaymentStatus({ paid: false })).toEqual({ paid: false, cancelled: false })
  })

  it('never reports a paid record as cancelled', () => {
    expect(readPaymentStatus({ paid: true, cancelled: true })).toEqual({ paid: true, cancelled: false })
  })

  it('treats a missing response as not paid', () => {
    expect(readPaymentStatus(null)).toEqual({ paid: false, cancelled: false })
  })
})

describe('checkSubscriptionReturn', () => {
  const ret = { kind: 'subscription', merchantSlug: 'shop', entityId: 'p1' }

  function api(overrides) {
    return {
      fetchPaymentStatus: vi.fn().mockResolvedValue({ paymentId: 'p1', status: 'pending_online', paid: false, cancelled: false }),
      fetchPlanStatus: vi.fn().mockResolvedValue({ active: true, valid: true, onTrial: false, needsPayment: false }),
      isPlanPaid: isSubscriptionPaid,
      ...overrides
    }
  }

  it('asks the payment status endpoint with the store and paymentId', async () => {
    const a = api()
    await expect(checkSubscriptionReturn(ret, a)).resolves.toEqual({ paid: false, cancelled: false })
    expect(a.fetchPaymentStatus).toHaveBeenCalledWith('shop', 'p1', '')
    expect(a.fetchPlanStatus).not.toHaveBeenCalled()
  })

  it('passes the Paystack reference to the payment status endpoint', async () => {
    const a = api()
    await checkSubscriptionReturn({ ...ret, reference: 'ref_9' }, a)
    expect(a.fetchPaymentStatus).toHaveBeenCalledWith('shop', 'p1', 'ref_9')
  })

  it('reports paid and cancelled from the payment status', async () => {
    const paid = api({ fetchPaymentStatus: vi.fn().mockResolvedValue({ paid: true, cancelled: false }) })
    await expect(checkSubscriptionReturn(ret, paid)).resolves.toEqual({ paid: true, cancelled: false })
    const cancelled = api({ fetchPaymentStatus: vi.fn().mockResolvedValue({ paid: false, cancelled: true }) })
    await expect(checkSubscriptionReturn(ret, cancelled)).resolves.toEqual({ paid: false, cancelled: true })
  })

  it('falls back to the plan status only when the payment status answers 404', async () => {
    const notFound = Object.assign(new Error('not found'), { status: 404 })
    const a = api({ fetchPaymentStatus: vi.fn().mockRejectedValue(notFound) })
    await expect(checkSubscriptionReturn(ret, a)).resolves.toEqual({ paid: true, cancelled: false })
    expect(a.fetchPlanStatus).toHaveBeenCalledWith('shop')
  })

  it('passes other errors on so the page can show them', async () => {
    const forbidden = Object.assign(new Error('forbidden'), { status: 403 })
    const a = api({ fetchPaymentStatus: vi.fn().mockRejectedValue(forbidden) })
    await expect(checkSubscriptionReturn(ret, a)).rejects.toBe(forbidden)
    expect(a.fetchPlanStatus).not.toHaveBeenCalled()
  })

  it('uses the plan status when the return has no paymentId', async () => {
    const a = api()
    await expect(checkSubscriptionReturn({ ...ret, entityId: '' }, a)).resolves.toEqual({ paid: true, cancelled: false })
    expect(a.fetchPaymentStatus).not.toHaveBeenCalled()
  })
})

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

  it('never runs two checks at once, even when a check is slower than the interval', async () => {
    vi.useFakeTimers()
    let inFlight = 0
    let maxInFlight = 0
    const slowCheck = vi.fn(async () => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS * 3))
      inFlight -= 1
      return false
    })
    const vm = makeVm(slowCheck)
    vm.startPolling()
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 11)
    expect(maxInFlight).toBe(1)
    // Each check takes 3 intervals and the next starts 1 interval after it ends: checks start at 0, 4 and 8.
    expect(slowCheck).toHaveBeenCalledTimes(3)
    vm.stopPolling()
  })

  it('stays under 20 checks a minute with a fast server', async () => {
    vi.useFakeTimers()
    const check = vi.fn().mockResolvedValue(false)
    const vm = makeVm(check)
    vm.startPolling()
    await vi.advanceTimersByTimeAsync(60000)
    expect(check.mock.calls.length).toBeLessThanOrEqual(20)
    expect(check.mock.calls.length).toBe(MAX_ATTEMPTS)
  })

  it('lets Check again start a fresh run after a timeout', async () => {
    vi.useFakeTimers()
    const check = vi.fn().mockResolvedValue(false)
    const vm = makeVm(check)
    vm.startPolling()
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * (MAX_ATTEMPTS + 1))
    expect(vm.timedOut).toBe(true)
    check.mockResolvedValue(true)
    vm.startPolling()
    expect(vm.timedOut).toBe(false)
    await vi.advanceTimersByTimeAsync(0)
    expect(vm.paid).toBe(true)
    expect(vm.polling).toBe(false)
  })

  it('stops scheduling when the page stops polling while a check is in flight', async () => {
    vi.useFakeTimers()
    let release
    const check = vi.fn(() => new Promise((resolve) => (release = resolve)))
    const vm = makeVm(check)
    vm.startPolling()
    vm.stopPolling()
    release(false)
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 5)
    expect(check).toHaveBeenCalledTimes(1)
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

describe('readPaymentReturn', () => {
  it('reads an order return with the reference Paystack appends', () => {
    const ret = readPaymentReturn(
      { merchantSlug: 'shop' },
      { kind: 'order', id: 'o1', email: 'a@b.co', trxref: 'ref1', reference: 'ref1' }
    )
    expect(ret).toEqual({
      kind: 'order',
      merchantSlug: 'shop',
      entityId: 'o1',
      customerEmail: 'a@b.co',
      reference: 'ref1',
      cancelled: false
    })
    expect(isPaymentReturnIncomplete(ret)).toBe(false)
  })

  it('reads a booking return and falls back to trxref', () => {
    const ret = readPaymentReturn({ merchantSlug: 'salon' }, { kind: 'BOOKING', id: 'b1', email: 'a@b.co', trxref: 't9' })
    expect(ret.kind).toBe('booking')
    expect(ret.entityId).toBe('b1')
    expect(ret.reference).toBe('t9')
  })

  it('reads a subscription return from paymentId and merchant on /payment/return', () => {
    const ret = readPaymentReturn({}, { kind: 'subscription', paymentId: 'p1', merchant: 'shop', reference: 'r1' })
    expect(ret.kind).toBe('subscription')
    expect(ret.merchantSlug).toBe('shop')
    expect(ret.entityId).toBe('p1')
    expect(isPaymentReturnIncomplete(ret)).toBe(false)
  })

  it('defaults an unknown kind to order and takes the first of repeated keys', () => {
    const ret = readPaymentReturn({ merchantSlug: 'shop' }, { kind: 'weird', id: ['o1', 'o2'] })
    expect(ret.kind).toBe('order')
    expect(ret.entityId).toBe('o1')
  })

  it('detects a cancelled checkout', () => {
    expect(readPaymentReturn({}, { cancelled: '1' }).cancelled).toBe(true)
    expect(readPaymentReturn({}, { status: 'Cancelled' }).cancelled).toBe(true)
    expect(readPaymentReturn({}, { status: 'success' }).cancelled).toBe(false)
  })

  it('flags returns that cannot be checked', () => {
    expect(isPaymentReturnIncomplete(readPaymentReturn({}, { kind: 'order', id: 'o1' }))).toBe(true)
    expect(isPaymentReturnIncomplete(readPaymentReturn({ merchantSlug: 'shop' }, { kind: 'order' }))).toBe(true)
    expect(isPaymentReturnIncomplete(readPaymentReturn({}, { kind: 'subscription', paymentId: 'p1' }))).toBe(true)
    expect(isPaymentReturnIncomplete(null)).toBe(true)
  })
})

describe('paymentStatusQuery', () => {
  it('sends the reference, and the email only when an older link has it', () => {
    expect(paymentStatusQuery({ reference: 'ref_1' })).toBe('?reference=ref_1')
    expect(paymentStatusQuery({ reference: 'ref_1', customerEmail: 'a@b.co' })).toBe(
      '?reference=ref_1&customerEmail=a%40b.co'
    )
    expect(paymentStatusQuery({ customerEmail: 'a@b.co' })).toBe('?customerEmail=a%40b.co')
  })

  it('is empty when there is nothing to send', () => {
    expect(paymentStatusQuery({})).toBe('')
    expect(paymentStatusQuery(null)).toBe('')
    expect(paymentStatusQuery({ reference: '  ' })).toBe('')
  })

  it('encodes values', () => {
    expect(paymentStatusQuery({ reference: 'a b&c' })).toBe('?reference=a+b%26c')
  })
})

describe('checkOrderOrBookingReturn', () => {
  function api() {
    return {
      fetchOrderStatus: vi.fn().mockResolvedValue({ paid: true }),
      fetchBookingStatus: vi.fn().mockResolvedValue({ paid: false, cancelled: true })
    }
  }

  it('checks an order with the Paystack reference and no email (new callback URL)', async () => {
    const a = api()
    const ret = readPaymentReturn({ merchantSlug: 'shop' }, { kind: 'order', id: 'o1', reference: 'ref_1', trxref: 'ref_1' })
    await expect(checkOrderOrBookingReturn(ret, a)).resolves.toEqual({ paid: true, cancelled: false })
    expect(a.fetchOrderStatus).toHaveBeenCalledWith('shop', 'o1', { reference: 'ref_1', customerEmail: '' })
    expect(a.fetchBookingStatus).not.toHaveBeenCalled()
  })

  it('checks a booking and still passes the email from an older link', async () => {
    const a = api()
    const ret = readPaymentReturn({ merchantSlug: 'salon' }, { kind: 'booking', id: 'b1', email: 'a@b.co', trxref: 't9' })
    await expect(checkOrderOrBookingReturn(ret, a)).resolves.toEqual({ paid: false, cancelled: true })
    expect(a.fetchBookingStatus).toHaveBeenCalledWith('salon', 'b1', { reference: 't9', customerEmail: 'a@b.co' })
  })

  it('keeps waiting without calling the API when there is neither reference nor email', async () => {
    const a = api()
    const ret = readPaymentReturn({ merchantSlug: 'shop' }, { kind: 'order', id: 'o1' })
    expect(hasStatusLookup(ret)).toBe(false)
    await expect(checkOrderOrBookingReturn(ret, a)).resolves.toEqual({ paid: false, cancelled: false })
    expect(a.fetchOrderStatus).not.toHaveBeenCalled()
  })
})
