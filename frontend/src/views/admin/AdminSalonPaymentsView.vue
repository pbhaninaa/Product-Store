<template>
  <div v-if="user">
    <v-card class="admin-card salon-pay-hero pa-4 pa-md-5 mb-4" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-md-row align-start">
        <v-avatar color="teal" size="48" class="mb-3 mb-md-0 mr-md-4">
          <v-icon dark>payments</v-icon>
        </v-avatar>
        <div>
          <div class="text-h6 font-weight-bold mb-1">Payments</div>
          <p class="text-body-2 text--secondary mb-0">
            Every payment for <strong>orders</strong><template v-if="merchantIsSalon"> and
            <strong>bookings</strong></template> in one place: online (card or Instant EFT), Manual EFT and cash in
            store. A payment shows as <strong>Paid</strong> once it is confirmed: online automatically, cash with the
            customer's code, Manual EFT after you check your bank. Confirm them on
            <strong>Orders</strong><template v-if="merchantIsSalon"> or <strong>Bookings</strong></template>.
          </p>
        </div>
      </div>
    </v-card>

    <v-row dense class="mb-2">
      <v-col v-for="t in summaryTiles" :key="t.label" cols="12" sm="4">
        <v-card class="admin-card pa-4" elevation="2" rounded="xl">
          <div class="text-caption text--secondary text-uppercase font-weight-bold">{{ t.label }}</div>
          <div class="text-h6 font-weight-bold">R {{ formatMoney(t.amount) }}</div>
          <div class="text-caption text--secondary">{{ t.count }} payment{{ t.count === 1 ? '' : 's' }}</div>
        </v-card>
      </v-col>
    </v-row>

    <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-sm-row align-start align-sm-center mb-4">
        <div class="card-label mb-0">All payments</div>
        <v-spacer />
        <div class="d-flex flex-wrap">
          <v-btn
            depressed
            outlined
            color="primary"
            class="text-none font-weight-bold mr-2 mb-2"
            :loading="loading"
            @click="loadPayments"
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
      <div class="d-flex flex-wrap align-center mb-4">
        <v-text-field
          v-model.trim="paySearch"
          dense
          outlined
          hide-details
          clearable
          label="Search customer or reference"
          prepend-inner-icon="search"
          class="rounded-lg mr-3 mb-2"
          style="max-width: 320px"
        />
        <v-btn-toggle v-model="statusFilter" mandatory dense class="mb-2" color="primary">
          <v-btn small value="all" class="text-none">All</v-btn>
          <v-btn small value="paid" class="text-none">Paid</v-btn>
          <v-btn small value="awaiting" class="text-none">Awaiting</v-btn>
          <v-btn small value="cancelled" class="text-none">Cancelled</v-btn>
        </v-btn-toggle>
      </div>
      <v-alert v-if="loadError" type="error" dense outlined class="rounded-lg">{{ loadError }}</v-alert>
      <v-data-table
        :headers="payHeaders"
        :items="payRowsFiltered"
        item-key="key"
        :items-per-page="15"
        sort-by="when"
        sort-desc
        class="rounded-lg elevation-0 salon-data-table"
        :loading="loading"
        no-data-text="No payments yet."
      >
        <template v-slot:[`item.kind`]="{ item }">
          <v-chip x-small label outlined>{{ item.kind }}</v-chip>
          <div class="text-caption text--secondary">{{ item.reference }}</div>
        </template>
        <template v-slot:[`item.customerName`]="{ item }">
          <div>{{ item.customerName }}</div>
          <div class="text-caption text--secondary">{{ item.detail }}</div>
        </template>
        <template v-slot:[`item.amount`]="{ item }">
          <span class="font-weight-bold">{{ item.amount == null ? NOT_SET : `R ${formatMoney(item.amount)}` }}</span>
        </template>
        <template v-slot:[`item.state`]="{ item }">
          <v-chip small label dark :color="stateColor(item.state)">{{ stateLabel(item.state) }}</v-chip>
        </template>
        <template v-slot:[`item.when`]="{ item }">
          {{ formatWhen(item.when) }}
          <div class="text-caption text--secondary">{{ item.state === 'paid' ? 'paid' : 'placed' }}</div>
        </template>
      </v-data-table>
      <div class="d-flex flex-wrap justify-end mt-4">
        <v-btn v-if="can('ORDERS')" text color="primary" class="text-none font-weight-bold" :to="ordersAdminLink">Open orders</v-btn>
        <v-btn v-if="merchantIsSalon && can('BOOKINGS')" text color="primary" class="text-none font-weight-bold" :to="bookingsAdminLink">
          Open bookings
        </v-btn>
        <v-btn v-if="can('STORE_SETTINGS')" text color="primary" class="text-none font-weight-bold" :to="storeAdminLink">
          Store &amp; banking
        </v-btn>
      </div>
    </v-card>
  </div>
