import { supabase, supabaseReady } from '@/supabase'

function friendlyRpcError(err, fallback) {
  const raw = String((err && (err.message || err.details || err.hint)) || '')
  const m = raw.toLowerCase()
  if (/invalid_name|invalid name/i.test(raw)) return 'Please enter your full name.'
  if (/invalid_email|invalid email/i.test(raw)) return 'Please enter a valid email address.'
  if (/delivery_address_required/i.test(raw)) return 'Please enter a full delivery address.'
  if (/empty_cart|too_many_lines/i.test(raw)) return 'Your cart is empty or has too many lines.'
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

  if (error) throw new Error(friendlyRpcError(error, 'Could not place order.'))
  if (!data) throw new Error('No order id returned.')
  return String(data)
}

export async function fetchOrdersForAdmin() {
  if (!supabaseReady || !supabase) return []
  const { data, error } = await supabase
    .from('orders')
    .select(
      `
      *,
      order_items (
        id,
        quantity,
        unit_price_zar,
        line_total_zar,
        products ( name )
      )
    `
    )
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

export async function confirmEftPayment(orderId) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured.')
  }
  const { data, error } = await supabase.rpc('confirm_eft_payment', {
    p_order_id: orderId
  })
  if (error) throw new Error(friendlyRpcError(error, 'Could not confirm payment.'))
  if (data !== true) throw new Error('Order was not updated (already confirmed or not EFT).')
  return true
}
