<template>
  <div class="support-page">
    <div class="d-flex flex-column flex-sm-row align-start align-sm-center mb-6">
      <div>
        <div class="text-h5 font-weight-bold">System overview</div>
        <p class="text-body-2 text--secondary mb-0">Platform-wide counts, orders, salon activity, and revenue.</p>
      </div>
      <v-spacer class="d-none d-sm-block" />
      <v-btn
        class="text-none font-weight-bold mt-3 mt-sm-0"
        color="primary"
        outlined
        @click="loadOverview"
        :loading="loadingOverview"
      >
        <v-icon left small>refresh</v-icon>
        Refresh
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" outlined dense class="rounded-lg mb-6">{{ error }}</v-alert>

    <v-row dense>
      <v-col cols="12" sm="6" md="4" lg="3" v-for="card in overviewCards" :key="card.key">
        <v-card outlined class="rounded-xl pa-4 support-metric-card" :class="card.tone">
          <div class="text-overline text--secondary mb-1">{{ card.label }}</div>
          <div class="text-h5 font-weight-bold">{{ card.value }}</div>
          <div v-if="card.caption" class="text-caption text--secondary mt-1">{{ card.caption }}</div>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import { fetchSupportOverview } from '@/services/supportApi'

export default {
  name: 'SupportDashboardView',
  data() {
    return {
      overview: null,
      loadingOverview: false,
      error: ''
    }
  },
  computed: {
    overviewCards() {
      const o = this.overview || {}
      const counts = o.counts || {}
      const orders = o.orders || {}
      const salon = o.salon || {}
      const revenue = o.revenue || {}
      const platformRoles = o.platformRoles || {}
      return [
        {
          key: 'tenants',
          label: 'Merchants (tenants)',
          value: this.fmtInt(counts.tenants),
          caption: `${this.fmtInt(platformRoles.supportUsers || 0)} support · ${this.fmtInt(platformRoles.platformAdmins || 0)} admins`,
          tone: 'tone-indigo'
        },
        {
          key: 'users',
          label: 'Users',
          value: this.fmtInt(counts.users),
          caption: `${this.fmtInt(counts.productsActive)} active products platform-wide`,
          tone: 'tone-teal'
        },
        {
          key: 'orders',
          label: 'Orders',
          value: this.fmtInt(orders.total),
          caption: `paid ${this.fmtInt(orders.paid)} · pending ${this.fmtInt(orders.pendingPayment)}`,
          tone: 'tone-amber'
        },
        {
          key: 'salon',
          label: 'Salon',
          value: this.fmtInt(salon.bookingsTotal),
          caption: `${this.fmtInt(salon.bookingsConfirmed)} confirmed · ${this.fmtInt(salon.servicesActiveAcrossTenants)} services · ${this.fmtInt(salon.staffActiveAcrossTenants)} staff`,
          tone: 'tone-rose'
        },
        {
          key: 'revenue',
          label: 'Paid order revenue',
          value: this.formatZar(revenue.paidOrdersTotalZar),
          caption: 'Sum of total_zar for paid orders',
          tone: 'tone-slate'
        }
      ]
    }
  },
  created() {
    this.loadOverview()
  },
  methods: {
    fmtInt(n) {
      const x = Number(n)
      if (!Number.isFinite(x)) return '-'
      return String(Math.trunc(x))
    },
    formatZar(v) {
      if (v === null || v === undefined || v === '') return '-'
      const n = Number(v)
      if (!Number.isFinite(n)) return String(v)
      try {
        return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR', maximumFractionDigits: 2 }).format(n)
      } catch {
        return `R ${n.toFixed(2)}`
      }
    },
    async loadOverview() {
      this.error = ''
      this.loadingOverview = true
      try {
        this.overview = await fetchSupportOverview()
      } catch (e) {
        this.error = e && e.message ? e.message : 'Failed to load overview.'
      } finally {
        this.loadingOverview = false
      }
    }
  }
}
</script>

<style scoped>
.support-metric-card {
  background: #fff;
}

.support-metric-card.tone-indigo {
  border-left: 4px solid #6366f1 !important;
}
.support-metric-card.tone-teal {
  border-left: 4px solid #14b8a6 !important;
}
.support-metric-card.tone-amber {
  border-left: 4px solid #f59e0b !important;
}
.support-metric-card.tone-rose {
  border-left: 4px solid #fb7185 !important;
}
.support-metric-card.tone-slate {
  border-left: 4px solid #64748b !important;
}
</style>
