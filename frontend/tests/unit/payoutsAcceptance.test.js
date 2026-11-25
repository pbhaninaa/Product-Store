import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'
import { referenceRule } from '@/utils/payouts'

const read = (rel) => fs.readFileSync(path.resolve(__dirname, '../../src', rel), 'utf8')

describe('payout acceptance gaps (QA task 12)', () => {
  it('rejects a reference made only of Unicode spaces, as the API must too (AC-11)', () => {
    for (const blank of ['  ', ' ', '　', ' \t\n ']) {
      expect(referenceRule(blank), JSON.stringify(blank)).toBe('Payment reference is required')
    }
  })

  it('Payout history keeps a changed or cleared range until the page is reopened (AC-2)', () => {
    const view = read('views/support/SupportPayoutHistoryView.vue')
    expect(view.match(/defaultDateRange\(/g)).toHaveLength(1)
    const data = view.slice(view.indexOf('data() {'), view.indexOf('computed:'))
    expect(data).toMatch(/const \{ startDate, endDate \} = defaultDateRange\(\)/)
    expect(view).toMatch(/clearDates\(\) \{\s*this\.startDate = ''\s*this\.endDate = ''/)
  })
})
