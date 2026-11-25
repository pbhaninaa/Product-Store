import { beforeEach, describe, expect, it, vi } from 'vitest'
import fs from 'fs'
import path from 'path'

vi.mock('@/services/api', () => ({ apiFetch: vi.fn(async () => ({})), getApiBase: () => '' }))

const { apiFetch } = await import('@/services/api')
const supportApi = await import('@/services/supportApi')
const { centsToAmountInput, formatCents, formatZar, parseZarToCents } = await import('@/utils/price')
const payouts = await import('@/utils/payouts')
const { friendlyError } = await import('@/utils/errorMessages')
const { auditDetail } = await import('@/utils/displayLabels')
const { isTabVisible, isPlatformAdminUser } = await import('@/views/support/supportNav')

const SUPPORT = path.resolve(__dirname, '../../src/views/support')
const read = (f) => fs.readFileSync(path.join(SUPPORT, f), 'utf8')

/** AC-7: one R1 000 payment, R25 fee, 10% commission. */
const AC7 = { paymentCount: 1, grossCents: 100000, knownFeeCents: 2500, unknownFeeCount: 0, commissionCents: 10000 }

describe('money in cents', () => {
  it('formats integer cents exactly like formatZar, without floating point', () => {
    expect(formatCents(100000)).toBe(formatZar(1000))
    expect(formatCents(87500)).toBe(formatZar(875))
    expect(formatCents(5)).toBe(formatZar(0.05))
    expect(formatCents('2500')).toBe(formatZar(25))
    // 0.1 + 0.2 style drift cannot happen: cents are split as integers.
    expect(formatCents(900719925474099)).toMatch(/,99$/)
  })

  it('never shows a dash for negatives and returns nothing for non cents', () => {
    expect(formatCents(-500)).toBe(`minus ${formatZar(5)}`)
    for (const bad of [1.5, 'abc', null, undefined, '', NaN]) expect(formatCents(bad)).toBe('')
  })

  it('parses typed rand amounts to cents as text (max 2 decimals, not negative)', () => {
    expect(parseZarToCents('30.00')).toBe(3000)
    expect(parseZarToCents('30')).toBe(3000)
    expect(parseZarToCents('30.5')).toBe(3050)
    expect(parseZarToCents('30,05')).toBe(3005)
    expect(parseZarToCents('R 1 000.00')).toBe(100000)
    expect(parseZarToCents('0')).toBe(0)
    for (const bad of ['', '  ', 'abc', '1.234', '-1', '1e3', null]) expect(parseZarToCents(bad)).toBeNull()
  })

  it('prefills an input from cents', () => {
    expect(centsToAmountInput(2500)).toBe('25.00')
    expect(centsToAmountInput(5)).toBe('0.05')
    expect(centsToAmountInput(0)).toBe('0.00')
    expect(centsToAmountInput(null)).toBe('')
  })
})

describe('platform commission (AC-3, AC-4)', () => {
  it('accepts 0 to 50 with up to 2 decimals and rejects everything else with the AC message', () => {
    for (const ok of ['0', '12.5', '12,5', '50', '50.00', ' 7.25 ']) expect(payouts.commissionRule(ok), ok).toBe(true)
    for (const bad of ['51', '50.01', '-1', 'abc', '', '12.345', null]) {
      expect(payouts.commissionRule(bad), String(bad)).toBe('Commission must be between 0% and 50%')
    }
    expect(friendlyError('commission_invalid')).toBe('Commission must be between 0% and 50%')
  })

  it('sends the percent as dot decimal text and shows the API value without trailing zeros', () => {
    expect(payouts.commissionPayload(' 12,5 ')).toEqual({ commissionPercent: '12.5' })
    expect(payouts.formatPercent('12.50')).toBe('12.5%')
    expect(payouts.formatPercent('10.00')).toBe('10%')
    expect(payouts.formatPercent('0.00')).toBe('0%')
    expect(payouts.formatPercent(undefined)).toBe('0%')
  })
})

