import { apiFetch } from '@/services/api'

export async function fetchNearbyOfferings({ kind, latitude, longitude, radiusKm, q }) {
  const params = new URLSearchParams()
  params.set('kind', kind)
  params.set('latitude', String(latitude))
  params.set('longitude', String(longitude))
  if (radiusKm != null) params.set('radiusKm', String(radiusKm))
  if (q) params.set('q', q)
  return apiFetch(`/api/clients/nearby/offerings?${params.toString()}`, { auth: true })
}

export async function fetchNearbyMerchants({ kind, latitude, longitude, radiusKm, names }) {
  const params = new URLSearchParams()
  params.set('kind', kind)
  params.set('latitude', String(latitude))
  params.set('longitude', String(longitude))
  if (radiusKm != null) params.set('radiusKm', String(radiusKm))
  if (names) params.set('names', names)
  return apiFetch(`/api/clients/nearby/merchants?${params.toString()}`, { auth: true })
}

export async function startClientOrderPayFast(orderId) {
  return apiFetch(`/api/clients/me/orders/${encodeURIComponent(orderId)}/payfast-checkout`, {
    method: 'POST',
    auth: true
  })
}

export async function startClientBookingPayFast(bookingId) {
  return apiFetch(`/api/clients/me/bookings/${encodeURIComponent(bookingId)}/payfast-checkout`, {
    method: 'POST',
    auth: true
  })
}