</template>

<script>
import { inAppPaymentLabel } from '@/utils/inAppPayment'
import { shortRef } from '@/utils/displayLabels'
import { fetchAdminStoreSettings, fetchAdminOrders } from '@/services/adminApi'
import { fetchAdminSalonBookings } from '@/services/salonAdmin'
import { isSalonShopType } from '@/services/shopType'
import adminAccessMixin from './mixins/adminAccessMixin'

const NOT_SET = 'Not set'
const NO_NAME = 'None'
const PAID_BOOKING_STATUSES = ['confirmed', 'in_progress', 'completed']

function methodLabel(method, onlineChannel) {
  const m = String(method || '').toLowerCase()
  if (m === 'cash_store') return 'Cash in store'
  if (m === 'eft') return 'Manual EFT'
  return inAppPaymentLabel(method, onlineChannel) || (m ? m : NOT_SET)
}

/** Paid / awaiting / cancelled for an order. */
function orderState(o) {
  const s = String(o.status || '').toLowerCase()
  if (s === 'paid') return 'paid'
  if (s === 'cancelled' || o.cancelledAt) return 'cancelled'
  return 'awaiting'
}

/** Bookings can only be confirmed once paid, so confirmed / in progress / completed means paid. */
function bookingState(b) {
  if (b.paid === true) return 'paid'
  const s = String(b.status || '').toLowerCase()
  if (PAID_BOOKING_STATUSES.includes(s)) return 'paid'
  if (s === 'cancelled') return 'cancelled'
  return 'awaiting'
}

