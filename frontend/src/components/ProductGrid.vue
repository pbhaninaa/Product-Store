<template>
  <div>
    <v-row v-if="loading" class="product-grid card-grid">
      <v-col v-for="n in 8" :key="n" cols="12" sm="6" md="4" lg="3">
        <!-- Sized like a real card (4:3 image + text + actions) so the grid does not jump when data arrives. -->
        <v-skeleton-loader
          type="image, list-item-two-line, actions"
          class="rounded-xl skeleton-card equal-card"
        />
      </v-col>
    </v-row>

    <empty-state
      v-else-if="products.length === 0"
      icon="inventory_2"
      title="Nothing here yet"
      text="Products you add in Admin will show up here automatically."
    />

    <v-row v-else class="product-grid card-grid">
      <v-col v-for="p in products" :key="p.id" cols="12" sm="6" md="4" lg="3">
        <ProductCard :product="p" />
      </v-col>
    </v-row>
  </div>
</template>

<script>
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/layout/EmptyState.vue'

export default {
  name: 'ProductGrid',
  components: { ProductCard, EmptyState },
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

.skeleton-card {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
}

.skeleton-card >>> .v-skeleton-loader__image {
  height: auto;
  aspect-ratio: 4 / 3;
  border-radius: 0;
}
</style>
