#!/usr/bin/env node
/**
 * Vercel entrypoint: pick build:sit|uat|prod from branch and ensure
 * VUE_APP_API_BASE is an absolute Railway origin (Vue CLI bakes it at build time).
 */
const { spawnSync } = require('child_process')
const path = require('path')

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

const branch = String(process.env.VERCEL_GIT_COMMIT_REF || '').trim()
let script = 'build'
if (branch === 'SIT') script = 'build:sit'
else if (branch === 'UAT') script = 'build:uat'
else if (branch === 'PROD' || branch === 'main' || branch === 'master') {
  script = 'build:prod'
}

let api = normalizeApiBase(process.env.VUE_APP_API_BASE)
if (!api) {
  api = DEFAULT_API_BASE
  console.warn(
    `[vercel-build] VUE_APP_API_BASE unset for branch "${branch || '(unknown)'}"; using ${api}`
  )
  console.warn(
    '[vercel-build] Set VUE_APP_API_BASE in Vercel Settings > Environment Variables to override.'
  )
} else {
  console.log(`[vercel-build] VUE_APP_API_BASE=${api}`)
}

console.log(`[vercel-build] branch=${branch || '(unknown)'} -> npm run ${script}`)

const env = { ...process.env, VUE_APP_API_BASE: api }
const result = spawnSync('npm run ' + script, {
  stdio: 'inherit',
  env,
  cwd: path.join(__dirname, '..'),
  shell: true
})

if (result.error) {
  console.error(result.error)
  process.exit(1)
}
process.exit(result.status == null ? 1 : result.status)
