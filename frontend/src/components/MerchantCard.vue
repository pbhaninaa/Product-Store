<template>
  <v-card
    class="merchant-card equal-card rounded-xl"
    :class="{ 'merchant-card--selected': selected }"
    outlined
    :elevation="selected ? 8 : 2"
    role="button"
    tabindex="0"
    @click="$emit('select', merchant)"
    @keydown.enter="$emit('select', merchant)"
  >
    <v-card-text class="pa-4 equal-card__body">
      <div class="d-flex align-start">
        <v-avatar size="48" class="mr-3 flex-shrink-0" color="grey lighten-3">
          <img v-if="logoSrc" :src="logoSrc" alt="" />
          <v-icon v-else color="secondary">{{ kind === 'salon' ? 'event_available' : 'storefront' }}</v-icon>
        </v-avatar>
        <div class="flex-grow-1 min-width-0">
          <div class="d-flex align-center flex-wrap">
            <div class="text-subtitle-1 font-weight-bold clamp-1 mr-2" :title="merchant.storeName">{{ merchant.storeName }}</div>
            <v-chip v-if="selected" x-small color="primary" text-color="white" class="text-none">Selected</v-chip>
          </div>
          <div class="d-flex align-center mt-1 flex-wrap" :aria-label="ratingAria">
            <v-rating
              :value="rating"
              background-color="grey lighten-1"
              color="amber darken-2"
              dense
              half-increments
              readonly
              size="16"
              class="mr-1"
            />
            <template v-if="reviewCount">
              <span class="text-body-2 font-weight-bold">{{ rating.toFixed(1) }}</span>
              <span class="text-caption text--secondary ml-1">
                avg · {{ reviewCount }} review{{ reviewCount === 1 ? '' : 's' }}
              </span>
            </template>
            <span v-else class="text-caption text--secondary">No reviews yet</span>
          </div>
          <!-- Distance: always shown in the directory; elsewhere only when known -->
          <div v-if="distanceLabel || showDistance" class="d-flex align-center mt-1 text-caption">
            <v-icon x-small class="mr-1" :color="distanceLabel ? 'primary' : 'grey'">place</v-icon>
            <span v-if="distanceLabel" class="font-weight-medium">{{ distanceLabel }}</span>
            <span v-else-if="located" class="text--secondary">Location not set</span>
            <a v-else href="#" class="merchant-card__locate" @click.prevent.stop="$emit('locate')">Show distance</a>
          </div>
          <v-chip
            v-if="offerCount > 0"
            x-small
            label
            color="deep-orange lighten-5"
            text-color="deep-orange darken-3"
            class="mt-2 font-weight-bold"
          >
            <v-icon left x-small>local_offer</v-icon>
            {{ offerCount }} {{ offerCount === 1 ? 'offer' : 'offers' }} running
          </v-chip>
        </div>
      </div>
      <div v-if="displayOfferings.length" class="text-caption text--secondary mt-3 mb-1">
        {{ kind === 'salon' ? 'Services' : 'Items sold' }}
      </div>
      <ul class="pl-4 mb-0 merchant-card__list">
        <li v-for="o in displayOfferings" :key="o.id" class="text-truncate" :title="o.name">
          {{ o.name }} · R {{ o.priceZar }}
        </li>
      </ul>
      <p v-if="offeringTotal > 4" class="text-caption text--secondary mb-0 mt-1">
        +{{ offeringTotal - 4 }} more
      </p>
      <div class="equal-card__actions pt-3 d-flex justify-end">
        <v-btn
          small
          depressed
          rounded
          color="primary"
          class="text-none font-weight-bold btn-amber merchant-card__choose px-4"
          @click.stop="$emit('choose', merchant)"
        >
          Choose provider
          <v-icon right small>arrow_forward</v-icon>
        </v-btn>
      </div>
    </v-card-text>
  </v-card>
</template>

<script>
import { resolveMediaUrl } from '@/services/api'

export default {
  name: 'MerchantCard',
  props: {
    merchant: { type: Object, required: true },
    selected: { type: Boolean, default: false },
    kind: { type: String, default: 'shop' },
    /** Always show the distance line (provider directory). */
    showDistance: { type: Boolean, default: false },
    /** Whether the visitor's location is known; without it the line offers "Show distance". */
    located: { type: Boolean, default: false }
  },
  computed: {
    logoSrc() {
      return resolveMediaUrl(this.merchant && this.merchant.logoUrl)
    },
    rating() {
      const n = Number(this.merchant && this.merchant.averageRating)
      return Number.isFinite(n) ? n : 0
    },
    reviewCount() {
      return Number(this.merchant && this.merchant.reviewCount) || 0
    },
    ratingAria() {
      if (!this.reviewCount) return 'No reviews yet'
      return `Average rating ${this.rating.toFixed(1)} out of 5 from ${this.reviewCount} reviews`
    },
    /** "850 m away" under 1 km, otherwise "12.4 km away"; empty when unknown. */
    distanceLabel() {
      const km = this.merchant && this.merchant.distanceKm
      if (km == null || !Number.isFinite(Number(km))) return ''
      const n = Number(km)
      if (n < 1) return `${Math.max(10, Math.round((n * 1000) / 10) * 10)} m away`
      return `${n < 10 ? n.toFixed(1) : Math.round(n)} km away`
    },
    offerings() {
      return Array.isArray(this.merchant && this.merchant.offerings) ? this.merchant.offerings : []
    },
    offerCount() {
      return Number(this.merchant && this.merchant.activePromotionCount) || 0
    },
    /** The public directory sends a short preview plus the full count. */
    offeringTotal() {
      const n = Number(this.merchant && this.merchant.offeringCount)
      return Number.isFinite(n) ? n : this.offerings.length
    },
    displayOfferings() {
      return this.offerings.slice(0, 4)
    }
  }
}
</script>

<style scoped>
.merchant-card {
  cursor: pointer;
  border: 1px solid rgba(15, 23, 42, 0.1);
}
.merchant-card--selected {
  border-color: #c2410c;
  box-shadow: 0 0 0 2px rgba(194, 65, 12, 0.25);
}
.merchant-card__locate {
  color: #c2410c;
  font-weight: 600;
  text-decoration: none;
}
.merchant-card__locate:hover {
  text-decoration: underline;
}
.merchant-card__list {
  font-size: 13px;
  min-width: 0;
}

/* Full-width action, pinned to the bottom of every card in the row. */
.merchant-card__choose.btn-amber {
  max-width: none;
}
</style>
