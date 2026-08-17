export function isMerchantUser(u: { roles?: string[] } | null | undefined): boolean {
  if (!u || !Array.isArray(u.roles)) return false;
  return u.roles.includes('MERCHANT_OWNER') || u.roles.includes('MERCHANT_STAFF');
}
