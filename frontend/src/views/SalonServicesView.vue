<template>
  <div>
    <section class="hero">
      <v-container class="hero-container py-10 py-md-16 px-3 px-sm-4">
        <v-row align="center">
          <v-col cols="12" md="4" >
            <div class="hero-eyebrow mb-3">
              <span class="hero-eyebrow__dot" />
              Salon services
            </div>
            <h1 class="hero-title">
              Treatments that fit your <span class="hero-title__accent">calendar</span>.
            </h1>
            <p class="hero-lead">
              Browse what we offer — durations and prices update as soon as the merchant publishes changes.
            </p>

            <div class="hero-search-wrap mt-8">
              <v-text-field
                v-model="bookingDate"
                solo
                flat
                hide-details="auto"
                height="56"
                type="date"
                class="hero-search hero-search-field mb-4"
                prepend-inner-icon="event"
                label="Day for your visit"
                aria-label="Preferred booking date"
              />
              <v-text-field
                v-model="search"
                solo
                flat
                hide-details
                clearable
                height="56"
                class="hero-search hero-search-field"
                prepend-inner-icon="search"
                placeholder="Search by service name…"
                aria-label="Search services"
              />
              <router-link
                v-if="showShopBrowseLink"
                :to="shopHomePath"
                class="hero-shop-link text-body-2 font-weight-medium d-inline-flex align-center mt-3"
              >
                <v-icon small class="mr-1" color="secondary">store</v-icon>
                Browse the product shop
              </router-link>
              <div class="hero-meta text-caption text--secondary mt-3">
                <v-icon small color="secondary" class="mr-1">bolt</v-icon>
                Same look as the storefront — pick a service, then book a slot
              </div>
            </div>
          </v-col>

          <v-col
            cols="12"
            md="8"
            :class="heroAdvertUrl ? 'd-flex justify-center mt-8 mt-md-0' : 'd-none d-md-flex justify-center'"
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
      <v-alert v-if="error" type="error" border="left" colored-border prominent class="mb-10 rounded-lg">
        {{ error }}
      </v-alert>
      <v-alert
        v-else-if="!loading && salonEnabled === false"
        type="info"
        border="left"
        colored-border
        prominent
        class="mb-10 rounded-lg"
      >
        This shop is configured as a <strong>normal store</strong> — bookings are disabled. Ask the merchant to switch
        to <strong>Salon</strong> under Admin → Store settings.
      </v-alert>

      <template v-else>
        <div class="section-head d-flex flex-column flex-sm-row align-start align-sm-end mb-8">
          <div>
            <div class="section-kicker">Menu</div>
            <h2 class="section-title">Services</h2>
          </div>
          <div v-if="!loading && services.length" class="section-count text-body-2 text--secondary mt-2 mt-sm-0">
            Showing {{ filteredServices.length }} of {{ services.length }}
          </div>
        </div>

        <v-row v-if="loading" class="service-grid">
          <v-col v-for="n in 6" :key="n" cols="12" md="6" lg="4">
            <v-skeleton-loader type="image" class="rounded-xl skeleton-card" height="220" />
          </v-col>
        </v-row>

        <div
          v-else-if="!filteredServices.length"
          class="empty-state rounded-xl pa-10 pa-md-14 text-center"
        >
          <div class="empty-state__icon-wrap mb-6">
            <v-icon size="44" color="white">spa</v-icon>
          </div>
          <h3 class="empty-state__title mb-2">{{ services.length ? 'No matches' : 'Nothing here yet' }}</h3>
          <p class="empty-state__text text--secondary mb-0">
            {{
              services.length
                ? 'Try another search term.'
                : 'Services the merchant adds in Admin will show up here automatically.'
            }}
          </p>
        </div>

        <v-row v-else class="service-grid">
          <v-col v-for="s in filteredServices" :key="s.id" cols="12" md="6" lg="4">
            <v-hover v-slot="{ hover }">
              <v-card
                class="service-card"
                :class="{ 'service-card--hover': hover }"
                :elevation="hover ? 12 : 2"
                rounded="xl"
              >
                <v-img
                  v-if="serviceCardImage(s)"
                  :src="serviceCardImage(s)"
                  :alt="s.name"
                  height="160"
                  cover
                  class="rounded-t-xl"
                />
                <div v-else class="service-card__placeholder rounded-t-xl d-flex align-center justify-center">
                  <v-icon size="40" color="grey lighten-1">spa</v-icon>
                </div>
                <v-card-title class="service-card__title pb-1">{{ s.name }}</v-card-title>
                <v-card-text class="pt-0 pb-4">
                  <div class="service-card__desc text-body-2 text--secondary mb-3" style="white-space: pre-wrap">
                    {{ s.description }}
                  </div>
                  <div class="d-flex flex-wrap align-center">
                    <v-chip small class="mr-2 mb-2" color="primary" text-color="white">
                      {{ s.durationMinutes }} min
                    </v-chip>
                    <v-chip small class="mb-2" color="secondary" text-color="white">
                      R {{ formatMoney(s.priceZar) }}
                    </v-chip>
                  </div>
                </v-card-text>
                <v-card-actions class="pt-0 px-4 pb-4">
                  <v-spacer />
                  <v-btn
                    color="primary"
            class="product-card__add text-none font-weight-bold"
                    depressed
                    :to="bookRouteFor(s.id)"
                  >
                    Book
                  </v-btn>
                </v-card-actions>
              </v-card>
            </v-hover>
          </v-col>
        </v-row>
      </template>
    </v-container>
  </div>
