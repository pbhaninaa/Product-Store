<template>
  <div>
    <section class="hero">
      <v-container class="hero-container py-10 py-md-16 px-3 px-sm-4">
        <v-row align="center">
          <v-col cols="12" md="7" class="pr-md-8">
            <div class="hero-eyebrow mb-3">
              <span class="hero-eyebrow__dot" />
              Live catalogue
            </div>
            <h1 class="hero-title">
              Discover products that feel <span class="hero-title__accent">worth it</span>.
            </h1>
            <p class="hero-lead">
              Browse our lineup in real time — new items appear the moment they’re published.
            </p>

            <div class="hero-search-wrap mt-8">
              <v-text-field
                v-model="search"
                solo
                flat
                hide-details
                clearable
                height="56"
                class="hero-search hero-search-field"
                prepend-inner-icon="search"
                placeholder="Search by product name…"
                aria-label="Search products"
              />
              <v-select
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
                Updates instantly — no refresh needed
              </div>
            </div>
          </v-col>

          <v-col cols="12" md="5" class="d-none d-md-flex justify-center">
            <div class="hero-visual" aria-hidden="true">
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
      <v-alert
        v-if="supabaseConfigHint"
        type="warning"
        border="left"
        colored-border
        prominent
        class="mb-10 rounded-lg"
      >
        {{ supabaseConfigHint }}
      </v-alert>

      <div class="section-head d-flex flex-column flex-sm-row align-start align-sm-end mb-8">
        <div>
          <div class="section-kicker">Collection</div>
          <h2 class="section-title">All products</h2>
        </div>
        <div v-if="!loading && products.length" class="section-count text-body-2 text--secondary mt-2 mt-sm-0">
          Showing {{ filteredProducts.length }} of {{ products.length }}
        </div>
      </div>

      <ProductGrid :products="filteredProducts" :loading="loading" />
    </v-container>
  </div>
</template>

<script>
import ProductGrid from '@/components/ProductGrid.vue'
import { supabaseSetupMessage } from '@/supabase'
import { compareProductsByCategoryThenName, subscribeToProducts } from '@/services/products'

export default {
  name: 'HomeView',
  components: {
    ProductGrid
  },
  data() {
    return {
      loading: true,
      products: [],
      search: '',
      /** Empty string = show all categories */
      categoryFilter: '',
      supabaseConfigHint: supabaseSetupMessage
    }
  },
  computed: {
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
  created() {
    this.unsub = subscribeToProducts((products) => {
      this.products = products
      this.loading = false
    })
  },
  beforeDestroy() {
    if (this.unsub) this.unsub()
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

.hero-visual {
  position: relative;
  width: 280px;
  height: 280px;
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
</style>
