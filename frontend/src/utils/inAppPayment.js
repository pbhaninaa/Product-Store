/** PayFast is the current in-app rail (Wheel Hub). `peach` remains on leftover rows. */
export function isInAppPaymentMethod(raw) {
  const m = String(raw || '').toLowerCase()
  return m === 'peach' || m === 'payfast'
}

export function matchesPaymentMethodFilter(orderMethod, filter) {
  const m = String(orderMethod || '').toLowerCase()
  const f = String(filter || '').trim().toLowerCase()
  if (!f) return true
  if (f === 'peach' || f === 'payfast') return isInAppPaymentMethod(m)
  return m === f
}

export function inAppPaymentLabel(method, subtype) {
  if (!isInAppPaymentMethod(method)) return null
  const s = String(subtype || '').toUpperCase()
  if (s === 'CARD') return 'PAYFAST · CARD'
  if (s === 'EFT') return 'PAYFAST · INSTANT EFT'
  return 'PAYFAST'
}
