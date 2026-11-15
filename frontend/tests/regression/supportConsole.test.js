import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'

const SRC = path.resolve(__dirname, '../../src')
const SUPPORT = path.join(SRC, 'views/support')
const read = (p) => fs.readFileSync(p, 'utf8')
const supportFiles = fs
  .readdirSync(SUPPORT)
  .filter((f) => /\.(vue|js)$/.test(f))
  .map((f) => ({ f, s: read(path.join(SUPPORT, f)) }))

describe('support console', () => {
  it('has no garbled characters (U+FFFD, stray cp1252 bytes, or "?" placeholders for dashes)', () => {
    const decoder = new TextDecoder('utf-8', { fatal: true })
    const files = [
      ...fs.readdirSync(SUPPORT).map((f) => path.join(SUPPORT, f)),
      path.join(SRC, 'router/index.js'),
      path.join(SRC, 'services/supportApi.js')
    ]
    const bad = []
    for (const f of files) {
      let text
      try {
        text = decoder.decode(fs.readFileSync(f))
      } catch {
        bad.push(`${path.basename(f)}: invalid UTF-8`)
        continue
      }
      if (text.includes('�')) bad.push(`${path.basename(f)}: U+FFFD`)
      if (/return '\?'|: '\?'|^\s*\? (created|pending|services|staff)\b/m.test(text)) {
        bad.push(`${path.basename(f)}: "?" placeholder`)
      }
    }
    expect(bad).toEqual([])
  })

  it('never links straight into merchant admin (the guard bounces support users without a shadow session)', () => {
    const offenders = []
    for (const { f, s } of supportFiles) {
      if (/:to="\{\s*name:\s*'merchant-admin/.test(s)) offenders.push(f)
      if (/adminPath/.test(s)) offenders.push(`${f}: opens adminPath directly`)
    }
    expect(offenders).toEqual([])
  })

  it('opens orders, bookings and merchant admin through a shadow session', () => {
    const byName = Object.fromEntries(supportFiles.map(({ f, s }) => [f, s]))
    expect(byName['SupportOrdersView.vue']).toMatch(/openMerchantAdminAsShadow\(this\.\$router, item\.tenantSlug, 'merchant-admin-orders'\)/)
    expect(byName['SupportBookingsView.vue']).toMatch(
      /openMerchantAdminAsShadow\(this\.\$router, item\.tenantSlug, 'merchant-admin-salon-bookings'\)/
    )
    expect(byName['SupportMerchantsView.vue']).toMatch(/openMerchantAdminAsShadow\(this\.\$router, slug, 'merchant-admin'\)/)
    for (const f of ['SupportOrdersView.vue', 'SupportBookingsView.vue', 'SupportMerchantsView.vue']) {
      expect(byName[f]).toMatch(/can\('USE_SHADOW'\)/)
    }
  })

  it('does not count retired legacy proofs in the Billing tab', () => {
    const shell = read(path.join(SUPPORT, 'SupportShell.vue'))
    expect(shell).not.toMatch(/fetchPendingSubscriptionProofs|pendingProofs/)
    const dash = read(path.join(SUPPORT, 'SupportDashboardView.vue'))
    expect(dash).not.toMatch(/Pending proofs/)
    const subs = read(path.join(SUPPORT, 'SupportSubscriptionsView.vue'))
    expect(subs).not.toMatch(/approveSubscriptionProof|rejectSubscriptionProof|forceActivateSubscription/)
  })

  it('signs out to the support sign-in, not the merchant pages', () => {
    const shell = read(path.join(SUPPORT, 'SupportShell.vue'))
    expect(shell).toMatch(/path: '\/m\/platform\/admin', query: \{ support: '1' \}/)
    expect(shell).not.toMatch(/name: 'login'/)
  })

  it('lets platform admins edit support permissions on the Staff page', () => {
    const staff = read(path.join(SUPPORT, 'SupportStaffView.vue'))
    expect(staff).toMatch(/updateSupportStaffPermissions/)
    expect(staff).toMatch(/isPlatformAdmin/)
  })
})

describe('support permission set', () => {
  it('matches the backend SupportPermission enum', async () => {
    const { SUPPORT_PERMISSIONS } = await import('@/views/support/supportPermissions')
    const enumSrc = read(
      path.resolve(
        __dirname,
        '../../../backend/src/main/java/com/productstore/platform/constants/SupportPermission.java'
      )
    )
    const start = enumSrc.indexOf('enum SupportPermission {') + 'enum SupportPermission {'.length
    const body = enumSrc.slice(start, enumSrc.indexOf(';', start))
    const backend = body.split(',').map((s) => s.trim()).filter(Boolean)
    expect(SUPPORT_PERMISSIONS.map((p) => p.value)).toEqual(backend)
  })
})
