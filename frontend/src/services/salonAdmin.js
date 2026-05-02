import { apiFetch, apiFetchMultipart } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'

function slugFromRoute(route) {
  return requireMerchantSlugForApi(route)
}

export function fetchAdminSalonServices(route, all = true) {
  const slug = slugFromRoute(route)
  const q = all ? '?all=true' : ''
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/services${q}`, { auth: true })
}

export function fetchAdminSalonStaff(route, all = true) {
  const slug = slugFromRoute(route)
  const q = all ? '?all=true' : ''
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/staff${q}`, { auth: true })
}

export function upsertAdminSalonService(route, body) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/services`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

/** Create/update with optional image (multipart). */
export function upsertAdminSalonServiceMultipart(route, { id, name, description, durationMinutes, priceZar, active, image }) {
  const slug = slugFromRoute(route)
  const fd = new FormData()
  if (id) fd.append('id', String(id))
  fd.append('name', String(name || ''))
  fd.append('description', String(description || ''))
  fd.append('durationMinutes', String(durationMinutes != null ? durationMinutes : 60))
  fd.append('priceZar', String(priceZar != null ? priceZar : '0'))
  fd.append('active', active === false ? 'false' : 'true')
  if (image instanceof File) fd.append('image', image)
  return apiFetchMultipart(`/api/m/${encodeURIComponent(slug)}/admin/salon/services`, {
    method: 'POST',
    formData: fd,
    auth: true
  })
}

export function upsertAdminSalonStaff(route, body) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/staff`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

export function addAdminSalonAvailability(route, body) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/availability`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

export function fetchAdminSalonAvailability(route) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/availability`, { auth: true })
}

export function deleteAdminSalonAvailability(route, id) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/availability/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    auth: true
  })
}

export function fetchAdminSalonBookings(route) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/bookings`, { auth: true })
}

/** Hard-delete — server rejects confirmed (paid / locked) appointments. */
export function deleteAdminSalonBooking(route, bookingId) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/bookings/${encodeURIComponent(bookingId)}`, {
    method: 'DELETE',
    auth: true
  })
}

export function updateAdminSalonBookingStatus(route, bookingId, status) {
  const slug = slugFromRoute(route)
  return apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/salon/bookings/${encodeURIComponent(bookingId)}/status`, {
    method: 'POST',
    json: { status },
    auth: true
  })
}

/** decision: 'approve' | 'reject' — for EFT bookings in manual payment review. */
export function postAdminSalonBookingEftPaymentDecision(route, bookingId, decision) {
  const slug = slugFromRoute(route)
  return apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/salon/bookings/${encodeURIComponent(bookingId)}/eft-payment-decision`,
    {
      method: 'POST',
      json: { decision: String(decision || '').trim().toLowerCase() },
      auth: true
    }
  )
}

/** Customer’s cash payment code — marks booking paid (confirmed) when valid. */
export function postAdminSalonBookingCashConfirm(route, bookingId, code) {
  const slug = slugFromRoute(route)
  return apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/salon/bookings/${encodeURIComponent(bookingId)}/confirm-cash`,
    {
      method: 'POST',
      json: { code: String(code || '').trim() },
      auth: true
    }
  )
}
