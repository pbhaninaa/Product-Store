/**
 * Calendar ranges for date filters. Dates are ISO calendar days (YYYY-MM-DD) on the browser's local
 * calendar, the same format AppDateField uses. Users are in South Africa, so local = SAST in practice.
 */

const pad = (n) => String(n).padStart(2, '0')

/** "2026-11-23" for a Date on the local calendar (not UTC, so late evening never shifts a day). */
export function toIsoDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * The last `n` calendar days including today: today and the n - 1 days before it.
 * lastNDaysRange(7) on 2026-11-23 gives { startDate: '2026-11-17', endDate: '2026-11-23' }.
 */
export function lastNDaysRange(n = 7, today = new Date()) {
  const days = Math.max(1, Math.floor(Number(n) || 1))
  // Build from the calendar fields so daylight saving changes cannot skip or repeat a day.
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1))
  return { startDate: toIsoDate(start), endDate: toIsoDate(today) }
}

/** The range every date filtered page opens with (FR-1): the last 7 days. */
export function defaultDateRange(today = new Date()) {
  return lastNDaysRange(7, today)
}

/**
 * Insights "Period" presets. Returns the range for a preset, `{ startDate: '', endDate: '' }` for
 * 'all' (no limit), and null for 'custom' or an unknown preset (keep the dates the user picked).
 * "Last N days" counts today, so 7d is exactly 7 calendar days.
 */
export function statsPresetRange(preset, today = new Date()) {
  if (preset === 'all') return { startDate: '', endDate: '' }
  if (preset === '7d') return lastNDaysRange(7, today)
  if (preset === '30d') return lastNDaysRange(30, today)
  if (preset === 'month') {
    return { startDate: toIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)), endDate: toIsoDate(today) }
  }
  return null
}
