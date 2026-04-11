import { supabase, supabaseReady } from '@/supabase'

function friendlyRpcError(err, fallback) {
  const raw = String((err && (err.message || err.details || err.hint)) || '')
  const m = raw.toLowerCase()
  if (/invalid_name|invalid name/i.test(raw)) return 'Please enter your full name.'
  if (/invalid_email|invalid email/i.test(raw)) return 'Please enter a valid email address.'
  if (/invalid_phone/i.test(raw))
    return 'Please enter a valid phone number (at least 8 digits, e.g. 082 123 4567).'
  if (/delivery_address_required/i.test(raw)) return 'Please enter a full delivery address.'
  if (/empty_cart|too_many_lines/i.test(raw)) return 'Your cart is empty or has too many lines.'
  if (/insufficient_stock/i.test(raw))
    return 'Not enough stock for one or more items. Reduce quantities or remove items and try again.'
  if (/insufficient_stock_on_confirm/i.test(raw))
    return 'Not enough stock to confirm this order (inventory may have changed). Adjust stock or cancel the order.'
  if (/invalid_line|product_not_found/i.test(raw)) return 'A product in your cart is no longer available. Refresh and try again.'
  if (/invalid_delivery_distance/i.test(raw))
    return 'Delivery distance could not be calculated. Try another point on the map.'
  if (/delivery_location_required/i.test(raw))
    return 'Tap the map to set where we should deliver, or use “Use my location”.'
  if (/invalid_delivery_coordinates/i.test(raw)) return 'That map location is invalid. Try another point.'
  if (/store_location_not_set/i.test(raw))
    return 'The shop has not set a store location yet. Please contact us or choose pickup.'
  if (/invalid_delivery_type|invalid_payment_method/i.test(raw)) return 'Invalid delivery or payment selection.'
  if (/not_authenticated/i.test(raw)) return 'You must be signed in to confirm payments.'
  if (/JWT|session|not authenticated/i.test(m)) return 'Sign in again on Admin.'
  if (/row-level security|RLS|permission denied/i.test(m)) {
    return 'Permission denied. In Supabase → SQL Editor, run the latest supabase/all.sql (delete policies for orders).'
  }
  return raw || fallback
}

export async function fetchShopSettings() {
  if (!supabaseReady || !supabase) {
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
      eftBankInstructions: ''
    }
  }
  const { data, error } = await supabase.from('shop_settings').select('*').eq('id', 1).maybeSingle()
  if (error) {
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
      eftBankInstructions: ''
    }
  }
  const row = data || {}
  const fee = Number(row.delivery_fee_zar)
  const perKm = Number(row.delivery_fee_per_km_zar)
  const mode = row.delivery_fee_mode === 'per_km' ? 'per_km' : 'standard'
  const sl = row.store_lat != null ? Number(row.store_lat) : null
  const so = row.store_lng != null ? Number(row.store_lng) : null
  return {
    deliveryFeeZar: Number.isFinite(fee) ? fee : 50,
    deliveryFeeMode: mode,
    deliveryFeePerKmZar: Number.isFinite(perKm) ? perKm : 8,
    storeLat: Number.isFinite(sl) ? sl : null,
    storeLng: Number.isFinite(so) ? so : null,
    bankName: String(row.bank_name || '').trim(),
    bankAccountHolder: String(row.bank_account_holder || '').trim(),
    bankAccountNumber: String(row.bank_account_number || '').trim(),
    bankBranchCode: String(row.bank_branch_code || '').trim(),
    eftBankInstructions: String(row.eft_bank_instructions || '')
  }
}

/**
 * Staff: banking details for EFT (customers see these at checkout).
 * @param {{ bankName: string, bankAccountHolder: string, bankAccountNumber: string, bankBranchCode?: string, eftBankInstructions?: string }} params
 */
export async function updateBankingDetails(params) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  const p = params || {}
  const bank_name = String(p.bankName != null ? p.bankName : '').trim()
  const bank_account_holder = String(p.bankAccountHolder != null ? p.bankAccountHolder : '').trim()
  const bank_account_number = String(p.bankAccountNumber != null ? p.bankAccountNumber : '').trim()
  const bank_branch_code = String(p.bankBranchCode != null ? p.bankBranchCode : '').trim()
  const eft_bank_instructions = String(p.eftBankInstructions != null ? p.eftBankInstructions : '').trim()

  if (bank_name.length < 2) {
    throw new Error('Enter the bank name.')
  }
  if (bank_account_holder.length < 2) {
    throw new Error('Enter the account holder name (as it appears on the account).')
  }
  const digits = bank_account_number.replace(/\D/g, '')
  if (digits.length < 4 || bank_account_number.length > 34) {
    throw new Error('Enter a valid account number.')
  }

  const { error } = await supabase
    .from('shop_settings')
    .update({
      bank_name,
      bank_account_holder,
      bank_account_number,
      bank_branch_code: bank_branch_code.length ? bank_branch_code : null,
      eft_bank_instructions: eft_bank_instructions
    })
    .eq('id', 1)
  if (error) throw new Error(friendlyRpcError(error, 'Could not save banking details.'))
}