describe('release screen rules', () => {
  it('names the missing bank fields but does not block release (AC-9)', () => {
    expect(payouts.missingBankFields({ missingBankFields: ['accountNumber', 'branchCode'] })).toEqual([
      'accountNumber',
      'branchCode'
    ])
    expect(
      payouts.missingBankFields({ bankDetails: { bankName: 'FNB', accountHolder: ' ', accountNumber: '123', branchCode: null } })
    ).toEqual(['accountHolder', 'branchCode'])
    const msg = payouts.missingBankMessage(['accountNumber', 'branchCode'])
    expect(msg).toMatch(/account number and branch code are missing/)
    expect(msg).toMatch(/You can still mark the payout as released/)
    expect(payouts.missingBankMessage([])).toBe('')
  })

  it('computes the live net from the fee total typed in (AC-7, AC-10)', () => {
    expect(payouts.releaseNetCents(AC7, '25.00')).toBe(87500)
    expect(payouts.releaseNetCents(AC7, '30')).toBe(87000)
    expect(payouts.releaseNetCents(AC7, '-1')).toBeNull()
  })

  it('rejects a negative fee and a fee that leaves nothing to pay (AC-10)', () => {
    const rule = payouts.feeTotalRule(AC7)
    expect(rule('30.00')).toBe(true)
    expect(rule('0')).toBe(true)
    expect(rule('-5')).toMatch(/Enter the fee total/)
    expect(rule('900.00')).toMatch(/must leave more than/)
    expect(rule('950.00')).toMatch(/must leave more than/)
  })

  it('requires a reference that is not only spaces, up to 100 characters (AC-11)', () => {
    expect(payouts.referenceRule('')).toBe('Payment reference is required')
    expect(payouts.referenceRule('    ')).toBe('Payment reference is required')
    expect(payouts.referenceRule('x'.repeat(101))).toMatch(/100 characters or fewer/)
    expect(payouts.referenceRule(' EFT 2611 ')).toBe(true)
    expect(friendlyError('payout_reference_required')).toBe('Payment reference is required')
  })

  it('posts exactly the payments and version on screen, sending the fee only when adjusted', () => {
    const preview = {
      merchant: { tenantId: 't1', name: 'Acme', slug: 'acme' },
      payments: [{ attemptId: 'a1' }, { attemptId: 'a2' }],
      summary: AC7,
      payableVersion: 'v1'
    }
    expect(payouts.releasePayload(preview, { feeText: '25.00', reference: '  REF1 ', note: '  ' })).toEqual({
      tenantId: 't1',
      attemptIds: ['a1', 'a2'],
      payableVersion: 'v1',
      reference: 'REF1'
    })
    expect(payouts.releasePayload(preview, { feeText: '30', reference: 'REF1', note: ' paid by EFT ' })).toEqual({
      tenantId: 't1',
      attemptIds: ['a1', 'a2'],
      payableVersion: 'v1',
      reference: 'REF1',
      feeTotalCents: 3000,
      note: 'paid by EFT'
    })
  })

  it('treats already released and changed amounts as a stale screen with the AC wording (AC-13, AC-14)', () => {
    expect(payouts.isStaleReleaseError({ code: 'payout_already_released' })).toBe(true)
    expect(payouts.isStaleReleaseError({ code: 'payout_amounts_changed' })).toBe(true)
    expect(payouts.isStaleReleaseError({ code: 'payout_reference_required' })).toBe(false)
    expect(payouts.isStaleReleaseError(null)).toBe(false)
    expect(friendlyError('payout_already_released')).toBe('These payments have already been released')
    expect(friendlyError('payout_amounts_changed')).toBe('Amounts have changed, please refresh')
    for (const code of ['payout_fee_invalid', 'payout_net_not_positive', 'payout_nothing_owed', 'payout_note_too_long']) {
      expect(friendlyError(code, 'FALLBACK'), code).not.toBe('FALLBACK')
    }
  })

  it('needs both history dates or neither', () => {
    expect(payouts.dateRangeRule('2026-11-17', '2026-11-23')).toBe(true)
    expect(payouts.dateRangeRule('', '')).toBe(true)
    expect(payouts.dateRangeRule('2026-11-17', '')).toMatch(/both a start and end date/)
  })
})

