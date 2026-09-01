<template>
  <v-container class="py-8 px-3 px-sm-4 pb-12">
    <v-btn text small class="text-none px-0 mb-4" to="/">
      <v-icon left small>arrow_back</v-icon>
      Hub
    </v-btn>

    <div class="text-overline mb-1">{{ kind === 'salon' ? 'Salons' : 'Shops' }} near you</div>
    <h1 class="text-h4 font-weight-bold mb-2">All nearby providers</h1>
    <p class="text-body-2 text--secondary mb-6" style="max-width: 40rem">
      Every {{ kindLabel }} in your radius is listed, best-rated first. Select the one you like ù ratings and review
      counts are on each card to make that easy.
    </p>

    <v-card class="pa-4 pa-sm-6 rounded-xl mb-6" outlined>
      <div class="font-weight-medium mb-3">Your location</div>
      <v-text-field
        v-model="addressQuery"
        outlined
        hide-details="auto"
        label="Search an address (optional)"
        prepend-inner-icon="place"
        :loading="addressSearching"
        :disabled="loading"
        @keyup.enter="searchAddress"
      />
      <v-btn
        text
        small
        color="primary"
        class="text-none mt-1 mb-3"
        :disabled="loading || addressSearching || !addressQuery.trim()"
        @click="searchAddress"
      >
        Find this place
      </v-btn>
      <p v-if="placeLabel" class="text-caption text--secondary mb-3">Using: {{ placeLabel }}</p>
      <MapLocationPicker
        :value="mapValue"
        :center-lat="lat"
        :center-lng="lng"
        :hint="mapHint"
        :height="240"
        @input="onMapInput"
      />
      <v-alert v-if="locationError" type="warning" dense outlined class="mt-3 mb-0 rounded-lg">
        {{ locationError }}
      </v-alert>
    </v-card>

    <ClientSearchRadiusControl
      v-model="radiusKm"
      :kind="kind"
      :disabled="loading"
      :show-extend-hint="searched && !loading && merchants.length === 0 && hasLocation"
    />

    <v-text-field
      v-model="filterText"
      outlined
      clearable
      hide-details="auto"
      class="mb-6"
      :label="kind === 'salon' ? 'Filter by salon or service name (optional)' : 'Filter by shop or product name (optional)'"
      prepend-inner-icon="search"
    />

    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">{{ error }}</v-alert>
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-6 rounded" />

    <p v-if="!loading && hasLocation && visibleMerchants.length" class="text-caption text--secondary mb-4">
      Showing {{ visibleMerchants.length }}
      {{ visibleMerchants.length === 1 ? kindLabelSingular : kindLabel }}
      ù sorted by rating, then reviews, then distance
    </p>

    <v-row v-if="visibleMerchants.length">
      <v-col v-for="m in visibleMerchants" :key="m.merchantSlug" cols="12" md="6" lg="4">
        <MerchantCard
          :merchant="m"
          :kind="kind"
          :selected="selectedSlug === m.merchantSlug"
          @select="selectMerchant"
          @choose="chooseMerchant"
        />
      </v-col>
    </v-row>

    <v-card
      v-else-if="!loading && hasLocation && searched && !filterText"
      class="pa-8 rounded-xl text-center"
      outlined
    >
      <v-icon large color="grey">place</v-icon>
      <p class="text-body-1 font-weight-medium mt-3 mb-1">No {{ kindLabel }} in this radius yet</p>
      <p class="text-body-2 text--secondary mb-0">
        Move the pin, widen the radius, or check that nearby merchants have set their store location.
      </p>
    </v-card>

    <v-card
      v-else-if="!loading && hasLocation && searched && filterText && !visibleMerchants.length"
      class="pa-8 rounded-xl text-center"
      outlined
    >
      <p class="text-body-1 font-weight-medium mb-1">No matches for that filter</p>
      <p class="text-body-2 text--secondary mb-0">Clear the search to see every nearby {{ kindLabelSingular }} again.</p>
    </v-card>

    <v-card v-else-if="!loading && !hasLocation" class="pa-8 rounded-xl text-center" outlined>
      <p class="text-body-1 font-weight-medium mb-1">Set your location to see providers</p>
      <p class="text-body-2 text--secondary mb-0">Use my location, search an address, or tap the map.</p>
    </v-card>

    <v-footer v-if="selected" padless color="transparent" class="continue-bar mt-6">
      <v-card class="pa-4 rounded-xl" outlined elevation="4">
        <div class="d-flex align-center flex-wrap">
          <div class="mr-4 mb-2 mb-sm-0">
            <div class="font-weight-bold">{{ selected.storeName }}</div>
            <div class="text-caption text--secondary">
              {{ ratingLine(selected) }}
              <span v-if="selected.distanceKm != null"> ù {{ selected.distanceKm }} km</span>
            </div>
          </div>
          <v-spacer />
          <v-btn depressed color="primary" class="text-none font-weight-bold btn-amber" @click="chooseMerchant(selected)">
            Continue with this {{ kind === 'salon' ? 'salon' : 'shop' }}
          </v-btn>
        </div>
      </v-card>
    </v-footer>
  </v-container>
