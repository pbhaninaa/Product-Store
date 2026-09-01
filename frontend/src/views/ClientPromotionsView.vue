<template>
  <v-container class="py-10 px-3 px-sm-4">
    <h1 class="text-h4 font-weight-bold mb-2">Promotions</h1>
    <p class="text-body-2 text--secondary mb-6">Active discounts from this store.</p>
    <v-alert v-if="error" type="error" dense outlined>{{ error }}</v-alert>
    <v-card v-for="p in items" :key="p.id" class="pa-4 rounded-xl mb-4" outlined>
      <div class="font-weight-bold">{{ p.title }}</div>
      <div class="text-body-2">{{ p.description }}</div>
      <div class="text-caption text--secondary mt-2">
        {{ p.discountType === 'PERCENTAGE' ? `${p.discountValue}% off` : `R${p.discountValue} off` }}
        · {{ p.startDate }} to {{ p.endDate }}
      </div>
    </v-card>
    <p v-if="!items.length && !error" class="text-body-2 text--secondary">No promotions running today.</p>
  </v-container>
</template>

<script>
import { apiFetch } from '@/services/api'

export default {
  name: 'ClientPromotionsView',
  data() {
    return { items: [], error: '' }
  },
  async created() {
    const slug = String(this.$route.params.merchantSlug || '').trim()
    try {
      const res = await apiFetch(`/api/public/m/${encodeURIComponent(slug)}/promotions`)
      this.items = Array.isArray(res) ? res : []
    } catch (e) {
      this.error = (e && e.message) || 'Could not load promotions.'
    }
  }
}
</script>
