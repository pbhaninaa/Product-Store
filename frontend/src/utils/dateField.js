/** Helpers for {@code AppDateField}: values are ISO calendar dates ({@code YYYY-MM-DD}) or ''. */

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

/** Human label for an ISO date in the viewer's locale (e.g. "23 Nov 2026"); '' when empty or invalid. */
export function formatDateLabel(iso, locale = undefined) {
  const m = ISO_DATE.exec(String(iso || '').trim())
  if (!m) return ''
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  if (Number.isNaN(d.getTime()) || d.getDate() !== Number(m[3])) return ''
  return d.toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' })
}

/**
 * The field shows a formatted label, so validation rules are re-bound to run against the ISO value
 * they were written for, not the text in the box.
 */
export function bindRulesToValue(rules, getValue) {
  return (Array.isArray(rules) ? rules : []).map((rule) => () => rule(getValue()))
}
