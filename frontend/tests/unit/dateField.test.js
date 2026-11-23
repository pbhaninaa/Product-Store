import { describe, expect, it } from 'vitest'
import { bindRulesToValue, formatDateLabel } from '@/utils/dateField'

describe('formatDateLabel', () => {
  it('formats an ISO date as day, short month and year', () => {
    expect(formatDateLabel('2026-11-23', 'en-GB')).toBe('23 Nov 2026')
  })

  it('returns empty for empty, malformed or impossible dates', () => {
    expect(formatDateLabel('')).toBe('')
    expect(formatDateLabel(null)).toBe('')
    expect(formatDateLabel('23/11/2026')).toBe('')
    expect(formatDateLabel('2026-02-30')).toBe('')
  })
})

describe('bindRulesToValue', () => {
  it('runs each rule against the ISO value, not the displayed label', () => {
    const seen = []
    const rule = (v) => {
      seen.push(v)
      return v === '2026-01-02' || 'bad'
    }
    let value = '2026-01-02'
    const bound = bindRulesToValue([rule], () => value)

    expect(bound[0]('2 Jan 2026')).toBe(true)
    value = '2026-01-01'
    expect(bound[0]('1 Jan 2026')).toBe('bad')
    expect(seen).toEqual(['2026-01-02', '2026-01-01'])
  })

  it('treats missing rules as none', () => {
    expect(bindRulesToValue(undefined, () => '')).toEqual([])
  })
})
