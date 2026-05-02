import { apiFetch, apiFetchMultipart } from '@/services/api'
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
      acceptCustomerEft: true,
      acceptCustomerCash: true
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
    storeLogoUrl: String(res.storeLogoUrl || ''),
    storeHeroUrl: String(res.storeHeroUrl || ''),
    contactEmail: String(res.contactEmail || ''),
    contactPhone: String(res.contactPhone || ''),
    contactAddress: String(res.contactAddress || ''),
    contactNotes: String(res.contactNotes || ''),
    openingHoursJson: String(res.openingHoursJson != null && String(res.openingHoursJson).trim() !== '' ? res.openingHoursJson : '[]'),
    acceptCustomerEft: res.acceptCustomerEft !== false,
    acceptCustomerCash: res.acceptCustomerCash !== false
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
    items
  }

  const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/checkout/orders`, {
    method: 'POST',
    json: payload
  })

  const id = res && res.orderId ? String(res.orderId) : ''
  if (!id) throw new Error('No order reference returned.')
  return {
    orderId: id,
    needsEftProof: Boolean(res && res.needsEftProof),
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

