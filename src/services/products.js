import { STORAGE_BUCKET, supabase, supabaseReady } from '@/supabase'

function supabaseErrorMessage(err, fallback) {
  const msg = (err && (err.message || err.error_description || err.details)) || ''
  const s = String(msg)
  if (/row-level security|RLS/i.test(s)) {
    return 'Permission denied. In Supabase: run SQL in supabase/schema.sql and storage-policies.sql, and sign in on /admin.'
  }
  if (/JWT|session|not authenticated|Invalid login/i.test(s)) {
    return 'Sign in again on /admin. Check Authentication → Users in Supabase.'
  }
  if (/Bucket not found|No such|storage/i.test(s)) {
    return 'Storage bucket missing. Create public bucket product-images and run supabase/storage-policies.sql.'
  }
  return s || fallback
}

function mapRow(row) {
  if (!row) return null
  const s = row.stock
  const stock = s == null || s === '' ? 0 : Math.max(0, parseInt(String(s), 10) || 0)
  return {
    id: row.id,
    name: row.name,
    price: row.price,
    imageUrl: row.image_url,
    imagePath: row.image_path,
    stock
  }
}

async function fetchProducts() {
  if (!supabase) return []
  const { data, error } = await supabase
    .from('products')
    .select('*')
    .order('created_at', { ascending: false })

  if (error) throw new Error(supabaseErrorMessage(error, 'Could not load products.'))
  return (data || []).map(mapRow)
}

/**
 * @param {string[]} ids Product UUIDs
 */
export async function fetchProductsByIds(ids) {
  if (!supabaseReady || !supabase) return []
  const uniq = [...new Set((ids || []).filter(Boolean))]
  if (!uniq.length) return []
  const { data, error } = await supabase.from('products').select('*').in('id', uniq)
  if (error) throw new Error(supabaseErrorMessage(error, 'Could not load products.'))
  return (data || []).map(mapRow)
}

export function subscribeToProducts(callback) {
  if (!supabaseReady || !supabase) {
    callback([])
    return () => {}
  }

  let cancelled = false

  const load = async () => {
    try {
      const products = await fetchProducts()
      if (!cancelled) callback(products)
    } catch (e) {
      if (!cancelled) {
        // eslint-disable-next-line no-console
        console.warn('[products]', e && e.message ? e.message : e)
        callback([])
      }
    }
  }

  load()

  const channel = supabase
    .channel('products_changes')
    .on(
      'postgres_changes',
      { event: '*', schema: 'public', table: 'products' },
      () => {
        load()
      }
    )
    .subscribe()

  return () => {
    cancelled = true
    supabase.removeChannel(channel)
  }
}

export async function updateProductStock({ id, stock }) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }
  if (!id) throw new Error('Missing product id.')
  const n = typeof stock === 'string' ? parseInt(stock, 10) : stock
  const qty = Number.isFinite(n) ? Math.floor(n) : 0
  if (qty < 0) throw new Error('Stock cannot be negative.')

  const { error } = await supabase.from('products').update({ stock: qty }).eq('id', id)
  if (error) throw new Error(supabaseErrorMessage(error, 'Could not update stock.'))
}

export async function createProduct({ name, price, file, stock: initialStock }) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }

  const safeName = String(name ?? '').trim()
  if (!safeName) throw new Error('Please enter a product name.')
  if (!file) throw new Error('Please choose an image.')

  const priceNumber = typeof price === 'string' ? Number(price) : price
  const priceValue = Number.isFinite(priceNumber)
    ? String(priceNumber)
    : String(price ?? '').trim()

  const fileExt = (file.name || '').split('.').pop() || 'jpg'
  const imagePath = `products/${Date.now()}-${Math.random().toString(16).slice(2)}.${fileExt}`

  const ext = String(fileExt).toLowerCase()
  const contentType =
    (file.type && String(file.type).trim()) ||
    (ext === 'jpg' || ext === 'jpeg'
      ? 'image/jpeg'
      : ext === 'png'
        ? 'image/png'
        : ext === 'gif'
          ? 'image/gif'
          : ext === 'webp'
            ? 'image/webp'
            : 'application/octet-stream')

  const { error: upErr } = await supabase.storage.from(STORAGE_BUCKET).upload(imagePath, file, {
    cacheControl: '31536000',
    upsert: false,
    contentType
  })

  if (upErr) throw new Error(supabaseErrorMessage(upErr, 'Image upload failed.'))

  const { data: urlData } = supabase.storage.from(STORAGE_BUCKET).getPublicUrl(imagePath)
  const imageUrl = urlData?.publicUrl
  if (!imageUrl) throw new Error('Could not get public URL for uploaded image.')

  const stockRaw = typeof initialStock === 'string' ? parseInt(initialStock, 10) : initialStock
  const stock = Number.isFinite(stockRaw) ? Math.max(0, Math.floor(stockRaw)) : 0

  const { error: insErr } = await supabase.from('products').insert({
    name: safeName,
    price: priceValue,
    image_url: imageUrl,
    image_path: imagePath,
    stock
  })

  if (insErr) {
    try {
      await supabase.storage.from(STORAGE_BUCKET).remove([imagePath])
    } catch (e) {
      // ignore
    }
    throw new Error(supabaseErrorMessage(insErr, 'Could not save product.'))
  }
}

export async function deleteProduct({ id, imagePath }) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }

  if (!id) throw new Error('Missing product id.')

  const { error: delRowErr } = await supabase.from('products').delete().eq('id', id)
  if (delRowErr) throw new Error(supabaseErrorMessage(delRowErr, 'Could not delete product.'))

  if (imagePath) {
    try {
      await supabase.storage.from(STORAGE_BUCKET).remove([imagePath])
    } catch (e) {
      // If the file is already gone, keep the UX smooth.
    }
  }
}