/**
 * Staff: update delivery pricing (row id = 1 in shop_settings).
 * @param {{ deliveryFeeZar: number|string, deliveryFeeMode?: 'standard'|'per_km', deliveryFeePerKmZar?: number|string, storeLat?: number|null, storeLng?: number|null }} params
 */
export async function updateShopSettings(params) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  const p = params || {}
  const mode = p.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard'
  const rawFlat = p.deliveryFeeZar
  const n = typeof rawFlat === 'string' ? parseFloat(String(rawFlat).replace(',', '.')) : Number(rawFlat)
  if (!Number.isFinite(n) || n < 0 || n > 999999.99) {
    throw new Error('Enter a valid standard delivery fee between 0 and 999999.99.')
  }
  const delivery_fee_zar = Math.round(n * 100) / 100
  const rawPerKm = p.deliveryFeePerKmZar
  const pk =
    typeof rawPerKm === 'string' ? parseFloat(String(rawPerKm).replace(',', '.')) : Number(rawPerKm)
  if (!Number.isFinite(pk) || pk < 0 || pk > 100000) {
    throw new Error('Enter a valid rate per km between 0 and 100000.')
  }
  const delivery_fee_per_km_zar = Math.round(pk * 100) / 100

  const patch = {
    delivery_fee_zar,
    delivery_fee_mode: mode,
    delivery_fee_per_km_zar
  }
  if (p.storeLat != null && p.storeLng != null) {
    const la = typeof p.storeLat === 'string' ? parseFloat(p.storeLat) : Number(p.storeLat)
    const ln = typeof p.storeLng === 'string' ? parseFloat(p.storeLng) : Number(p.storeLng)
    if (!Number.isFinite(la) || la < -90 || la > 90 || !Number.isFinite(ln) || ln < -180 || ln > 180) {
      throw new Error('Store map location must be a valid latitude and longitude.')
    }
    patch.store_lat = la
    patch.store_lng = ln
  }
  const { error } = await supabase.from('shop_settings').update(patch).eq('id', 1)
  if (error) throw new Error(friendlyRpcError(error, 'Could not save shop settings.'))
}

/**
 * @param {object} params
 * @param {string} params.customerName
 * @param {string} params.customerEmail
 * @param {string} params.customerPhone Required — at least 8 digits (spaces/symbols allowed)
 * @param {'pickup'|'delivery'} params.deliveryType
 * @param {string} params.deliveryAddress
 * @param {'eft'|'cash_store'} params.paymentMethod
 * @param {{ product_id: string, quantity: number }[]} params.items
 * @param {number|null} [params.deliveryLat] Customer delivery pin (WGS84), required for per-km pricing
 * @param {number|null} [params.deliveryLng]
 * @returns {Promise<string>} Human-readable order reference (e.g. ORD-000042)
 */
/** @param {unknown} err */
export function isInsufficientStockError(err) {
  return Boolean(err && typeof err === 'object' && err.code === 'INSUFFICIENT_STOCK')
}

export async function placeOrder(params) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }

  const items = (params.items || []).map((r) => ({
    product_id: r.product_id,
    quantity: r.quantity
  }))

  let pDeliveryLat = null
  let pDeliveryLng = null
  if (params.deliveryType === 'delivery') {
    const la = params.deliveryLat
    const ln = params.deliveryLng
    if (la != null && ln != null && Number.isFinite(Number(la)) && Number.isFinite(Number(ln))) {
      pDeliveryLat = Number(la)
      pDeliveryLng = Number(ln)
    }
  }

  const { data, error } = await supabase.rpc('create_order', {
    p_customer_name: params.customerName,
    p_customer_email: params.customerEmail,
    p_customer_phone: params.customerPhone != null ? String(params.customerPhone).trim() : '',
    p_delivery_type: params.deliveryType,
    p_delivery_address: params.deliveryType === 'delivery' ? params.deliveryAddress : null,
    p_payment_method: params.paymentMethod,
    p_items: items,
    p_delivery_lat: pDeliveryLat,
    p_delivery_lng: pDeliveryLng
  })

  if (error) {
    const raw = String((error && (error.message || error.details || error.hint)) || '')
    const friendly = friendlyRpcError(error, 'Could not place order.')
    const e = new Error(friendly)
    if (/insufficient_stock/i.test(raw)) e.code = 'INSUFFICIENT_STOCK'
    throw e
  }
  if (!data) throw new Error('No order reference returned.')
  return String(data)
}

