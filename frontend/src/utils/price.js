export function formatZar(value) {
  const n = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(n)) return String(value ?? '')

  return new Intl.NumberFormat('en-ZA', {
    style: 'currency',
    currency: 'ZAR'
  }).format(n)
}

