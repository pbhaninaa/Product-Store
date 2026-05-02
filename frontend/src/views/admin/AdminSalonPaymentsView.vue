<template>
  <div>
    <v-alert v-if="user && !merchantIsSalon" type="warning" outlined class="rounded-lg mb-6">
      Payments for salon checkout are available when the store type is <strong>Salon + store</strong> or
      <strong>Salon only</strong>. Update this under <strong>Store</strong>.
    </v-alert>

    <template v-else-if="user">
      <v-card class="admin-card salon-pay-hero pa-4 pa-md-5 mb-4" elevation="3" rounded="xl">
        <div class="d-flex flex-column flex-md-row align-start">
          <v-avatar color="teal" size="48" class="mb-3 mb-md-0 mr-md-4">
            <v-icon dark>payments</v-icon>
          </v-avatar>
          <div>
            <div class="text-h6 font-weight-bold mb-1">Checkout &amp; payments</div>
            <p class="text-body-2 text--secondary mb-0">
              Product-Store records <strong>order payments</strong> (EFT and cash in store). Confirm receipts on
              <strong>Orders</strong>; keep payout banking under <strong>Store</strong> for checkout.
            </p>
          </div>
        </div>
      </v-card>

      <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
        <div class="d-flex flex-column flex-sm-row align-start align-sm-center mb-4">
          <div class="card-label mb-0">Recent orders</div>
          <v-spacer />
          <div class="d-flex flex-wrap">
            <v-btn
              depressed
              outlined
              color="primary"
              class="text-none font-weight-bold mr-2 mb-2"
              :loading="ordersPayLoading"
              @click="loadPaymentOrders"
            >
              <v-icon left small>refresh</v-icon>
              Refresh
            </v-btn>
            <v-btn depressed color="primary" class="text-none font-weight-bold mb-2 btn-amber" @click="exportPaymentsCsv">
              <v-icon left small color="white">download</v-icon>
              Export CSV
            </v-btn>
          </div>
        </div>
        <v-text-field
          v-model.trim="paySearch"
          dense
          outlined
          hide-details
          clearable
          label="Search client or reference"
          prepend-inner-icon="search"
          class="rounded-lg mb-4"
          style="max-width: 360px"
        />
        <v-data-table
          :headers="payHeaders"
          :items="payRowsFiltered"
          :items-per-page="10"
          class="rounded-lg elevation-0 salon-data-table"
          :loading="ordersPayLoading"
          no-data-text="No orders loaded yet."
        >
          <template v-slot:[`item.totalZar`]="{ item }">
            R {{ formatMoney(item.totalZar) }}
          </template>
          <template v-slot:[`item.status`]="{ item }">
            <v-chip small label dark :color="paymentStatusColor(item.status)">{{ item.status }}</v-chip>
          </template>
          <template v-slot:[`item.createdAt`]="{ item }">
            {{ formatOrderWhen(item.createdAt) }}
          </template>
        </v-data-table>
        <div class="d-flex flex-wrap justify-end mt-4">
          <v-btn text color="primary" class="text-none font-weight-bold" :to="ordersAdminLink">Open orders</v-btn>
          <v-btn text color="primary" class="text-none font-weight-bold" :to="storeAdminLink">Store &amp; banking</v-btn>
        </div>
      </v-card>
    </template>
  </div>
</template>

<script>
import { fetchAdminStoreSettings, fetchAdminOrders } from '@/services/adminApi'
import { isSalonShopType } from '@/services/shopType'

const EM_DASH = '\u2014'

