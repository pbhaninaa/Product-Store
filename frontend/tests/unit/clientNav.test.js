import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'
import {
  CLIENT_SHELL_ROUTES,
  clientSidebarFooter,
  clientSidebarItems,
  clientWorkspaceTitle,
  usesClientShell
} from '@/views/client/clientNav'

const router = fs.readFileSync(path.resolve(__dirname, '../../src/router/index.js'), 'utf8')
const routeNames = new Set([...router.matchAll(/name: '([^']+)'/g)].map((m) => m[1]))

describe('client workspace shell', () => {
  it('wraps the signed-in customer pages only', () => {
    for (const name of ['client-hub', 'client-discover', 'provider-directory', 'client-history']) {
      expect(usesClientShell(name, true), name).toBe(true)
    }
  })

  it('keeps guests on the plain top bar, including the public provider directory', () => {
    for (const name of CLIENT_SHELL_ROUTES) {
      expect(usesClientShell(name, false), name).toBe(false)
    }
  })

  it('leaves storefront, checkout, booking and sign in pages out of the shell', () => {
    for (const name of [
      'merchant-home',
      'merchant-checkout',
      'merchant-salon-book',
      'merchant-track',
      'payment-return',
      'client-login',
      'client-register',
      'merchant-admin'
    ]) {
      expect(usesClientShell(name, true), name).toBe(false)
    }
  })

  it('only names routes the router defines', () => {
    for (const name of CLIENT_SHELL_ROUTES) expect(routeNames.has(name), name).toBe(true)
    for (const item of clientSidebarItems()) expect(routeNames.has(item.to.name), item.key).toBe(true)
  })
})

describe('client sidebar', () => {
  it('groups pages into Explore then My account, with unique keys', () => {
    const items = clientSidebarItems()
    const groups = [...new Set(items.map((i) => i.group))]
    expect(groups).toEqual(['Explore', 'My account'])
    expect(new Set(items.map((i) => i.key)).size).toBe(items.length)
  })

  it('opens Discover for items and for services with the same ?kind= the hub uses', () => {
    const kinds = clientSidebarItems()
      .filter((i) => i.to.name === 'client-discover')
      .map((i) => i.to.query.kind)
    expect(kinds).toEqual(['shop', 'salon'])
  })

  it('marks only the hub as an exact match', () => {
    expect(clientSidebarItems().filter((i) => i.exact).map((i) => i.key)).toEqual(['client-hub'])
  })

  it('offers sign out as a footer action handled by the shell', () => {
    expect(clientSidebarFooter()).toEqual([{ key: 'logout', label: 'Sign out', icon: 'logout' }])
  })

  it('titles the sidebar with the customer name, or My account', () => {
    expect(clientWorkspaceTitle('  Thandi ')).toBe('Thandi')
    expect(clientWorkspaceTitle('')).toBe('My account')
    expect(clientWorkspaceTitle(null)).toBe('My account')
  })
})
