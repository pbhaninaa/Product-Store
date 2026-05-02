import { apiFetch, apiFetchMultipart } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'

function slugFromRoute(route) {
  return requireMerchantSlugForApi(route)
}

export async function fetchAdminOrders(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/orders`, { auth: true })
}

/** EFT: send `{}`. Cash in store: send `{ cashCode: '123456' }` from the customer’s code. */
export async function confirmAdminOrderPayment(route, orderId, { cashCode } = {}) {
  const slug = slugFromRoute(route)
  const trimmed = cashCode != null ? String(cashCode).trim() : ''
  const json = trimmed ? { cashCode: trimmed } : {}
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/orders/${encodeURIComponent(orderId)}/confirm-payment`, {
    method: 'POST',
    json,
    auth: true
  })
}

export async function cancelAdminOrder(route, orderId) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/orders/${encodeURIComponent(orderId)}/cancel`, {
    method: 'POST',
    auth: true
  })
}

/** Permanent delete — server only allows unpaid (non-paid) orders. */
export async function deleteAdminOrderPermanent(route, orderId) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/orders/${encodeURIComponent(orderId)}`, {
    method: 'DELETE',
    auth: true
  })
}

export async function fetchAdminStoreSettings(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/settings`, { auth: true })
}

export async function updateAdminDelivery(route, payload) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/delivery`, {
    method: 'PUT',
    json: payload,
    auth: true
  })
}

export async function updateAdminBanking(route, payload) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/banking`, {
    method: 'PUT',
    json: payload,
    auth: true
  })
}

export async function updateAdminContact(route, payload) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/contact`, {
    method: 'PUT',
    json: payload,
    auth: true
  })
}

export async function updateAdminShopType(route, shopType) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/shop-type`, {
    method: 'PUT',
    json: { shopType: String(shopType || 'normal_store') },
    auth: true
  })
}

export async function updateAdminOpeningHours(route, openingHoursJson) {
  const slug = slugFromRoute(route)
  /** POST (not only PUT) — backend accepts both; avoids 404/method issues with some proxies or stale deployments. */
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/opening-hours`, {
    method: 'POST',
    json: { openingHoursJson: openingHoursJson == null ? '[]' : String(openingHoursJson) },
    auth: true
  })
}

export async function updateAdminBranding(route, payload) {
  const slug = slugFromRoute(route)
  const st = payload && payload.shopType != null ? String(payload.shopType).trim() : ''
  const storeName =
    payload && payload.storeName != null ? String(payload.storeName).trim() : ''
  const logo = pickImageFile(payload && payload.logoFile)
  const hero = pickImageFile(payload && payload.heroFile)
  const removeLogo = Boolean(payload && payload.removeLogo)
  const removeHero = Boolean(payload && payload.removeHero)

  if (logo || hero || removeLogo || removeHero) {
    const fd = new FormData()
    fd.append('storeName', storeName)
    /** Always send shop type with multipart branding so store type is not skipped when saving images. */
    fd.append('shopType', st || 'normal_store')
    fd.append('removeLogo', removeLogo ? 'true' : 'false')
    fd.append('removeHero', removeHero ? 'true' : 'false')
    if (logo) fd.append('logo', logo)
    if (hero) fd.append('hero', hero)
    return await apiFetchMultipart(`/api/m/${encodeURIComponent(slug)}/admin/store/branding`, {
      method: 'PUT',
      formData: fd,
      auth: true
    })
  }

  const safe = {
    storeName,
    /** Never omit shopType — backend only updates type when non-blank; explicit value avoids accidental normal_store. */
    shopType: st || 'normal_store'
  }
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/store/branding`, {
    method: 'PUT',
    json: safe,
    auth: true
  })
}

function pickImageFile(raw) {
  if (!raw) return null
  if (Array.isArray(raw)) return raw[0] instanceof File ? raw[0] : null
  return raw instanceof File ? raw : null
}

export async function createAdminProduct(route, payload) {
  const slug = slugFromRoute(route)
  const img = pickImageFile(payload && payload.file)
  if (img) {
    const fd = new FormData()
    fd.append('name', String((payload && payload.name) || ''))
    fd.append('category', String((payload && payload.category) || ''))
    fd.append('price', String((payload && payload.price) ?? ''))
    fd.append('stock', String((payload && payload.stock) ?? '0'))
    fd.append('image', img)
    return await apiFetchMultipart(`/api/m/${encodeURIComponent(slug)}/admin/products`, {
      method: 'POST',
      formData: fd,
      auth: true
    })
  }
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/products`, {
    method: 'POST',
    json: {
      name: String((payload && payload.name) || ''),
      category: String((payload && payload.category) || ''),
      price: String((payload && payload.price) ?? ''),
      stock: String((payload && payload.stock) ?? '0')
    },
    auth: true
  })
}

export async function updateAdminProduct(route, productId, payload) {
  const slug = slugFromRoute(route)
  const img = pickImageFile(payload && payload.imageFile)
  if (img) {
    const fd = new FormData()
    fd.append('name', String((payload && payload.name) || ''))
    fd.append('category', String((payload && payload.category) || ''))
    fd.append('price', String((payload && payload.price) ?? ''))
    fd.append('stock', String((payload && payload.stock) ?? '0'))
    fd.append('image', img)
    return await apiFetchMultipart(`/api/m/${encodeURIComponent(slug)}/admin/products/${encodeURIComponent(productId)}`, {
      method: 'PUT',
      formData: fd,
      auth: true
    })
  }
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/products/${encodeURIComponent(productId)}`, {
    method: 'PUT',
    json: {
      name: String((payload && payload.name) || ''),
      category: String((payload && payload.category) || ''),
      price: String((payload && payload.price) ?? ''),
      stock:
        payload && payload.stock != null && Number.isFinite(Number(payload.stock))
          ? Math.max(0, Math.floor(Number(payload.stock)))
          : 0
    },
    auth: true
  })
}

export async function deleteAdminProduct(route, productId) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/products/${encodeURIComponent(productId)}`, {
    method: 'DELETE',
    auth: true
  })
}

