/** Customer emails. Allows a normal domain or `@localhost` for local/SIT seeds. */
export function isValidCustomerEmail(raw) {
  return /^[^\s@]+@([^\s@]+\.[^\s@]+|localhost)$/i.test(String(raw || '').trim())
}
