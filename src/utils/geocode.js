/**
 * Reverse geocode (browser, no API key) — BigDataCloud client endpoint.
 * Returns a single line like "Johannesburg, Gauteng, South Africa".
 */
function formatPlaceFromReversePayload(d) {
  if (!d || typeof d !== 'object') return ''
  const place = String(d.locality || d.city || '').trim()
  const region = String(d.principalSubdivision || '').trim()
  const country = String(d.countryName || '').trim()
  const parts = []
  if (place) parts.push(place)
  if (region && region.toLowerCase() !== place.toLowerCase()) parts.push(region)
  if (country) parts.push(country)
  return parts.join(', ')
}

export async function fetchReversePlaceLabel(lat, lng) {
  const la = Number(lat)
  const ln = Number(lng)
  if (!Number.isFinite(la) || !Number.isFinite(ln)) return ''
  try {
    const url = `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${encodeURIComponent(
      la
    )}&longitude=${encodeURIComponent(ln)}&localityLanguage=en`
    const res = await fetch(url)
    if (!res.ok) return ''
    const data = await res.json()
    return formatPlaceFromReversePayload(data)
  } catch {
    return ''
  }
}
