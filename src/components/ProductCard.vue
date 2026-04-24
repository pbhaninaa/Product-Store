<template>
  <v-hover v-slot="{ hover }">
    <v-card
      class="product-card"
      :class="{
        'product-card--hover': hover && !isSoldOut,
        'product-card--sold-out': isSoldOut
      }"
      :elevation="hover && !isSoldOut ? 12 : 2"
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
        <div v-if="isSoldOut" class="product-card__sold-overlay" aria-hidden="true" />
        <div v-if="isSoldOut" class="product-card__sold-badge">
          <span class="product-card__sold-badge-text">Sold out</span>
        </div>
      </div>

      <v-card-text class="product-card__body">
        <div class="product-card__name text-truncate">
          {{ product.name }}
        </div>
        <div v-if="categoryLabel" class="product-card__category text-caption text--secondary text-truncate mt-1">
          {{ categoryLabel }}
        </div>
        <div class="product-card__stock text-caption mt-1" :class="stockBadgeClass">
          {{ stockCustomerMessage }}
        </div>
        <div class="product-card__qty-row" @click.stop>
          <v-text-field
            v-model.number="qtyChoice"
            type="number"
            dense
            outlined
            hide-details="auto"
            label="Quantity"
            :min="1"
            :max="maxQtyForCard"
            min-width="15%"
            max-width="15%"
            :disabled="!canAddToCart"
            class="product-card__qty-field rounded-lg"
            @click.stop
            @keydown.stop
            @blur="clampQtyChoice"
          />
        </div>
        <div class="product-card__price-row d-flex align-center justify-space-between flex-wrap">
          <span class="product-card__price" :class="{ 'product-card__price--muted': isSoldOut }">
            {{ formatZar(product.price) }}
          </span>
          <v-btn
            small
            depressed
            color="primary"
            class="product-card__add text-none font-weight-bold"
            :class="{ 'product-card__add--disabled': !canAddToCart }"
            :disabled="!canAddToCart"
            :aria-disabled="!canAddToCart"
            @click.stop="addWithQty"
          >
            <v-icon left small :color="canAddToCart ? 'white' : 'grey lighten-2'">add_shopping_cart</v-icon>
            {{ addButtonLabel }}
          </v-btn>
        </div>
      </v-card-text>
    </v-card>
  </v-hover>
</template>

<script>
import { formatZar } from '@/utils/price'
import { addToCart, MAX_LINE_QUANTITY } from '@/services/cart'

export default {
  name: 'ProductCard',
  props: {
    product: { type: Object, required: true }
  },
  data() {
    return {
      qtyChoice: 1
    }
  },
  computed: {
    maxQtyForCard() {
      if (this.stockNum == null) return MAX_LINE_QUANTITY
      return Math.min(MAX_LINE_QUANTITY, this.stockNum)
    },
    categoryLabel() {
      const c = this.product && this.product.category
      if (c == null || String(c).trim() === '') return 'Uncategorized'
      return String(c).trim()
    },
    stockNum() {
      const s = this.product.stock
      if (s == null || s === '') return null
      const n = parseInt(String(s), 10)
      return Number.isFinite(n) ? n : null
    },
    isSoldOut() {
      return this.stockNum !== null && this.stockNum <= 0
    },
    stockCustomerMessage() {
      if (this.stockNum == null) return 'Availability not shown — you can still try to add.'
      if (this.isSoldOut) return 'This item is sold out right now.'
      return `${this.stockNum} available`
    },
    stockBadgeClass() {
      if (this.stockNum == null) return 'text--secondary'
      if (this.isSoldOut) return 'product-card__stock--out font-weight-semibold'
      return 'text--secondary'
    },
    canAddToCart() {
      if (this.stockNum == null) return true
      return this.stockNum > 0
    },
    addButtonLabel() {
      return this.canAddToCart ? 'Add to cart' : 'Sold out'
    }
  },
  watch: {
    product() {
      this.qtyChoice = 1
    }
  },
  methods: {
    formatZar,
    clampQtyChoice() {
      let q = parseInt(String(this.qtyChoice), 10)
      if (!Number.isFinite(q) || q < 1) q = 1
      const m = this.maxQtyForCard
      if (q > m) q = m
      this.qtyChoice = q
    },
    addWithQty() {
      if (!this.canAddToCart) return
      this.clampQtyChoice()
      addToCart(this.product, this.qtyChoice)
      this.qtyChoice = 1
    }
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

.product-card--sold-out {
  opacity: 0.97;
}

.product-card--sold-out.product-card--hover {
  transform: none;
}

.product-card__media-wrap {
  position: relative;
  aspect-ratio: 4 / 3;
  background: #e2e8f0;
}

.product-card__sold-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: rgba(15, 23, 42, 0.42);
  pointer-events: none;
}

.product-card__sold-badge {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 3;
  transform: translate(-50%, -50%);
  pointer-events: none;
  padding: 10px 20px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(185, 28, 28, 0.25);
  box-shadow: 0 12px 28px -8px rgba(15, 23, 42, 0.35);
}

.product-card__sold-badge-text {
  font-size: 0.8125rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #b91c1c;
}

.product-card--sold-out .product-card__img {
  filter: grayscale(0.25) brightness(0.92);
}

.product-card--sold-out .product-card__shine {
  opacity: 0 !important;
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

.font-weight-semibold {
  font-weight: 600 !important;
}

.product-card__stock--out {
  color: #b91c1c !important;
}

.product-card__price--muted {
  opacity: 0.55;
}

.product-card__qty-row {
  margin-top: 10px;
}

.product-card__qty-field {
  max-width: 100%;
}

.product-card__qty-field >>> .v-input__slot {
  min-height: 40px !important;
}

.product-card__price-row {
  margin-top: 10px;
  gap: 8px;
}

.product-card__add {
  border-radius: 12px !important;
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  box-shadow: 0 6px 16px -8px rgba(194, 65, 12, 0.55);
}

.product-card__add--disabled {
  background: #94a3b8 !important;
  color: rgba(255, 255, 255, 0.92) !important;
  box-shadow: none !important;
  opacity: 1;
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
