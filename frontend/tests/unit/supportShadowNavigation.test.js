import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/supportApi', () => ({ mintShadowToken: vi.fn() }))
vi.mock('@/services/auth', () => ({ enterShadowSession: vi.fn() }))

const { mintShadowToken } = await import('@/services/supportApi')
const { enterShadowSession } = await import('@/services/auth')
const { confirmShadowEntry, openMerchantAdminAsShadow, shadowErrorMessage } = await import(
  '@/views/support/shadowNavigation'
)

describe('openMerchantAdminAsShadow', () => {
  beforeEach(() => {
    mintShadowToken.mockReset()
    enterShadowSession.mockReset()
  })

  it('mints an audited shadow token, enters the session, then opens the requested admin page', async () => {
    const tenant = { id: 't1', slug: 'acme', name: 'Acme' }
    mintShadowToken.mockResolvedValue({ token: 'shadow-jwt', tenant })
    const calls = []
    enterShadowSession.mockImplementation(() => calls.push('enter'))
    const router = { push: vi.fn(async () => calls.push('push')) }

    await openMerchantAdminAsShadow(router, ' acme ', 'merchant-admin-orders')

    expect(mintShadowToken).toHaveBeenCalledWith('acme')
    expect(enterShadowSession).toHaveBeenCalledWith('shadow-jwt', tenant)
    expect(router.push).toHaveBeenCalledWith({ name: 'merchant-admin-orders', params: { merchantSlug: 'acme' } })
    // The session must exist before navigating, otherwise the router guard bounces the support user.
    expect(calls).toEqual(['enter', 'push'])
  })

  it('does not navigate when the API refuses the shadow session', async () => {
    const err = Object.assign(new Error('forbidden'), { status: 403 })
    mintShadowToken.mockRejectedValue(err)
    const router = { push: vi.fn() }

    await expect(openMerchantAdminAsShadow(router, 'acme', 'merchant-admin')).rejects.toBe(err)
    expect(enterShadowSession).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })

  it('rejects a blank slug without calling the API', async () => {
    await expect(openMerchantAdminAsShadow({ push: vi.fn() }, '  ')).rejects.toThrow()
    expect(mintShadowToken).not.toHaveBeenCalled()
  })
})

describe('shadowErrorMessage', () => {
  it('explains missing permission and disabled feature', () => {
    expect(shadowErrorMessage({ status: 403, message: 'forbidden' })).toMatch(/Use shadow help/)
    expect(shadowErrorMessage({ status: 409, body: { error: 'feature_disabled' } })).toMatch(/Features/)
    expect(shadowErrorMessage({ status: 409, body: { error: 'owner_suspended' } })).toMatch(/suspended/)
  })
})

describe('confirmShadowEntry', () => {
  it('returns false when the agent cancels', async () => {
    const dialog = { confirm: vi.fn().mockRejectedValue(new Error('cancelled')) }
    expect(await confirmShadowEntry(dialog, 'Acme')).toBe(false)
  })

  it('returns true when confirmed', async () => {
    const dialog = { confirm: vi.fn().mockResolvedValue(true) }
    expect(await confirmShadowEntry(dialog, 'Acme')).toBe(true)
    expect(dialog.confirm.mock.calls[0][0].message).toMatch(/audit log/)
  })
})
