<template>
  <div>
    <v-row v-if="loading" class="product-grid">
      <v-col v-for="n in 8" :key="n" cols="6" sm="6" md="4" lg="3">
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
      <v-col v-for="p in products" :key="p.id" cols="6" sm="6" md="4" lg="3">
        <div class="product-card-wrapper" @click="selectProduct(p)">
          <ProductCard :product="p" />
        </div>
      </v-col>
    </v-row>

    <!-- Product Detail Dialog -->
    <v-dialog v-model="showDetailDialog" max-width="600px" scrollable>
      <v-card v-if="selectedProduct" class="product-detail-card">
        <v-card-title class="d-flex align-center justify-space-between pa-6">
          <span>Product Details</span>
          <v-btn icon small @click="showDetailDialog = false">
            <v-icon>close</v-icon>
          </v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text class="pa-6">
          <div class="detail-image-wrap mb-6">
            <v-img
              :src="selectedProduct.imageUrl"
              height="300"
              class="rounded-lg"
              contain
            >
              <template #placeholder>
                <v-row class="fill-height ma-0" align="center" justify="center">
                  <v-progress-circular indeterminate size="32" width="2" color="grey lighten-1" />
                </v-row>
              </template>
            </v-img>
          </div>
          
          <div class="detail-name mb-2">
            <h2>{{ selectedProduct.name }}</h2>
          </div>
          
          <div class="detail-category text-caption text--secondary mb-4">
            {{ categoryLabel }}
          </div>
          
          <div class="detail-price mb-4">
            <span class="text-h5 font-weight-bold">{{ formatZar(selectedProduct.price) }}</span>
          </div>
          
          <div class="detail-stock mb-6" :class="stockBadgeClass">
            {{ stockCustomerMessage }}
          </div>
          
          <v-divider class="mb-6" />
          
          <div v-if="selectedProduct.description" class="detail-description mb-6">
            <h4 class="mb-2">Description</h4>
            <p class="text--secondary">{{ selectedProduct.description }}</p>
          </div>

          <div class="detail-actions d-flex gap-3">
            <v-text-field
              v-model.number="detailQtyChoice"
              type="number"
              dense
              outlined
              label="Quantity"
              :min="1"
              :max="maxQtyForDetail"
              class="flex-shrink-1"
              style="max-width: 120px"
              @blur="clampDetailQtyChoice"
            />
            <v-btn
              color="primary"
              class="flex-grow-1 text-none font-weight-bold"
              :disabled="!canAddToCart"
              large
              @click="addToCartFromDetail"
            >
              <v-icon left>add_shopping_cart</v-icon>
              {{ addButtonLabel }}
            </v-btn>
          </div>
        </v-card-text>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import ProductCard from '@/components/ProductCard.vue'
import { formatZar } from '@/utils/price'
import { addToCart, MAX_LINE_QUANTITY } from '@/services/cart'

export default {
  name: 'ProductGrid',
  components: { ProductCard },
  props: {
    products: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  },
  data() {
    return {
      showDetailDialog: false,
      selectedProduct: null,
      detailQtyChoice: 1
    }
  },
  computed: {
    maxQtyForDetail() {
      if (this.stockNum == null) return MAX_LINE_QUANTITY
      return Math.min(MAX_LINE_QUANTITY, this.stockNum)
    },
    categoryLabel() {
      const c = this.selectedProduct && this.selectedProduct.category
      if (c == null || String(c).trim() === '') return 'Uncategorized'
      return String(c).trim()
    },
    stockNum() {
      const s = this.selectedProduct && this.selectedProduct.stock
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
      if (this.isSoldOut) return 'detail-stock--out font-weight-semibold'
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
    showDetailDialog(newVal) {
      if (!newVal) {
        this.selectedProduct = null
        this.detailQtyChoice = 1
      }
    }
  },
  methods: {
    formatZar,
    selectProduct(product) {
      this.selectedProduct = product
      this.detailQtyChoice = 1
      this.showDetailDialog = true
    },
    clampDetailQtyChoice() {
      let q = parseInt(String(this.detailQtyChoice), 10)
      if (!Number.isFinite(q) || q < 1) q = 1
      const m = this.maxQtyForDetail
      if (q > m) q = m
      this.detailQtyChoice = q
    },
    addToCartFromDetail() {
      if (!this.canAddToCart) return
      this.clampDetailQtyChoice()
      addToCart(this.selectedProduct, this.detailQtyChoice)
      this.showDetailDialog = false
    }
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

.product-card-wrapper {
  cursor: pointer;
  transition: transform 0.2s ease;
  height: 100%;
}

.product-card-wrapper:active {
  transform: scale(0.98);
}

.product-detail-card {
  border-radius: 20px !important;
}

.detail-image-wrap {
  width: 100%;
}

.detail-name h2 {
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.detail-category {
  display: block;
}

.detail-stock {
  display: inline-block;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.05);
  font-size: 0.875rem;
}

.detail-stock--out {
  color: #b91c1c;
  background: rgba(185, 28, 28, 0.1);
}

.detail-description p {
  font-size: 0.95rem;
  line-height: 1.6;
}

.detail-actions {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}
</style>
