<template>
  <div>
    <section class="hero">
      <v-container class="hero-container py-6 py-md-10 px-3 px-sm-4">
        <v-row align="center">
          <v-col cols="12" :md="heroAdvertUrl ? 5 : 7" class="pr-md-8">
            <div class="hero-eyebrow mb-3">
              <span class="hero-eyebrow__dot" />
              {{ heroCopy.eyebrow }}
            </div>
            <h1 class="hero-title">
              {{ heroCopy.title }} <span class="hero-title__accent">{{ heroCopy.accent }}</span>.
            </h1>
            <p class="hero-lead">{{ heroCopy.lead }}</p>
            <div v-if="reviewCount > 0" class="d-flex align-center mt-4">
              <v-icon small color="amber darken-2" class="mr-1">star</v-icon>
              <span class="text-body-2 font-weight-medium">{{ reviewAverage }}</span>
              <span class="text-caption text--secondary ml-2">{{ reviewCount }} review{{ reviewCount === 1 ? '' : 's' }}</span>
            </div>

            <div class="hero-search-wrap mt-6">
              <v-text-field
                v-model="search"
                solo
                flat
                hide-details
                clearable
                height="56"
                class="hero-search hero-search-field"
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
                class="hero-search hero-category-field mt-3"
                aria-label="Filter by category"
              />
              <div class="hero-meta text-caption text--secondary mt-3">
                <v-icon small color="secondary" class="mr-1">bolt</v-icon>
                Updates instantly, no refresh needed
              </div>
            </div>
          </v-col>

          <v-col
            cols="12"
            :md="heroAdvertUrl ? 7 : 5"
            :class="heroAdvertUrl ? 'd-flex justify-center mt-4 mt-md-0' : 'd-none d-md-flex justify-center'"
          >
            <div v-if="heroAdvertUrl" class="hero-visual hero-visual--advert">
              <img :src="heroAdvertUrl" alt="" class="hero-advert-img" loading="eager" />
            </div>
            <div v-else class="hero-visual" aria-hidden="true">
              <div class="hero-visual__ring" />
              <div class="hero-visual__card hero-visual__card--1" />
              <div class="hero-visual__card hero-visual__card--2" />
              <div class="hero-visual__badge">
                <v-icon color="white" small>local_offer</v-icon>
              </div>
            </div>
          </v-col>
        </v-row>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16 px-3 px-sm-4">
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
            <div class="section-kicker">Items sold</div>
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
            <div class="section-kicker">Bookings</div>
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
          <div class="section-kicker">Offers</div>
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
    StorePromotionGrid
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
.hero {
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 96px;
  background: linear-gradient(180deg, transparent, #eef2f7);
  pointer-events: none;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: rgba(15, 23, 42, 0.55);
}

.hero-eyebrow__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  box-shadow: 0 0 0 4px rgba(234, 88, 12, 0.2);
}

.hero-title {
  font-size: clamp(2rem, 4vw, 2.75rem);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.035em;
  color: #0f172a;
  max-width: 18ch;
}

.hero-title__accent {
  background: linear-gradient(120deg, #ea580c, #c2410c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-lead {
  margin-top: 1rem;
  font-size: 1.0625rem;
  line-height: 1.65;
  color: rgba(15, 23, 42, 0.62);
  max-width: 40ch;
}

.hero-search-wrap {
  width: 100%;
  max-width: 440px;
}

.hero-search-field >>> .v-input__slot,
.hero-category-field >>> .v-input__slot {
  border-radius: 16px !important;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 12px 40px -16px rgba(15, 23, 42, 0.25) !important;
  border: 1px solid rgba(15, 23, 42, 0.06) !important;
  padding-left: 4px !important;
}

.hero-search-field >>> input::placeholder {
  color: rgba(100, 116, 139, 0.85);
}

.hero-meta {
  display: flex;
  align-items: center;
}

/* Decorative art keeps a fixed footprint so it never opens a wide empty column beside the copy. */
.hero-visual {
  position: relative;
  width: 260px;
  max-width: 100%;
  height: 260px;
}

.hero-visual--advert {
  width: 100%;
  height: auto;
}

.hero-advert-img {
  width: 100%;
  max-height: 380px;
  object-fit: cover;
  border-radius: 20px;
  box-shadow: 0 24px 50px -20px rgba(15, 23, 42, 0.35);
}

.hero-visual__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px dashed rgba(15, 23, 42, 0.12);
  animation: spin-slow 28s linear infinite;
}

.hero-visual__card {
  position: absolute;
  border-radius: 20px;
  box-shadow: 0 24px 50px -20px rgba(15, 23, 42, 0.35);
}

.hero-visual__card--1 {
  width: 140px;
  height: 180px;
  left: 50%;
  top: 50%;
  transform: translate(-62%, -58%) rotate(-8deg);
  background: linear-gradient(160deg, #1e3a5f, #0f172a);
}

.hero-visual__card--2 {
  width: 120px;
  height: 150px;
  left: 50%;
  top: 50%;
  transform: translate(-22%, -32%) rotate(10deg);
  background: linear-gradient(160deg, #fed7aa, #fb923c);
}

.hero-visual__badge {
  position: absolute;
  right: 12%;
  top: 18%;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 28px -10px rgba(194, 65, 12, 0.7);
}

@keyframes spin-slow {
  to {
    transform: rotate(360deg);
  }
}

.section-kicker {
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 0.35rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.section-count {
  margin-left: auto;
}
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
