/**
 * Pure rules shared by the merchant admin views (no Vue / API imports so they stay unit-testable).
 */
import { isInAppPaymentMethod } from '@/utils/inAppPayment'
import { canOpenRoute, effectiveAccess, hasPermission } from '@/utils/merchantPermissions'

/**
 * True when this signed-in user may open the admin route. Staff need the page's permission
 * (`accessResponse` is `/admin/access/me`; without it staff get the default staff access). A deep link to a
 * page they can't use shows a "no access" card instead of a page full of failing requests.
 */
export function canOpenAdminRoute(user, routeName, accessResponse) {
  return canOpenRoute(effectiveAccess(user, accessResponse), routeName)
}

/**
 * Team & Payroll sub-tabs a user may see. Managing the team is owner-only; payroll needs TEAM_PAYROLL;
 * everyone sees their own income.
 */
export function teamHubTabsFor(user, accessResponse) {
  const access = effectiveAccess(user, accessResponse)
  if (access.isOwner) return ['team', 'payroll', 'income']
  return hasPermission(access, 'TEAM_PAYROLL') ? ['payroll', 'income'] : ['income']
}

const FULFILMENT_ORDER = ['processing', 'ready', 'completed']

/**
 * Fulfilment choices for a paid order: the current step plus forward steps only
 * (processing → ready → completed, or processing → completed). The server rejects moving backwards.
 */
export function forwardFulfilmentOptions(currentStatus, isDelivery) {
  const labels = {
    processing: 'Processing',
    ready: isDelivery ? 'Out for delivery' : 'Ready for pickup',
    completed: 'Completed'
  }
  const cur = FULFILMENT_ORDER.includes(currentStatus) ? currentStatus : 'processing'
  const from = FULFILMENT_ORDER.indexOf(cur)
  return FULFILMENT_ORDER.slice(from).map((value) => ({ text: labels[value], value }))
}

/**
 * "Confirm booking" (manual status change to confirmed) is only for pending bookings whose payment is settled
 * outside the app. Online (in app) bookings confirm themselves when the payment arrives;
 * cash uses the payment-code dialog; manual EFT goes through proof review.
 */
export function canMerchantConfirmBooking(item) {
  if (!item) return false
  if (String(item.status || '').toLowerCase() !== 'pending') return false
  const pm = String(item.clientPaymentMethod || '').toLowerCase()
  if (isInAppPaymentMethod(pm)) return false
  if (pm === 'cash_store') return false
  const pv = String(item.paymentVerificationState || '').toLowerCase()
  if (pm === 'eft' && (pv === 'awaiting_proof' || pv === 'manual_pending')) return false
  return true
}

/** True for a pending online booking that is still waiting for the customer's payment. */
export function bookingAwaitingInAppPayment(item) {
  if (!item) return false
  return (
    String(item.status || '').toLowerCase() === 'pending' &&
    isInAppPaymentMethod(String(item.clientPaymentMethod || '').toLowerCase())
  )
}

/** Public storefront paths for a merchant (shoppers land on /m/<slug>, not the platform root). */
export function storefrontShopPath(slug) {
  const s = String(slug || '').trim()
  return s ? `/m/${encodeURIComponent(s)}` : '/'
}

export function storefrontContactPath(slug) {
  const s = String(slug || '').trim()
  return s ? `/m/${encodeURIComponent(s)}/contact` : '/'
}

/** Absolute URL for showing/copying a storefront link (falls back to the path outside a browser). */
export function absoluteStorefrontUrl(path) {
  try {
    if (typeof window !== 'undefined' && window.location && window.location.origin) {
      return `${window.location.origin}${path}`
    }
  } catch {
    // ignore
  }
  return path
}
