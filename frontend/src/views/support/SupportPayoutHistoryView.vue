<template>
  <div class="support-page">
    <div class="sc-page-head">
      <div>
        <div class="text-h5 font-weight-bold">Payout history</div>
        <p class="text-body-2 text--secondary mb-0">Payouts marked as released, newest first. Open one to see the payments it covers.</p>
      </div>
      <div class="sc-page-head__actions">
        <v-btn class="text-none font-weight-bold" color="primary" outlined :to="{ name: 'support-payouts' }">
          <v-icon left small>account_balance</v-icon>
          Payouts
        </v-btn>
      </div>
    </div>

    <v-alert v-if="forbidden" type="warning" outlined dense class="rounded-lg mb-4">
      Only platform admins can see payout history.
    </v-alert>

    <v-card v-else class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Released payouts</div>
        <div class="sc-toolbar__end">
          <span class="text-caption text--secondary">{{ totalLabel }}</span>
          <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        </div>
      </div>

      <v-row dense class="mb-1">
        <v-col cols="6" sm="4" md="3">
          <AppDateField
            ref="startDateField"
            v-model="startDate"
            outlined
            dense
            hide-details="auto"
            clearable
            label="From"
            :max="endDate || undefined"
            :rules="startDateRules"
          />
        </v-col>
        <v-col cols="6" sm="4" md="3">
          <AppDateField
            ref="endDateField"
            v-model="endDate"
            outlined
            dense
            hide-details="auto"
            clearable
            label="To"
            :min="startDate || undefined"
            :rules="endDateRules"
          />
        </v-col>
        <v-col cols="12" sm="4" md="4">
          <v-autocomplete
            v-model="tenantId"
            :items="merchantItems"
            :loading="merchantsLoading"
            outlined
            dense
            clearable
            hide-details
            label="Store"
            no-data-text="No stores"
          />
        </v-col>
        <v-col cols="12" md="2" class="d-flex align-center">
          <v-btn v-if="startDate || endDate" text small class="text-none" @click="clearDates">All dates</v-btn>
        </v-col>
      </v-row>

      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>

      <v-data-table
        :headers="headers"
        :items="payouts"
        :options.sync="options"
        :server-items-length="total"
        :loading="loading"
        :footer-props="{ 'items-per-page-options': [25, 50, 100, 200] }"
        disable-sort
        item-key="id"
        loading-text="Loading payouts…"
        :no-data-text="emptyText"
        class="elevation-0 history-table"
        @click:row="openDetail"
      >
        <template v-slot:[`item.releasedAt`]="{ item }">{{ formatDateTime(item.releasedAt) }}</template>
        <template v-slot:[`item.merchantName`]="{ item }">
          <div class="font-weight-medium">{{ item.merchantName || item.merchantSlug }}</div>
          <div class="text-caption text--secondary">{{ paymentsLabel(item.paymentCount) }}</div>
        </template>
        <template v-slot:[`item.grossCents`]="{ item }">{{ formatCents(item.grossCents) }}</template>
        <template v-slot:[`item.feeCents`]="{ item }">
          <div class="text-no-wrap">{{ formatCents(item.feeCents) }}</div>
          <v-chip v-if="item.feeAdjusted" x-small label class="mt-1">Adjusted</v-chip>
        </template>
        <template v-slot:[`item.commissionCents`]="{ item }">
          <div class="text-no-wrap">{{ formatCents(item.commissionCents) }}</div>
          <div class="text-caption text--secondary">{{ formatPercent(item.commissionPercent) }}</div>
        </template>
        <template v-slot:[`item.netCents`]="{ item }">
          <span class="font-weight-bold text-no-wrap">{{ formatCents(item.netCents) }}</span>
        </template>
        <template v-slot:[`item.reference`]="{ item }"><span class="sc-code">{{ item.reference }}</span></template>
        <template v-slot:[`item.actions`]="{ item }">
          <v-btn small text color="primary" class="text-none" :aria-label="`View payout ${item.reference}`" @click.stop="openDetail(item)">
            View
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="detailOpen" max-width="860" scrollable>
      <v-card class="rounded-xl">
        <v-card-title class="text-h6 font-weight-bold d-flex flex-nowrap">
          <span class="detail-title">Payout: {{ detailPayout ? detailPayout.merchantName || detailPayout.merchantSlug : '' }}</span>
          <v-spacer />
          <v-btn icon aria-label="Close" @click="detailOpen = false"><v-icon>close</v-icon></v-btn>
        </v-card-title>
        <v-card-text>
          <v-alert v-if="detailError" type="error" dense outlined class="rounded-lg">{{ detailError }}</v-alert>
          <template v-if="detailPayout">
            <v-row dense class="mb-2">
              <v-col v-for="f in detailFields" :key="f.key" cols="6" sm="4">
                <div class="text-caption text--secondary">{{ f.label }}</div>
                <div class="text-body-2 font-weight-medium detail-value">{{ f.value }}</div>
              </v-col>
            </v-row>
            <div v-if="detailPayout.note" class="mb-3">
              <div class="text-caption text--secondary">Note (internal)</div>
              <div class="text-body-2 detail-value">{{ detailPayout.note }}</div>
            </div>
          </template>
          <div class="card-label mb-2">Payments covered</div>
          <v-data-table
            :headers="lineHeaders"
            :items="detailLines"
            :loading="detailLoading"
            :items-per-page="-1"
            hide-default-footer
            dense
            item-key="attemptId"
            loading-text="Loading payments…"
            no-data-text="No payments to show."
            class="elevation-0"
          >
            <template v-slot:[`item.paidAt`]="{ item }">{{ formatDateTime(item.paidAt) }}</template>
            <template v-slot:[`item.kind`]="{ item }">{{ paymentKindLabel(item.kind) }}</template>
            <template v-slot:[`item.recordRef`]="{ item }"><span class="sc-code">{{ item.recordRef || shortRef(item.recordId) }}</span></template>
            <template v-slot:[`item.grossCents`]="{ item }">{{ formatCents(item.grossCents) }}</template>
            <template v-slot:[`item.feeCents`]="{ item }">
              <v-chip v-if="item.feeCents == null" x-small label color="orange lighten-5" text-color="deep-orange darken-3" class="font-weight-bold">
                Fee unknown
              </v-chip>
              <template v-else>{{ formatCents(item.feeCents) }}</template>
            </template>
            <template v-slot:[`item.commissionCents`]="{ item }">{{ formatCents(item.commissionCents) }}</template>
          </v-data-table>
        </v-card-text>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import AppDateField from '@/components/AppDateField.vue'
