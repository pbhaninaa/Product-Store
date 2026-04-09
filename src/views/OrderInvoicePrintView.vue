<template>
  <div class="invoice-root">
    <div class="no-print invoice-toolbar pa-4">
      <v-container class="py-0 d-flex flex-wrap align-center">
        <v-btn text class="text-none font-weight-bold" color="primary" @click="goBack">
          <v-icon left small>arrow_back</v-icon>
          Back to admin
        </v-btn>
        <v-spacer />
        <v-btn depressed color="primary" class="text-none font-weight-bold btn-amber" :disabled="!order" @click="print">
          <v-icon left small color="white">print</v-icon>
          Print / Save as PDF
        </v-btn>
      </v-container>
    </div>

    <v-container class="invoice-container pb-12">
      <div v-if="loading" class="text-center py-12">
        <v-progress-circular indeterminate color="primary" />
      </div>

      <v-alert v-else-if="error" type="error" outlined class="rounded-lg">{{ error }}</v-alert>

      <article v-else-if="order" class="invoice-sheet pa-8 pa-md-10">
        <header class="invoice-header d-flex flex-column flex-sm-row align-start justify-space-between mb-8">
          <div>
            <div class="invoice-brand">{{ siteName }}</div>
            <h1 class="invoice-title mt-2 mb-0">Invoice</h1>
            <p class="invoice-meta mb-0 mt-2">
              Order no. <span class="invoice-mono">{{ order.id }}</span>
            </p>
          </div>
          <div class="invoice-header-right text-sm-right mt-4 mt-sm-0">
            <div class="invoice-meta">
              <strong>Date</strong><br />
              {{ formatWhen(order.created_at) }}
            </div>
          </div>
        </header>

        <section class="invoice-columns d-flex flex-column flex-md-row mb-8">
          <div class="invoice-col flex-grow-1 pr-md-6 mb-6 mb-md-0">
            <div class="invoice-col-label">Bill to</div>
            <div class="invoice-col-body">
              <strong>{{ order.customer_name }}</strong><br />
              {{ order.customer_email }}<br />
              <template v-if="order.customer_phone">{{ order.customer_phone }}<br /></template>
            </div>
          </div>
          <div class="invoice-col flex-grow-1">
            <div class="invoice-col-label">Delivery</div>
            <div class="invoice-col-body">
              <strong>{{ order.delivery_type === 'delivery' ? 'Delivery' : 'Pickup in store' }}</strong><br />
              <template v-if="order.delivery_type === 'delivery' && order.delivery_address">
                {{ order.delivery_address }}
              </template>
              <template v-else>Customer collects at the store.</template>
            </div>
          </div>
        </section>

        <section class="invoice-items mb-8">
          <div class="invoice-col-label mb-3">Items</div>
          <table class="invoice-table">
            <thead>
              <tr>
                <th class="text-left">Description</th>
                <th class="text-right">Qty</th>
                <th class="text-right">Unit</th>
                <th class="text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr><td colspan="4" class="invoice-table-spacer" /></tr>
              <tr v-for="it in order.order_items || []" :key="it.id">
                <td>{{ lineName(it) }}</td>
                <td class="text-right">{{ it.quantity }}</td>
                <td class="text-right">{{ formatZar(it.unit_price_zar) }}</td>
                <td class="text-right">{{ formatZar(it.line_total_zar) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="invoice-totals">
          <div class="invoice-total-row d-flex justify-space-between">
            <span>Subtotal</span>
            <span>{{ formatZar(order.subtotal_zar) }}</span>
          </div>
          <div class="invoice-total-row d-flex justify-space-between">
            <span>Delivery</span>
            <span>{{ formatZar(order.delivery_fee_zar) }}</span>
          </div>
          <div class="invoice-total-row invoice-total-row--grand d-flex justify-space-between">
            <span><strong>Total (ZAR)</strong></span>
            <span><strong>{{ formatZar(order.total_zar) }}</strong></span>
          </div>
        </section>

        <section class="invoice-payment mt-8 pt-6">
          <div class="invoice-col-label mb-2">Payment</div>
          <p class="mb-1">
            <strong>{{ order.payment_method === 'eft' ? 'Bank transfer (EFT)' : 'Cash' }}</strong>
          </p>
          <p v-if="order.payment_method === 'eft'" class="mb-0 invoice-note">
            Status:
            {{
              order.payment_confirmed
                ? 'Payment confirmed — thank you.'
                : 'Awaiting payment confirmation on bank statement. Items stay reserved until then.'
            }}
          </p>
          <p v-else class="mb-0 invoice-note">
            Status:
            {{
              order.payment_confirmed
                ? 'Cash payment confirmed — stock has been adjusted.'
                : 'Awaiting cash on collection or delivery. Items are reserved until staff confirms payment.'
            }}
          </p>
        </section>

        <footer class="invoice-footer mt-10 pt-6">
          <p class="mb-0 text-caption">Thank you for your business. For queries, contact us using details on file.</p>
        </footer>
      </article>
    </v-container>
  </div>
</template>

<script>
import { fetchOrderByIdForAdmin } from '@/services/orders'
import { formatZar } from '@/utils/price'

export default {
  name: 'OrderInvoicePrintView',
  data() {
    return {
      loading: true,
      error: '',
      order: null
    }
  },
  computed: {
    siteName() {
      return process.env.VUE_APP_SITE_NAME || 'Store'
    }
  },
  async created() {
    const id = this.$route.params.orderId
    if (!id) {
      this.error = 'Missing order.'
      this.loading = false
      return
    }
    try {
      this.order = await fetchOrderByIdForAdmin(id)
      if (!this.order) this.error = 'Order not found or you do not have access.'
    } catch (e) {
      this.error = e && e.message ? e.message : 'Could not load invoice.'
    } finally {
      this.loading = false
    }
  },
  methods: {
    formatZar,
    formatWhen(iso) {
      if (!iso) return ''
      try {
        return new Date(iso).toLocaleString(undefined, {
          dateStyle: 'medium',
          timeStyle: 'short'
        })
      } catch {
        return String(iso)
      }
    },
    lineName(it) {
      if (it.products && it.products.name) return it.products.name
      return 'Product'
    },
    print() {
      window.print()
    },
    goBack() {
      this.$router.push({ name: 'admin' })
    }
  }
}
</script>

<style scoped>
.invoice-root {
  min-height: 100%;
  background: #f1f5f9;
}

.invoice-toolbar {
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  position: sticky;
  top: 0;
  z-index: 2;
}

.invoice-container {
  max-width: 900px;
}

.invoice-sheet {
  background: #fff;
  border-radius: 4px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 12px 40px -24px rgba(15, 23, 42, 0.25);
}

.invoice-brand {
  font-size: 1.125rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.invoice-title {
  font-size: 1.75rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.invoice-meta {
  font-size: 0.875rem;
  color: rgba(15, 23, 42, 0.7);
  line-height: 1.5;
}

.invoice-mono {
  font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
  font-size: 0.8em;
  word-break: break-all;
}

.invoice-col-label {
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 0.5rem;
}

.invoice-col-body {
  font-size: 0.9375rem;
  line-height: 1.55;
  color: #0f172a;
}

.invoice-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9375rem;
}

.invoice-table th {
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.5);
  padding: 8px 0;
  border-bottom: 2px solid #0f172a;
}

.invoice-table td {
  padding: 10px 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  vertical-align: top;
}

.invoice-table-spacer {
  padding: 0 !important;
  border: none !important;
  height: 8px;
}

.invoice-total-row {
  font-size: 0.9375rem;
  padding: 6px 0;
  color: rgba(15, 23, 42, 0.85);
}

.invoice-total-row--grand {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 2px solid #0f172a;
  font-size: 1.125rem;
  color: #0f172a;
}

.invoice-note {
  font-size: 0.875rem;
  color: rgba(15, 23, 42, 0.72);
}

.invoice-footer {
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  color: rgba(15, 23, 42, 0.55);
}

.btn-amber {
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
}

@media print {
  .no-print {
    display: none !important;
  }

  .invoice-root {
    background: #fff !important;
  }

  .invoice-container {
    max-width: none !important;
    padding: 0 !important;
  }

  .invoice-sheet {
    border: none !important;
    box-shadow: none !important;
    border-radius: 0 !important;
    padding: 0 !important;
  }
}
</style>
