<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
      <div class="d-flex flex-wrap align-baseline mb-1">
        <div class="card-label mb-0 my-income__title">
          My income<template v-if="isEmployee && bundle.displayName">: {{ bundle.displayName }}</template>
        </div>
        <v-spacer />
        <div class="text-body-2 text--secondary">{{ periodLabel }}</div>
      </div>
      <div v-if="isEmployee" class="text-caption text--secondary mb-4">
        Services: {{ describeServicePay(bundle.payMethod, bundle.payRate) }}. Orders: {{ describeOrderPay(bundle.orderPayMethod, bundle.orderPayRate) }}.
      </div>

      <div class="my-income__filters mb-4">
        <AppDateField v-model="startDate" dense outlined hide-details label="From" clearable class="rounded-lg my-income__date" />
        <AppDateField
          ref="endDateField"
          v-model="endDate"
          dense
          outlined
          hide-details="auto"
          label="To"
          clearable
          :min="startDate || undefined"
          :rules="endDateRules"
          class="rounded-lg my-income__date"
        />
        <div class="d-flex">
          <v-btn depressed color="primary" :loading="loading" @click="load">Refresh</v-btn>
          <v-btn v-if="startDate || endDate" text class="ml-1" @click="clearDates">All dates</v-btn>
        </div>
      </div>

      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>

      <template v-if="isEmployee">
        <v-row dense class="admin-card-row mb-4">
          <v-col v-for="tile in tiles" :key="tile.key" cols="12" sm="4">
            <div class="stat-tile rounded-xl pa-4" :class="{ 'stat-tile--accent': tile.accent }">
              <div class="stat-tile__label admin-clamp-1">{{ tile.label }}</div>
              <div class="stat-tile__value">{{ formatZar(tile.total) }}</div>
              <div class="stat-tile__hint">{{ tile.hint }}</div>
            </div>
          </v-col>
        </v-row>

        <v-simple-table dense class="my-income__table mb-4">
          <thead>
            <tr>
              <th></th>
              <th class="text-right">Owed</th>
              <th class="text-right">Upcoming</th>
              <th class="text-right">Paid out</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in summaryRows" :key="row.key" :class="{ 'font-weight-bold': row.isTotal }">
              <td class="text-no-wrap">{{ row.label }}</td>
              <td class="text-right text-no-wrap">{{ formatZar(row.owed) }}</td>
              <td class="text-right text-no-wrap">{{ formatZar(row.upcoming) }}</td>
              <td class="text-right text-no-wrap">{{ formatZar(row.paid) }}</td>
            </tr>
          </tbody>
        </v-simple-table>

        <v-simple-table v-if="lines.length" dense class="my-income__table">
          <thead>
            <tr>
              <th>Job</th>
              <th>When</th>
              <th class="text-right">Amount</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in lines" :key="line.jobType + line.jobId">
              <td
                class="text-caption text-no-wrap"
                :title="jobTypeLabel(line.jobType) + ' ' + shortRef(line.jobId) + ', ' + payKindLabel(line.jobType)"
              >
                {{ incomeJobRef(line) }}
              </td>
              <td class="text-caption text-no-wrap">{{ incomeWhen(line) }}</td>
              <td class="text-right text-no-wrap">{{ formatZar(line.expectedAmount || 0) }}</td>
              <td>
                <v-chip
                  x-small
                  label
                  :color="incomeStatusMeta(line).color"
                  :text-color="incomeStatusMeta(line).textColor"
                  class="font-weight-bold"
                >
                  {{ incomeStatusMeta(line).label }}
                </v-chip>
              </td>
            </tr>
          </tbody>
        </v-simple-table>
        <p v-else-if="!loading && !error" class="text-body-2 text--secondary mb-0">
          {{ startDate && endDate ? 'No work in these dates. Try "All dates".' : 'No work yet. Bookings made with you and paid orders credited to you show here as Upcoming, and move to Owed once the work is done.' }}
        </p>
      </template>
      <p v-else-if="!loading && !error && !isStoreOwner" class="text-body-2 text--secondary mb-0">
        No attributed work yet. Ask the owner to assign you on paid orders / confirmed bookings.
      </p>
    </v-card>

    <!-- Owners only (AC-17): store earnings from in app payments and payouts from the platform. -->
    <v-card v-if="isStoreOwner" class="admin-card pa-4 pa-sm-6 mt-4" elevation="3" rounded="xl">
      <div class="d-flex flex-wrap align-baseline mb-1">
        <div class="card-label mb-0">Store earnings</div>
        <v-spacer />
        <div class="text-body-2 text--secondary">{{ rangeLabel }}</div>
      </div>
      <p class="text-caption text--secondary mb-4">
        In app payments are collected by the platform and paid out to your store, less Paystack fees and the platform
        commission. Cash and bank transfer orders are paid to you directly and are not shown here.
      </p>

      <v-progress-linear v-if="storeLoading" indeterminate height="3" class="mb-3" />
      <v-alert v-if="storeError" type="error" dense outlined class="mb-3 rounded-lg">{{ storeError }}</v-alert>

      <template v-if="earnings">
        <v-row dense class="admin-card-row mb-4">
          <v-col v-for="tile in owedTiles" :key="tile.key" cols="12" sm="6" md="3">
            <div class="stat-tile rounded-xl pa-4" :class="{ 'stat-tile--accent': tile.accent }">
              <div class="stat-tile__label admin-clamp-1">{{ tile.label }}</div>
              <div class="stat-tile__value">{{ tile.value }}</div>
              <div class="stat-tile__hint">{{ tile.hint }}</div>
            </div>
          </v-col>
        </v-row>

        <div class="d-flex flex-wrap align-baseline mb-2">
          <div class="text-subtitle-2 font-weight-bold">In app payments</div>
          <v-spacer />
          <div class="text-caption text--secondary">
            {{ paymentsLabel(storePayments.count) }}, {{ formatCents(storePayments.grossCents || 0) }} gross
          </div>
        </div>
        <v-simple-table v-if="storePaymentItems.length" dense class="my-income__table mb-2">
          <thead>
            <tr>
              <th>Paid</th>
              <th>Order or booking</th>
              <th class="text-right">Gross</th>
              <th class="text-right">Fee</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(p, i) in storePaymentItems" :key="`${p.recordRef}-${p.paidAt}-${i}`">
              <td class="text-caption text-no-wrap">{{ formatDateTime(p.paidAt) }}</td>
              <td class="text-caption text-no-wrap">{{ paymentKindLabel(p.kind) }} {{ p.recordRef }}</td>
              <td class="text-right text-no-wrap">{{ formatCents(p.grossCents) }}</td>
              <td class="text-right text-no-wrap">
                <v-chip v-if="p.feeCents == null" x-small label color="orange lighten-5" text-color="deep-orange darken-3" class="font-weight-bold">
                  Fee unknown
                </v-chip>
                <template v-else>{{ formatCents(p.feeCents) }}</template>
              </td>
              <td>
                <v-chip x-small label :color="incomeStatusMeta(p).color" :text-color="incomeStatusMeta(p).textColor" class="font-weight-bold">
                  {{ incomeStatusMeta(p).label }}
                </v-chip>
              </td>
            </tr>
          </tbody>
        </v-simple-table>
        <p v-else class="text-body-2 text--secondary mb-2">No in app payments in these dates.</p>
        <p v-if="storePayments.truncated" class="text-caption text--secondary mb-2">
          Showing the first {{ storePaymentItems.length }} payments. Choose fewer dates to see the rest.
        </p>

        <div class="text-subtitle-2 font-weight-bold mt-4 mb-2">Payouts received</div>
        <v-simple-table v-if="storePayouts.length" dense class="my-income__table">
          <thead>
            <tr>
              <th>Date</th>
              <th class="text-right">Net paid</th>
              <th class="text-right">Commission</th>
              <th class="text-right">Fees</th>
              <th>Reference</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in storePayouts" :key="p.id">
              <td class="text-caption text-no-wrap">{{ formatDate(p.releasedAt) }}</td>
              <td class="text-right text-no-wrap font-weight-bold">{{ formatCents(p.netCents) }}</td>
              <td class="text-right text-no-wrap">
                {{ formatCents(p.commissionCents) }}
                <span class="text-caption text--secondary">({{ formatPercent(p.commissionPercent) }})</span>
              </td>
              <td class="text-right text-no-wrap">{{ formatCents(p.feeCents) }}</td>
              <td class="text-caption my-income__ref">{{ p.reference }}</td>
            </tr>
          </tbody>
        </v-simple-table>
        <p v-else class="text-body-2 text--secondary mb-0">No payouts in these dates.</p>
      </template>
    </v-card>
  </div>
