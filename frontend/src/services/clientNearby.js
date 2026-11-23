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

/** Starts online payment for one of the signed in customer's unpaid orders: `{ paymentId, paymentRedirectUrl }`. */
export async function startClientOrderOnlinePayment(orderId) {
  return apiFetch(`/api/clients/me/orders/${encodeURIComponent(orderId)}/online-checkout`, {
    method: 'POST',
    auth: true
  })
}

/** Starts online payment for one of the signed in customer's unpaid bookings: `{ paymentId, paymentRedirectUrl }`. */
export async function startClientBookingOnlinePayment(bookingId) {
  return apiFetch(`/api/clients/me/bookings/${encodeURIComponent(bookingId)}/online-checkout`, {
    method: 'POST',
    auth: true
  })
}
