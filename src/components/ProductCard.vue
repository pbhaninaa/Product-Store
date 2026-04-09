<template>
  <v-hover v-slot="{ hover }">
    <v-card
      class="product-card"
      :class="{ 'product-card--hover': hover }"
      :elevation="hover ? 12 : 2"
      rounded="xl"
    >
      <div class="product-card__media-wrap">
        <v-img
          :src="product.imageUrl"
          height="100%"
          class="product-card__img grey lighten-4"
          gradient="to bottom, rgba(15,23,42,0) 55%, rgba(15,23,42,0.06) 100%"
        >
          <template #placeholder>
            <v-row class="fill-height ma-0" align="center" justify="center">
              <v-progress-circular indeterminate size="32" width="2" color="grey lighten-1" />
            </v-row>
          </template>
        </v-img>
        <div class="product-card__shine" aria-hidden="true" />
      </div>

      <v-card-text class="product-card__body">
        <div class="product-card__name text-truncate">
          {{ product.name }}
        </div>
        <div class="product-card__price-row">
          <span class="product-card__price">{{ formatZar(product.price) }}</span>
        </div>
      </v-card-text>
    </v-card>
  </v-hover>
</template>

<script>
import { formatZar } from '@/utils/price'

export default {
  name: 'ProductCard',
  props: {
    product: { type: Object, required: true }
  },
  methods: {
    formatZar
  }
}
</script>

<style scoped>
.product-card {
  border-radius: 20px !important;
  border: 1px solid rgba(15, 23, 42, 0.06);
  overflow: hidden;
  transition:
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.35s ease;
  will-change: transform;
}

.product-card--hover {
  transform: translateY(-6px);
}

.product-card__media-wrap {
  position: relative;
  aspect-ratio: 4 / 3;
  background: #e2e8f0;
}

.product-card__img {
  position: absolute;
  inset: 0;
}

.product-card__shine {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0;
  background: linear-gradient(
    125deg,
    transparent 40%,
    rgba(255, 255, 255, 0.35) 50%,
    transparent 60%
  );
  transition: opacity 0.4s ease;
}

.product-card--hover .product-card__shine {
  opacity: 1;
}

.product-card__body {
  padding: 18px 20px 20px !important;
}

.product-card__name {
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.35;
}

.product-card__price-row {
  margin-top: 10px;
}

.product-card__price {
  font-size: 0.9375rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  background: linear-gradient(120deg, #c2410c, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

</style>
