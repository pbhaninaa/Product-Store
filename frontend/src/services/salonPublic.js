import { apiFetch, apiFetchMultipart } from '@/services/api'
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
    needsEftProof: Boolean(res.needsEftProof),
    paymentReferenceHint: String(res.paymentReferenceHint || res.bookingId || ''),
    bookingStatus: String(res.bookingStatus || ''),
    cashPaymentCode: res.cashPaymentCode != null ? String(res.cashPaymentCode) : '',
    needsCashPaymentCode: Boolean(res.needsCashPaymentCode)
  }
}

/** Public multipart: bank reference auto-check; booking may confirm or go to merchant review. */
export async function submitSalonBookingEftProof(merchantSlug, bookingId, { customerEmail, bankReference, file }) {
  const slug = String(merchantSlug || '').trim()
  const id = String(bookingId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or booking.')
  if (!(file instanceof File)) throw new Error('Choose a PDF or image of your proof of payment.')
  const fd = new FormData()
  fd.append('customerEmail', String(customerEmail || '').trim())
  fd.append('bankReference', String(bankReference || '').trim())
  fd.append('proof', file)
  return await apiFetchMultipart(`/api/public/m/${encodeURIComponent(slug)}/salon/bookings/${encodeURIComponent(id)}/eft-proof`, {
    method: 'POST',
    formData: fd,
    auth: false
  })
}

