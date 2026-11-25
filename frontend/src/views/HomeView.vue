<template>
  <div>
    <store-hero :kicker="heroCopy.eyebrow" :advert-url="heroAdvertUrl" advert-wide>
      <template #title>
        {{ heroCopy.title }} <span class="store-hero__accent">{{ heroCopy.accent }}</span>.
      </template>
      <p class="admin-lead mb-0">{{ heroCopy.lead }}</p>
      <div v-if="reviewCount > 0" class="d-flex align-center mt-4">
        <v-icon small color="amber darken-2" class="mr-1" aria-hidden="true">star</v-icon>
        <span class="text-body-2 font-weight-medium">{{ reviewAverage }}</span>
        <span class="text-caption text--secondary ml-2">{{ reviewCount }} review{{ reviewCount === 1 ? '' : 's' }}</span>
      </div>

      <div class="store-hero__search mt-6">
        <v-text-field
          v-model="search"
          solo
          flat
          hide-details
          clearable
          height="56"
          class="store-hero__field"
          prepend-inner-icon="search"
          :placeholder="searchPlaceholder"
          :aria-label="searchPlaceholder"
        />
        <v-select
          v-if="showProducts && categoryMenuItems.length > 2"
          v-model="categoryFilter"
          :items="categoryMenuItems"
          item-text="text"
          item-value="value"
          solo
          flat
          hide-details
          clearable
          height="56"
          label="Category"
          placeholder="All categories"
          prepend-inner-icon="category"
          class="store-hero__field mt-3"
          aria-label="Filter by category"
        />
        <div class="store-hero__meta text-caption text--secondary mt-3">
          <v-icon small color="secondary" class="mr-1" aria-hidden="true">bolt</v-icon>
          Updates instantly, no refresh needed
        </div>
      </div>
    </store-hero>

    <v-container class="pt-6 pb-12 pb-md-16 px-3 px-sm-4">
      <!-- What's in this store: one page, filtered here instead of links in the top bar. Sticks under the top bar. -->
      <div v-if="filters.length > 1" class="sticky-filters mb-6">
      <v-chip-group
        :value="show"
        mandatory
        column
        class="store-filters"
        aria-label="Show"
        @change="setShow"
      >
        <v-chip
          v-for="f in filters"
          :key="f.value"
          :value="f.value"
          :outlined="show !== f.value"
          :color="show === f.value ? 'primary' : undefined"
          :dark="show === f.value"
          class="store-filter text-none"
          :class="{ 'store-filter--active': show === f.value }"
        >
          <v-icon left small>{{ f.icon }}</v-icon>
          {{ f.label }}
          <span v-if="f.count != null" class="store-filter__count ml-2">{{ f.count }}</span>
        </v-chip>
      </v-chip-group>
      </div>

      <section v-if="showProducts" class="store-section">
        <div class="section-head d-flex flex-column flex-sm-row align-start align-sm-end mb-6">
          <div>
            <div class="card-label mb-1">Items sold</div>
            <h2 class="section-title">Items</h2>
          </div>
          <div v-if="!loading && products.length" class="section-count text-body-2 text--secondary mt-2 mt-sm-0">
            Showing {{ filteredProducts.length }} of {{ products.length }}
          </div>
        </div>
        <ProductGrid :products="filteredProducts" :loading="loading" />
      </section>

      <section v-if="showServices" class="store-section">
        <div class="section-head d-flex flex-column flex-sm-row align-start align-sm-end mb-6">
          <div>
            <div class="card-label mb-1">Bookings</div>
            <h2 class="section-title">Services</h2>
          </div>
          <div v-if="!servicesLoading && services.length" class="section-count text-body-2 text--secondary mt-2 mt-sm-0">
            Showing {{ filteredServices.length }} of {{ services.length }}
          </div>
        </div>
        <StoreServiceGrid
          :services="filteredServices"
          :loading="servicesLoading"
          :merchant-slug="slug"
          :empty-title="services.length ? 'No matches' : 'No services yet'"
          :empty-text="services.length ? 'Try another search.' : 'Services this provider adds will show up here.'"
        />
      </section>

      <section v-if="showPromotions" class="store-section">
        <div class="section-head mb-6">
          <div class="card-label mb-1">Offers</div>
          <h2 class="section-title">Promotions</h2>
          <p class="text-body-2 text--secondary mb-0 mt-1">
            No code needed. The best offer is applied automatically at checkout.
          </p>
        </div>
        <StorePromotionGrid :items="promotions" :loading="promotionsLoading" />
      </section>
    </v-container>
  </div>
