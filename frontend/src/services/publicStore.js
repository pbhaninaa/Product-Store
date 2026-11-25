import { apiFetch, resolveMediaUrl } from '@/services/api'
import { normalizeShopType } from '@/services/shopType'
import { ONLINE_PAYMENT_METHOD, readOnlinePaymentStart } from '@/utils/onlinePayment'
import { paymentStatusQuery } from '@/utils/paymentReturn'
import { readHandoverCode } from '@/utils/handoverCode'

export async function fetchCatalog(merchantSlug) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) return []
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/catalog`)
  return res && res.products ? res.products : []
}

export async function fetchProductsByIds(merchantSlug, ids) {
  const slug = String(merchantSlug || '').trim()
  const uniq = [...new Set((ids || []).filter(Boolean))]
  if (!slug || !uniq.length) return []
  const q = uniq.join(',')
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/catalog/by-ids?ids=${encodeURIComponent(q)}`)
  return res && res.products ? res.products : []
}

export async function fetchShopSettings(merchantSlug) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) {
    return {
      deliveryFeeZar: 50,
      deliveryFeeMode: 'standard',
      deliveryFeePerKmZar: 8,
      storeLat: null,
      storeLng: null,
      shopType: 'normal_store',
      salonEnabled: false,
      storeName: '',
      storeLogoUrl: '',
      storeHeroUrl: '',
      contactEmail: '',
      contactPhone: '',
      contactAddress: '',
      contactNotes: '',
      openingHoursJson: '[]',
      acceptCustomerOnline: true,
      acceptCustomerCash: true,
      onlinePaymentConfigured: false
    }
  }
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/shop-settings`)
  return {
    deliveryFeeZar: Number(res.deliveryFeeZar),
    deliveryFeeMode: res.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard',
    deliveryFeePerKmZar: Number(res.deliveryFeePerKmZar),
    storeLat: res.storeLat != null ? Number(res.storeLat) : null,
    storeLng: res.storeLng != null ? Number(res.storeLng) : null,
    shopType: normalizeShopType(res.shopType),
    salonEnabled: Boolean(res.salonEnabled),
    storeName: String(res.storeName || ''),
    storeLogoUrl: resolveMediaUrl(String(res.storeLogoUrl || '')),
    storeHeroUrl: resolveMediaUrl(String(res.storeHeroUrl || '')),
    contactEmail: String(res.contactEmail || ''),
    contactPhone: String(res.contactPhone || ''),
    contactAddress: String(res.contactAddress || ''),
    contactNotes: String(res.contactNotes || ''),
    openingHoursJson: String(res.openingHoursJson != null && String(res.openingHoursJson).trim() !== '' ? res.openingHoursJson : '[]'),
    acceptCustomerOnline: res.acceptCustomerOnline !== false,
    acceptCustomerCash: res.acceptCustomerCash !== false,
    onlinePaymentConfigured: Boolean(res.onlinePaymentConfigured),
    customerPaymentsCashOnly: Boolean(res.customerPaymentsCashOnly)
  }
}

/**
 * Best currently-valid promotion for a cart (`{ items }`) or a salon booking (`{ serviceId }`).
 * The server prices the discount; send the returned promoId with the order so checkout re-validates it.
 * Returns `{ promoId, title, discountZar }` (promoId null and discountZar 0 when nothing applies).
 */
export async function quotePromotion(merchantSlug, { items, serviceId } = {}) {
  const slug = String(merchantSlug || '').trim()
  const none = { promoId: null, title: '', discountZar: 0 }
  if (!slug) return none
  const body = serviceId
    ? { serviceId: String(serviceId) }
    : { items: (items || []).map((r) => ({ product_id: r.product_id, quantity: r.quantity })) }
  if (!body.serviceId && !body.items.length) return none
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/promotions/quote`, {
    method: 'POST',
    json: body
  })
  const discount = Number(res && res.discountZar)
  if (!res || !res.promoId || !Number.isFinite(discount) || discount <= 0) return none
  return { promoId: String(res.promoId), title: String(res.title || ''), discountZar: discount }
}

