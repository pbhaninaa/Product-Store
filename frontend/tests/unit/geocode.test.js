import { describe, expect, it } from 'vitest'
import { formatFullAddress } from '@/utils/geocode'

describe('formatFullAddress', () => {
  it('builds a full street address', () => {
    expect(
      formatFullAddress({
        house_number: '12',
        road: 'Jan Smuts Avenue',
        suburb: 'Rosebank',
        city: 'Johannesburg',
        state: 'Gauteng',
        postcode: '2196',
        country: 'South Africa'
      })
    ).toBe('12 Jan Smuts Avenue, Rosebank, Johannesburg, 2196, South Africa')
  })

  it('works without a house number and skips repeats', () => {
    expect(
      formatFullAddress({ road: 'Church Street', suburb: 'Pretoria', city: 'Pretoria', country: 'South Africa' })
    ).toBe('Church Street, Pretoria, South Africa')
  })

  it('uses the province when there is no town', () => {
    expect(formatFullAddress({ road: 'N1', state: 'Limpopo', country: 'South Africa' })).toBe('N1, Limpopo, South Africa')
  })

  it('returns empty for nothing', () => {
    expect(formatFullAddress(null)).toBe('')
  })
})
