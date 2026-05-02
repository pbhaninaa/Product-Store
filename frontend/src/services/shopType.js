/** Backend shop_type values (legacy `salon` = hybrid). */

export const SHOP_NORMAL = 'normal_store'
export const SHOP_SALON_AND_STORE = 'salon_and_store'
export const SHOP_SALON_ONLY = 'salon_only'
export const LEGACY_SALON = 'salon'

export function normalizeShopType(raw) {
  const s = String(raw || '').trim().toLowerCase()
  if (s === SHOP_SALON_ONLY) return SHOP_SALON_ONLY
  if (s === SHOP_SALON_AND_STORE || s === LEGACY_SALON) return SHOP_SALON_AND_STORE
  return SHOP_NORMAL
}

export function isSalonShopType(raw) {
  const n = normalizeShopType(raw)
  return n === SHOP_SALON_AND_STORE || n === SHOP_SALON_ONLY
}

export function isSalonOnlyShopType(raw) {
  return normalizeShopType(raw) === SHOP_SALON_ONLY
}

/** Hybrid: product catalogue plus salon booking. */
export function isSalonAndStoreShopType(raw) {
  return normalizeShopType(raw) === SHOP_SALON_AND_STORE
}

export function isNormalStoreShopType(raw) {
  return normalizeShopType(raw) === SHOP_NORMAL
}
