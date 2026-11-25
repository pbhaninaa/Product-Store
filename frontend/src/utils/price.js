const ZAR = new Intl.NumberFormat('en-ZA', {
  style: 'currency',
  currency: 'ZAR'
})

export function formatZar(value) {
  const n = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(n)) return String(value ?? '')

  return ZAR.format(n)
}

/**
 * Integer cents as ZAR, in the same look as formatZar ("R 1 234,50"), without floating point:
 * rands and cents are split as integers and only whole rands go through Intl.
 * '' when the value is not a whole number of cents. Negatives read "minus R 5,00" (the app never
 * shows a dash).
 */
export function formatCents(cents) {
  const n = typeof cents === 'string' && cents.trim() !== '' ? Number(cents) : cents
  if (typeof n !== 'number' || !Number.isSafeInteger(n)) return ''
  if (n < 0) return `minus ${formatCents(-n)}`
  const rands = Math.floor(n / 100)
  const rest = String(n % 100).padStart(2, '0')
  return ZAR.formatToParts(rands)
    .map((p) => (p.type === 'fraction' ? rest : p.value))
    .join('')
}

/** Integer cents as a plain amount for an input box: 3050 gives "30.50". '' when not whole cents. */
export function centsToAmountInput(cents) {
  const n = typeof cents === 'string' && cents.trim() !== '' ? Number(cents) : cents
  if (typeof n !== 'number' || !Number.isSafeInteger(n) || n < 0) return ''
  return `${Math.floor(n / 100)}.${String(n % 100).padStart(2, '0')}`
}

/**
 * A typed rand amount ("30", "30.5", "30,50", "R 1 000.00") as integer cents, parsed as text so no
 * floating point is involved. Null when empty, negative, not a number or more than 2 decimals.
 */
export function parseZarToCents(text) {
  const s = String(text == null ? '' : text)
    .replace(/\s/g, '')
    .replace(/^R/i, '')
  const m = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(s)
  if (!m) return null
  const cents = Number(m[1]) * 100 + Number((m[2] || '').padEnd(2, '0'))
  return Number.isSafeInteger(cents) ? cents : null
}
