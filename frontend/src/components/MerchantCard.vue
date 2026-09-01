<template>
  <v-card
    class="merchant-card rounded-xl"
    :class="{ 'merchant-card--selected': selected }"
    outlined
    :elevation="selected ? 8 : 2"
    role="button"
    tabindex="0"
    @click="$emit('select', merchant)"
    @keydown.enter="$emit('select', merchant)"
  >
    <v-card-text class="pa-4">
      <div class="d-flex align-start">
        <v-avatar size="48" class="mr-3 flex-shrink-0" color="grey lighten-3">
          <img v-if="logoSrc" :src="logoSrc" alt="" />
          <v-icon v-else color="secondary">{{ kind === 'salon' ? 'content_cut' : 'storefront' }}</v-icon>
        </v-avatar>
        <div class="flex-grow-1 min-width-0">
          <div class="d-flex align-center flex-wrap">
            <div class="text-subtitle-1 font-weight-bold text-truncate mr-2">{{ merchant.storeName }}</div>
            <v-chip v-if="selected" x-small color="primary" text-color="white" class="text-none">Selected</v-chip>
          </div>
          <div class="d-flex align-center mt-1 flex-wrap">
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
            <span class="text-body-2 font-weight-medium">{{ ratingLabel }}</span>
            <span class="text-caption text--secondary ml-1">({{ reviewCount }})</span>
            <span v-if="merchant.distanceKm != null" class="text-caption text--secondary ml-3">
              {{ merchant.distanceKm }} km away
            </span>
          </div>
        </div>
      </div>
      <div v-if="displayOfferings.length" class="text-caption text--secondary mt-3 mb-1">
        {{ kind === 'salon' ? 'Services' : 'Products' }}
      </div>
      <ul class="pl-4 mb-0 merchant-card__list">
        <li v-for="o in displayOfferings" :key="o.id">
          {{ o.name }} — R {{ o.priceZar }}
        </li>
      </ul>
      <p v-if="offerings.length > 4" class="text-caption text--secondary mb-0 mt-1">
        +{{ offerings.length - 4 }} more
      </p>
      <v-btn
        block
        depressed
        color="primary"
        class="mt-4 text-none font-weight-bold btn-amber"
        @click.stop="$emit('choose', merchant)"
      >
        Choose this {{ kind === 'salon' ? 'salon' : 'shop' }}
      </v-btn>
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
    kind: { type: String, default: 'shop' }
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
    ratingLabel() {
      if (!this.reviewCount) return 'New'
      return this.rating.toFixed(1)
    },
    offerings() {
      return Array.isArray(this.merchant && this.merchant.offerings) ? this.merchant.offerings : []
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
.merchant-card__list {
  font-size: 13px;
}
</style>