</template>

<script>
import { fetchMyExpectedIncome, fetchStoreEarnings } from '@/services/teamApi'
import { rules } from '@/utils/validation'
import { formatDate, formatDateTime, jobTypeLabel, shortRef } from '@/utils/displayLabels'
import { describeOrderPay, describeServicePay, incomeSummaryRows, payKindLabel } from '@/utils/employeePay'
import { formatCents, formatZar } from '@/utils/price'
import { formatPercent, ownerOwedTiles, paymentKindLabel, paymentsLabel } from '@/utils/payouts'
import adminAccessMixin from './mixins/adminAccessMixin'
import {
  incomeJobRef,
  incomePeriodLabel,
  incomeStatusMeta,
  incomeWhen,
  sortIncomeLines
} from '@/utils/myIncome'

const jobs = (n, one, many) => `${n} ${n === 1 ? one : many}`

import AppDateField from '@/components/AppDateField.vue'
import { defaultDateRange } from '@/utils/dateRange'

export default {
  name: 'AdminMyIncomeView',
  components: { AppDateField },
  // isStoreOwner: owners also see the store earnings section; staff keep the employee view only (AC-19).
  mixins: [adminAccessMixin],
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    // Opens on the last 7 days (FR-1). Set only here, so a change or "All dates" is never overwritten.
    const { startDate, endDate } = defaultDateRange()
    return {
      loading: false,
      error: '',
      startDate,
      endDate,
      bundle: null,
      earnings: null,
      storeLoading: false,
      storeError: '',
      storeSeq: 0
    }
  },
  computed: {
    endDateRules() {
      return [rules.notBefore(() => this.startDate, 'To date cannot be before the From date')]
    },
    user() {
      return this.adminSession && this.adminSession.user
    },
    /** An owner without an employee record gets an empty bundle (employeeId null). */
    isEmployee() {
      return !!(this.bundle && this.bundle.employeeId)
    },
    periodLabel() {
      const b = this.bundle || {}
      return incomePeriodLabel(b.payPeriodStart, b.payPeriodEnd)
    },
    /** The range the store section was loaded for (the API echoes it), else the chosen dates. */
    rangeLabel() {
      const r = (this.earnings && this.earnings.range) || {}
      return incomePeriodLabel(r.startDate || this.startDate, r.endDate || this.endDate)
    },
    owedTiles() {
      return ownerOwedTiles(this.earnings && this.earnings.owed)
    },
    storePayments() {
      return (this.earnings && this.earnings.payments) || {}
    },
    storePaymentItems() {
      return this.storePayments.items || []
    },
    storePayouts() {
      return (this.earnings && this.earnings.payouts) || []
    },
    lines() {
      return sortIncomeLines(this.bundle && this.bundle.lines)
    },
    /** Service pay, order pay and total for owed / upcoming / paid out. */
    summaryRows() {
      return incomeSummaryRows(this.bundle)
    },
    tiles() {
      const b = this.bundle || {}
      const owed = b.owedTotal != null ? b.owedTotal : b.pendingExpected
      const owedCount = b.owedCount != null ? b.owedCount : this.lines.filter((l) => !l.employerPaid).length
      return [
        {
          key: 'owed',
          label: 'Owed to me',
          total: Number(owed || 0),
          hint: `${jobs(owedCount, 'job', 'jobs')} done, not paid out yet`,
          accent: true
        },
        {
          key: 'upcoming',
          label: 'Upcoming',
          total: Number(b.upcomingTotal || 0),
          hint: `${jobs(Number(b.upcomingCount || 0), 'job', 'jobs')} not done yet`
        },
        {
          key: 'paid',
          label: 'Paid out',
          total: Number(b.paidOutTotal || 0),
          hint: 'Marked paid by your employer'
        }
      ]
    }
  },
  watch: {
    user: {
      immediate: true,
      handler(u) {
        if (u) this.load()
      }
    },
    /** Owner access can arrive after the first load (access/me); load the store part then. */
    isStoreOwner(owner) {
      if (owner && this.user && !this.earnings && !this.storeLoading) this.loadStoreEarnings()
    }
  },
  methods: {
    formatCents,
    formatDate,
    formatDateTime,
    formatPercent,
    formatZar,
    incomeJobRef,
    incomeStatusMeta,
    incomeWhen,
    describeOrderPay,
    describeServicePay,
    jobTypeLabel,
    payKindLabel,
    paymentKindLabel,
    paymentsLabel,
    shortRef,
    clearDates() {
      this.startDate = ''
      this.endDate = ''
      this.load()
    },
    load() {
      const end = this.$refs.endDateField
      if (end && !end.validate(true)) return
      // Independent requests: a plan or employee error on one never hides the other.
      this.loadIncome()
      if (this.isStoreOwner) this.loadStoreEarnings()
    },
    range() {
      return { startDate: this.startDate || undefined, endDate: this.endDate || undefined }
    },
    async loadIncome() {
      this.loading = true
      this.error = ''
      try {
        this.bundle = await fetchMyExpectedIncome(this.$route, this.range())
      } catch (e) {
        this.bundle = null
        // Payroll is a plan feature; an owner still gets the store section, so no error for that.
        const ownerWithoutPayroll = this.isStoreOwner && e && e.code === 'payroll_requires_plan'
        this.error = ownerWithoutPayroll ? '' : (e && e.message) || 'Not available (owner only account or no employee record)'
      } finally {
        this.loading = false
      }
    },
    async loadStoreEarnings() {
      const seq = ++this.storeSeq
      this.storeLoading = true
      this.storeError = ''
      try {
        const res = await fetchStoreEarnings(this.$route, this.range())
        if (seq === this.storeSeq) this.earnings = res || null
      } catch (e) {
        if (seq !== this.storeSeq) return
        this.earnings = null
        this.storeError = (e && e.message) || 'Could not load your store earnings.'
      } finally {
        if (seq === this.storeSeq) this.storeLoading = false
      }
    }
  }
}
</script>

<style scoped>
.my-income__title {
  min-width: 0;
  overflow-wrap: anywhere;
}

.my-income__filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (min-width: 600px) {
  .my-income__filters {
    flex-direction: row;
    align-items: center;
  }

  .my-income__date {
    flex: 0 1 180px;
    max-width: 180px;
  }
}

.my-income__ref {
  overflow-wrap: anywhere;
}

.my-income__table th,
.my-income__table td {
  padding-left: 6px !important;
  padding-right: 6px !important;
}
</style>
