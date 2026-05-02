<template>
  <div>
    <v-row v-if="loading" class="product-grid">
      <v-col v-for="n in 8" :key="n" cols="12" sm="6" md="4" lg="3">
        <v-skeleton-loader
          type="image"
          class="rounded-xl skeleton-card"
          height="220"
        />
      </v-col>
    </v-row>

    <div v-else-if="products.length === 0" class="empty-state rounded-xl pa-10 pa-md-14 text-center">
      <div class="empty-state__icon-wrap mb-6">
        <v-icon size="44" color="white">inventory_2</v-icon>
      </div>
      <h3 class="empty-state__title mb-2">Nothing here yet</h3>
      <p class="empty-state__text text--secondary mb-0">
        Products you add in Admin will show up here automatically.
      </p>
    </div>

    <v-row v-else class="product-grid">
      <v-col v-for="p in products" :key="p.id" cols="12" sm="6" md="4" lg="3">
        <ProductCard :product="p" />
      </v-col>
    </v-row>
  </div>
</template>

<script>
import ProductCard from '@/components/ProductCard.vue'

export default {
  name: 'ProductGrid',
  components: { ProductCard },
  props: {
    products: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  }
}
</script>

<style scoped>
.product-grid {
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
</style>