describe('payout API calls (contract section 4)', () => {
  beforeEach(() => apiFetch.mockClear())

  it('uses the agreed methods and paths', async () => {
    await supportApi.fetchPlatformCommission()
    await supportApi.updatePlatformCommission('12.5')
    await supportApi.fetchPayoutsOwed()
    await supportApi.fetchPayoutOwedForMerchant('t 1')
    await supportApi.releasePayout({ tenantId: 't1' })
    await supportApi.fetchPayoutDetail('p1')
    expect(apiFetch.mock.calls.map(([url, opts]) => `${opts.method || 'GET'} ${url}`)).toEqual([
      'GET /api/support/platform-commission',
      'PUT /api/support/platform-commission',
      'GET /api/support/payouts/owed',
      'GET /api/support/payouts/owed/t%201',
      'POST /api/support/payouts',
      'GET /api/support/payouts/p1'
    ])
    expect(apiFetch.mock.calls[1][1].json).toEqual({ commissionPercent: '12.5' })
    expect(apiFetch.mock.calls.every(([, opts]) => opts.auth === true)).toBe(true)
  })

  it('sends only the history filters that are set, with a zero based page', async () => {
    await supportApi.fetchPayoutHistory({ tenantId: '', startDate: '2026-11-17', endDate: '2026-11-23', page: 0, size: 50 })
    await supportApi.fetchPayoutHistory()
    expect(apiFetch.mock.calls.map(([url]) => url)).toEqual([
      '/api/support/payouts?startDate=2026-11-17&endDate=2026-11-23&page=0&size=50',
      '/api/support/payouts'
    ])
  })
})

describe('payout pages are for platform admins only (AC-5)', () => {
  const staffCan = () => true // support staff with every permission

  it('hides admin only tabs from support staff, even with every permission', () => {
    const tab = { name: 'support-payouts', adminOnly: true }
    expect(isTabVisible(tab, { platformAdmin: false, can: staffCan })).toBe(false)
    expect(isTabVisible(tab, { platformAdmin: true, can: staffCan })).toBe(true)
    expect(isTabVisible({ perm: 'VIEW_OPS' }, { platformAdmin: false, can: (p) => p === 'VIEW_OPS' })).toBe(true)
    expect(isPlatformAdminUser({ roles: ['SUPPORT_USER'] })).toBe(false)
    expect(isPlatformAdminUser({ roles: ['PLATFORM_ADMIN'] })).toBe(true)
  })

  it('marks both sidebar tabs admin only in the Merchants group', () => {
    const shell = read('SupportShell.vue')
    expect(shell).toMatch(/name: 'support-payouts', label: 'Payouts', icon: '[a-z_]+', adminOnly: true/)
    expect(shell).toMatch(/name: 'support-payout-history', label: 'Payout history', icon: '[a-z_]+', adminOnly: true/)
    expect(shell).toMatch(/'support-payouts': 'Merchants'/)
    expect(shell).toMatch(/isTabVisible\(t, \{ platformAdmin: this\.platformAdmin, can: this\.can \}\)/)
  })
})

describe('payout pages', () => {
  it('release screen: one request at a time, refresh on a stale answer', () => {
    const view = read('SupportPayoutReleaseView.vue')
    expect(view).toMatch(/if \(this\.submitting \|\| !this\.preview/)
    expect(view).toMatch(/:disabled="submitting \|\| loading \|\| Boolean\(staleMessage\)"/)
    expect(view).toMatch(/isStaleReleaseError\(e\)/)
    expect(view).toMatch(/@click="refreshAfterConflict"/)
    expect(view).toMatch(/releasePayload\(this\.preview/)
  })

  it('history opens on the last 7 days with the shared date field and a detail dialog (AC-16)', () => {
    const view = read('SupportPayoutHistoryView.vue')
    expect(view).toMatch(/const \{ startDate, endDate \} = defaultDateRange\(\)/)
    expect(view.match(/<AppDateField/g)).toHaveLength(2)
    expect(view).toMatch(/fetchSupportMerchants/)
    expect(view).toMatch(/fetchPayoutDetail\(item\.id\)/)
  })

  it('payouts page has no date filter (all owed money is always shown)', () => {
    const view = read('SupportPayoutsView.vue')
    expect(view).not.toMatch(/AppDateField|startDate|endDate/)
  })

  it('no payout page uses a native date input', () => {
    for (const f of ['SupportPayoutsView.vue', 'SupportPayoutReleaseView.vue', 'SupportPayoutHistoryView.vue']) {
      expect(read(f), f).not.toMatch(/type="date"/)
    }
  })
})

describe('payout audit entries read as sentences', () => {
  it('commission change and release', () => {
    expect(auditDetail({ action: 'COMMISSION_UPDATE', detail: 'from=1000bp;to=1250bp' })).toBe('From 10% to 12.5%')
    expect(
      auditDetail({
        action: 'PAYOUT_RELEASE',
        detail:
          'merchant=acme;tenantId=t1;payments=1;grossCents=100000;feeCents=3000;feeAdjusted=true;commissionBp=1000;commissionCents=10000;netCents=87000;reference=EFT 1'
      })
    ).toBe(`Store /acme, 1 payment, net ${formatZar(870)}, reference EFT 1, fee total adjusted`)
  })
})
