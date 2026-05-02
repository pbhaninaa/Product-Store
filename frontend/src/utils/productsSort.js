/** Sort by category A–Z, then product name A–Z (locale-aware). */
export function compareProductsByCategoryThenName(a, b) {
  const ca = String(a.category || 'Uncategorized').localeCompare(
    String(b.category || 'Uncategorized'),
    undefined,
    { sensitivity: 'base' }
  )
  if (ca !== 0) return ca
  return String(a.name || '').localeCompare(String(b.name || ''), undefined, { sensitivity: 'base' })
}

