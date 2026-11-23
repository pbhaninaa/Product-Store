import { beforeEach, describe, expect, it, vi } from 'vitest'
import fs from 'fs'
import path from 'path'

vi.mock('@/services/api', () => ({ apiFetch: vi.fn(async () => ({})) }))
vi.mock('@/services/auth', () => ({ requireMerchantSlugForApi: (route) => route.params.merchantSlug }))

const { apiFetch } = await import('@/services/api')
const { fetchStoreEarnings } = await import('@/services/teamApi')
const { ownerOwedTiles } = await import('@/utils/payouts')
const { formatZar } = await import('@/utils/price')

const VIEW = fs.readFileSync(path.resolve(__dirname, '../../src/views/admin/AdminMyIncomeView.vue'), 'utf8')
const template = VIEW.slice(VIEW.indexOf('<template>'), VIEW.indexOf('<script>'))
const route = { params: { merchantSlug: 'acme' } }

describe('store earnings API (endpoint 8)', () => {
  beforeEach(() => apiFetch.mockClear())

  it('calls the owner endpoint with the chosen range, or none when cleared', async () => {
    await fetchStoreEarnings(route, { startDate: '2026-11-17', endDate: '2026-11-23' })
    await fetchStoreEarnings(route, {})
    expect(apiFetch.mock.calls).toEqual([
      ['/api/m/acme/admin/store-earnings?startDate=2026-11-17&endDate=2026-11-23', { auth: true }],
      ['/api/m/acme/admin/store-earnings', { auth: true }]
    ])
  })
})

describe('owner "Owed by the platform" tiles (AC-17)', () => {
  it('shows net owed, gross, fees and commission from the owed block', () => {
    const tiles = ownerOwedTiles({
      paymentCount: 1,
      grossCents: 100000,
      knownFeeCents: 2500,
      unknownFeeCount: 0,
      commissionPercent: '10.00',
      commissionCents: 10000,
      netOwedCents: 87500
    })
    expect(tiles.map((t) => [t.label, t.value])).toEqual([
      ['Owed by the platform', formatZar(875)],
      ['Gross', formatZar(1000)],
      ['Paystack fees', formatZar(25)],
      ['Commission (10%)', formatZar(100)]
    ])
    expect(tiles[0].hint).toBe('1 payment not paid out yet, all dates')
  })

  it('flags unknown fees instead of showing them as R0 silently, and survives an empty block', () => {
    expect(ownerOwedTiles({ unknownFeeCount: 2, paymentCount: 2 })[2].hint).toBe('2 fees unknown, settled at payout')
    expect(ownerOwedTiles(null).map((t) => t.value)).toEqual(Array(4).fill(formatZar(0)))
  })
})

describe('My income for owners and staff', () => {
  it('loads store earnings for owners only, independently of the employee request (AC-17, AC-19)', () => {
    expect(VIEW).toMatch(/if \(this\.isStoreOwner\) this\.loadStoreEarnings\(\)/)
    expect(VIEW).toMatch(/this\.loadIncome\(\)/)
    expect(VIEW).toMatch(/<v-card v-if="isStoreOwner"/)
    expect(VIEW).toMatch(/mixins: \[adminAccessMixin\]/)
  })

  it('replaces "No attributed work yet" for owners but keeps it for staff', () => {
    expect(VIEW).toMatch(/v-else-if="!loading && !error && !isStoreOwner"[^>]*>\s*No attributed work yet/)
  })

  it('never shows the admin note, the releasing admin or bank details to the owner (AC-18)', () => {
    expect(template).not.toMatch(/\.note\b|releasedByEmail|bankDetails|accountNumber/)
  })
})