import { fetchPayoutDetail, fetchPayoutHistory, fetchSupportMerchants } from '@/services/supportApi'
import { defaultDateRange } from '@/utils/dateRange'
import { formatDateTime, shortRef } from '@/utils/displayLabels'
import { dateRangeRule, formatPercent, paymentKindLabel, paymentsLabel } from '@/utils/payouts'
import { formatCents } from '@/utils/price'
import { rules } from '@/utils/validation'

export default {
  name: 'SupportPayoutHistoryView',
  components: { AppDateField },
  data() {
    // Opens on the last 7 days (FR-1). Set only here, so a change or a cleared range is kept.
    const { startDate, endDate } = defaultDateRange()
    return {
      loading: false,
      error: '',
      forbidden: false,
      startDate,
      endDate,
      tenantId: null,
      merchants: [],
      merchantsLoading: false,
      payouts: [],
      total: 0,
      options: { page: 1, itemsPerPage: 50 },
      requestSeq: 0,
      detailOpen: false,
      detailLoading: false,
      detailError: '',
      detailPayout: null,
      detailLines: [],
      detailSeq: 0,
      headers: [
        { text: 'Released', value: 'releasedAt' },
        { text: 'Store', value: 'merchantName' },
        { text: 'Gross', value: 'grossCents', align: 'end' },
        { text: 'Fees', value: 'feeCents', align: 'end' },
        { text: 'Commission', value: 'commissionCents', align: 'end' },
        { text: 'Net', value: 'netCents', align: 'end' },
        { text: 'Reference', value: 'reference' },
        { text: 'Released by', value: 'releasedByEmail' },
        { text: '', value: 'actions', align: 'end' }
      ],
      lineHeaders: [
        { text: 'Paid', value: 'paidAt' },
        { text: 'Type', value: 'kind' },
        { text: 'Order or booking', value: 'recordRef' },
        { text: 'Paystack reference', value: 'paymentReference' },
        { text: 'Gross', value: 'grossCents', align: 'end' },
        { text: 'Fee', value: 'feeCents', align: 'end' },
        { text: 'Commission', value: 'commissionCents', align: 'end' }
      ]
    }
  },
  computed: {
    bothOrNeither() {
      return () => dateRangeRule(this.startDate, this.endDate)
    },
    startDateRules() {
      return [this.bothOrNeither]
    },
    endDateRules() {
      return [this.bothOrNeither, rules.notBefore(() => this.startDate, 'To date cannot be before the From date')]
    },
    rangeValid() {
      return this.bothOrNeither() === true && (!this.startDate || this.endDate >= this.startDate)
    },
    merchantItems() {
      return this.merchants.map((m) => ({ value: m.id, text: m.name ? `${m.name} (${m.slug})` : m.slug }))
    },
    totalLabel() {
      return this.total === 1 ? '1 payout' : `${this.total} payouts`
    },
    emptyText() {
      return this.startDate ? 'No payouts were released in these dates.' : 'No payouts have been released yet.'
    },
    detailFields() {
      const p = this.detailPayout || {}
      return [
        { key: 'released', label: 'Released', value: formatDateTime(p.releasedAt) },
        { key: 'by', label: 'Released by', value: p.releasedByEmail || 'Not set' },
        { key: 'reference', label: 'Reference', value: p.reference || 'Not set' },
        { key: 'gross', label: 'Gross', value: formatCents(p.grossCents) },
        { key: 'captured', label: 'Fees captured', value: this.capturedLabel(p) },
        { key: 'fee', label: p.feeAdjusted ? 'Fee total used (adjusted)' : 'Fee total used', value: formatCents(p.feeCents) },
        { key: 'commission', label: `Commission (${formatPercent(p.commissionPercent)})`, value: formatCents(p.commissionCents) },
        { key: 'net', label: 'Net paid', value: formatCents(p.netCents) },
        { key: 'count', label: 'Payments', value: paymentsLabel(p.paymentCount) }
      ]
    }
  },
  watch: {
    options: {
      handler(next, prev) {
        if (prev && next.page === prev.page && next.itemsPerPage === prev.itemsPerPage) return
        this.load()
      },
      deep: true
    },
    startDate() {
      this.onFilterChange()
    },
    endDate() {
      this.onFilterChange()
    },
    tenantId() {
      this.onFilterChange()
    }
  },
  created() {
    this.load()
    this.loadMerchants()
  },
  methods: {
    formatCents,
    formatDateTime,
    formatPercent,
    paymentKindLabel,
    shortRef,
    paymentsLabel,
    capturedLabel(p) {
      const base = formatCents(p.capturedFeeCents)
      const unknown = Number(p.unknownFeeCount || 0)
      return unknown > 0 ? `${base} (${unknown} unknown)` : base
    },
    clearDates() {
      this.startDate = ''
      this.endDate = ''
    },
    onFilterChange() {
      if (!this.rangeValid) return
      if (this.options.page !== 1) this.options = { ...this.options, page: 1 }
      else this.load()
    },
    async load() {
      if (!this.rangeValid) return
      const seq = ++this.requestSeq
      this.loading = true
      this.error = ''
      try {
        const res = await fetchPayoutHistory({
          tenantId: this.tenantId,
          startDate: this.startDate,
          endDate: this.endDate,
          page: this.options.page - 1,
          size: this.options.itemsPerPage
        })
        if (seq !== this.requestSeq) return
        this.payouts = (res && res.payouts) || []
        this.total = Number((res && res.total) || 0)
      } catch (e) {
        if (seq !== this.requestSeq) return
        if (e && e.status === 403) this.forbidden = true
        else this.error = (e && e.message) || 'Could not load payout history.'
      } finally {
        if (seq === this.requestSeq) this.loading = false
      }
    },
    async loadMerchants() {
      this.merchantsLoading = true
      try {
        const rows = await fetchSupportMerchants()
        this.merchants = (Array.isArray(rows) ? rows : []).filter((m) => m && m.id)
      } catch {
        // The store filter is optional; the list still works without it.
        this.merchants = []
      } finally {
        this.merchantsLoading = false
      }
    },
    async openDetail(item) {
      if (!item || !item.id) return
      const seq = ++this.detailSeq
      this.detailOpen = true
      this.detailPayout = item
      this.detailLines = []
      this.detailError = ''
      this.detailLoading = true
      try {
        const res = await fetchPayoutDetail(item.id)
        if (seq !== this.detailSeq) return
        this.detailPayout = (res && res.payout) || item
        this.detailLines = (res && res.lines) || []
      } catch (e) {
        if (seq === this.detailSeq) this.detailError = (e && e.message) || 'Could not load this payout.'
      } finally {
        if (seq === this.detailSeq) this.detailLoading = false
      }
    }
  }
}
</script>

<style scoped>
.history-table >>> tbody tr {
  cursor: pointer;
}

.detail-title {
  min-width: 0;
  overflow-wrap: anywhere;
  white-space: normal;
}

.detail-value {
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}
</style>
