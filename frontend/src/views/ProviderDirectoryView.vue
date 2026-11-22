<template>
  <v-container class="py-8 px-3 px-sm-4 pb-12">
    <div class="d-flex flex-wrap align-start dir-head mb-6">
      <div class="dir-head__text">
        <div class="text-overline mb-1">Provider directory</div>
        <h1 class="text-h4 font-weight-bold mb-2">All providers</h1>
        <p class="text-body-2 text--secondary mb-0">
          Every provider on {{ siteName }}: items for sale and services you can book. No invite or account needed.
        </p>
      </div>
      <v-btn
        outlined
        color="primary"
        class="text-none font-weight-bold dir-head__share"
        @click="copyLink"
      >
        <v-icon left small>{{ copied ? 'check' : 'link' }}</v-icon>
        {{ copied ? 'Link copied' : 'Copy link' }}
      </v-btn>
    </div>

    <!-- Pinned under the top bar while scrolling: chips + tools on larger screens, only the chips on phones. -->
    <div class="sticky-filters" :class="isPhone ? 'mb-2' : 'mb-6'">
      <!-- Same filters as inside a store: All · Services · Items sold · Promotions -->
      <v-chip-group :value="kind" mandatory column aria-label="Show" @change="setKind">
        <v-chip
          v-for="f in filters"
          :key="f.value"
          :value="f.value"
          :outlined="kind !== f.value"
          :color="kind === f.value ? 'primary' : undefined"
          :dark="kind === f.value"
          class="dir-filter text-none"
          :class="{ 'dir-filter--active': kind === f.value }"
        >
          <v-icon left small>{{ f.icon }}</v-icon>
          {{ f.label }}
          <span v-if="f.count != null" class="dir-filter__count ml-2">{{ f.count }}</span>
        </v-chip>
      </v-chip-group>
      <DirectoryTools
        v-if="!isPhone"
        v-model="filterText"
        :has-location="hasLocation"
        :locating="locating"
        class="mt-1"
        @locate="useMyLocation"
        @clear-location="clearLocation"
      />
    </div>
    <DirectoryTools
      v-if="isPhone"
      v-model="filterText"
      :has-location="hasLocation"
      :locating="locating"
      class="mb-6"
      @locate="useMyLocation"
      @clear-location="clearLocation"
    />

    <!-- Where distances are measured from: the visitor's full address -->
    <div v-if="hasLocation" class="d-flex align-start text-body-2 mb-4 dir-location">
      <v-icon small color="primary" class="mr-2 mt-n1">my_location</v-icon>
      <div class="min-width-0">
        <span class="text--secondary">Distances from </span>
        <span v-if="placeLabel" class="font-weight-medium">{{ placeLabel }}</span>
        <span v-else-if="resolvingPlace" class="text--secondary">finding your address…</span>
        <span v-else class="font-weight-medium">{{ lat.toFixed(5) }}, {{ lng.toFixed(5) }}</span>
        <span v-if="accuracyM" class="text-caption text--secondary"> · accurate to about {{ accuracyM }} m</span>
      </div>
    </div>
    <v-alert v-if="locationError" type="warning" dense outlined class="mb-4 rounded-lg">{{ locationError }}</v-alert>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">{{ error }}</v-alert>
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-6 rounded" />

    <p v-if="!loading && visibleProviders.length" class="text-caption text--secondary mb-4">
      {{ visibleProviders.length }} {{ visibleProviders.length === 1 ? 'provider' : 'providers' }}
      · {{ hasLocation ? 'nearest first' : 'best rated first' }}
    </p>

    <v-row v-if="visibleProviders.length" class="card-grid">
      <v-col v-for="m in visibleProviders" :key="m.merchantSlug" cols="12" sm="6" lg="4" class="d-flex">
        <MerchantCard
          :merchant="m"
          :kind="kind === 'services' ? 'salon' : m.kind"
          show-distance
          :located="hasLocation"
          @locate="useMyLocation"
          @select="openProvider"
          @choose="openProvider"
        />
      </v-col>
    </v-row>

    <v-card v-else-if="!loading && loaded && !error" class="pa-8 rounded-xl text-center" outlined>
      <v-icon large color="grey">storefront</v-icon>
      <p class="text-body-1 font-weight-medium mt-3 mb-1">
        {{ emptyTitle }}
      </p>
      <p class="text-body-2 text--secondary mb-0">
        {{ filterText ? 'Clear the search to see every provider again.' : 'Check back soon.' }}
      </p>
    </v-card>
  </v-container>
