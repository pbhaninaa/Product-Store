/** ISO weekday: Monday = 1 … Sunday = 7 (matches stored JSON). */
const ORDER = [1, 2, 3, 4, 5, 6, 7]

const LABELS = {
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday'
}

function padTime(t) {
  const s = String(t || '').trim()
  if (s.length >= 5) return s.slice(0, 5)
  return s || '—'
}

/**
 * @param {string|null|undefined} jsonStr
 * @returns {{ dayOfWeek: number, label: string, closed: boolean, line: string }[]}
 */
export function openingHoursRowsForPublicDisplay(jsonStr) {
  const raw = jsonStr == null ? '' : String(jsonStr).trim()
  if (!raw || raw === '[]' || raw === '{}') return []

  let arr
  try {
    arr = JSON.parse(raw)
  } catch {
    return []
  }
  if (!Array.isArray(arr) || !arr.length) return []

  const byDow = {}
  arr.forEach((node) => {
    if (!node || typeof node !== 'object') return
    const dow = Number(node.dayOfWeek)
    if (!Number.isInteger(dow) || dow < 1 || dow > 7) return
    if (node.closed === true) {
      byDow[dow] = { closed: true }
    } else if (node.start != null && node.end != null) {
      byDow[dow] = {
        closed: false,
        line: `${padTime(node.start)} – ${padTime(node.end)}`
      }
    }
  })

  return ORDER.map((dow) => {
    const hit = byDow[dow]
    const label = LABELS[dow] || `Day ${dow}`
    if (!hit) return { dayOfWeek: dow, label, closed: true, line: 'Closed' }
    if (hit.closed) return { dayOfWeek: dow, label, closed: true, line: 'Closed' }
    return { dayOfWeek: dow, label, closed: false, line: hit.line }
  })
}