export default {
  name: 'AdminSalonPaymentsView',
  mixins: [adminAccessMixin],
  data() {
    return {
      NOT_SET,
      merchantIsSalon: false,
      orders: [],
      bookings: [],
      loading: false,
      loadError: '',
      paySearch: '',
      statusFilter: 'all'
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
    bookingsAdminLink() {
      return { name: 'merchant-admin-salon-bookings', params: { merchantSlug: this.merchantSlug } }
    },
    storeAdminLink() {
      return { name: 'merchant-admin-store', params: { merchantSlug: this.merchantSlug } }
    },
    payHeaders() {
      return [
        { text: 'Type', value: 'kind', sortable: true },
        { text: 'Customer', value: 'customerName', sortable: true },
        { text: 'Amount', value: 'amount', align: 'end', sortable: true },
        { text: 'Method', value: 'method', sortable: true },
        { text: 'Status', value: 'state', sortable: true },
        { text: 'Date', value: 'when', sortable: true }
      ]
    },
    payRows() {
      const orderRows = (this.orders || []).map((o) => {
        const state = orderState(o)
        const items = Array.isArray(o.order_items) ? o.order_items.length : 0
        return {
          key: `order-${o.id}`,
          id: o.id,
          kind: 'Order',
          reference: shortRef(o.id),
          customerName: o.customerName || o.customer_name || NO_NAME,
          detail: items ? `${items} item${items === 1 ? '' : 's'}` : '',
          amount: this.toNumber(o.totalZar != null ? o.totalZar : o.total_zar),
          method: methodLabel(o.paymentMethod || o.payment_method, o.onlinePaymentChannel),
          state,
          when:
            (state === 'paid' && (o.paymentConfirmedAt || o.payment_confirmed_at)) || o.createdAt || o.created_at || ''
        }
      })
      const bookingRows = (this.bookings || []).map((b) => {
        const state = bookingState(b)
        return {
          key: `booking-${b.id}`,
          id: b.id,
          kind: 'Booking',
          reference: shortRef(b.id),
          customerName: b.customerName || NO_NAME,
          detail: b.serviceName || '',
          amount: this.toNumber(b.amountZar),
          method: methodLabel(b.clientPaymentMethod, b.onlinePaymentChannel),
          state,
          when: (state === 'paid' && b.completedAt) || b.createdAt || ''
        }
      })
      return [...orderRows, ...bookingRows]
    },
    payRowsFiltered() {
      const q = String(this.paySearch || '').trim().toLowerCase()
      return this.payRows.filter((r) => {
        if (this.statusFilter !== 'all' && r.state !== this.statusFilter) return false
        if (!q) return true
        return (
          String(r.customerName || '').toLowerCase().includes(q) ||
          String(r.reference || '').toLowerCase().includes(q) ||
          String(r.id || '').toLowerCase().includes(q)
        )
      })
    },
    summaryTiles() {
      const sum = (state) => {
        const rows = this.payRows.filter((r) => r.state === state)
        return { count: rows.length, amount: rows.reduce((t, r) => t + (r.amount || 0), 0) }
      }
      return [
        { label: 'Received', ...sum('paid') },
        { label: 'Awaiting payment', ...sum('awaiting') },
        { label: 'Cancelled', ...sum('cancelled') }
      ]
    }
  },
  watch: {
    user(u) {
      if (u) this.loadPayments()
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.loadPayments()
    }
  },
  created() {
    if (this.user) this.loadPayments()
  },
  methods: {
    toNumber(v) {
      const n = Number(v)
      return Number.isFinite(n) ? n : null
    },
    formatMoney(v) {
      const n = Number(v)
      if (!Number.isFinite(n)) return String(v || '')
      return n.toFixed(2)
    },
    formatWhen(iso) {
      if (!iso) return NOT_SET
      try {
        return new Date(iso).toLocaleString()
      } catch {
        return String(iso)
      }
    },
    stateLabel(state) {
      if (state === 'paid') return 'Paid'
      if (state === 'cancelled') return 'Cancelled'
      return 'Awaiting payment'
    },
    stateColor(state) {
      if (state === 'paid') return 'success'
      if (state === 'cancelled') return 'error'
      return 'warning'
    },
    async loadPayments() {
      this.loading = true
      this.loadError = ''
      try {
        try {
          const s = await fetchAdminStoreSettings(this.$route)
          this.merchantIsSalon = isSalonShopType(s.shopType)
        } catch {
          this.merchantIsSalon = false
        }
        const [ordersRes, bookingsRes] = await Promise.all([
          fetchAdminOrders(this.$route).catch(() => null),
          this.merchantIsSalon ? fetchAdminSalonBookings(this.$route).catch(() => null) : Promise.resolve([])
        ])
        if (ordersRes == null && bookingsRes == null) {
          this.loadError = 'Could not load payments. Try Refresh.'
        }
        this.orders = ordersRes && Array.isArray(ordersRes.orders) ? ordersRes.orders : []
        this.bookings = Array.isArray(bookingsRes) ? bookingsRes : []
      } finally {
        this.loading = false
      }
    },
    exportPaymentsCsv() {
      const rows = this.payRowsFiltered
      const cols = [
        ['kind', 'Type'],
        ['reference', 'Reference'],
        ['customerName', 'Customer'],
        ['detail', 'Item'],
        ['amount', 'Amount (R)'],
        ['method', 'Payment method'],
        ['state', 'Status'],
        ['when', 'Date']
      ]
      const esc = (v) => {
        const x = String(v ?? '')
        return /[",\n]/.test(x) ? `"${x.replace(/"/g, '""')}"` : x
      }
      const lines = [cols.map((c) => c[1]).join(',')]
      rows.forEach((r) => {
        lines.push(
          cols
            .map(([k]) => {
              if (k === 'state') return esc(this.stateLabel(r.state))
              if (k === 'amount') return esc(r.amount == null ? '' : r.amount.toFixed(2))
              return esc(r[k])
            })
            .join(',')
        )
      })
      const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `payments-${this.merchantSlug || 'export'}.csv`
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
