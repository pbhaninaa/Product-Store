#!/usr/bin/env node
/**
 * On Vercel, ensure VUE_APP_API_BASE is usable. Prefer the project env var;
 * otherwise fall back to the known Railway origin so builds do not fail empty.
 */
if (!process.env.VERCEL) {
  process.exit(0)
}

const DEFAULT_API_BASE = 'https://product-store-production-b8bf.up.railway.app'

function normalizeApiBase(raw) {
  let b = String(raw == null ? '' : raw).trim().replace(/\/+$/, '')
  if (!b || b === '...') return ''
  if (!/^https?:\/\//i.test(b)) {
    b = 'https://' + b.replace(/^\/+/, '')
  }
  b = b.replace(/\/+$/, '')
  if (/\/api$/i.test(b)) {
    b = b.replace(/\/api$/i, '')
  }
  return b
}

let api = normalizeApiBase(process.env.VUE_APP_API_BASE)
if (!api) {
  api = DEFAULT_API_BASE
  console.warn(
    `\n[verify-vercel-env] VUE_APP_API_BASE missing; using default ${api}`
  )
  console.warn(
    '[verify-vercel-env] Override in Vercel → Settings → Environment Variables.\n'
  )
  process.env.VUE_APP_API_BASE = api
} else {
  process.env.VUE_APP_API_BASE = api
  console.log(`[verify-vercel-env] VUE_APP_API_BASE=${api}`)
}

process.exit(0)