export default {
  name: 'AdminSalonPaymentsView',
  inject: {
    adminSession: { default: null }
  },
  data() {
    return {
      merchantIsSalon: false,
      ordersList: [],
      ordersPayLoading: false,
      paySearch: ''
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user ? this.adminSession.user : null
    },
    merchantSlug() {
      return String((this.$route && this.$route.params && this.$route.params.merchantSlug) || '').trim()
    },
    ordersAdminLink() {
      return { name: 'merchant-admin-orders', params: { merchantSlug: this.merchantSlug } }
    },
    storeAdminLink() {
      return { name: 'merchant-admin-store', params: { merchantSlug: this.merchantSlug } }
    },
    payHeaders() {
      return [
        { text: 'Customer', value: 'customerName', sortable: true },
        { text: 'Amount', value: 'totalZar', align: 'end', sortable: true },
        { text: 'Method', value: 'paymentMethod', sortable: true },
        { text: 'Status', value: 'status', sortable: true },
        { text: 'Date', value: 'createdAt', sortable: true }
      ]
    },
    payRows() {
      return (this.ordersList || []).map((o) => ({
        id: o.id,
        customerName: o.customerName || EM_DASH,
        totalZar: o.totalZar,
        paymentMethod: o.paymentMethod === 'cash_store' ? 'Cash in store' : 'EFT',
        status: String(o.status || '').replace(/_/g, ' ') || EM_DASH,
        createdAt: o.createdAt || ''
      }))
    },
    payRowsFiltered() {
      const q = String(this.paySearch || '').trim().toLowerCase()
      if (!q) return this.payRows
      return this.payRows.filter(
        (r) =>
          String(r.customerName || '')
            .toLowerCase()
            .includes(q) || String(r.id || '').toLowerCase().includes(q)
      )
    }
  },
  watch: {
    user(u) {
      if (u) this.bootstrap()
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.bootstrap()
    }
  },
  created() {
    if (this.user) this.bootstrap()
  },
  methods: {
    formatMoney(v) {
      const n = Number(v)
      if (!Number.isFinite(n)) return String(v || '')
      return n.toFixed(2)
    },
    formatOrderWhen(iso) {
      if (!iso) return EM_DASH
      try {
        return new Date(iso).toLocaleString()
      } catch {
        return String(iso)
      }
    },
    paymentStatusColor(status) {
      const s = String(status || '').toLowerCase()
      if (s.includes('paid')) return 'success'
      if (s.includes('cancel')) return 'error'
      return 'warning'
    },
    async bootstrap() {
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.merchantIsSalon = isSalonShopType(s.shopType)
      } catch {
        this.merchantIsSalon = false
      }
      if (this.merchantIsSalon) await this.loadPaymentOrders()
    },
    async loadPaymentOrders() {
      this.ordersPayLoading = true
      try {
        const res = await fetchAdminOrders(this.$route)
        const raw = res && res.orders ? res.orders : []
        this.ordersList = [...raw].sort((a, b) => {
          const ta = new Date(a.createdAt || 0).getTime()
          const tb = new Date(b.createdAt || 0).getTime()
          return tb - ta
        })
      } catch {
        this.ordersList = []
      } finally {
        this.ordersPayLoading = false
      }
    },
    exportPaymentsCsv() {
      const rows = this.payRowsFiltered.length ? this.payRowsFiltered : this.payRows
      const header = ['id', 'customerName', 'totalZar', 'paymentMethod', 'status', 'createdAt']
      const lines = [header.join(',')]
      rows.forEach((r) => {
        const esc = (v) => {
          const x = String(v ?? '')
          if (x.includes('"') || x.includes(',') || x.includes('\n')) return `"${x.replace(/"/g, '""')}"`
          return x
        }
        lines.push(header.map((h) => esc(r[h])).join(','))
      })
      const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `orders-${this.merchantSlug || 'export'}.csv`
      a.click()
      URL.revokeObjectURL(url)
    }
  }
}
</script>

<style scoped>
.salon-pay-hero {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.98) 0%, rgba(241, 245, 249, 0.95) 100%);
}
.salon-data-table ::v-deep th {
  font-weight: 700 !important;
  font-size: 12px !important;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: rgba(15, 23, 42, 0.55) !important;
}
</style>
