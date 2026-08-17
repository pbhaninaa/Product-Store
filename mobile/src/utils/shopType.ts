export const SHOP_NORMAL = 'normal_store';
export const SHOP_SALON_AND_STORE = 'salon_and_store';
export const SHOP_SALON_ONLY = 'salon_only';
export const LEGACY_SALON = 'salon';

export type ShopType =
  | typeof SHOP_NORMAL
  | typeof SHOP_SALON_AND_STORE
  | typeof SHOP_SALON_ONLY;

export function normalizeShopType(raw: unknown): ShopType {
  const s = String(raw || '')
    .trim()
    .toLowerCase();
  if (s === SHOP_SALON_ONLY) return SHOP_SALON_ONLY;
  if (s === SHOP_SALON_AND_STORE || s === LEGACY_SALON) return SHOP_SALON_AND_STORE;
  return SHOP_NORMAL;
}

export function isSalonOnlyShopType(raw: unknown): boolean {
  return normalizeShopType(raw) === SHOP_SALON_ONLY;
}

export function isSalonAndStoreShopType(raw: unknown): boolean {
  return normalizeShopType(raw) === SHOP_SALON_AND_STORE;
}
