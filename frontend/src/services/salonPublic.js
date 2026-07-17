import { apiFetch } from '@/services/api'
import { normalizeShopType } from '@/services/shopType'

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
    json: payload
  })
  if (!res || !res.bookingId) throw new Error('No booking reference returned.')
  return {
    bookingId: String(res.bookingId),
    paymentMethod: String(res.paymentMethod || ''),
    needsPeachCheckout: Boolean(res.needsPeachCheckout),
    peachRedirectUrl: res.peachRedirectUrl != null ? String(res.peachRedirectUrl) : '',
    peachCheckoutId: res.peachCheckoutId != null ? String(res.peachCheckoutId) : '',
    paymentReferenceHint: String(res.paymentReferenceHint || res.bookingId || ''),
    bookingStatus: String(res.bookingStatus || ''),
    cashPaymentCode: res.cashPaymentCode != null ? String(res.cashPaymentCode) : '',
    needsCashPaymentCode: Boolean(res.needsCashPaymentCode)
  }
}

export async function fetchBookingPeachStatus(merchantSlug, bookingId, customerEmail) {
  const slug = String(merchantSlug || '').trim()
  const id = String(bookingId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or booking.')
  const q = encodeURIComponent(String(customerEmail || '').trim())
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/salon/bookings/${encodeURIComponent(id)}/peach-status?customerEmail=${q}`
  )
}

