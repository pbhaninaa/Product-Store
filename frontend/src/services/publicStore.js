import { apiFetch, apiFetchMultipart, resolveMediaUrl } from '@/services/api'
import { normalizeShopType } from '@/services/shopType'

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
      bankName: '',
      bankAccountHolder: '',
      bankAccountNumber: '',
      bankBranchCode: '',
      eftBankInstructions: '',
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
      acceptCustomerPeach: true,
      acceptCustomerEft: true,
      acceptCustomerCash: true,
      peachConfigured: false
    }
  }
  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/shop-settings`)
  return {
    deliveryFeeZar: Number(res.deliveryFeeZar),
    deliveryFeeMode: res.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard',
    deliveryFeePerKmZar: Number(res.deliveryFeePerKmZar),
    storeLat: res.storeLat != null ? Number(res.storeLat) : null,
    storeLng: res.storeLng != null ? Number(res.storeLng) : null,
    bankName: String(res.bankName || ''),
    bankAccountHolder: String(res.bankAccountHolder || ''),
    bankAccountNumber: String(res.bankAccountNumber || ''),
    bankBranchCode: String(res.bankBranchCode || ''),
    eftBankInstructions: String(res.eftBankInstructions || ''),
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
    acceptCustomerPeach: res.acceptCustomerPeach !== false,
    acceptCustomerEft: res.acceptCustomerEft !== false,
    acceptCustomerCash: res.acceptCustomerCash !== false,
    peachConfigured: Boolean(res.peachConfigured || res.payfastConfigured),
    payfastConfigured: Boolean(res.payfastConfigured || res.peachConfigured)
  }
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
    peachPaymentMethod: params.paymentMethod === 'peach' || params.paymentMethod === 'payfast' ? params.peachPaymentMethod : null,
    payFastPaymentMethod: params.paymentMethod === 'peach' || params.paymentMethod === 'payfast' ? params.peachPaymentMethod : null,
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
    needsEftProof: Boolean(res && res.needsEftProof),
    needsPeachCheckout: Boolean(res && res.needsPeachCheckout),
    needsPayFastCheckout: Boolean(res && (res.needsPayFastCheckout || res.needsPeachCheckout)),
    processUrl: res && res.processUrl != null ? String(res.processUrl) : '',
    fields: res && res.fields && typeof res.fields === 'object' ? res.fields : null,
    peachRedirectUrl: res && res.peachRedirectUrl != null ? String(res.peachRedirectUrl) : '',
    peachCheckoutId: res && res.peachCheckoutId != null ? String(res.peachCheckoutId) : '',
    cashPaymentCode: res && res.cashPaymentCode != null ? String(res.cashPaymentCode) : '',
    needsCashPaymentCode: Boolean(res && res.needsCashPaymentCode)
  }
}

/** Public multipart: bank reference auto-check against order id; paid if match else merchant manual review. */
export async function submitCheckoutOrderEftProof(merchantSlug, orderId, { customerEmail, bankReference, file }) {
  const slug = String(merchantSlug || '').trim()
  const id = String(orderId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or order.')
  if (!(file instanceof File)) throw new Error('Choose a PDF or image of your proof of payment.')
  const fd = new FormData()
  fd.append('customerEmail', String(customerEmail || '').trim())
  fd.append('bankReference', String(bankReference || '').trim())
  fd.append('proof', file)
  return await apiFetchMultipart(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders/${encodeURIComponent(id)}/eft-proof`,
    {
      method: 'POST',
      formData: fd,
      auth: false
    }
  )
}

export async function fetchOrderPeachStatus(merchantSlug, orderId, customerEmail) {
  const slug = String(merchantSlug || '').trim()
  const id = String(orderId || '').trim()
  if (!slug || !id) throw new Error('Missing merchant or order.')
  const q = encodeURIComponent(String(customerEmail || '').trim())
  return await apiFetch(
    `/api/public/m/${encodeURIComponent(slug)}/checkout/orders/${encodeURIComponent(id)}/peach-status?customerEmail=${q}`
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
