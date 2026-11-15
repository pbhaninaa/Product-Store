import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  SESSION_EXPIRED_EVENT,
  isJwtPayloadExpired,
  isSessionExpiredResponse,
  notifySessionExpired,
  resetSessionExpiredNotice
} from '@/services/sessionExpiry'

describe('isSessionExpiredResponse', () => {
  it('treats a 401 on an authenticated call as session expiry', () => {
    expect(isSessionExpiredResponse({ status: 401, sentToken: 'jwt', path: '/api/merchant/orders' })).toBe(true)
  })

  it('ignores 401 when no token was sent', () => {
    expect(isSessionExpiredResponse({ status: 401, sentToken: '', path: '/api/merchant/orders' })).toBe(false)
  })

  it('ignores 401 from auth endpoints (wrong password is not expiry)', () => {
    expect(isSessionExpiredResponse({ status: 401, sentToken: 'jwt', path: '/api/auth/change-password' })).toBe(false)
  })

  it('ignores other statuses such as 403', () => {
    expect(isSessionExpiredResponse({ status: 403, sentToken: 'jwt', path: '/api/merchant/orders' })).toBe(false)
  })
})

describe('isJwtPayloadExpired', () => {
  it('compares exp (seconds) to now (ms)', () => {
    expect(isJwtPayloadExpired({ exp: 100 }, 100_001)).toBe(true)
    expect(isJwtPayloadExpired({ exp: 200 }, 100_000)).toBe(false)
  })

  it('treats tokens without exp as unexpired', () => {
    expect(isJwtPayloadExpired({ sub: 'u' })).toBe(false)
    expect(isJwtPayloadExpired(null)).toBe(false)
  })
})

describe('notifySessionExpired', () => {
  afterEach(() => {
    resetSessionExpiredNotice()
    vi.unstubAllGlobals()
  })

  it('fires once until a new token resets it', () => {
    const dispatchEvent = vi.fn()
    vi.stubGlobal('window', { dispatchEvent })
    vi.stubGlobal('Event', class { constructor(type) { this.type = type } })
    notifySessionExpired()
    notifySessionExpired()
    expect(dispatchEvent).toHaveBeenCalledTimes(1)
    expect(dispatchEvent.mock.calls[0][0].type).toBe(SESSION_EXPIRED_EVENT)
    resetSessionExpiredNotice()
    notifySessionExpired()
    expect(dispatchEvent).toHaveBeenCalledTimes(2)
  })
})
