import { beforeEach, describe, expect, it } from 'vitest'

// Node test environment: a minimal localStorage.
const store = new Map()
global.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
  key: (i) => Array.from(store.keys())[i] ?? null,
  get length() {
    return store.size
  }
}

const { getClientCheckoutPrefill, rememberClientPhone, clearClientProfile } = await import('@/services/auth')

describe('client booking prefill (booking for myself)', () => {
  beforeEach(() => store.clear())

  it('has no phone until the client has used one', () => {
    store.set('ps_client_profile', JSON.stringify({ email: 'thandi@example.com', displayName: 'Thandi' }))
    expect(getClientCheckoutPrefill()).toMatchObject({ displayName: 'Thandi', email: 'thandi@example.com', phone: '' })
  })

  it('offers the phone the client last used for themselves', () => {
    rememberClientPhone(' 082 123 4567 ')
    expect(getClientCheckoutPrefill().phone).toBe('082 123 4567')
  })

  it('ignores a blank phone and forgets it with the profile', () => {
    rememberClientPhone('0821234567')
    rememberClientPhone('   ')
    expect(getClientCheckoutPrefill().phone).toBe('0821234567')
    clearClientProfile()
    expect(getClientCheckoutPrefill().phone).toBe('')
  })
})
