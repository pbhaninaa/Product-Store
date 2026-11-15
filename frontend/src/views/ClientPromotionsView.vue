<template>
  <v-container class="py-8 px-3 px-sm-4">
    <h1 class="text-h4 font-weight-bold mb-2">Promotions</h1>
    <p class="text-body-2 text--secondary mb-6">
      Active discounts from this store. You don’t need a code — the best offer for your cart or booking is applied
      automatically at checkout.
    </p>
    <v-alert v-if="error" type="error" dense outlined class="mb-6">{{ error }}</v-alert>

    <v-row v-if="loading" class="card-grid">
      <v-col v-for="n in 3" :key="n" cols="12" sm="6" lg="4">
        <v-skeleton-loader type="list-item-three-line, list-item" class="rounded-xl equal-card promo-skeleton" />
      </v-col>
    </v-row>

    <v-row v-else-if="items.length" class="card-grid">
      <v-col v-for="p in items" :key="p.id" cols="12" sm="6" lg="4">
        <v-card class="promo-card equal-card pa-4 rounded-xl" outlined>
          <div class="equal-card__body">
            <div class="d-flex align-start mb-2">
              <v-icon color="primary" class="mr-2 mt-n1 flex-shrink-0">local_offer</v-icon>
              <div class="font-weight-bold clamp-2" :title="p.title">{{ p.title }}</div>
            </div>
            <div v-if="p.description" class="text-body-2 text--secondary clamp-3 mb-3" :title="p.description">
              {{ p.description }}
            </div>
            <div class="equal-card__actions">
              <div class="promo-card__value font-weight-bold">{{ discountLabel(p) }}</div>
              <div class="text-caption text--secondary">
                <template v-if="p.minimumOrderValue">Min. spend R{{ p.minimumOrderValue }} · </template>
                {{ p.startDate }} to {{ p.endDate }}
              </div>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <p v-else-if="!error" class="text-body-2 text--secondary">No promotions running today.</p>
  </v-container>
</template>

<script>
import { apiFetch } from '@/services/api'

export default {
  name: 'ClientPromotionsView',
  data() {
    return { items: [], error: '', loading: true }
  },
  async created() {
    const slug = String(this.$route.params.merchantSlug || '').trim()
    try {
      const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/promotions`)
      this.items = Array.isArray(res) ? res : []
    } catch (e) {
      this.error = (e && e.message) || 'Could not load promotions.'
    } finally {
      this.loading = false
    }
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
  border: 1px solid rgba(15, 23, 42, 0.08) !important;
}

.promo-card__value {
  color: #c2410c;
}

.promo-skeleton {
  background: #fff;
}
</style>