</template>

<script>
import { fetchShopSettings } from '@/services/publicStore'
import { isSalonAndStoreShopType, isSalonShopType } from '@/services/shopType'
import { fetchSalonServices } from '@/services/salonPublic'

export default {
  name: 'SalonServicesView',
  inject: {
    shopDisplay: {
      default: () => ({ storeName: '', logoUrl: '', heroUrl: '', shopType: 'normal_store' })
    }
  },
  data() {
    const today = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const yyyyMmDd = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
    return {
      bookingDate: yyyyMmDd,
      search: '',
      loading: true,
      error: '',
      salonEnabled: true,
      services: []
    }
  },
  computed: {
    heroAdvertUrl() {
      const u = this.shopDisplay && String(this.shopDisplay.heroUrl || '').trim()
      return u || ''
    },
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    shopHomePath() {
      return { name: 'merchant-home', params: { merchantSlug: this.merchantSlug || 'demo' } }
    },
    showShopBrowseLink() {
      const st = this.shopDisplay && this.shopDisplay.shopType
      return isSalonAndStoreShopType(st)
    },
    filteredServices() {
      const q = String(this.search || '').trim().toLowerCase()
      let list = [...(this.services || [])]
      if (q) {
        list = list.filter((s) => String(s.name || '').toLowerCase().includes(q))
      }
      return list
    }
  },
  async created() {
    const slug = this.merchantSlug
    try {
      const s = await fetchShopSettings(slug)
      if (!isSalonShopType(s.shopType)) {
        await this.$router.replace({ name: 'merchant-home', params: { merchantSlug: slug } })
        this.loading = false
        return
      }
    } catch {
      // continue; fetchSalonServices may still work
    }
    try {
      const res = await fetchSalonServices(slug)
      this.salonEnabled = Boolean(res && res.salonEnabled)
      this.services = (res && res.services) || []
    } catch (e) {
      this.error = e && e.message ? e.message : 'Could not load services.'
    } finally {
      this.loading = false
    }
  },
  methods: {
    serviceCardImage(s) {
      const u = s && s.imageUrl != null ? String(s.imageUrl).trim() : ''
      return u || ''
    },
    formatMoney(v) {
      const n = Number(v)
      if (!Number.isFinite(n)) return String(v || '')
      return n.toFixed(2)
    },
    bookRouteFor(serviceId) {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      return {
        name: 'merchant-salon-book',
        params: { merchantSlug: slug, serviceId: String(serviceId) },
        query: { date: String(this.bookingDate || '').trim() }
      }
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

.hero-search-field >>> .v-input__slot {
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

.hero-shop-link {
  color: rgba(30, 58, 95, 0.9) !important;
  text-decoration: none !important;
}

.hero-shop-link:hover {
  color: #c2410c !important;
  text-decoration: underline !important;
}

.hero-meta {
  display: flex;
  align-items: center;
}

.hero-visual {
  position: relative;
  width: 100%;
  height: 100%;
}

.hero-visual--advert {
  width: min(100%, 100%);
  height: auto;
  min-height: 90%;
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

.service-grid {
  margin-top: -8px;
}

.skeleton-card >>> .v-skeleton-loader__bone {
  border-radius: 20px;
}

.empty-state {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.75), rgba(248, 250, 252, 0.95));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.empty-state__icon-wrap {
  width: 88px;
  height: 88px;
  margin-left: auto;
  margin-right: auto;
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #0f172a 0%, #1e3a5f 55%, #c2410c 100%);
  box-shadow: 0 20px 40px -24px rgba(15, 23, 42, 0.5);
}

.empty-state__title {
  font-size: 1.25rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.empty-state__text {
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}

.service-card {
  border: 1px solid rgba(15, 23, 42, 0.06) !important;
  transition: transform 0.2s ease;
}

.service-card--hover {
  transform: translateY(-2px);
}

.service-card__placeholder {
  height: 160px;
  background: linear-gradient(160deg, #f1f5f9 0%, #e2e8f0 100%);
}

.service-card__title {
  font-size: 1.125rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.service-card__desc {
  line-height: 1.55;
}
</style>
