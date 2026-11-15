<template>
  <v-row v-if="loading" class="card-grid">
    <v-col v-for="n in 3" :key="n" cols="12" sm="6" lg="4">
      <v-skeleton-loader type="list-item-three-line, list-item" class="rounded-xl equal-card" />
    </v-col>
  </v-row>

  <div v-else-if="!items.length" class="store-empty rounded-xl pa-8 text-center">
    <v-icon size="40" color="grey lighten-1">local_offer</v-icon>
    <p class="text-body-1 font-weight-medium mt-3 mb-1">No promotions running today</p>
    <p class="text-body-2 text--secondary mb-0">Check back soon for discounts from this store.</p>
  </div>

  <v-row v-else class="card-grid">
    <v-col v-for="p in items" :key="p.id" cols="12" sm="6" lg="4" class="d-flex">
      <v-card class="promo-card equal-card pa-4 rounded-xl" outlined>
        <div class="equal-card__body">
          <div class="d-flex align-start mb-2">
            <v-icon color="primary" class="mr-2 mt-n1 flex-shrink-0">local_offer</v-icon>
            <div class="font-weight-bold clamp-2" :title="p.title">{{ p.title }}</div>
          </div>
          <div v-if="p.description" class="text-body-2 text--secondary clamp-3 mb-3" :title="p.description">
            {{ p.description }}
          </div>
        </div>
        <div class="equal-card__actions">
          <div class="promo-card__value font-weight-bold">{{ discountLabel(p) }}</div>
          <div class="text-caption text--secondary">
            <template v-if="p.minimumOrderValue">Min. spend R{{ p.minimumOrderValue }} · </template>
            {{ p.startDate }} to {{ p.endDate }}
          </div>
        </div>
      </v-card>
    </v-col>
  </v-row>
</template>

<script>
export default {
  name: 'StorePromotionGrid',
  props: {
    items: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  },
  methods: {
    discountLabel(p) {
      return p.discountType === 'PERCENTAGE' ? `${p.discountValue}% off` : `R${p.discountValue} off`
    }
  }
}
</script>

<style scoped>
.promo-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(15, 23, 42, 0.08) !important;
}

.promo-card__value {
  color: #c2410c;
}

.store-empty {
  background: rgba(255, 255, 255, 0.7);
  border: 1px dashed rgba(15, 23, 42, 0.15);
}
</style>
