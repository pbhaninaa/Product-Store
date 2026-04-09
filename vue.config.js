const path = require('path')
const fs = require('fs')
const dotenv = require('dotenv')

/**
 * Load `VUE_APP_*` from `.env` next to this file (not from `process.cwd()`).
 * Also push them into the webpack DefinePlugin so the client bundle always gets
 * them even if the shell has empty `VUE_APP_*` vars or load order differs.
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
