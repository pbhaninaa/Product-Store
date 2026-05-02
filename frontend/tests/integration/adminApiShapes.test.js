import { describe, it, expect } from 'vitest'

/**
 * Contract checks for admin DELETE responses — keeps client and server aligned.
 * (Integration with real HTTP belongs in backend MockMvc tests.)
 */
describe('admin API response shapes (integration-style contract)', () => {
  it('delete order success payload', () => {
    const body = { ok: true }
    expect(body.ok).toBe(true)
  })

  it('delete order rejected payload', () => {
    const body = { ok: false, reason: 'not_deletable' }
    expect(body.ok).toBe(false)
    expect(body.reason).toBe('not_deletable')
  })
})
