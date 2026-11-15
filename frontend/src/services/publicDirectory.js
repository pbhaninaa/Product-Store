import { apiFetch } from '@/services/api'

/**
 * Open provider directory behind the shareable /providers link (no sign-in).
 * kind: 'all' | 'shop' (items sold) | 'salon' (bookable services) — API values. Pass latitude and longitude together to sort nearest first.
 */
export async function fetchProviderDirectory({ kind = 'all', latitude, longitude } = {}) {
  const params = new URLSearchParams()
  if (kind && kind !== 'all') params.set('kind', kind)
  if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
    params.set('latitude', String(latitude))
    params.set('longitude', String(longitude))
  }
  const qs = params.toString()
  const rows = await apiFetch(`/api/public/providers${qs ? `?${qs}` : ''}`)
  return Array.isArray(rows) ? rows : []
}
