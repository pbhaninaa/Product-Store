import { describe, expect, it } from 'vitest'
import { rules, isBlank } from '@/utils/validation'

const ok = (rule, v) => rule(v) === true

describe('validation rules', () => {
  it('required rejects blank values only', () => {
    const r = rules.required('Name')
    expect(r('')).toBe('Name is required')
    expect(r('   ')).toBe('Name is required')
    expect(r(null)).toBe('Name is required')
    expect(r([])).toBe('Name is required')
    expect(ok(r, 'x')).toBe(true)
    expect(ok(r, 0)).toBe(true)
  })

  it('optional rules pass on empty input', () => {
    for (const r of [rules.email(), rules.phone(), rules.slug(), rules.money(), rules.integer(), rules.time()]) {
      expect(r('')).toBe(true)
    }
    expect(isBlank(' ')).toBe(true)
  })

  it('email matches the API (allows localhost)', () => {
    const r = rules.email()
    expect(ok(r, 'a@b.co')).toBe(true)
    expect(ok(r, 'demo@localhost')).toBe(true)
    expect(ok(r, 'nope')).toBe(false)
    expect(ok(r, 'a@b')).toBe(false)
  })

  it('password needs 8 characters', () => {
    expect(ok(rules.password(), '1234567')).toBe(false)
    expect(ok(rules.password(), '12345678')).toBe(true)
  })

  it('phone counts 7–15 digits and allows formatting', () => {
    const r = rules.phone()
    expect(ok(r, '+27 82 123 4567')).toBe(true)
    expect(ok(r, '(011) 555-1234')).toBe(true)
    expect(ok(r, '12345')).toBe(false)
    expect(ok(r, '082abc4567')).toBe(false)
  })

  it('slug follows TenantSlugUtil', () => {
    const r = rules.slug()
    expect(ok(r, 'demo-store')).toBe(true)
    expect(ok(r, 'a')).toBe(false)
    expect(ok(r, 'Demo')).toBe(false)
    expect(ok(r, 'demo--store')).toBe(false)
    expect(ok(r, 'x'.repeat(49))).toBe(false)
  })

  it('money: number, 2 decimals, bounds', () => {
    const r = rules.money()
    expect(ok(r, '120')).toBe(true)
    expect(ok(r, '120.50')).toBe(true)
    expect(ok(r, '120,50')).toBe(true)
    expect(ok(r, '1.234')).toBe(false)
    expect(ok(r, '-1')).toBe(false)
    expect(ok(r, 'abc')).toBe(false)
    expect(ok(rules.money({ allowZero: false }), '0')).toBe(false)
  })

  it('integer and percent bounds', () => {
    expect(ok(rules.integer({ min: 0 }), '3')).toBe(true)
    expect(ok(rules.integer({ min: 0 }), '3.5')).toBe(false)
    expect(ok(rules.integer({ min: 0 }), '-1')).toBe(false)
    expect(ok(rules.percent(), '100')).toBe(true)
    expect(ok(rules.percent(), '101')).toBe(false)
  })

  it('time, digits, url, dates, matches', () => {
    expect(ok(rules.time(), '09:30')).toBe(true)
    expect(ok(rules.time(), '24:00')).toBe(false)
    expect(ok(rules.digits({ min: 6, max: 6, label: 'Branch code' }), '250655')).toBe(true)
    expect(rules.digits({ min: 6, max: 6, label: 'Branch code' })('25065')).toBe('Branch code must be 6 digits')
    expect(ok(rules.url(), 'https://x.co/a')).toBe(true)
    expect(ok(rules.url(), 'x.co')).toBe(false)
    expect(ok(rules.notBefore(() => '2026-01-10'), '2026-01-09')).toBe(false)
    expect(ok(rules.matches(() => 'abc'), 'abc')).toBe(true)
  })
})
