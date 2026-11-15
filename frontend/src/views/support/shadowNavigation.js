import { enterShadowSession } from '@/services/auth'
import { mintShadowToken } from '@/services/supportApi'

/** Why a shadow session could not start, in words a support agent can act on. */
export function shadowErrorMessage(e) {
  const code = String((e && e.body && e.body.error) || (e && e.message) || '')
  if (e && e.status === 403) return 'Your account does not have the "Use shadow help" permission.'
  if (code.includes('feature_disabled')) return 'Shadow help is switched off. Turn it on under Features first.'
  if (code.includes('owner_suspended')) return 'The store owner is suspended, so a shadow session cannot start.'
  if (code.includes('owner_missing')) return 'This store has no owner account to shadow.'
  if (code.includes('merchant_not_found')) return 'That merchant no longer exists.'
  return code || 'Could not start a shadow session.'
}

/**
 * Starts an audited shadow session for `slug` (same flow as the Shadow tab: the API checks
 * USE_SHADOW and records SHADOW_ENTER) and then opens `routeName` in that merchant's admin.
 * Support users are only let into `/admin` routes while a shadow session is active, so links into
 * merchant admin must go through here rather than a plain router-link.
 */
export async function openMerchantAdminAsShadow(router, slug, routeName = 'merchant-admin') {
  const s = String(slug || '').trim()
  if (!s) throw new Error('merchant_not_found')
  const res = await mintShadowToken(s)
  if (!res || !res.token || !res.tenant || !res.tenant.slug) throw new Error('Could not start a shadow session.')
  enterShadowSession(res.token, res.tenant)
  await router.push({ name: routeName, params: { merchantSlug: res.tenant.slug } })
  return res
}

/** Confirms with the agent before entering a merchant's admin as the owner. */
export async function confirmShadowEntry(dialog, merchantLabel) {
  if (!dialog) return true
  try {
    await dialog.confirm({
      title: 'Open merchant admin?',
      message: `You will enter ${merchantLabel || 'this store'}'s admin as the store owner (shadow help). The visit is recorded in the audit log. Exit from the banner in merchant admin to return here.`,
      confirmLabel: 'Enter as owner'
    })
    return true
  } catch {
    return false
  }
}