</template>

<script>
import ProductGrid from '@/components/ProductGrid.vue'
import StoreServiceGrid from '@/components/store/StoreServiceGrid.vue'
import StorePromotionGrid from '@/components/store/StorePromotionGrid.vue'
import StoreHero from '@/components/store/StoreHero.vue'
import { compareProductsByCategoryThenName } from '@/utils/productsSort'
import { fetchCatalog, fetchShopSettings, fetchReviewSummary } from '@/services/publicStore'
import { fetchSalonServices } from '@/services/salonPublic'
import { apiFetch } from '@/services/api'
import { isSalonOnlyShopType, isSalonShopType, normalizeShopType, SHOP_NORMAL } from '@/services/shopType'
import { addToCart } from '@/services/cart'

/** Store page sections, chosen with the filter chips under the hero (?show=...). */
const SHOW_VALUES = ['all', 'services', 'items', 'promotions']
/** Older links used salon/shop. */
const SHOW_ALIASES = { salon: 'services', shop: 'items' }

export default {
  name: 'HomeView',
  components: {
    ProductGrid,
    StoreServiceGrid,
    StorePromotionGrid,
    StoreHero
  },
  inject: {
    shopDisplay: {
      default: () => ({ storeName: '', logoUrl: '', heroUrl: '' })
    }
  },
  data() {
    return {
      loading: true,
      products: [],
      search: '',
      /** Empty string = show all categories */
      categoryFilter: '',
      reviewAverage: 0,
      reviewCount: 0,
      focusedProductId: '',
      shopType: SHOP_NORMAL,
      services: [],
      servicesLoading: false,
      promotions: [],
      promotionsLoading: true
    }
  },
  computed: {
    slug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    sellsProducts() {
      return !isSalonOnlyShopType(this.shopType)
    },
    offersServices() {
      return isSalonShopType(this.shopType)
    },
    /** Chips under the hero, in the same order as the provider directory: All · Services · Items sold · Promotions. */
    filters() {
      const list = [{ value: 'all', label: 'All', icon: 'apps', count: null }]
      if (this.offersServices) {
        list.push({
          value: 'services',
          label: 'Services',
          icon: 'event_available',
          count: this.servicesLoading ? null : this.services.length
        })
      }
      if (this.sellsProducts) {
        list.push({ value: 'items', label: 'Items sold', icon: 'store', count: this.loading ? null : this.products.length })
      }
      // Only what this provider has: Promotions appears once it has an offer running.
      if (!this.promotionsLoading && this.promotions.length > 0) {
        list.push({ value: 'promotions', label: 'Promotions', icon: 'local_offer', count: this.promotions.length })
      }
      return list
    },
    show() {
      const given = String((this.$route.query && this.$route.query.show) || 'all').toLowerCase()
      const raw = SHOW_ALIASES[given] || given
      const valid = SHOW_VALUES.includes(raw) && this.filters.some((f) => f.value === raw)
      return valid ? raw : 'all'
    },
    showProducts() {
      return this.sellsProducts && (this.show === 'all' || this.show === 'items')
    },
    showServices() {
      return this.offersServices && (this.show === 'all' || this.show === 'services')
    },
    /** On "All", offers only appear when there are some; the Promotions chip always shows the section. */
    showPromotions() {
      if (this.show === 'promotions') return true
      return this.show === 'all' && !this.promotionsLoading && this.promotions.length > 0
    },
    searchPlaceholder() {
      if (this.showProducts && this.showServices) return 'Search items and services…'
      if (this.showServices) return 'Search services…'
      return 'Search items…'
    },
    heroCopy() {
      if (!this.sellsProducts) {
        return {
          eyebrow: 'Services',
          title: 'Book a time that fits your',
          accent: 'calendar',
          lead: 'Pick a service and book a time that suits you.'
        }
      }
      if (this.offersServices) {
        return {
          eyebrow: 'Items sold & services',
          title: 'Items and services, all in',
          accent: 'one place',
          lead: 'Browse what’s for sale or book a service, and use the filters below to see just what you want.'
        }
      }
      return {
        eyebrow: 'Live catalogue',
        title: 'Discover items that feel',
        accent: 'worth it',
        lead: 'Browse our lineup in real time. New items appear the moment they’re published.'
      }
    },
    filteredServices() {
      const q = String(this.search || '').trim().toLowerCase()
      const list = this.services || []
      if (!q) return list
      return list.filter(
        (s) =>
          String(s.name || '').toLowerCase().includes(q) || String(s.description || '').toLowerCase().includes(q)
      )
    },
    heroAdvertUrl() {
      const u = this.shopDisplay && String(this.shopDisplay.heroUrl || '').trim()
      return u || ''
    },
    categoryMenuItems() {
      const set = new Set()
      ;(this.products || []).forEach((p) => {
        const c = String(p.category || 'Uncategorized').trim()
        if (c) set.add(c)
      })
      const sorted = [...set].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      return [{ text: 'All categories', value: '' }, ...sorted.map((c) => ({ text: c, value: c }))]
    },
    filteredProducts() {
      let list = [...(this.products || [])]
      const cat = String(this.categoryFilter || '').trim()
      if (cat) {
        const want = cat.toLowerCase()
        list = list.filter((p) => String(p.category || 'Uncategorized').trim().toLowerCase() === want)
      }
      const q = String(this.search || '').trim().toLowerCase()
      if (q) {
        list = list.filter((p) => String(p.name || '').toLowerCase().includes(q))
      }
      list.sort(compareProductsByCategoryThenName)
      return list
    }
  },
  watch: {
    products() {
      this.focusProductFromQuery()
    },
    '$route.query.productId'() {
      this.focusedProductId = ''
      this.focusProductFromQuery()
    }
  },
  async created() {
    const slug = this.slug
    try {
      const s = await fetchShopSettings(slug)
      this.shopType = normalizeShopType(s && s.shopType)
    } catch {
      // keep the normal-store default
    }
    this.loadPromotions(slug)
    if (this.offersServices) this.loadServices(slug)
    try {
      fetchReviewSummary(slug)
        .then((summary) => {
          this.reviewAverage = summary.averageRating
          this.reviewCount = summary.reviewCount
        })
        .catch(() => {})
      if (this.sellsProducts) this.products = await fetchCatalog(slug)
    } finally {
      this.loading = false
    }
    if (!this.sellsProducts) return
    this._poll = setInterval(async () => {
      try {
        this.products = await fetchCatalog(slug)
      } catch {
        // ignore transient failures
      }
    }, 5000)
  },
  beforeDestroy() {
    if (this._poll) clearInterval(this._poll)
  },
  methods: {
    setShow(value) {
      const query = { ...this.$route.query }
      if (!value || value === 'all') delete query.show
      else query.show = value
      this.$router.replace({ query }).catch(() => {})
    },
    async loadServices(slug) {
      this.servicesLoading = true
      try {
        const res = await fetchSalonServices(slug)
        this.services = Array.isArray(res.services) ? res.services : []
      } catch {
        this.services = []
      } finally {
        this.servicesLoading = false
      }
    },
    async loadPromotions(slug) {
      this.promotionsLoading = true
      try {
        const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/promotions`)
        this.promotions = Array.isArray(res) ? res : []
      } catch {
        this.promotions = []
      } finally {
        this.promotionsLoading = false
      }
    },
    focusProductFromQuery() {
      const id = String((this.$route.query && this.$route.query.productId) || '').trim()
      if (!id || this.focusedProductId === id) return
      const p = (this.products || []).find((x) => String(x.id) === id)
      if (!p) return
      this.focusedProductId = id
      if (p.name) this.search = p.name
      addToCart(p, 1)
      this.$nextTick(() => {
        const el = document.getElementById(`product-${id}`)
        if (el && typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      })
    }
  }
}
</script>

<style scoped>
.store-filters {
  margin-top: -8px;
}

/* Filters stay in reach while scrolling: pinned just under the 72px top bar. */
.sticky-filters {
  position: sticky;
  top: 72px;
  z-index: 3;
  margin: 0 -12px;
  padding: 8px 12px 4px;
  background: rgba(238, 242, 247, 0.92);
  backdrop-filter: blur(8px);
}

.sticky-filters .store-filters {
  margin-top: 0;
}

.store-filter {
  font-weight: 600;
}

.store-filter__count {
  min-width: 1.25rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  font-size: 0.75rem;
  line-height: 1.25rem;
  text-align: center;
}

.store-filter--active .store-filter__count {
  background: rgba(255, 255, 255, 0.2);
}

.store-section + .store-section {
  margin-top: 48px;
}
</style>
