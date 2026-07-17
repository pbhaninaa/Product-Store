/** Client-side preview of store slug (server allocates uniqueness). */
export function slugFromBusinessName(businessName) {
  let base = String(businessName || '')
    .trim()
    .toLowerCase()
  base = base.replace(/[^a-z0-9]+/g, '-')
  base = base.replace(/-{2,}/g, '-')
  base = base.replace(/^-+/, '').replace(/-+$/, '')
  if (base.length < 2) throw new Error('invalid_business_name')
  if (base.length > 48) {
    base = base.slice(0, 48).replace(/-+$/, '')
  }
  if (base.length < 2) throw new Error('invalid_business_name')
  return base
}
