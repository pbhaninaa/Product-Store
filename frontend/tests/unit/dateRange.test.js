import { afterEach, describe, expect, it, vi } from 'vitest'
import fs from 'fs'
import path from 'path'
import { defaultDateRange, lastNDaysRange, statsPresetRange, toIsoDate } from '@/utils/dateRange'

const SRC = path.resolve(__dirname, '../../src')
const read = (rel) => fs.readFileSync(path.join(SRC, rel), 'utf8')
const day = (y, m, d) => new Date(y, m - 1, d, 9, 30)

describe('last N days range', () => {
  afterEach(() => vi.useRealTimers())

  it('is today and the 6 days before it, as local ISO dates (AC-1)', () => {
    expect(lastNDaysRange(7, day(2026, 11, 23))).toEqual({ startDate: '2026-11-17', endDate: '2026-11-23' })
  })

  it('crosses month and year ends on the local calendar', () => {
    expect(lastNDaysRange(7, day(2026, 3, 2))).toEqual({ startDate: '2026-02-24', endDate: '2026-03-02' })
    expect(lastNDaysRange(7, day(2027, 1, 3))).toEqual({ startDate: '2026-12-28', endDate: '2027-01-03' })
    expect(lastNDaysRange(1, day(2026, 11, 23))).toEqual({ startDate: '2026-11-23', endDate: '2026-11-23' })
  })

  it('uses the local date late in the evening, not the UTC one', () => {
    expect(toIsoDate(new Date(2026, 10, 23, 23, 59))).toBe('2026-11-23')
    expect(toIsoDate(new Date(2026, 10, 24, 0, 1))).toBe('2026-11-24')
  })

  it('defaults to the last 7 days from the clock', () => {
    vi.useFakeTimers()
    vi.setSystemTime(day(2026, 11, 23))
    expect(defaultDateRange()).toEqual({ startDate: '2026-11-17', endDate: '2026-11-23' })
  })
})

describe('insights period presets', () => {
  const today = day(2026, 11, 23)

  it('7d is exactly 7 days (was 8) and 30d exactly 30', () => {
    expect(statsPresetRange('7d', today)).toEqual({ startDate: '2026-11-17', endDate: '2026-11-23' })
    expect(statsPresetRange('30d', today)).toEqual({ startDate: '2026-10-25', endDate: '2026-11-23' })
  })

  it('month starts on the 1st, all clears the dates, custom keeps the user dates', () => {
    expect(statsPresetRange('month', today)).toEqual({ startDate: '2026-11-01', endDate: '2026-11-23' })
    expect(statsPresetRange('all', today)).toEqual({ startDate: '', endDate: '' })
    expect(statsPresetRange('custom', today)).toBeNull()
  })
})

describe('pages that open on the last 7 days (AC-1, AC-2)', () => {
  it('My income and Payment calculations set the default once, in data()', () => {
    for (const f of ['views/admin/AdminMyIncomeView.vue', 'views/admin/AdminTeamPayrollView.vue']) {
      const s = read(f)
      const data = s.slice(s.indexOf('data() {'), s.indexOf('computed:'))
      expect(data, f).toMatch(/const \{ startDate, endDate \} = defaultDateRange\(\)/)
      // Clearing goes to "no limit" and is never replaced by the default again.
      expect(s, f).toMatch(/clearDates\(\) \{\s*this\.startDate = ''\s*this\.endDate = ''/)
      expect(s.match(/defaultDateRange\(/g), f).toHaveLength(1)
    }
  })

  it('Insights opens on the 7 day preset and a date edit switches to a custom range', () => {
    const mixin = read('views/admin/mixins/adminModuleMixin.js')
    expect(mixin).toMatch(/statsPreset: '7d'/)
    expect(mixin.match(/this\.applyStatsPreset\('[a-z0-9]+'\)/g)).toEqual(["this.applyStatsPreset('7d')"])
    expect(read('views/admin/AdminInsightsView.vue')).toMatch(/v-model="statsDateFrom"[\s\S]*?@input="bumpStatsToCustom"/)
  })

  it('leaves promotion start and end dates alone', () => {
    const promoFiles = fs.readdirSync(path.join(SRC, 'views/admin')).filter((f) => /promo/i.test(f))
    for (const f of promoFiles) expect(read(`views/admin/${f}`), f).not.toMatch(/dateRange/)
  })
})