</template>

<script>
import MapLocationPicker from '@/components/MapLocationPicker.vue'
import ClientSearchRadiusControl from '@/components/ClientSearchRadiusControl.vue'
import MerchantCard from '@/components/MerchantCard.vue'
import { fetchNearbyMerchants } from '@/services/clientNearby'
import { fetchReversePlaceLabel } from '@/utils/geocode'
import { DEFAULT_CLIENT_SEARCH_RADIUS_KM, normalizeClientSearchRadiusKm } from '@/utils/clientSearchRadius'
import { loadSavedSearchLocation, saveSearchLocation } from '@/utils/clientSearchLocation'

export default {
  name: 'ClientDiscoverView',
  components: { MapLocationPicker, ClientSearchRadiusControl, MerchantCard },
  data() {
    const saved = loadSavedSearchLocation()
    return {
      lat: saved.lat,
      lng: saved.lng,
      placeLabel: saved.label,
      addressQuery: saved.label || '',
      addressSearching: false,
      radiusKm: DEFAULT_CLIENT_SEARCH_RADIUS_KM,
      merchants: [],
      selectedSlug: '',
      filterText: '',
      loading: false,
      searched: false,
      error: '',
      locationError: '',
      searchSeq: 0
    }
  },
  computed: {
    kind() {
      const k = String((this.$route.query && this.$route.query.kind) || 'shop').toLowerCase()
      return k === 'salon' ? 'salon' : 'shop'
    },
    kindLabel() {
      return this.kind === 'salon' ? 'salons' : 'shops'
    },
    kindLabelSingular() {
      return this.kind === 'salon' ? 'salon' : 'shop'
    },
    hasLocation() {
      return Number.isFinite(this.lat) && Number.isFinite(this.lng)
    },
    mapValue() {
      return { lat: this.lat, lng: this.lng }
    },
    mapHint() {
      return 'Tap the map or use my location. We list every matching provider in your radius.'
    },
    visibleMerchants() {
      const q = String(this.filterText || '')
        .trim()
        .toLowerCase()
      const list = Array.isArray(this.merchants) ? this.merchants : []
      if (!q) return list
      return list.filter((m) => {
        const name = String((m && m.storeName) || '').toLowerCase()
        if (name.includes(q)) return true
        const offs = Array.isArray(m && m.offerings) ? m.offerings : []
        return offs.some((o) => String((o && o.name) || '').toLowerCase().includes(q))
      })
    },
    selected() {
      if (!this.selectedSlug) return null
      return this.visibleMerchants.find((m) => m.merchantSlug === this.selectedSlug) || null
    }
  },
  watch: {
    kind() {
      this.selectedSlug = ''
      this.filterText = ''
      this.loadMerchants()
    },
    radiusKm() {
      this.loadMerchants()
    }
  },
  created() {
    if (this.hasLocation) {
      this.loadMerchants()
    } else {
      this.tryGeolocate()
    }
  },
  methods: {
    onMapInput(v) {
      const lat = v && Number(v.lat)
      const lng = v && Number(v.lng)
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) return
      this.lat = lat
      this.lng = lng
      this.locationError = ''
      this.refreshPlaceLabel(lat, lng)
      this.loadMerchants()
    },
    async refreshPlaceLabel(lat, lng) {
      const label = await fetchReversePlaceLabel(lat, lng)
      if (label) {
        this.placeLabel = label
        this.addressQuery = label
      }
      saveSearchLocation(lat, lng, this.placeLabel)
    },
    tryGeolocate() {
      if (!navigator.geolocation) {
        this.locationError = 'Location is not available ù search an address or tap the map.'
        return
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          this.lat = pos.coords.latitude
          this.lng = pos.coords.longitude
          this.locationError = ''
          this.refreshPlaceLabel(this.lat, this.lng)
          this.loadMerchants()
        },
        () => {
          this.locationError = 'Allow location, or set a pin on the map, to see every nearby provider.'
        },
        { enableHighAccuracy: true, timeout: 12000 }
      )
    },
    async searchAddress() {
      const q = String(this.addressQuery || '').trim()
      if (!q) return
      this.addressSearching = true
      this.locationError = ''
      try {
        const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(q)}`
        const res = await fetch(url)
        const rows = res.ok ? await res.json() : []
        const hit = Array.isArray(rows) && rows[0]
        const lat = hit ? Number(hit.lat) : NaN
        const lng = hit ? Number(hit.lon) : NaN
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
          this.locationError = 'That address was not found. Try a suburb or city name, or tap the map.'
          return
        }
        this.lat = lat
        this.lng = lng
        this.placeLabel = String(hit.display_name || q)
        saveSearchLocation(lat, lng, this.placeLabel)
        this.loadMerchants()
      } catch {
        this.locationError = 'Address lookup failed. Tap the map instead.'
      } finally {
        this.addressSearching = false
      }
    },
    async loadMerchants() {
      if (!this.hasLocation) return
      const seq = ++this.searchSeq
      this.loading = true
      this.error = ''
      try {
        const rows = await fetchNearbyMerchants({
          kind: this.kind,
          latitude: this.lat,
          longitude: this.lng,
          radiusKm: normalizeClientSearchRadiusKm(this.radiusKm)
        })
        if (seq !== this.searchSeq) return
        this.merchants = Array.isArray(rows) ? rows : []
        this.searched = true
        saveSearchLocation(this.lat, this.lng, this.placeLabel)
        if (this.selectedSlug && !this.merchants.some((m) => m.merchantSlug === this.selectedSlug)) {
          this.selectedSlug = ''
        }
      } catch (e) {
        if (seq !== this.searchSeq) return
        this.error = (e && e.message) || 'Could not load nearby providers.'
        this.merchants = []
        this.searched = true
      } finally {
        if (seq === this.searchSeq) this.loading = false
      }
    },
    selectMerchant(merchant) {
      this.selectedSlug = merchant && merchant.merchantSlug ? merchant.merchantSlug : ''
    },
    ratingLine(m) {
      const count = Number(m && m.reviewCount) || 0
      const avg = Number(m && m.averageRating)
      if (!count) return 'New ù no reviews yet'
      return `${Number.isFinite(avg) ? avg.toFixed(1) : '0.0'} stars (${count})`
    },
    chooseMerchant(merchant) {
      if (!merchant || !merchant.merchantSlug) return
      const slug = merchant.merchantSlug
      if (this.kind === 'salon') {
        this.$router
          .push({ name: 'merchant-salon-services', params: { merchantSlug: slug } })
          .catch(() => {})
        return
      }
      this.$router.push({ name: 'merchant-home', params: { merchantSlug: slug } }).catch(() => {})
    }
  }
}
</script>
