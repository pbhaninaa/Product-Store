import { beforeEach, describe, expect, it, vi } from 'vitest'
import fs from 'fs'
import path from 'path'

vi.mock('@/services/api', () => ({ apiFetch: vi.fn(async () => ({})), getApiBase: () => '' }))

const { apiFetch } = await import('@/services/api')
const { fetchSupportUsers, suspendSupportUser, reactivateSupportUser, sendSupportUserPasswordReset } =
  await import('@/services/supportApi')
const { SUPPORT_PERMISSIONS } = await import('@/views/support/supportPermissions')

const SUPPORT = path.resolve(__dirname, '../../src/views/support')
const read = (f) => fs.readFileSync(path.join(SUPPORT, f), 'utf8')

describe('support users API', () => {
  beforeEach(() => apiFetch.mockClear())

  it('sends only the filters that are set, with a zero-based page', async () => {
    await fetchSupportUsers({ q: ' ann ', role: 'CLIENT', store: '', status: null, page: 0, size: 25 })
    expect(apiFetch).toHaveBeenCalledWith('/api/support/users?q=ann&role=CLIENT&page=0&size=25', { auth: true })
  })

  it('calls the audited action endpoints with POST', async () => {
    await suspendSupportUser('u1')
    await reactivateSupportUser('u1')
    await sendSupportUserPasswordReset('u1')
    expect(apiFetch.mock.calls.map(([url, opts]) => `${opts.method} ${url}`)).toEqual([
      'POST /api/support/users/u1/suspend',
      'POST /api/support/users/u1/reactivate',
      'POST /api/support/users/u1/send-password-reset'
    ])
  })
})

describe('support users page', () => {
  it('offers VIEW_USERS and MANAGE_USERS in the staff permissions editor', () => {
    const values = SUPPORT_PERMISSIONS.map((p) => p.value)
    expect(values).toContain('VIEW_USERS')
    expect(values).toContain('MANAGE_USERS')
  })

  it('shows the Users sidebar item only with VIEW_USERS, and does not grant it by default', () => {
    const shell = read('SupportShell.vue')
    expect(shell).toMatch(/name: 'support-users', label: 'Users', icon: '[a-z_]+', perm: 'VIEW_USERS'/)
    const defaults = shell.slice(shell.indexOf('DEFAULT_SUPPORT_PERMS = ['), shell.indexOf(']', shell.indexOf('DEFAULT_SUPPORT_PERMS = [')))
    expect(defaults).not.toMatch(/VIEW_USERS|MANAGE_USERS/)
  })

  it('paginates on the server and hides actions without MANAGE_USERS', () => {
    const view = read('SupportUsersView.vue')
    expect(view).toMatch(/:server-items-length="total"/)
    expect(view).toMatch(/can\('MANAGE_USERS'\)/)
    expect(view).toMatch(/v-if="canActOn\(item\)"/)
    expect(view).toMatch(/supportDialog\.confirm/)
  })
})
