/**
 * Compare cart lines to freshly loaded products (authoritative stock).
 * @param {{ product: Record<string, unknown>, quantity: number }[]} cartLines
 * @param {{ id: string, name?: string, price?: unknown, stock?: number, imageUrl?: string, [k: string]: unknown }[]} liveProducts
 */
export function reconcileCartLinesAgainstStock(cartLines, liveProducts) {
  const byId = new Map(liveProducts.map((p) => [p.id, p]))

  /** @type {{ name: string, wanted: number, have: number }[]} */
  const unavailable = []
  /** @type {{ name: string, wanted: number, have: number, willOrder: number }[]} */
  const reduced = []
  /** @type {{ product: Record<string, unknown>, quantity: number }[]} */
  const nextLines = []

  for (const line of cartLines) {
    const pid = line.product.id
    const live = byId.get(pid)
    const have = live ? Math.max(0, parseInt(String(live.stock ?? 0), 10) || 0) : 0
    const wanted = line.quantity
    const name = String((live && live.name) || line.product.name || 'Item')

    if (have <= 0) {
      unavailable.push({ name, wanted, have: 0 })
      continue
    }

    const q = Math.min(wanted, have)
    if (q < wanted) {
      reduced.push({ name, wanted, have, willOrder: q })
    }

    const product = live
      ? { ...line.product, ...live, id: live.id, stock: have }
      : { ...line.product, stock: have }
    nextLines.push({ product, quantity: q })
  }

  return { unavailable, reduced, nextLines }
}
