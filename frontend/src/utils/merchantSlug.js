/**
 * Merchant slug from the URL path only.
 * Vue Router 3 keeps leftover `params.merchantSlug` when navigating to named routes
 * like the client hub (`/`), which must not trigger storefront fetches.
 */
export function merchantSlugFromRoute(route) {
  const path = String((route && route.path) || '')
  const m = path.match(/^\/m\/([^/]+)/i)
  if (!m) return ''
  let slug = String(m[1] || '').trim()
  try {
    slug = decodeURIComponent(slug).trim()
  } catch {
    // keep raw segment
  }
  if (!slug) return ''
  return slug
}

export function isPublicStorefrontSlug(slug) {
  const s = String(slug || '').trim().toLowerCase()
  return Boolean(s) && s !== 'platform'
}
