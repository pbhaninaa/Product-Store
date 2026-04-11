<template>
  <div class="map-location-picker">
    <div ref="mapEl" class="map-location-picker__map" :style="{ height: `${height}px` }" />
    <div v-if="showGeolocate" class="map-location-picker__toolbar mt-2">
      <v-btn
        small
        outlined
        color="primary"
        class="text-none"
        :loading="geoLoading"
        :disabled="geoLoading"
        @click="useMyLocation"
      >
        <v-icon left small>my_location</v-icon>
        Use my location
      </v-btn>
    </div>
    <p v-if="hint" class="text-caption text--secondary mb-0 mt-2">{{ hint }}</p>
  </div>
</template>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const DEFAULT_CENTER = [-26.2041, 28.0473]

export default {
  name: 'MapLocationPicker',
  props: {
    /** @type {{ lat: number|null, lng: number|null }} */
    value: {
      type: Object,
      default: () => ({ lat: null, lng: null })
    },
    centerLat: { type: Number, default: null },
    centerLng: { type: Number, default: null },
    zoom: { type: Number, default: 12 },
    height: { type: Number, default: 280 },
    hint: { type: String, default: '' },
    showGeolocate: { type: Boolean, default: true }
  },
  data() {
    return {
      geoLoading: false,
      map: null,
      marker: null
    }
  },
  watch: {
    value: {
      deep: true,
      handler(v) {
        this.syncMarkerFromValue(v)
      }
    },
    centerLat() {
      this.recenter()
    },
    centerLng() {
      this.recenter()
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.initMap()
      setTimeout(() => this.invalidate(), 400)
    })
    window.addEventListener('resize', this.invalidate)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.invalidate)
    if (this.map) {
      this.map.remove()
      this.map = null
    }
  },
  methods: {
    invalidate() {
      if (this.map) this.map.invalidateSize()
    },
    pinIcon() {
      return L.divIcon({
        className: 'map-location-picker__pin',
        html: '<span class="map-location-picker__pin-dot" />',
        iconSize: [26, 26],
        iconAnchor: [13, 26]
      })
    },
    centerPair() {
      const clat = Number.isFinite(this.centerLat) ? this.centerLat : null
      const clng = Number.isFinite(this.centerLng) ? this.centerLng : null
      if (clat != null && clng != null) return [clat, clng]
      const v = this.value || {}
      if (Number.isFinite(v.lat) && Number.isFinite(v.lng)) return [v.lat, v.lng]
      return DEFAULT_CENTER
    },
    initMap() {
      const el = this.$refs.mapEl
      if (!el) return
      const [lat, lng] = this.centerPair()
      this.map = L.map(el, { zoomControl: true }).setView([lat, lng], this.zoom)
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap'
      }).addTo(this.map)

      this.map.on('click', (e) => {
        const next = { lat: e.latlng.lat, lng: e.latlng.lng }
        this.placeMarker(next.lat, next.lng)
        this.$emit('input', next)
      })

      this.syncMarkerFromValue(this.value)
    },
    placeMarker(lat, lng) {
      if (!this.map) return
      if (this.marker) {
        this.marker.setLatLng([lat, lng])
      } else {
        this.marker = L.marker([lat, lng], { icon: this.pinIcon(), draggable: true }).addTo(this.map)
        this.marker.on('dragend', () => {
          const p = this.marker.getLatLng()
          this.$emit('input', { lat: p.lat, lng: p.lng })
        })
      }
    },
    syncMarkerFromValue(v) {
      const o = v || {}
      if (Number.isFinite(o.lat) && Number.isFinite(o.lng)) {
        if (this.map) this.placeMarker(o.lat, o.lng)
      } else if (this.marker && this.map) {
        this.map.removeLayer(this.marker)
        this.marker = null
      }
    },
    recenter() {
      if (!this.map) return
      const [la, ln] = this.centerPair()
      this.map.setView([la, ln], this.zoom)
    },
    useMyLocation() {
      if (!navigator.geolocation) return
      this.geoLoading = true
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lat = pos.coords.latitude
          const lng = pos.coords.longitude
          this.placeMarker(lat, lng)
          if (this.map) this.map.setView([lat, lng], Math.max(this.zoom, 14))
          this.$emit('input', { lat, lng })
          this.geoLoading = false
        },
        () => {
          this.geoLoading = false
        },
        { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 }
      )
    }
  }
}
</script>

<style scoped>
.map-location-picker__map {
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.12);
  min-height: 200px;
  z-index: 0;
}
</style>

<style>
/* Leaflet + divIcon marker */
.map-location-picker .map-location-picker__pin {
  background: transparent;
  border: none;
}
.map-location-picker .map-location-picker__pin-dot {
  display: block;
  width: 22px;
  height: 22px;
  margin: 2px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f59e0b 0%, #ea580c 100%);
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.35);
}
.map-location-picker .leaflet-container {
  font-family: inherit;
}
</style>
