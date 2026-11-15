<template>
  <v-row v-if="loading" class="card-grid">
    <v-col v-for="n in 3" :key="n" cols="12" sm="6" lg="4">
      <v-skeleton-loader type="image, list-item-three-line, actions" class="rounded-xl equal-card" />
    </v-col>
  </v-row>

  <div v-else-if="!services.length" class="store-empty rounded-xl pa-8 text-center">
    <v-icon size="40" color="grey lighten-1">spa</v-icon>
    <p class="text-body-1 font-weight-medium mt-3 mb-1">{{ emptyTitle }}</p>
    <p class="text-body-2 text--secondary mb-0">{{ emptyText }}</p>
  </div>

  <v-row v-else class="card-grid">
    <v-col v-for="s in services" :key="s.id" cols="12" sm="6" lg="4" class="d-flex">
      <v-card class="service-card equal-card" rounded="xl" elevation="2">
        <v-img v-if="image(s)" :src="image(s)" :alt="s.name" :aspect-ratio="16 / 9" cover class="rounded-t-xl flex-grow-0" />
        <div v-else class="service-card__placeholder rounded-t-xl d-flex align-center justify-center">
          <v-icon size="40" color="grey lighten-1">spa</v-icon>
        </div>
        <v-card-title class="pb-1">
          <span class="clamp-2" :title="s.name">{{ s.name }}</span>
        </v-card-title>
        <v-card-text class="pt-0 pb-2 equal-card__body">
          <div v-if="s.description" class="text-body-2 text--secondary clamp-3 mb-3">{{ s.description }}</div>
          <div class="d-flex flex-wrap align-center">
            <v-chip v-if="duration(s.durationMinutes)" small class="mr-2 mb-2" color="primary" text-color="white">
              {{ duration(s.durationMinutes) }}
            </v-chip>
            <v-chip small class="mb-2" color="secondary" text-color="white">R {{ money(s.priceZar) }}</v-chip>
          </div>
        </v-card-text>
        <v-card-actions class="px-4 pb-4 pt-0">
          <v-spacer />
          <v-btn
            color="primary"
            depressed
            class="text-none font-weight-bold"
            :to="{ name: 'merchant-salon-book', params: { merchantSlug, serviceId: String(s.id) } }"
          >
            Book
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-col>
  </v-row>
</template>

<script>
import { resolveMediaUrl } from '@/services/api'

export default {
  name: 'StoreServiceGrid',
  props: {
    services: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    merchantSlug: { type: String, required: true },
    emptyTitle: { type: String, default: 'No services yet' },
    emptyText: { type: String, default: 'Services this provider adds will show up here.' }
  },
  methods: {
    image(s) {
      return resolveMediaUrl(s && s.imageUrl != null ? String(s.imageUrl).trim() : '')
    },
    money(v) {
      const n = Number(v)
      return Number.isFinite(n) ? n.toFixed(2) : String(v || '')
    },
    duration(minutes) {
      const m = Number(minutes)
      if (!Number.isFinite(m) || m <= 0) return ''
      if (m < 60) return `${m} min`
      const h = Math.floor(m / 60)
      const rest = m % 60
      return rest ? `${h} h ${rest} min` : `${h} hour${h === 1 ? '' : 's'}`
    }
  }
}
</script>

<style scoped>
.service-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.service-card__placeholder {
  aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, #f1f5f9, #e2e8f0);
}

.store-empty {
  background: rgba(255, 255, 255, 0.7);
  border: 1px dashed rgba(15, 23, 42, 0.15);
}
</style>
