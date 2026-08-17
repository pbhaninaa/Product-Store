export function formatZar(value: unknown): string {
  const n = typeof value === 'string' ? Number(value) : Number(value);
  if (!Number.isFinite(n)) return String(value ?? '');
  try {
    return new Intl.NumberFormat('en-ZA', {
      style: 'currency',
      currency: 'ZAR',
    }).format(n);
  } catch {
    return `R ${n.toFixed(2)}`;
  }
}
