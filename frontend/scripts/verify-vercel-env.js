#!/usr/bin/env node
/**
 * On Vercel there is no `.env` in git. Fail the build if the API base is missing
 * so the logs say exactly what to fix (Vue CLI bakes VUE_APP_* at build time).
 */
if (!process.env.VERCEL) {
  process.exit(0)
}

const keys = ['VUE_APP_API_BASE']
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
  '\nVUE_APP_API_BASE = Railway backend origin only (no trailing slash, no /api).'
)
console.error(
  'Check the boxes for Production and Preview, Save, then Redeploy.\n'
)
process.exit(1)
