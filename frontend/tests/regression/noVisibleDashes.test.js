import { describe, it, expect } from 'vitest'
import fs from 'fs'
import path from 'path'
import {
  humanize,
  orderStatusLabel,
  formatDate,
  formatDateTime,
  auditDetail
} from '@/utils/displayLabels'
import { incomeJobRef, incomeWhen, incomePeriodLabel } from '@/utils/myIncome'
import { openingHoursRowsForPublicDisplay } from '@/utils/openingHoursDisplay'
import { rules } from '@/utils/validation'

/**
 * Regression guard for the CTO rule "the system never shows a dash".
 *
 * Scans what a person can read: template text and display attributes in every .vue file, and string
 * literals that read like prose (they contain a space) in every .vue script and src .js module.
 * Code is never scanned: comments, {{ expressions }} (only their string literals), class lists, route
 * names, event names, CSS and regexes are skipped.
 *
 * Heuristic limits (documented on purpose): an all-lowercase literal made only of word tokens
 * (e.g. 'deep-orange darken-3', 'mt-0 mb-4') is treated as a CSS class list and skipped.
 */

const SRC = path.resolve(__dirname, '../../src')

/** Every kind of visible dash: en/em/figure dashes and minus sign, " - " separators, word-word and 7-day. */
const UNICODE_DASH = /[‒–—―−]/
const SEPARATOR = /(^|\s)-(\s|$)/
const COMPOUND = /\b[A-Za-z]+-[A-Za-z]+\b|\b\d+-[A-Za-z]{2,}\b|\b\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}\b/

/**
 * Unavoidable or not-really-a-dash cases, each with its reason. Keys are "relative/path: exact text".
 * Keep this list small; prefer rewording.
 */
const ALLOWLIST = new Set([
  // Empty today: every hit found so far could be reworded. Add "path: text" entries with a reason.
])

/** Literal text that is code even though it contains a space. */
const CODE_LIKE = [
  /^[a-z0-9_\s.:#>[\]="'()-]+$/, // class lists, selectors, event names ('deep-orange darken-3', '.v-btn > span')
  /^\s*(width|content|max-width|min-width|calc)\b/i, // CSS / meta viewport values
  /^(application|multipart|text|image)\//i, // MIME types
  /^[a-z-]+\([^)]*\)$/i, // CSS functions
  /^[./#]/, // URL paths and CSS selectors ('/api/m/X/admin/team/...', '.error--text, .v-input')
  /^<[a-z]+\s+class="[^"]*"\s*\/?>$/ // a bare element built in JS for a map marker
]

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    if (fs.statSync(p).isDirectory()) walk(p, out)
    else if (/\.(vue|js)$/.test(name)) out.push(p)
  }
  return out
}

function stripJsComments(src) {
  // Keep string contents intact while dropping // and /* */ comments.
  let out = ''
  let i = 0
  let quote = null
  while (i < src.length) {
    const c = src[i]
    const n = src[i + 1]
    if (quote) {
      out += c
      if (c === '\\') {
        out += n || ''
        i += 2
        continue
      }
      if (c === quote) quote = null
      i++
      continue
    }
    if (c === '/' && n === '*') {
      const end = src.indexOf('*/', i + 2)
      i = end < 0 ? src.length : end + 2
      continue
    }
    if (c === '/' && n === '/' && src[i - 1] !== ':') {
      while (i < src.length && src[i] !== '\n') i++
      continue
    }
    if (c === "'" || c === '"' || c === '`') quote = c
    out += c
    i++
  }
  return out
}

/** String literals ('…', "…", `…` with ${} parts blanked) from a chunk of JS. */
function stringLiterals(js) {
  const found = []
  const re = /'((?:\\.|[^'\\\n])*)'|"((?:\\.|[^"\\\n])*)"|`((?:\\.|[^`\\])*)`/g
  let m
  while ((m = re.exec(js))) {
    const raw = m[1] ?? m[2] ?? m[3] ?? ''
    found.push(raw.replace(/\$\{[^}]*\}/g, 'X'))
  }
  return found
}

