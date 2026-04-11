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
  const categoryRaw = row.category
  const category =
    categoryRaw != null && String(categoryRaw).trim() !== ''
      ? String(categoryRaw).trim()
      : 'Uncategorized'
  return {
    id: row.id,
    name: row.name,
    category,
    price: row.price,
    imageUrl: row.image_url,
    imagePath: row.image_path,
    stock
  }
}

/** Sort by category A–Z, then product name A–Z (locale-aware). */
export function compareProductsByCategoryThenName(a, b) {
  const ca = String(a.category || 'Uncategorized').localeCompare(
    String(b.category || 'Uncategorized'),
    undefined,
    { sensitivity: 'base' }
  )
  if (ca !== 0) return ca
  return String(a.name || '').localeCompare(String(b.name || ''), undefined, { sensitivity: 'base' })
}

async function fetchProducts() {
  if (!supabase) return []
  const { data, error } = await supabase
    .from('products')
    .select('*')
    .is('archived_at', null)
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
  const { data, error } = await supabase
    .from('products')
    .select('*')
    .in('id', uniq)
    .is('archived_at', null)
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

export async function updateProductInventory({ id, stock, category }) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }
  if (!id) throw new Error('Missing product id.')
  const n = typeof stock === 'string' ? parseInt(stock, 10) : stock
  const qty = Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 0

  const safeCategory = String(category ?? '').trim()
  if (!safeCategory) throw new Error('Please enter a category.')

  const { error } = await supabase
    .from('products')
    .update({ stock: qty, category: safeCategory })
    .eq('id', id)
  if (error) throw new Error(supabaseErrorMessage(error, 'Could not save product changes.'))
}

function buildImageContentType(fileName, fileType) {
  const t = String(fileType || '').trim()
  if (t && t.startsWith('image/')) return t
  const ext = String(fileName || '').split('.').pop().toLowerCase()
  if (ext === 'jpg' || ext === 'jpeg') return 'image/jpeg'
  if (ext === 'png') return 'image/png'
  if (ext === 'gif') return 'image/gif'
  if (ext === 'webp') return 'image/webp'
  return 'application/octet-stream'
}

/**
 * Staff: update any product field. Pass `imageFile` to replace the listing image (previous storage object removed when given).
 * @param {{ id: string, name: string, category: string, price: string|number, stock: number|string, imageFile?: File|null, previousImagePath?: string|null }} params
 */
export async function updateProduct(params) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }
  const p = params || {}
  const id = p.id
  if (!id) throw new Error('Missing product id.')

  const safeName = String(p.name != null ? p.name : '').trim()
  if (safeName.length < 1) throw new Error('Please enter a product name.')

  const safeCategory = String(p.category != null ? p.category : '').trim()
  if (!safeCategory) throw new Error('Please enter a category.')

  const priceRaw = p.price
  const priceNumber =
    typeof priceRaw === 'string' ? parseFloat(String(priceRaw).replace(',', '.')) : Number(priceRaw)
  if (!Number.isFinite(priceNumber) || priceNumber < 0 || priceNumber > 999999999.99) {
    throw new Error('Please enter a valid price.')
  }
  const priceValue = String(priceNumber)

  const stockRaw = p.stock
  const sn = typeof stockRaw === 'string' ? parseInt(stockRaw, 10) : stockRaw
  const stockVal = Number.isFinite(sn) ? Math.max(0, Math.floor(sn)) : 0

  const imageFile = p.imageFile
  const previousImagePath = p.previousImagePath != null ? String(p.previousImagePath).trim() : ''

  let newImagePath = null
  let newImageUrl = null

  if (imageFile) {
    if (!(imageFile instanceof Blob)) throw new Error('Please choose a valid image file.')
    const fileExt = (imageFile.name || '').split('.').pop() || 'jpg'
    const imagePath = `products/${Date.now()}-${Math.random().toString(16).slice(2)}.${fileExt}`
    const contentType = buildImageContentType(imageFile.name, imageFile.type)

    const { error: upErr } = await supabase.storage.from(STORAGE_BUCKET).upload(imagePath, imageFile, {
      cacheControl: '31536000',
      upsert: false,
      contentType
    })
    if (upErr) throw new Error(supabaseErrorMessage(upErr, 'Image upload failed.'))

    const { data: urlData } = supabase.storage.from(STORAGE_BUCKET).getPublicUrl(imagePath)
    const imageUrl = urlData?.publicUrl
    if (!imageUrl) {
      try {
        await supabase.storage.from(STORAGE_BUCKET).remove([imagePath])
      } catch {
        // ignore
      }
      throw new Error('Could not get public URL for uploaded image.')
    }
    newImagePath = imagePath
    newImageUrl = imageUrl
  }

  const patch = {
    name: safeName,
    category: safeCategory,
    price: priceValue,
    stock: stockVal
  }
  if (newImagePath && newImageUrl) {
    patch.image_path = newImagePath
    patch.image_url = newImageUrl
  }

  const { error } = await supabase.from('products').update(patch).eq('id', id)
  if (error) {
    if (newImagePath) {
      try {
        await supabase.storage.from(STORAGE_BUCKET).remove([newImagePath])
      } catch {
        // ignore
      }
    }
    throw new Error(supabaseErrorMessage(error, 'Could not save product.'))
  }

  if (newImagePath && previousImagePath && previousImagePath !== newImagePath) {
    try {
      await supabase.storage.from(STORAGE_BUCKET).remove([previousImagePath])
    } catch {
      // ignore missing file
    }
  }
}

export async function createProduct({ name, category, price, file, stock: initialStock }) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }

  const safeName = String(name ?? '').trim()
  if (!safeName) throw new Error('Please enter a product name.')
  const safeCategory = String(category ?? '').trim()
  if (!safeCategory) throw new Error('Please enter a category (e.g. Clothing, Electronics).')
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
    category: safeCategory,
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

/**
 * Soft-delete: archives the product so past order_items keep a valid FK.
 * Row stays; `archived_at` hides it from the shop. Storage image is kept for order history.
 */
export async function deleteProduct({ id }) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }

  if (!id) throw new Error('Missing product id.')

  const { error } = await supabase
    .from('products')
    .update({ archived_at: new Date().toISOString() })
    .eq('id', id)
    .is('archived_at', null)
  if (error) throw new Error(supabaseErrorMessage(error, 'Could not remove product from the shop.'))
}
