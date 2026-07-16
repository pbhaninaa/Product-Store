<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="d-flex mb-3">
        <div class="card-label mb-0">Recent orders (platform)</div>
        <v-spacer />
        <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-data-table :headers="headers" :items="orders" :items-per-page="20" class="elevation-0">
        <template v-slot:[`item.link`]="{ item }">
          <router-link
            v-if="item.tenantSlug"
            :to="{ name: 'merchant-admin-orders', params: { merchantSlug: item.tenantSlug } }"
          >
            Open
          </router-link>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportOrders } from '@/services/supportApi'

export default {
  name: 'SupportOrdersView',
  data() {
    return {
      loading: false,
      error: '',
      orders: [],
      headers: [
        { text: 'Merchant', value: 'tenantName' },
        { text: 'Customer', value: 'customerName' },
        { text: 'Status', value: 'status' },
        { text: 'Total', value: 'totalZar' },
        { text: 'Created', value: 'createdAt' },
        { text: '', value: 'link', sortable: false }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await fetchSupportOrders()
        this.orders = (res && res.orders) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load orders'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
