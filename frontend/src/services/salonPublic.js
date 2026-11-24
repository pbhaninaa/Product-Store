import { apiFetch } from '@/services/api'
import { normalizeShopType } from '@/services/shopType'
import { readOnlinePaymentStart } from '@/utils/onlinePayment'
import { paymentStatusQuery } from '@/utils/paymentReturn'
import { readHandoverCode } from '@/utils/handoverCode'
import { readBookingFee } from '@/utils/bookingFee'

export async function fetchSalonServices(merchantSlug) {
  const slug = String(merchantSlug || '').trim()
  if (!slug)
    return {
      salonEnabled: false,
      shopType: 'normal_store',
      services: []
    }
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/salon/services`)
  return {
    salonEnabled: Boolean(res && res.salonEnabled),
    shopType: normalizeShopType(res && res.shopType),
    services: res && res.services ? res.services : []
  }
}

export async function fetchSalonAvailability(merchantSlug, serviceId, date) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) throw new Error('Missing merchant.')
  const res = await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/salon/availability?serviceId=${encodeURIComponent(
      serviceId
    )}&date=${encodeURIComponent(date)}`
  )
  return res && res.slots ? res.slots : []
}

export async function createSalonBooking(merchantSlug, payload) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) throw new Error('Missing merchant.')
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/salon/bookings`, {
    method: 'POST',
    json: payload,
    auth: true
  })
  if (!res || !res.bookingId) throw new Error('No booking reference returned.')
  return {
    bookingId: String(res.bookingId),
    paymentMethod: String(res.paymentMethod || ''),
    ...readOnlinePaymentStart(res),
    bookingStatus: String(res.bookingStatus || ''),
    handoverCode: readHandoverCode(res),
    cashPaymentCode: res.cashPaymentCode != null ? String(res.cashPaymentCode) : '',
    needsCashPaymentCode: Boolean(res.needsCashPaymentCode),
    bookingFee: readBookingFee(res)
  }
}

/**
 * Booking payment status; the server verifies with the provider while the booking is still pending.
 * `lookup` is `{ reference, customerEmail }` (see fetchOrderPaymentStatus).
 */
export async function fetchBookingPaymentStatus(merchantSlug, bookingId, lookup) {
  const slug = String(merchantSlug || '').trim()
  const id = String(bookingId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or booking.')
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/salon/bookings/${encodeURIComponent(id)}/payment-status${paymentStatusQuery(lookup)}`
  )
}

export async function lookupPublicBooking(merchantSlug, bookingId, customerEmail) {
  const slug = String(merchantSlug || '').trim()
  const id = String(bookingId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or booking.')
  const q = encodeURIComponent(String(customerEmail || '').trim())
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/salon/bookings/${encodeURIComponent(id)}?customerEmail=${q}`
  )
}


/** Customer cancels their own pay in store booking from the booking page (full booking id plus the booking email). */
export async function cancelPublicBooking(merchantSlug, bookingId, customerEmail) {
  const slug = String(merchantSlug || '').trim()
  const id = String(bookingId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or booking.')
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/salon/bookings/${encodeURIComponent(id)}/cancel`,
    { method: 'POST', json: { customerEmail: String(customerEmail || '').trim() } }
  )
}
