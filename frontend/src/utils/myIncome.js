import { shortRef } from '@/utils/displayLabels'

/** Staff "My income" line statuses, as sent by GET …/admin/team/my-expected-income. */
export const INCOME_STATUS = Object.freeze({
  UPCOMING: 'UPCOMING',
  OWED: 'OWED',
  PAID_OUT: 'PAID_OUT'
})

const STATUS_META = {
  UPCOMING: { label: 'Upcoming', color: 'blue lighten-5', textColor: 'blue darken-3' },
  OWED: { label: 'Owed', color: 'orange lighten-5', textColor: 'deep-orange darken-3' },
  PAID_OUT: { label: 'Paid out', color: 'green lighten-5', textColor: 'green darken-3' }
}

/** A line's status; older servers sent only `employerPaid`. */
export function incomeStatus(line) {
  const s = String((line && line.status) || '').toUpperCase()
  if (STATUS_META[s]) return s
  return line && line.employerPaid ? INCOME_STATUS.PAID_OUT : INCOME_STATUS.OWED
}

export function incomeStatusMeta(line) {
  return STATUS_META[incomeStatus(line)]
}

const time = (line) => {
  const t = line && line.when ? new Date(line.when).getTime() : NaN
  return Number.isNaN(t) ? -Infinity : t
}

/** Upcoming first, then newest first (a copy; the input is not changed). */
export function sortIncomeLines(lines) {
  return [...(lines || [])].sort((a, b) => {
    const au = incomeStatus(a) === INCOME_STATUS.UPCOMING ? 0 : 1
    const bu = incomeStatus(b) === INCOME_STATUS.UPCOMING ? 0 : 1
    return au - bu || time(b) - time(a)
  })
}

/** "BK 7F2A" for a booking, "OR 993B" for an order (display only; never typed back in). */
export function incomeJobRef(line) {
  const prefix = String((line && line.jobType) || '').toUpperCase() === 'ORDER' ? 'OR' : 'BK'
  return `${prefix} ${shortRef(line && line.jobId).slice(0, 4)}`
}

/** "22 Nov 14:00" for a booking, "20 Nov" for an order (orders have no appointment time). */
export function incomeWhen(line) {
  const t = time(line)
  if (t === -Infinity) return 'Not set'
  const d = new Date(t)
  const isOrder = String(line.jobType || '').toUpperCase() === 'ORDER'
  const day = d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
  if (isOrder) return day
  return `${day} ${d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false })}`
}

/** "1 to 30 Nov" / "28 Oct to 3 Nov" / "All dates" for the page header. */
export function incomePeriodLabel(startDate, endDate) {
  if (!startDate || !endDate) return 'All dates'
  const s = new Date(`${startDate}T00:00:00`)
  const e = new Date(`${endDate}T00:00:00`)
  if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime())) return 'All dates'
  const month = (d) => d.toLocaleDateString(undefined, { month: 'short' })
  const sameYear = s.getFullYear() === e.getFullYear()
  if (sameYear && s.getMonth() === e.getMonth()) {
    return s.getDate() === e.getDate() ? `${s.getDate()} ${month(s)}` : `${s.getDate()} to ${e.getDate()} ${month(e)}`
  }
  return `${s.getDate()} ${month(s)} to ${e.getDate()} ${month(e)}`
}
