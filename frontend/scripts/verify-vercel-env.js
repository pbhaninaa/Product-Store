#!/usr/bin/env node
/**
 * On Vercel there is no `.env` in git. If Supabase vars are missing at build time,
 * webpack still produces a bundle that shows "Supabase is not configured".
 * Fail the build here so the logs say exactly what to fix.
 */
if (!process.env.VERCEL) {
  process.exit(0)
}

const keys = ['VUE_APP_SUPABASE_URL', 'VUE_APP_SUPABASE_ANON_KEY']
const bad = keys.filter((k) => {
  const v = process.env[k]
  return v == null || String(v).trim() === '' || String(v).trim() === '...'
})

if (bad.length === 0) {
  process.exit(0)
}

console.error(
  '\n[verify-vercel-env] Build stopped: these are missing or empty for this Vercel environment:',
  bad.join(', ')
)
console.error(
  '\nFix: Vercel dashboard → your project → Settings → Environment Variables.'
)
console.error('Add EXACT names (Vue CLI needs VUE_APP_*, not VITE_*):')
for (const k of keys) console.error('  -', k)
console.error(
  '\nCheck the boxes for the environments you deploy to (Production and Preview), Save, then Redeploy.\n'
)
process.exit(1)
