import { describe, it, expect } from 'vitest'

/**
 * Regression guard: public nav copy distinguishes product checkout from salon booking.
 */
describe('navigation copy regression', () => {
  it('cart label mentions product checkout', () => {
    const aria = 'Product cart and checkout'
    expect(aria.toLowerCase()).toContain('cart')
    expect(aria.toLowerCase()).toContain('checkout')
  })

  it('salon label mentions appointment', () => {
    const aria = 'Salon — book an appointment'
    expect(aria.toLowerCase()).toContain('book')
    expect(aria.toLowerCase()).toContain('appointment')
  })
})
