import { supabase, supabaseReady } from '@/supabase'

function friendlyRpcError(err, fallback) {
  const raw = String((err && (err.message || err.details || err.hint)) || '')
  const m = raw.toLowerCase()
  if (/invalid_name|invalid name/i.test(raw)) return 'Please enter your full name.'
  if (/invalid_email|invalid email/i.test(raw)) return 'Please enter a valid email address.'
  if (/delivery_address_required/i.test(raw)) return 'Please enter a full delivery address.'
  if (/empty_cart|too_many_lines/i.test(raw)) return 'Your cart is empty or has too many lines.'
  if (/insufficient_stock/i.test(raw))
    return 'Not enough stock for one or more items. Reduce quantities or remove items and try again.'
  if (/insufficient_stock_on_confirm/i.test(raw))
    return 'Not enough stock to confirm this order (inventory may have changed). Adjust stock or cancel the order.'
  if (/invalid_line|product_not_found/i.test(raw)) return 'A product in your cart is no longer available. Refresh and try again.'
  if (/invalid_delivery_type|invalid_payment_method/i.test(raw)) return 'Invalid delivery or payment selection.'
  if (/not_authenticated/i.test(raw)) return 'You must be signed in to confirm payments.'
  if (/JWT|session|not authenticated/i.test(m)) return 'Sign in again on Admin.'
  return raw || fallback
}

export async function fetchShopSettings() {
  if (!supabaseReady || !supabase) {
    return { deliveryFeeZar: 50, eftBankInstructions: '' }
  }
  const { data, error } = await supabase.from('shop_settings').select('*').eq('id', 1).maybeSingle()
  if (error) {
    return { deliveryFeeZar: 50, eftBankInstructions: '' }
  }
  const row = data || {}
  const fee = Number(row.delivery_fee_zar)
  return {
    deliveryFeeZar: Number.isFinite(fee) ? fee : 50,
    eftBankInstructions: String(row.eft_bank_instructions || '')
  }
}

/**
 * @param {object} params
 * @param {string} params.customerName
 * @param {string} params.customerEmail
 * @param {string} params.customerPhone
 * @param {'pickup'|'delivery'} params.deliveryType
 * @param {string} params.deliveryAddress
 * @param {'eft'|'cash_store'} params.paymentMethod
 * @param {{ product_id: string, quantity: number }[]} params.items
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

  const { data, error } = await supabase.rpc('create_order', {
    p_customer_name: params.customerName,
    p_customer_email: params.customerEmail,
    p_customer_phone: params.customerPhone || null,
    p_delivery_type: params.deliveryType,
    p_delivery_address: params.deliveryType === 'delivery' ? params.deliveryAddress : null,
    p_payment_method: params.paymentMethod,
    p_items: items
  })

  if (error) {
    const raw = String((error && (error.message || error.details || error.hint)) || '')
    const friendly = friendlyRpcError(error, 'Could not place order.')
    const e = new Error(friendly)
    if (/insufficient_stock/i.test(raw)) e.code = 'INSUFFICIENT_STOCK'
    throw e
  }
  if (!data) throw new Error('No order id returned.')
  return String(data)
}

const ORDER_DETAIL_SELECT = `
  *,
  order_items (
    id,
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
