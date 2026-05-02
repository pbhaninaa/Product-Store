/**
 * Great-circle distance (km) between two WGS84 points — matches Postgres `public.haversine_km`.
 * @param {number} lat1
 * @param {number} lon1
 * @param {number} lat2
 * @param {number} lon2
 * @returns {number|null}
 */
export function haversineKm(lat1, lon1, lat2, lon2) {
  const a1 = Number(lat1)
  const o1 = Number(lon1)
  const a2 = Number(lat2)
  const o2 = Number(lon2)
  if (![a1, o1, a2, o2].every((n) => Number.isFinite(n))) return null
  const r = 6371
  const dLat = ((a2 - a1) * Math.PI) / 180
  const dLon = ((o2 - o1) * Math.PI) / 180
  const x =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((a1 * Math.PI) / 180) * Math.cos((a2 * Math.PI) / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x))
  const km = r * c
  return Math.round(km * 1000) / 1000
}
