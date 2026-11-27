<template>
  <div class="support-page">
    <div class="sc-page-head">
      <div>
        <div class="text-h5 font-weight-bold">Payouts</div>
        <p class="text-body-2 text--secondary mb-0">
          What each store is owed from in-app Paystack payments. Release sends the money to the store's verified bank
          account.
        </p>
      </div>
      <div class="sc-page-head__actions">
        <v-btn class="text-none font-weight-bold" color="primary" outlined :to="{ name: 'support-platform-settings' }">
          <v-icon left small>settings</v-icon>
          Platform settings
        </v-btn>
        <v-btn class="text-none font-weight-bold" color="primary" outlined :to="{ name: 'support-payout-history' }">
          <v-icon left small>receipt_long</v-icon>
          Payout history
        </v-btn>
        <v-btn class="text-none font-weight-bold" color="primary" outlined :loading="loading" @click="load">
          <v-icon left small>refresh</v-icon>
          Refresh
        </v-btn>
      </div>
    </div>

    <v-alert v-if="forbidden" type="warning" outlined dense class="rounded-lg mb-4">
      Only platform admins can see payouts.
    </v-alert>

    <template v-else>
      <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>

      <v-row dense class="mb-2">
        <v-col v-for="tile in totalTiles" :key="tile.key" cols="6" md="3" class="d-flex">
          <v-card outlined class="rounded-xl pa-4 sc-card sc-stat">
            <div class="text-overline text--secondary mb-1 sc-stat__label">{{ tile.label }}</div>
            <v-skeleton-loader v-if="!owed && loading" type="heading" class="my-1" />
            <div v-else class="text-h6 font-weight-bold sc-stat__value">{{ tile.value }}</div>
          </v-card>
        </v-col>
      </v-row>

      <v-card class="admin-card pa-4" elevation="3" rounded="xl">
        <div class="sc-toolbar">
          <div class="card-label mb-0">Owed per store</div>
          <div class="sc-toolbar__end">
            <span class="text-caption text--secondary">{{ merchantsLabel }}</span>
          </div>
        </div>
        <v-data-table
          :headers="headers"
          :items="merchants"
          :items-per-page="25"
          :loading="loading"
          item-key="tenantId"
          sort-by="netOwedCents"
          sort-desc
          loading-text="Loading what is owed…"
          no-data-text="Nothing is owed to any store right now."
          class="elevation-0"
        >
          <template v-slot:[`item.merchantName`]="{ item }">
            <div class="font-weight-medium">{{ item.merchantName || item.merchantSlug }}</div>
            <div class="text-caption text--secondary">/{{ item.merchantSlug }}</div>
          </template>
          <template v-slot:[`item.grossCents`]="{ item }">{{ formatCents(item.grossCents) }}</template>
          <template v-slot:[`item.knownFeeCents`]="{ item }">
            <div class="text-no-wrap">{{ formatCents(item.knownFeeCents) }}</div>
            <v-chip v-if="item.unknownFeeCount > 0" x-small label color="orange lighten-5" text-color="deep-orange darken-3" class="font-weight-bold mt-1">
              {{ unknownFeeLabel(item.unknownFeeCount) }}
            </v-chip>
          </template>
          <template v-slot:[`item.commissionCents`]="{ item }">{{ formatCents(item.commissionCents) }}</template>
          <template v-slot:[`item.netOwedCents`]="{ item }">
            <span class="font-weight-bold">{{ formatCents(item.netOwedCents) }}</span>
          </template>
          <template v-slot:[`item.oldestPaidAt`]="{ item }">{{ formatDate(item.oldestPaidAt) }}</template>
          <template v-slot:[`item.actions`]="{ item }">
            <v-btn
              small
              depressed
              color="primary"
              class="text-none font-weight-bold"
              :to="{ name: 'support-payout-release', params: { tenantId: item.tenantId } }"
            >
              Release
            </v-btn>
          </template>
        </v-data-table>
      </v-card>
    </template>
  </div>
</template>

<script>
import { fetchPayoutsOwed } from '@/services/supportApi'
import { formatDate } from '@/utils/displayLabels'
import { formatPercent, unknownFeeLabel } from '@/utils/payouts'
import { formatCents } from '@/utils/price'

export default {
  name: 'SupportPayoutsView',
  data() {
    return {
      loading: false,
      error: '',
      forbidden: false,
      owed: null,
      headers: [
        { text: 'Store', value: 'merchantName' },
        { text: 'Payments', value: 'paymentCount', align: 'end' },
        { text: 'Gross', value: 'grossCents', align: 'end' },
        { text: 'Paystack fees', value: 'knownFeeCents', align: 'end' },
        { text: 'Commission', value: 'commissionCents', align: 'end' },
        { text: 'Net owed', value: 'netOwedCents', align: 'end' },
        { text: 'Oldest payment', value: 'oldestPaidAt' },
        { text: '', value: 'actions', sortable: false, align: 'end' }
      ]
    }
  },
  computed: {
    merchants() {
      return (this.owed && this.owed.merchants) || []
    },
    merchantsLabel() {
      const n = this.merchants.length
      return n === 1 ? '1 store' : `${n} stores`
    },
    totalTiles() {
      const t = (this.owed && this.owed.totals) || {}
      const percent = this.owed ? formatPercent(this.owed.commissionPercent) : ''
      return [
        { key: 'gross', label: 'Gross', value: formatCents(t.grossCents || 0) },
        { key: 'fees', label: 'Known fees', value: formatCents(t.knownFeeCents || 0) },
        { key: 'commission', label: percent ? `Commission (${percent})` : 'Commission', value: formatCents(t.commissionCents || 0) },
        { key: 'net', label: 'Net owed', value: formatCents(t.netOwedCents || 0) }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    formatCents,
    formatDate,
    unknownFeeLabel,
    handleError(e, fallback) {
      if (e && e.status === 403) {
        this.forbidden = true
        return ''
      }
      return (e && e.message) || fallback
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        this.owed = await fetchPayoutsOwed()
      } catch (e) {
        this.error = this.handleError(e, 'Could not load what is owed.')
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
