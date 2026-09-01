import { describe, expect, it } from 'vitest'
import { isValidCustomerEmail } from '@/utils/customerEmail'

describe('isValidCustomerEmail', () => {
  it('accepts a normal domain and localhost seeds', () => {
    expect(isValidCustomerEmail('client@localhost')).toBe(true)
    expect(isValidCustomerEmail('pat@client.test')).toBe(true)
    expect(isValidCustomerEmail('not-an-email')).toBe(false)
    expect(isValidCustomerEmail('a@b')).toBe(false)
  })
})
