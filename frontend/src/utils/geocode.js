/**
 * Reverse geocoding in the browser (no API key).
 *
 * Full street address first from OpenStreetMap Nominatim, e.g.
 * "12 Jan Smuts Avenue, Rosebank, Johannesburg, 2196, South Africa";
 * falls back to BigDataCloud's town-level label ("Johannesburg, Gauteng, South Africa").
 */

function joinUnique(parts) {
  const out = []
  for (const p of parts) {
    const v = String(p || '').trim()
    if (v && !out.some((x) => x.toLowerCase() === v.toLowerCase())) out.push(v)
  }
  return out.join(', ')
}

/** Nominatim `address` object → "12 Main Rd, Suburb, City, 2196, Country". */
export function formatFullAddress(a) {
  if (!a || typeof a !== 'object') return ''
  const street = joinUnique([a.house_number, a.road || a.pedestrian || a.footway || a.street]).replace(', ', ' ')
  const suburb = a.suburb || a.neighbourhood || a.quarter || a.city_district || ''
  const city = a.city || a.town || a.village || a.municipality || a.county || ''
  // Street first; nearby place/shop names are left out (they read as if you were inside them).
  return joinUnique([street, suburb, city, a.state && !city ? a.state : '', a.postcode, a.country])
}

function formatPlaceFromReversePayload(d) {
  if (!d || typeof d !== 'object') return ''
  return joinUnique([d.locality || d.city, d.principalSubdivision, d.countryName])
}

async function fetchJson(url) {
  const res = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!res.ok) throw new Error(`geocode ${res.status}`)
  return res.json()
}

/**
 * Full address for a point (street, suburb, city, postal code, country). Returns '' when nothing resolves.
 */
export async function fetchReversePlaceLabel(lat, lng) {
  const la = Number(lat)
  const ln = Number(lng)
  if (!Number.isFinite(la) || !Number.isFinite(ln)) return ''
  try {
    const d = await fetchJson(
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18&accept-language=en&lat=${encodeURIComponent(
        la
      )}&lon=${encodeURIComponent(ln)}`
    )
    const full = formatFullAddress(d && d.address)
    if (full) return full
  } catch {
    // fall through to the town-level service
  }
  try {
    const d = await fetchJson(
      `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${encodeURIComponent(
        la
      )}&longitude=${encodeURIComponent(ln)}&localityLanguage=en`
    )
    return formatPlaceFromReversePayload(d)
  } catch {
    return ''
  }
}
