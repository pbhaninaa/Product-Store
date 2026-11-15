/**
 * Shared form validation rules (Vuetify 2 `:rules` arrays) and helpers.
 *
 * Each factory returns a rule: (value) => true | 'message'. Rules other than `required` pass on empty
 * values, so optional fields only get checked once something is typed. Limits mirror the API:
 * - email: Emails.java (`x@y.z` or `x@localhost`)
 * - password: 8+ characters (AuthController / PasswordResetService)
 * - customer name 2+, phone 7+ characters (CheckoutService / SalonBookingService)
 * - store slug: 2–48 of a-z, 0-9, hyphen (TenantSlugUtil)
 * - delivery address 6+, payment reference 3+, promotion title <= 200, review <= 2000,
 *   ticket subject <= 200 / body <= 4000
 */

export const LIMITS = Object.freeze({
  PASSWORD_MIN: 8,
  NAME_MIN: 2,
  PHONE_MIN_DIGITS: 7,
  PHONE_MAX_DIGITS: 15,
  SLUG_MIN: 2,
  SLUG_MAX: 48,
  ADDRESS_MIN: 6,
  REFERENCE_MIN: 3,
  TITLE_MAX: 200,
  REVIEW_MAX: 2000,
  TICKET_BODY_MAX: 4000,
  MONEY_MAX: 1000000
})

const EMAIL_RE = /^[^@\s]+@([^@\s]+\.[^@\s]+|localhost)$/i
const SLUG_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/
const URL_RE = /^https?:\/\/[^\s/$.?#].[^\s]*$/i

export function isBlank(v) {
  if (v == null) return true
  if (Array.isArray(v)) return v.length === 0
  return String(v).trim() === ''
}

function text(v) {
  return v == null ? '' : String(v).trim()
}

function num(v) {
  if (typeof v === 'number') return v
  const s = text(v).replace(/\s/g, '').replace(',', '.')
  return s === '' ? NaN : Number(s)
}

export const rules = {
  required: (label = 'This field') => (v) => !isBlank(v) || `${label} is required`,

  minLength: (n, label = 'This field') => (v) =>
    isBlank(v) || text(v).length >= n || `${label} must be at least ${n} characters`,

  maxLength: (n, label = 'This field') => (v) =>
    isBlank(v) || text(v).length <= n || `${label} must be ${n} characters or fewer`,

  email: () => (v) => isBlank(v) || EMAIL_RE.test(text(v)) || 'Enter a valid email address',

  password: () => (v) =>
    isBlank(v) || String(v).length >= LIMITS.PASSWORD_MIN || `Password must be at least ${LIMITS.PASSWORD_MIN} characters`,

  /** Pass a function returning the other value, e.g. rules.matches(() => this.password, 'Passwords'). */
  matches: (other, message = 'Values do not match') => (v) => isBlank(v) || v === other() || message,

  phone: () => (v) => {
    if (isBlank(v)) return true
    const s = text(v)
    if (!/^\+?[\d\s()-]+$/.test(s)) return 'Use digits, spaces, +, - or brackets only'
    const digits = s.replace(/\D/g, '').length
    return (
      (digits >= LIMITS.PHONE_MIN_DIGITS && digits <= LIMITS.PHONE_MAX_DIGITS) ||
      `Enter a phone number with ${LIMITS.PHONE_MIN_DIGITS}–${LIMITS.PHONE_MAX_DIGITS} digits`
    )
  },

  slug: () => (v) => {
    if (isBlank(v)) return true
    const s = text(v)
    if (s.length < LIMITS.SLUG_MIN || s.length > LIMITS.SLUG_MAX) {
      return `Use ${LIMITS.SLUG_MIN}–${LIMITS.SLUG_MAX} characters`
    }
    return SLUG_RE.test(s) || 'Use lowercase letters, numbers and single hyphens only'
  },

  /** Rand amount: number, max 2 decimals, within [min, max]. */
  money: ({ min = 0, max = LIMITS.MONEY_MAX, allowZero = true } = {}) => (v) => {
    if (isBlank(v)) return true
    const n = num(v)
    if (!Number.isFinite(n)) return 'Enter an amount, e.g. 120.00'
    if (!/^-?\d+(?:[.,]\d{1,2})?$/.test(text(v).replace(/\s/g, ''))) return 'Use at most 2 decimal places'
    if (!allowZero && n === 0) return 'Amount must be more than R0'
    if (n < min) return `Amount must be at least R${min}`
    if (n > max) return `Amount must be at most R${max.toLocaleString('en-ZA')}`
    return true
  },

  integer: ({ min = 0, max = Number.MAX_SAFE_INTEGER, label = 'Value' } = {}) => (v) => {
    if (isBlank(v)) return true
    const n = num(v)
    if (!Number.isInteger(n)) return `${label} must be a whole number`
    if (n < min) return `${label} must be at least ${min}`
    if (n > max) return `${label} must be at most ${max}`
    return true
  },

  number: ({ min = -Infinity, max = Infinity, label = 'Value' } = {}) => (v) => {
    if (isBlank(v)) return true
    const n = num(v)
    if (!Number.isFinite(n)) return `${label} must be a number`
    if (n < min) return `${label} must be at least ${min}`
    if (n > max) return `${label} must be at most ${max}`
    return true
  },

  percent: () => rules.number({ min: 0, max: 100, label: 'Percentage' }),

  /** HH:mm, 24-hour. */
  time: () => (v) => isBlank(v) || TIME_RE.test(text(v)) || 'Use 24-hour time, e.g. 09:00',

  url: () => (v) => isBlank(v) || URL_RE.test(text(v)) || 'Enter a full link starting with https://',

  latitude: () => rules.number({ min: -90, max: 90, label: 'Latitude' }),
  longitude: () => rules.number({ min: -180, max: 180, label: 'Longitude' }),

  /** Digits only, length within [min, max] (bank account numbers, branch codes, PINs). */
  digits: ({ min = 1, max = 20, label = 'This field' } = {}) => (v) => {
    if (isBlank(v)) return true
    const s = text(v).replace(/\s/g, '')
    if (!/^\d+$/.test(s)) return `${label} must contain digits only`
    if (s.length < min || s.length > max) {
      return min === max ? `${label} must be ${min} digits` : `${label} must be ${min}–${max} digits`
    }
    return true
  },

  /** Date (YYYY-MM-DD) not before another date; `other` returns the other value. */
  notBefore: (other, message = 'End date cannot be before the start date') => (v) => {
    const o = other()
    if (isBlank(v) || isBlank(o)) return true
    return String(v) >= String(o) || message
  },

  /** File input: max size in MB and allowed MIME prefixes, e.g. ['image/']. */
  file: ({ maxMb = 5, types = [] } = {}) => (v) => {
    const files = Array.isArray(v) ? v : v ? [v] : []
    for (const f of files) {
      if (!f) continue
      if (f.size > maxMb * 1024 * 1024) return `File must be ${maxMb} MB or smaller`
      if (types.length && !types.some((t) => String(f.type || '').startsWith(t))) {
        return 'This file type is not allowed'
      }
    }
    return true
  }
}

/**
 * Validate a <v-form ref="..."> before submitting. Returns true when valid; otherwise shows the field
 * messages and scrolls the first invalid field into view.
 */
export function validateForm(form) {
  if (!form || typeof form.validate !== 'function') return true
  const ok = form.validate()
  if (!ok && typeof document !== 'undefined') {
    setTimeout(() => {
      const el = form.$el && form.$el.querySelector('.error--text, .v-input--has-state.error--text')
      if (el && typeof el.scrollIntoView === 'function') el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 0)
  }
  return ok
}
