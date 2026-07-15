#!/usr/bin/env node
/**
 * On Vercel there is no `.env` in git. Fail the build if the API base is missing
 * so the logs say exactly what to fix (Vue CLI bakes VUE_APP_* at build time).
 */
if (!process.env.VERCEL) {
  process.exit(0)
}

const raw = process.env.VUE_APP_API_BASE
const val = raw == null ? '' : String(raw).trim()

if (!val || val === '...') {
  console.error(
    '\n[verify-vercel-env] Build stopped: VUE_APP_API_BASE is missing or empty.'
  )
  console.error(
    '\nFix: Vercel → Settings → Environment Variables → add VUE_APP_API_BASE'
  )
  console.error(
    'Example: https://product-store-production-b8bf.up.railway.app'
  )
  console.error('(must include https://, no trailing slash, no /api)\n')
  process.exit(1)
}

if (!/^https?:\/\//i.test(val)) {
  console.error(
    '\n[verify-vercel-env] Build stopped: VUE_APP_API_BASE must be an absolute URL.'
  )
  console.error(`You set: ${val}`)
  console.error(
    'Wrong (relative — becomes productstore.../railway-host/...): product-store-xxx.up.railway.app'
  )
  console.error(
    'Right: https://product-store-xxx.up.railway.app\n'
  )
  process.exit(1)
}

if (/\/api\/?$/i.test(val)) {
  console.error(
    '\n[verify-vercel-env] Build stopped: VUE_APP_API_BASE must NOT end with /api.'
  )
  console.error(`You set: ${val}`)
  console.error('Use the Railway origin only, e.g. https://....up.railway.app\n')
  process.exit(1)
}

process.exit(0)
