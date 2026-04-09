import Vue from 'vue'

const state = Vue.observable({
  /** @type {{ product: Record<string, unknown>, quantity: number }[]} */
  lines: []
})

function clampQty(n) {
  const x = parseInt(String(n), 10)
  if (!Number.isFinite(x)) return 1
  return Math.max(1, Math.min(100, x))
}

export function getCartState() {
  return state
}

export function addToCart(product, qty = 1) {
  if (!product || !product.id) return
  const q = clampQty(qty)
  const line = state.lines.find((l) => l.product.id === product.id)
  if (line) {
    line.quantity = clampQty(line.quantity + q)
  } else {
    state.lines.push({
      product: { ...product },
      quantity: q
    })
  }
}

export function setLineQuantity(productId, qty) {
  const line = state.lines.find((l) => l.product.id === productId)
  if (!line) return
  line.quantity = clampQty(qty)
}

export function removeFromCart(productId) {
  const i = state.lines.findIndex((l) => l.product.id === productId)
  if (i !== -1) state.lines.splice(i, 1)
}

export function clearCart() {
  state.lines.splice(0, state.lines.length)
}

export function cartItemCount() {
  return state.lines.reduce((s, l) => s + l.quantity, 0)
}

/** Display-only subtotal; server recomputes on submit. */
export function cartSubtotalNumber() {
  return state.lines.reduce((sum, line) => {
    const n = typeof line.product.price === 'string' ? Number(line.product.price) : line.product.price
    const unit = Number.isFinite(n) ? n : 0
    return sum + unit * line.quantity
  }, 0)
}