function isProse(text) {
  const t = text.trim()
  if (!/\s/.test(t)) return false
  if (/^\^|\\[dswb]|\[\^/.test(t)) return false // regex source
  return !CODE_LIKE.some((re) => re.test(t))
}

function dashProblem(text) {
  if (UNICODE_DASH.test(text)) return 'unicode dash'
  if (SEPARATOR.test(text)) return 'hyphen separator'
  if (COMPOUND.test(text)) return 'hyphenated word or range'
  if (/(^|\s)-R\s?\d/.test(text)) return 'negative money'
  return null
}

const DISPLAY_ATTRS = [
  'label',
  'hint',
  'placeholder',
  'title',
  'subtitle',
  'text',
  'aria-label',
  'alt',
  'no-data-text',
  'loading-text',
  'no-results-text',
  'messages',
  'suffix',
  'prefix',
  'confirm-text'
]

/** One opening or closing tag, including quoted attribute values that contain '>'. */
const TAG = /<\/?[A-Za-z][^\s/>]*(?:\s+[^\s=/>]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*\s*\/?>/g

function templateOf(vueSrc) {
  const start = vueSrc.indexOf('<template')
  const end = vueSrc.lastIndexOf('</template>')
  if (start < 0 || end < 0) return ''
  return vueSrc.slice(start, end)
}

function scriptOf(vueSrc) {
  const m = vueSrc.match(/<script[^>]*>([\s\S]*?)<\/script>/)
  return m ? m[1] : ''
}

function findProblems() {
  const problems = []
  const report = (file, text, why) => {
    const rel = path.relative(SRC, file).replace(/\\/g, '/')
    const key = `${rel}: ${text.trim()}`
    if (!ALLOWLIST.has(key)) problems.push(`${key}  [${why}]`)
  }

  for (const file of walk(SRC)) {
    const src = fs.readFileSync(file, 'utf8')
    const isVue = file.endsWith('.vue')
    const js = stripJsComments(isVue ? scriptOf(src) : src)

    for (const lit of stringLiterals(js)) {
      if (!isProse(lit)) continue
      const why = dashProblem(lit)
      if (why) report(file, lit, why)
    }

    if (!isVue) continue
    const tpl = templateOf(src).replace(/<!--[\s\S]*?-->/g, '')

    // Mustache expressions: only their string literals can be shown.
    for (const m of tpl.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
      for (const lit of stringLiterals(m[1])) {
        const why = lit.trim() && (UNICODE_DASH.test(lit) || /^\s*-\s*$/.test(lit) ? 'dash placeholder' : null)
        if (why) report(file, lit, why)
      }
    }

    // Static display attributes (bound :attrs are JS and their literals are checked above when prose).
    for (const attr of DISPLAY_ATTRS) {
      const re = new RegExp(`\\s${attr}="([^"]*)"`, 'g')
      for (const m of tpl.matchAll(re)) {
        const why = dashProblem(m[1]) || (/^\s*-\s*$/.test(m[1]) ? 'dash placeholder' : null)
        if (why) report(file, m[1], why)
      }
    }
    // Bound display attributes: string literals inside them.
    for (const attr of DISPLAY_ATTRS) {
      const re = new RegExp(`\\s:${attr}="([^"]*)"`, 'g')
      for (const m of tpl.matchAll(re)) {
        for (const lit of stringLiterals(m[1])) {
          const why = UNICODE_DASH.test(lit) || /^\s*-\s*$/.test(lit) ? 'dash placeholder' : isProse(lit) && dashProblem(lit)
          if (why) report(file, lit, why)
        }
      }
    }

    // Text between tags, with {{ }} removed.
    const text = tpl.replace(/\{\{[\s\S]*?\}\}/g, ' X ').replace(TAG, '\n')
    for (const line of text.split('\n')) {
      const t = line.trim()
      if (!t) continue
      const why = dashProblem(t)
      if (why) report(file, t, why)
    }
  }
  return problems
}

describe('no visible dashes', () => {
  it('the detector catches every kind of dash it is meant to', () => {
    for (const sample of [
      'New booking placed - 314D906B',
      'Product Store — items for sale',
      '09:00–17:00',
      'Opens 09:00 - 17:00',
      'Read-only access',
      'Your 7-day free trial',
      'Discount −R50',
      'Discount -R50'
    ]) {
      expect(dashProblem(sample), sample).not.toBeNull()
    }
    expect(dashProblem('New booking placed: 314D906B')).toBeNull()
    expect(isProse('deep-orange darken-3')).toBe(false)
    expect(isProse('Read-only access')).toBe(true)
  })

  it('templates, display attributes and prose strings in src contain no dash characters', () => {
    expect(findProblems()).toEqual([])
  })

  it('shared formatters never return a dash', () => {
    const outputs = [
      humanize(''),
      humanize('SOME_VALUE'),
      orderStatusLabel(null),
      formatDate(''),
      formatDate('not a date'),
      formatDate('2026-11-22'),
      formatDateTime(''),
      formatDateTime('2026-11-22T14:05:00Z'),
      auditDetail({}),
      auditDetail({ action: 'DANGER_RESET_DATABASE', detail: '' }),
      incomeJobRef({ jobId: '7f2a1b3c-0000-0000-0000-000000000000', jobType: 'SALON_BOOKING' }),
      incomeJobRef({ jobId: '993b0000-0000-0000-0000-000000000000', jobType: 'ORDER' }),
      incomeWhen({ jobType: 'SALON_BOOKING', when: null }),
      incomePeriodLabel('2026-11-01', '2026-11-30'),
      incomePeriodLabel('2026-10-28', '2026-11-03'),
      rules.phone()('abc'),
      rules.phone()('12'),
      rules.slug()('a'),
      rules.digits({ min: 6, max: 10, label: 'Account number' })('1'),
      rules.time()('25:00'),
      ...openingHoursRowsForPublicDisplay(
        JSON.stringify([{ dayOfWeek: 1, start: '09:00', end: '17:00' }, { dayOfWeek: 2, closed: true }])
      ).map((r) => r.line)
    ]
    for (const out of outputs) {
      expect(String(out)).not.toMatch(/[-‒–—―−]/)
    }
  })

  it('reads a plain LocalDate as that calendar day', () => {
    expect(formatDate('2026-11-22')).toBe(new Date(2026, 10, 22).toLocaleDateString(undefined, { dateStyle: 'medium' }))
  })
})
