import { describe, it, expect } from 'vitest'
import { safeInternalPath } from '@/utils/safeRedirect'

describe('safeInternalPath', () => {
  it('keeps in-app paths', () => {
    expect(safeInternalPath('/m/shop/admin?tab=orders')).toBe('/m/shop/admin?tab=orders')
    expect(safeInternalPath(' /account ')).toBe('/account')
  })

  it('rejects anything that can leave the site', () => {
    for (const bad of [
      'https://evil.com',
      '//evil.com',
      '/\\evil.com',
      '\\\\evil.com',
      '/\t/evil.com',
      'javascript:alert(1)',
      'evil.com',
      '',
      null
    ]) {
      expect(safeInternalPath(bad, '/')).toBe('/')
    }
  })
})
