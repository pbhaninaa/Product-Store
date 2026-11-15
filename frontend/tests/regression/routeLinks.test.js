import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'

// Guards against links the router cannot resolve. PROD shipped a header Cart button and a post-signup
// redirect pointing at `/<slug>/…` while every storefront/admin route lives under `/m/:merchantSlug`,
// and dashboard tiles naming routes that had been removed — both render a blank page.
const SRC = path.resolve(__dirname, '../../src')

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name)
    return e.isDirectory() ? walk(p) : /\.(vue|js)$/.test(e.name) ? [p] : []
  })
}

const files = walk(SRC).map((f) => ({ f: path.relative(SRC, f), s: fs.readFileSync(f, 'utf8') }))
const router = fs.readFileSync(path.join(SRC, 'router/index.js'), 'utf8')
const routeNames = new Set([...router.matchAll(/name: '([^']+)'/g)].map((m) => m[1]))

describe('router links', () => {
  it('builds merchant storefront/admin links under /m/:merchantSlug', () => {
    const offenders = []
    for (const { f, s } of files) {
      for (const m of s.matchAll(/[`'"]\/\$\{(?:encodeURIComponent\()?[^}]*[sS]lug[^`'"]*/g)) {
        offenders.push(`${f}: ${m[0]}`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('only links to route names the router defines', () => {
    const missing = []
    for (const { f, s } of files) {
      if (f.startsWith('router')) continue
      for (const m of s.matchAll(/name:\s*'((?:merchant|client|support)-[a-z-]+)'/g)) {
        if (!routeNames.has(m[1])) missing.push(`${f}: ${m[1]}`)
      }
    }
    expect(missing).toEqual([])
  })
})
