<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Recent orders (platform)</div>
        <div class="sc-toolbar__end">
          <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        </div>
      </div>
      <p v-if="!canShadow" class="text-caption text--secondary mb-3">
        Opening a store's orders needs the <strong>Use shadow help</strong> permission. Ask a platform admin to grant it.
      </p>
      <v-data-table
        :headers="headers"
        :items="orders"
        :items-per-page="20"
        :loading="loading"
        loading-text="Loading orders…"
        class="elevation-0"
      >
        <template v-slot:[`item.status`]="{ item }">{{ orderStatusLabel(item.status) }}</template>
        <template v-slot:[`item.totalZar`]="{ item }">{{ formatZar(item.totalZar) }}</template>
        <template v-slot:[`item.createdAt`]="{ item }">{{ formatDateTime(item.createdAt) }}</template>
        <template v-slot:[`item.link`]="{ item }">
          <v-btn
            v-if="item.tenantSlug && canShadow"
            small
            text
            color="primary"
            class="text-none"
            :loading="opening === item.tenantSlug"
            :disabled="Boolean(opening)"
            @click="open(item)"
          >
            Open
          </v-btn>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportOrders } from '@/services/supportApi'
import { formatDateTime, orderStatusLabel } from '@/utils/displayLabels'
import { formatZar } from '@/utils/price'
import { confirmShadowEntry, openMerchantAdminAsShadow, shadowErrorMessage } from './shadowNavigation'

export default {
  name: 'SupportOrdersView',
  inject: {
    supportDialog: { default: null },
    supportAccess: { default: null }
  },
  data() {
    return {
      loading: false,
      opening: '',
      error: '',
      orders: [],
      headers: [
        { text: 'Merchant', value: 'tenantName' },
        { text: 'Customer', value: 'customerName' },
        { text: 'Status', value: 'status' },
        { text: 'Total', value: 'totalZar' },
        { text: 'Created', value: 'createdAt' },
        { text: '', value: 'link', sortable: false, align: 'end' }
      ]
    }
  },
  computed: {
    canShadow() {
      return Boolean(this.supportAccess && this.supportAccess.can('USE_SHADOW'))
    }
  },
  created() {
    this.load()
  },
  methods: {
    formatDateTime,
    formatZar,
    orderStatusLabel,
    async load() {
      this.loading = true
      try {
        const res = await fetchSupportOrders()
        this.orders = (res && res.orders) || []
      } catch (e) {
        if (e && e.status === 403) {
          this.error = 'You do not have permission to view platform orders.'
        } else {
          this.error = (e && e.message) || 'Could not load orders'
        }
      } finally {
        this.loading = false
      }
    },
    async open(item) {
      if (!(await confirmShadowEntry(this.supportDialog, item.tenantName || item.tenantSlug))) return
      this.opening = item.tenantSlug
      this.error = ''
      try {
        await openMerchantAdminAsShadow(this.$router, item.tenantSlug, 'merchant-admin-orders')
      } catch (e) {
        this.error = shadowErrorMessage(e)
      } finally {
        this.opening = ''
      }
    }
  }
}
</script>