/** Human-readable order number (e.g. ORD-000042); falls back to UUID if ref missing (pre-migration rows). */
export function orderDisplayRef(order) {
  if (!order) return ''
  const ref = order.order_ref != null ? String(order.order_ref).trim() : ''
  if (ref) return ref
  return String(order.id || '')
}

const ORDER_DETAIL_SELECT = `
  *,
  order_items (
    id,
    product_id,
    quantity,
    unit_price_zar,
    line_total_zar,
    products ( name )
  )
`

export async function fetchOrderByIdForAdmin(orderId) {
  if (!supabaseReady || !supabase) return null
  if (!orderId) return null
  const { data, error } = await supabase
    .from('orders')
    .select(ORDER_DETAIL_SELECT)
    .eq('id', orderId)
    .maybeSingle()

  if (error) throw new Error(friendlyRpcError(error, 'Could not load order.'))
  return data
}

export async function fetchOrdersForAdmin() {
  if (!supabaseReady || !supabase) return []
  const { data, error } = await supabase
    .from('orders')
    .select(ORDER_DETAIL_SELECT)
    .order('created_at', { ascending: false })

  if (error) throw new Error(friendlyRpcError(error, 'Could not load orders.'))
  return data || []
}

export function subscribeToOrders(callback) {
  if (!supabaseReady || !supabase) {
    callback([])
    return () => {}
  }

  let cancelled = false

  const load = async () => {
    try {
      const rows = await fetchOrdersForAdmin()
      if (!cancelled) callback(rows)
    } catch (e) {
      if (!cancelled) {
        // eslint-disable-next-line no-console
        console.warn('[orders]', e && e.message ? e.message : e)
        callback([])
      }
    }
  }

  load()

  const channel = supabase
    .channel('orders_admin')
    .on('postgres_changes', { event: '*', schema: 'public', table: 'orders' }, () => {
      load()
    })
    .subscribe()

  return () => {
    cancelled = true
    supabase.removeChannel(channel)
  }
}

/** Staff: cancel an unpaid order — releases reserved quantities (sets cancelled_at). */
export async function cancelUnpaidOrder(orderId) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  const { data, error } = await supabase.rpc('cancel_unpaid_order', {
    p_order_id: orderId
  })
  if (error) throw new Error(friendlyRpcError(error, 'Could not cancel order.'))
  if (data !== true) throw new Error('Order was not cancelled (already paid, cancelled, or not found).')
  return true
}

/** Staff: mark EFT or cash order as paid and subtract stock (see confirm_order_payment in SQL). */
export async function confirmOrderPayment(orderId) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  const { data, error } = await supabase.rpc('confirm_order_payment', {
    p_order_id: orderId
  })
  if (error) throw new Error(friendlyRpcError(error, 'Could not confirm payment.'))
  if (data !== true) throw new Error('Order was not updated (already confirmed or not found).')
  return true
}

/** @deprecated Use confirmOrderPayment; kept as alias (SQL may still expose confirm_eft_payment). */
export async function confirmEftPayment(orderId) {
  return confirmOrderPayment(orderId)
}

/** Allowed fulfillment statuses after payment is confirmed (set via Admin). */
export const ORDER_FULFILLMENT_STATUSES = ['processing', 'ready', 'completed']

/**
 * Staff: advance fulfillment (processing → ready → completed). Sets `completed_at` when status is completed.
 * @param {string} orderId
 * @param {'processing'|'ready'|'completed'} status
 */
export async function updateOrderStatus(orderId, status) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  if (!orderId) throw new Error('Missing order id.')
  const s = String(status || '').trim()
  if (!ORDER_FULFILLMENT_STATUSES.includes(s)) {
    throw new Error('Invalid order status.')
  }
  const completed_at = s === 'completed' ? new Date().toISOString() : null
  const { error } = await supabase
    .from('orders')
    .update({ order_status: s, completed_at })
    .eq('id', orderId)

  if (error) throw new Error(friendlyRpcError(error, 'Could not update order status.'))
}

/** Staff: permanently delete an order and its line items (requires RLS delete policies on orders + order_items). */
export async function deleteOrderPermanently(orderId) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  if (!orderId) throw new Error('Missing order id.')

  const { error } = await supabase.from('orders').delete().eq('id', orderId)
  if (error) throw new Error(friendlyRpcError(error, 'Could not delete order.'))
}