</template>

<script>
import MerchantCard from '@/components/MerchantCard.vue'
import DirectoryTools from '@/components/directory/DirectoryTools.vue'
import { fetchProviderDirectory } from '@/services/publicDirectory'
import { loadSavedSearchLocation, saveSearchLocation } from '@/utils/clientSearchLocation'
import { fetchReversePlaceLabel } from '@/utils/geocode'

const KINDS = ['all', 'services', 'items', 'promotions']
/** Older links used salon/shop (and ?kind=). */
const KIND_ALIASES = { salon: 'services', shop: 'items' }
const SALON_TYPES = ['salon_only', 'salon_and_store']
const SHOP_TYPES = ['normal_store', 'salon_and_store']
const isSalon = (m) => SALON_TYPES.includes(m && m.shopType)
const isShop = (m) => SHOP_TYPES.includes(m && m.shopType)
const hasOffers = (m) => Number(m && m.activePromotionCount) > 0

export default {
  name: 'ProviderDirectoryView',
  components: { MerchantCard, DirectoryTools },
  data() {
    const saved = loadSavedSearchLocation()
    return {
      /** Every provider (cards preview items, or services for services-only providers); used for counts and filters. */
      allProviders: [],
      /** Services filter: same providers with their services as the preview. */
      salonProviders: [],
      /** Provider name / item / service search; kept in ?q= so a search can be shared. */
      filterText: String((this.$route.query && this.$route.query.q) || ''),
      lat: saved.lat,
      lng: saved.lng,
      /** Full address of the visitor's position (street, suburb, city, postal code). */
      placeLabel: saved.label || '',
      resolvingPlace: false,
      accuracyM: null,
      loading: false,
      loaded: false,
      locating: false,
      error: '',
      locationError: '',
      copied: false,
      loadSeq: 0
    }
  },
  computed: {
    kind() {
      const q = this.$route.query || {}
      const given = String(q.show || q.kind || 'all').toLowerCase()
      const k = KIND_ALIASES[given] || given
      return KINDS.includes(k) ? k : 'all'
    },
    filters() {
      const all = this.allProviders
      const counted = (n) => (this.loaded ? n : null)
      return [
        { value: 'all', label: 'All', icon: 'apps', count: counted(all.length) },
        { value: 'services', label: 'Services', icon: 'event_available', count: counted(all.filter(isSalon).length) },
        { value: 'items', label: 'Items sold', icon: 'store', count: counted(all.filter(isShop).length) },
        { value: 'promotions', label: 'Promotions', icon: 'local_offer', count: counted(all.filter(hasOffers).length) }
      ]
    },
    /** Providers for the selected filter, before the search box. */
    providers() {
      if (this.kind === 'services') return this.salonProviders
      if (this.kind === 'items') return this.allProviders.filter(isShop)
      if (this.kind === 'promotions') return this.allProviders.filter(hasOffers)
      return this.allProviders
    },
    emptyTitle() {
      if (this.filterText) return 'No providers match that search'
      if (this.kind === 'promotions') return 'No providers are running promotions right now'
      if (this.kind === 'services') return 'No service providers listed yet'
      if (this.kind === 'items') return 'No providers selling items yet'
      return 'No providers listed yet'
    },
    /** Phones pin only the filter chips; the search and "Nearest first" scroll with the page. */
    isPhone() {
      return this.$vuetify.breakpoint.xsOnly
    },
    hasLocation() {
      return Number.isFinite(this.lat) && Number.isFinite(this.lng)
    },
    siteName() {
      return process.env.VUE_APP_SITE_NAME || 'Product Store'
    },
    visibleProviders() {
      const q = String(this.filterText || '').trim().toLowerCase()
      if (!q) return this.providers
      return this.providers.filter((m) => {
        // Provider name first (display name or its link name), then what it offers.
        if (String(m.storeName || '').toLowerCase().includes(q)) return true
        if (String(m.merchantSlug || '').toLowerCase().includes(q.replace(/\s+/g, '-'))) return true
        const offs = Array.isArray(m.offerings) ? m.offerings : []
        return offs.some((o) => String((o && o.name) || '').toLowerCase().includes(q))
      })
    }
  },
  watch: {
    filterText(v) {
      clearTimeout(this._qTimer)
      this._qTimer = setTimeout(() => {
        const q = String(v || '').trim()
        const query = { ...this.$route.query }
        if (q) query.q = q
        else delete query.q
        if ((this.$route.query.q || '') !== (query.q || '')) this.$router.replace({ query }).catch(() => {})
      }, 300)
    },
    kind(k) {
      if (k === 'services') this.load()
    }
  },
  created() {
    this.load()
    // Distances need the visitor's location: ask once on arrival (quietly; declining just leaves
    // "Show distance" on each card).
    if (!this.hasLocation) this.useMyLocation({ quiet: true })
    else if (!this.placeLabel) this.resolvePlace()
  },
  methods: {
    async load() {
      const seq = ++this.loadSeq
      this.loading = true
      this.error = ''
      try {
        const where = {
          latitude: this.hasLocation ? this.lat : undefined,
          longitude: this.hasLocation ? this.lng : undefined
        }
        const [all, salons] = await Promise.all([
          fetchProviderDirectory({ kind: 'all', ...where }),
          this.kind === 'services' ? fetchProviderDirectory({ kind: 'salon', ...where }) : Promise.resolve(null)
        ])
        if (seq !== this.loadSeq) return
        this.allProviders = all
        if (salons) this.salonProviders = salons
      } catch (e) {
        if (seq !== this.loadSeq) return
        this.allProviders = []
        this.salonProviders = []
        this.error = (e && e.message) || 'Could not load providers.'
      } finally {
        if (seq === this.loadSeq) {
          this.loading = false
          this.loaded = true
        }
      }
    },
    setKind(k) {
      const query = { ...this.$route.query }
      delete query.kind
      if (k === 'all') delete query.show
      else query.show = k
      this.$router.replace({ query }).catch(() => {})
    },
    useMyLocation({ quiet = false } = {}) {
      this.locationError = ''
      if (!navigator.geolocation) {
        if (!quiet) this.locationError = 'Location is not available on this device.'
        return
      }
      this.locating = true
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          this.locating = false
          this.lat = pos.coords.latitude
          this.lng = pos.coords.longitude
          const acc = Number(pos.coords.accuracy)
          this.accuracyM = Number.isFinite(acc) && acc > 0 ? Math.round(acc) : null
          this.placeLabel = ''
          saveSearchLocation(this.lat, this.lng, '')
          this.load()
          this.resolvePlace()
        },
        () => {
          this.locating = false
          if (!quiet) this.locationError = 'Allow location access in your browser to see how far each provider is.'
        },
        // Precise fix (GPS where available), not a cached or network-only estimate.
        { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 }
      )
    },
    async resolvePlace() {
      const lat = this.lat
      const lng = this.lng
      this.resolvingPlace = true
      try {
        const label = await fetchReversePlaceLabel(lat, lng)
        if (lat !== this.lat || lng !== this.lng) return
        this.placeLabel = label
        saveSearchLocation(lat, lng, label)
      } finally {
        this.resolvingPlace = false
      }
    },
    clearLocation() {
      this.lat = null
      this.lng = null
      this.placeLabel = ''
      this.accuracyM = null
      this.load()
    },
    openProvider(m) {
      if (!m || !m.merchantSlug) return
      // Open the store with the same filter the visitor picked here (its page has the same chips).
      const query = this.kind === 'all' ? {} : { show: this.kind }
      this.$router.push({ name: 'merchant-home', params: { merchantSlug: m.merchantSlug }, query }).catch(() => {})
    },
    async copyLink() {
      const url = `${window.location.origin}${this.$router.resolve({ name: 'provider-directory' }).href}`
      try {
        await navigator.clipboard.writeText(url)
      } catch {
        window.prompt('Copy this link', url)
        return
      }
      this.copied = true
      setTimeout(() => {
        this.copied = false
      }, 2500)
    }
  }
}
</script>

<style scoped>
.dir-head {
  gap: 16px;
}

.dir-head__text {
  flex: 1 1 28rem;
  max-width: 40rem;
}

.dir-head__share {
  margin-left: auto;
}

.dir-filter {
  font-weight: 600;
}

/* Pinned just under the 72px top bar. */
.sticky-filters {
  position: sticky;
  top: 72px;
  z-index: 3;
  margin: 0 -12px;
  padding: 8px 12px;
  background: rgba(238, 242, 247, 0.92);
  backdrop-filter: blur(8px);
}

.dir-filter__count {
  min-width: 1.25rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  font-size: 0.75rem;
  line-height: 1.25rem;
  text-align: center;
}

.dir-filter--active .dir-filter__count {
  background: rgba(255, 255, 255, 0.2);
}
</style>
