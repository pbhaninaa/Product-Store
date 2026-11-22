import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'
import vuetifyColors from 'vuetify/lib/util/colors'

// Every button takes the provider directory's look ("Choose provider": pill, sentence case, bold, amber
// gradient on filled primary buttons), set once in App.vue. Guards the two ways pages drifted from it:
// buttons coloured with a theme colour that does not exist (`tertiary` rendered as a plain grey button)
// and per-page `border-radius: … !important` on button classes, which beats the shared pill shape.
const SRC = path.resolve(__dirname, '../../src')

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name)
    return e.isDirectory() ? walk(p) : /\.(vue|css)$/.test(e.name) ? [p] : []
  })
}

const files = walk(SRC).map((f) => ({ f: path.relative(SRC, f), s: fs.readFileSync(f, 'utf8') }))
const appVue = fs.readFileSync(path.join(SRC, 'App.vue'), 'utf8')
const vuetifyPlugin = fs.readFileSync(path.join(SRC, 'plugins/vuetify.js'), 'utf8')
const lightTheme = vuetifyPlugin.match(/light:\s*\{([^}]*)\}/)
const themeColors = new Set(lightTheme ? [...lightTheme[1].matchAll(/(\w+):\s*'#/g)].map((m) => m[1]) : [])
// Vuetify's named palette ("grey", "deep-orange lighten-5", …) is always available.
const paletteColors = new Set(Object.keys(vuetifyColors).map((k) => k.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)))
const isKnownColor = (c) => themeColors.has(c) || paletteColors.has(c.split(' ')[0])

describe('app-wide button style', () => {
  it('defines the provider-directory button look once in App.vue', () => {
    expect(appVue).toMatch(/\.app-root \.v-btn \{\s*text-transform: none;/)
    expect(appVue).toMatch(/\.app-root \.v-btn:not\(\.v-btn--round\):not\(\.v-btn--tile\) \{\s*border-radius: 28px;/)
    expect(appVue).toMatch(
      /\.app-root \.v-btn\.v-btn--has-bg\.primary:not\(\.v-btn--disabled\):not\(\.v-btn--round\) \{\s*background: linear-gradient\(135deg, #ea580c 0%, #c2410c 100%\) !important;/
    )
  })

  it('colours buttons only with theme colours that exist', () => {
    expect(themeColors.has('primary')).toBe(true)
    const offenders = []
    for (const { f, s } of files) {
      for (const tag of s.matchAll(/<v-btn\b[^>]*>/g)) {
        const color = tag[0].match(/\scolor="([a-z][a-z0-9 -]*)"/)
        if (color && !isKnownColor(color[1])) offenders.push(`${f}: color="${color[1]}"`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('does not force a different corner radius on button classes', () => {
    const offenders = []
    for (const { f, s } of files) {
      for (const rule of s.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
        const selector = rule[1].trim()
        if (/btn/.test(selector) && /border-radius:[^;]*!important/.test(rule[2])) offenders.push(`${f}: ${selector}`)
      }
    }
    expect(offenders).toEqual([])
  })
})
