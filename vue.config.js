const path = require('path')
const fs = require('fs')
const dotenv = require('dotenv')

/**
 * Values inlined into the client bundle (DefinePlugin).
 * - Local: from `.env` next to this file (wins over empty shell vars).
 * - Vercel / CI: `.env` is not in git; merge non-empty `VUE_APP_*` from `process.env`
 *   (set in the host’s Environment Variables UI).
 */
const envPath = path.resolve(__dirname, '.env')
/** @type {Record<string, string>} */
const vueAppEnv = {}

if (fs.existsSync(envPath)) {
  let buf = fs.readFileSync(envPath)
  if (buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf) {
    buf = buf.subarray(3)
  }
  const parsed = dotenv.parse(buf)
  for (const key of Object.keys(parsed)) {
    if (key.startsWith('VUE_APP_')) {
      vueAppEnv[key] = parsed[key]
      process.env[key] = parsed[key]
    }
  }
}

for (const key of Object.keys(process.env)) {
  if (!key.startsWith('VUE_APP_')) continue
  const raw = process.env[key]
  if (raw === undefined || raw === null) continue
  const trimmed = String(raw).trim()
  if (!trimmed) continue
  if (vueAppEnv[key] === undefined) {
    vueAppEnv[key] = trimmed
  }
}

const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  chainWebpack: (config) => {
    config.plugin('define').tap((args) => {
      const defs = args[0]
      const proc = defs['process.env']
      if (proc && typeof proc === 'object') {
        for (const key of Object.keys(vueAppEnv)) {
          proc[key] = JSON.stringify(vueAppEnv[key])
        }
      }
      return args
    })
  }
})
