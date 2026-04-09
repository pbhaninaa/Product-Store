import { createClient } from '@supabase/supabase-js'

const url = process.env.VUE_APP_SUPABASE_URL
const anonKey = process.env.VUE_APP_SUPABASE_ANON_KEY

function isPlaceholder(value) {
  if (value === undefined || value === null) return true
  const s = String(value).trim()
  if (!s) return true
  if (s === '...') return true
  if (/^\.{2,}$/.test(s)) return true
  return false
}

const invalid = []
if (isPlaceholder(url)) invalid.push('VUE_APP_SUPABASE_URL')
if (isPlaceholder(anonKey)) invalid.push('VUE_APP_SUPABASE_ANON_KEY')

/** @type {import('@supabase/supabase-js').SupabaseClient | null} */
export const supabase =
  invalid.length === 0 ? createClient(String(url).trim(), String(anonKey).trim()) : null

export const supabaseReady = Boolean(supabase)

export const supabaseSetupMessage = supabaseReady
  ? null
  : `Supabase is not configured. In the project folder, copy .env.example to .env and set VUE_APP_SUPABASE_URL and VUE_APP_SUPABASE_ANON_KEY from Supabase → Project Settings → API. Missing or placeholder: ${invalid.join(
      ', '
    )}. Then stop and run npm run serve again.`

/** Storage bucket for product images (create in Supabase → Storage). */
export const STORAGE_BUCKET = 'product-images'
