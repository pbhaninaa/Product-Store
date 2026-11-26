<template>
  <div class="support-page">
    <div class="sc-page-head">
      <div>
        <div class="text-h5 font-weight-bold">Payouts</div>
        <p class="text-body-2 text--secondary mb-0">
          What each store is owed from in-app Paystack payments. Release sends the money to the store’s verified bank
          account. You can also turn on automatic payouts with a wait in days.
        </p>
      </div>
      <div class="sc-page-head__actions">
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
      Only platform admins can see payouts and change the commission.
    </v-alert>

    <template v-else>
      <v-card class="admin-card pa-4 mb-4 commission-card" elevation="3" rounded="xl">
        <div class="sc-toolbar">
          <div class="card-label mb-0">Platform commission</div>
          <div class="sc-toolbar__end text-caption text--secondary">
            <template v-if="commission && commission.updatedAt">Updated {{ formatDateTime(commission.updatedAt) }}</template>
          </div>
        </div>
        <p class="text-body-2 text--secondary mb-3">
          Taken from the gross of each in app payment, rounded to the cent. Payouts already released keep the
          commission they were released with.
        </p>
        <v-form ref="commissionForm" class="d-flex flex-wrap align-start sc-gap" @submit.prevent="saveCommission">
          <v-text-field
            v-model="commissionInput"
            outlined
            dense
            hide-details="auto"
            label="Commission"
            suffix="%"
            inputmode="decimal"
            class="commission-field"
            :rules="[commissionRule]"
            :disabled="savingCommission || commissionLoading"
            :loading="commissionLoading"
          />
          <v-btn
            type="submit"
            color="primary"
            depressed
            class="text-none font-weight-bold"
            :loading="savingCommission"
            :disabled="savingCommission || commissionLoading || !commissionChanged"
          >
            Save commission
          </v-btn>
        </v-form>
        <v-alert v-if="commissionError" type="error" dense outlined class="mt-3 mb-0 rounded-lg">{{ commissionError }}</v-alert>
        <v-alert v-if="commissionSaved" type="success" dense outlined class="mt-3 mb-0 rounded-lg" dismissible @input="commissionSaved = false">
          Commission saved. It applies to payouts released from now on.
        </v-alert>
        <v-divider class="my-4" />
        <div class="card-label mb-2">Automatic payouts</div>
        <p class="text-body-2 text--secondary mb-3">
          When this is on, Paystack pays verified store accounts after the wait below. You can still release a payout
          by hand.
        </p>
        <v-switch
          v-model="autoPayouts"
          label="Send payouts automatically"
          inset
          color="primary"
          hide-details
          class="mt-0"
          :disabled="savingSchedule || commissionLoading"
        />
        <div class="d-flex flex-wrap align-start sc-gap mt-3">
          <v-text-field
            v-model="delayDays"
            outlined
            dense
            hide-details="auto"
            label="Wait (days)"
            type="number"
            min="0"
            max="90"
            class="commission-field"
            :disabled="savingSchedule || commissionLoading || !autoPayouts"
          />
          <v-select
            v-model="weekday"
            outlined
            dense
            hide-details
            label="Payout weekday"
            :items="weekdayItems"
            class="commission-field"
            :disabled="savingSchedule || commissionLoading || !autoPayouts"
          />
          <v-btn
            color="primary"
            outlined
            class="text-none font-weight-bold"
            :loading="savingSchedule"
            :disabled="savingSchedule || commissionLoading"
            @click="saveSchedule"
          >
            Save schedule
          </v-btn>
        </div>
        <v-alert v-if="scheduleError" type="error" dense outlined class="mt-3 mb-0 rounded-lg">{{ scheduleError }}</v-alert>
        <v-alert v-if="scheduleSaved" type="success" dense outlined class="mt-3 mb-0 rounded-lg" dismissible @input="scheduleSaved = false">
          Payout schedule saved.
        </v-alert>
      </v-card>

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
import {
  fetchPayoutsOwed,
  fetchPlatformCommission,
  updatePlatformCommission,
  updatePlatformPayoutSchedule
} from '@/services/supportApi'
import { formatDate, formatDateTime } from '@/utils/displayLabels'
import { commissionPayload, commissionRule, formatPercent, unknownFeeLabel } from '@/utils/payouts'
import { formatCents } from '@/utils/price'
import { validateForm } from '@/utils/validation'

export default {
  name: 'SupportPayoutsView',
  data() {
    return {
      loading: false,
      error: '',
      forbidden: false,
      owed: null,
      commission: null,
      commissionInput: '',
      commissionLoading: false,
      savingCommission: false,
      commissionError: '',
      commissionSaved: false,
      autoPayouts: false,
      delayDays: '7',
      weekday: 0,
      savingSchedule: false,
      scheduleError: '',
      scheduleSaved: false,
      weekdayItems: [
        { text: 'Any day', value: 0 },
        { text: 'Monday', value: 1 },
        { text: 'Tuesday', value: 2 },
        { text: 'Wednesday', value: 3 },
        { text: 'Thursday', value: 4 },
        { text: 'Friday', value: 5 },
        { text: 'Saturday', value: 6 },
        { text: 'Sunday', value: 7 }
      ],
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
    },
    commissionChanged() {
      if (!this.commission) return true
      return Number(String(this.commissionInput).replace(',', '.')) !== Number(this.commission.commissionPercent)
    }
  },
  created() {
    this.loadCommission()
    this.load()
  },
  methods: {
    formatCents,
    formatDate,
    formatDateTime,
    commissionRule,
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
    },
    applyCommission(c) {
      this.commission = c || null
      this.commissionInput = c ? formatPercent(c.commissionPercent).replace('%', '') : ''
      this.autoPayouts = Boolean(c && c.automaticProviderPayouts)
      this.delayDays = String((c && c.automaticProviderPayoutDelayDays) != null ? c.automaticProviderPayoutDelayDays : 7)
      this.weekday = Number((c && c.automaticProviderPayoutWeekday) || 0)
    },
    async saveSchedule() {
      this.savingSchedule = true
      this.scheduleError = ''
      this.scheduleSaved = false
      try {
        this.applyCommission(
          await updatePlatformPayoutSchedule({
            automaticProviderPayouts: this.autoPayouts,
            automaticProviderPayoutDelayDays: Number(this.delayDays),
            automaticProviderPayoutWeekday: Number(this.weekday)
          })
        )
        this.scheduleSaved = true
      } catch (e) {
        this.scheduleError = this.handleError(e, 'Could not save the payout schedule.')
      } finally {
        this.savingSchedule = false
      }
    },
    async loadCommission() {
      this.commissionLoading = true
      this.commissionError = ''
      try {
        this.applyCommission(await fetchPlatformCommission())
      } catch (e) {
        this.commissionError = this.handleError(e, 'Could not load the commission.')
      } finally {
        this.commissionLoading = false
      }
    },
    async saveCommission() {
      if (this.savingCommission || !validateForm(this.$refs.commissionForm)) return
      this.savingCommission = true
      this.commissionError = ''
      this.commissionSaved = false
      try {
        const { commissionPercent } = commissionPayload(this.commissionInput)
        this.applyCommission(await updatePlatformCommission(commissionPercent))
        this.commissionSaved = true
        // Owed amounts use the current commission, so they change with it.
        this.load()
      } catch (e) {
        this.commissionError = this.handleError(e, 'Could not save the commission.')
      } finally {
        this.savingCommission = false
      }
    }
  }
}
</script>

<style scoped>
.commission-card {
  max-width: 760px;
}

.commission-field {
  flex: 0 1 200px;
  max-width: 200px;
}
</style>