export async function placeOrder(merchantSlug, params) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) throw new Error('Missing merchant.')

  const items = (params.items || []).map((r) => ({
    product_id: r.product_id,
    quantity: r.quantity
  }))

  const payload = {
    customerName: params.customerName,
    customerEmail: params.customerEmail,
    customerPhone: params.customerPhone != null ? String(params.customerPhone).trim() : '',
    deliveryType: params.deliveryType,
    deliveryAddress: params.deliveryType === 'delivery' ? params.deliveryAddress : '',
    deliveryLat: params.deliveryType === 'delivery' ? params.deliveryLat : null,
    deliveryLng: params.deliveryType === 'delivery' ? params.deliveryLng : null,
    paymentMethod: params.paymentMethod,
    onlinePaymentChannel: params.paymentMethod === ONLINE_PAYMENT_METHOD ? params.onlinePaymentChannel : null,
    promoId: params.promoId || null,
    items
  }

  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/checkout/orders`, {
    method: 'POST',
    json: payload,
    auth: true
  })

  const id = res && res.orderId ? String(res.orderId) : ''
  if (!id) throw new Error('No order reference returned.')
  return {
    orderId: id,
    ...readOnlinePaymentStart(res),
    handoverCode: readHandoverCode(res),
    cashPaymentCode: res && res.cashPaymentCode != null ? String(res.cashPaymentCode) : '',
    needsCashPaymentCode: Boolean(res && res.needsCashPaymentCode)
  }
}

/**
 * Order payment status; the server verifies with the provider while the order is still pending.
 * `lookup` is `{ reference, customerEmail }`: the Paystack reference proves the caller came back from checkout;
 * the email is only sent when an older return link carries it.
 */
export async function fetchOrderPaymentStatus(merchantSlug, orderId, lookup) {
  const slug = String(merchantSlug || '').trim()
  const id = String(orderId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or order.')
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders/${encodeURIComponent(id)}/payment-status${paymentStatusQuery(lookup)}`
  )
}

export async function lookupPublicOrder(merchantSlug, orderId, customerEmail) {
  const slug = String(merchantSlug || '').trim()
  const id = String(orderId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or order.')
  const q = encodeURIComponent(String(customerEmail || '').trim())
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders/${encodeURIComponent(id)}?customerEmail=${q}`
  )
}

export async function fetchReviewSummary(merchantSlug) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) return { averageRating: 0, reviewCount: 0 }
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/reviews/summary`)
  return {
    averageRating: Number(res && res.averageRating) || 0,
    reviewCount: Number(res && res.reviewCount) || 0
  }
}

export async function fetchReviewRated(merchantSlug, kind, id) {
  const slug = String(merchantSlug || '').trim()
  const k = String(kind || 'order').trim()
  const ident = String(id || '').trim()
  if (!slug || !ident) return false
  const res = await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/reviews/rated?kind=${encodeURIComponent(k)}&id=${encodeURIComponent(ident)}`
  )
  return Boolean(res && res.rated)
}

export async function submitPublicReview(merchantSlug, { kind, id, customerEmail, rating, comment }) {
  const slug = String(merchantSlug || '').trim()
  if (!slug) throw new Error('Missing merchant.')
  return await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/reviews`, {
    method: 'POST',
    json: {
      kind: String(kind || 'order'),
      id: String(id || ''),
      customerEmail: String(customerEmail || '').trim(),
      rating: Number(rating),
      comment: String(comment || '')
    }
  })
}

export async function fetchPublicFaqs(audience) {
  const q = audience ? `?audience=${encodeURIComponent(audience)}` : ''
  const res = await apiFetch(`/api/public/faqs${q}`)
  return res && res.sections ? res.sections : []
}
