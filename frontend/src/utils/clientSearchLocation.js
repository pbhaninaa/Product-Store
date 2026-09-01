const KEY = 'ps_client_search_location'

export function loadSavedSearchLocation() {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return { lat: null, lng: null, label: '' }
    const o = JSON.parse(raw)
    const lat = Number(o && o.lat)
    const lng = Number(o && o.lng)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return { lat: null, lng: null, label: '' }
    return { lat, lng, label: String((o && o.label) || '') }
  } catch {
    return { lat: null, lng: null, label: '' }
  }
}

export function saveSearchLocation(lat, lng, label) {
  const la = Number(lat)
  const ln = Number(lng)
  if (!Number.isFinite(la) || !Number.isFinite(ln)) return
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ lat: la, lng: ln, label: String(label || '') }))
  } catch {
    // ignore
  }
}
