import { describe, expect, it } from 'vitest'
import { DEFAULT_SITE_NAME, siteName } from '@/utils/siteName'

describe('site name', () => {
  it('uses the configured name for the environment', () => {
    expect(siteName({ VUE_APP_SITE_NAME: '  Acme Market (SIT) ' })).toBe('Acme Market (SIT)')
  })

  it('falls back to the default name when none is configured', () => {
    expect(siteName({})).toBe(DEFAULT_SITE_NAME)
    expect(siteName({ VUE_APP_SITE_NAME: '   ' })).toBe(DEFAULT_SITE_NAME)
    expect(siteName(undefined)).toBe(DEFAULT_SITE_NAME)
  })
})
