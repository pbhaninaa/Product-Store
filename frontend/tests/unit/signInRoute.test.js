import { describe, it, expect } from 'vitest'
import { forgotPasswordQuery, signInContextFromQuery, signInRouteFor } from '@/utils/signInRoute'

describe('sign-in routing for password reset and signup', () => {
  it('defaults shoppers to the customer sign-in', () => {
    expect(signInRouteFor(signInContextFromQuery({}))).toEqual({ name: 'client-login' })
    expect(signInRouteFor(signInContextFromQuery({ audience: 'client' }))).toEqual({ name: 'client-login' })
  })

  it('sends merchants with a known store to that store admin', () => {
    expect(signInRouteFor(signInContextFromQuery({ m: 'my-shop' }))).toEqual({ path: '/m/my-shop/admin' })
    expect(signInRouteFor(signInContextFromQuery({ audience: 'merchant', m: 'my-shop' }))).toEqual({
      path: '/m/my-shop/admin'
    })
  })

  it('never guesses a store for merchants without one', () => {
    expect(signInRouteFor({ audience: 'merchant', m: '' })).toEqual({
      name: 'client-login',
      query: { audience: 'merchant' }
    })
  })

  it('keeps support staff on the platform console', () => {
    expect(signInRouteFor(signInContextFromQuery({ support: '1' }))).toEqual({
      path: '/m/platform/admin',
      query: { support: '1' }
    })
  })

  it('carries the context into the forgot-password link', () => {
    expect(forgotPasswordQuery({ audience: 'client' }, 'a@b.co')).toEqual({ audience: 'client', email: 'a@b.co' })
    expect(forgotPasswordQuery({ audience: 'merchant', m: 'shop' })).toEqual({ audience: 'merchant', m: 'shop' })
  })
})
