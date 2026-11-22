import { describe, expect, it } from 'vitest'
import {
  incomeJobRef,
  incomePeriodLabel,
  incomeStatus,
  incomeStatusMeta,
  incomeWhen,
  sortIncomeLines
} from '@/utils/myIncome'

const line = (jobId, status, when, extra = {}) => ({ jobId, jobType: 'SALON_BOOKING', status, when, ...extra })

describe('my income lines', () => {
  it('reads the server status, falling back to employerPaid for older responses', () => {
    expect(incomeStatus({ status: 'UPCOMING' })).toBe('UPCOMING')
    expect(incomeStatus({ status: 'paid_out' })).toBe('PAID_OUT')
    expect(incomeStatus({ employerPaid: true })).toBe('PAID_OUT')
    expect(incomeStatus({ employerPaid: false })).toBe('OWED')
    expect(incomeStatusMeta({ status: 'OWED' }).label).toBe('Owed')
    expect(incomeStatusMeta({ status: 'UPCOMING' }).label).toBe('Upcoming')
    expect(incomeStatusMeta({ status: 'PAID_OUT' }).label).toBe('Paid out')
  })

  it('sorts upcoming first, then newest first, without changing the input', () => {
    const input = [
      line('paid', 'PAID_OUT', '2026-11-18T09:00:00Z'),
      line('owed', 'OWED', '2026-11-21T10:30:00Z'),
      line('up-late', 'UPCOMING', '2026-11-29T14:00:00Z'),
      line('order', 'OWED', '2026-11-20T08:00:00Z'),
      line('up-soon', 'UPCOMING', '2026-11-22T14:00:00Z')
    ]
    const copy = [...input]
    expect(sortIncomeLines(input).map((l) => l.jobId)).toEqual(['up-late', 'up-soon', 'owed', 'order', 'paid'])
    expect(input).toEqual(copy)
    expect(sortIncomeLines(null)).toEqual([])
  })

  it('shows a short job reference and an appointment time for bookings only', () => {
    expect(incomeJobRef({ jobId: '7f2a1b3c-0000-0000-0000-000000000000', jobType: 'SALON_BOOKING' })).toBe('BK 7F2A')
    expect(incomeJobRef({ jobId: '993b0000-0000-0000-0000-000000000000', jobType: 'ORDER' })).toBe('OR 993B')
    expect(incomeWhen({ jobType: 'ORDER', when: '2026-11-20T08:00:00Z' })).not.toMatch(/:/)
    expect(incomeWhen({ jobType: 'SALON_BOOKING', when: '2026-11-22T12:00:00Z' })).toMatch(/\d{2}:\d{2}/)
    expect(incomeWhen({ jobType: 'SALON_BOOKING', when: null })).toBe('Not set')
  })

  it('labels the period', () => {
    expect(incomePeriodLabel(null, null)).toBe('All dates')
    expect(incomePeriodLabel('2026-11-01', '2026-11-30')).toMatch(/^1 to 30 /)
    expect(incomePeriodLabel('2026-10-28', '2026-11-03')).toMatch(/^28 .+ to 3 /)
  })
})
