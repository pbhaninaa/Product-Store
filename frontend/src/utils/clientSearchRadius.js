/** Default radius when a client starts a nearby search. */
export const DEFAULT_CLIENT_SEARCH_RADIUS_KM = 25

/** Allowed search radii (km), ascending. */
export const CLIENT_SEARCH_RADIUS_OPTIONS_KM = [10, 25, 50, 75, 100, 150, 200]

export const MAX_CLIENT_SEARCH_RADIUS_KM =
  CLIENT_SEARCH_RADIUS_OPTIONS_KM[CLIENT_SEARCH_RADIUS_OPTIONS_KM.length - 1]

export function normalizeClientSearchRadiusKm(value) {
  const n = Number(value)
  if (!Number.isFinite(n) || n <= 0) return DEFAULT_CLIENT_SEARCH_RADIUS_KM
  const allowed = CLIENT_SEARCH_RADIUS_OPTIONS_KM
  let best = allowed[0]
  let bestDiff = Math.abs(n - best)
  for (const option of allowed) {
    const diff = Math.abs(n - option)
    if (diff < bestDiff) {
      best = option
      bestDiff = diff
    }
  }
  return best
}

export function formatClientSearchRadiusLabel(km) {
  const n = normalizeClientSearchRadiusKm(km)
  return `Within ${n} km`
}

export function clientSearchRadiusDropdownItems() {
  return CLIENT_SEARCH_RADIUS_OPTIONS_KM.map((km) => ({
    text: formatClientSearchRadiusLabel(km),
    value: km
  }))
}

export function nextClientSearchRadiusKm(currentKm) {
  const current = normalizeClientSearchRadiusKm(currentKm)
  const idx = CLIENT_SEARCH_RADIUS_OPTIONS_KM.indexOf(current)
  if (idx < 0 || idx >= CLIENT_SEARCH_RADIUS_OPTIONS_KM.length - 1) return null
  return CLIENT_SEARCH_RADIUS_OPTIONS_KM[idx + 1]
}

export function canExtendClientSearchRadius(currentKm) {
  return nextClientSearchRadiusKm(currentKm) != null
}
