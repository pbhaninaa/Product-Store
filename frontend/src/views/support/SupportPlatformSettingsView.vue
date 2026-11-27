<template>
  <div class="support-page">
    <div class="sc-page-head">
      <div>
        <div class="text-h5 font-weight-bold">Platform settings</div>
        <p class="text-body-2 text--secondary mb-0">
          Global configuration that applies to all merchants: commission rates and automatic payout scheduling.
        </p>
      </div>
    </div>

    <v-alert v-if="forbidden" type="warning" outlined dense class="rounded-lg mb-4">
      Only platform admins can change platform settings.
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
          Taken from the gross of each in-app payment, rounded to the cent. This applies to all merchants equally.
          Payouts already released keep the commission they were released with.
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
          Commission saved. It applies to all payouts released from now on.
        </v-alert>
      </v-card>

      <v-card class="admin-card pa-4 commission-card" elevation="3" rounded="xl">
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
    </template>
  </div>
</template>

<script>
import {
  fetchPlatformCommission,
  updatePlatformCommission,
  updatePlatformPayoutSchedule
} from '@/services/supportApi'
import { formatDateTime } from '@/utils/displayLabels'
import { commissionPayload, commissionRule, formatPercent } from '@/utils/payouts'
import { validateForm } from '@/utils/validation'

export default {
  name: 'SupportPlatformSettingsView',
  data() {
    return {
      forbidden: false,
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
      ]
    }
  },
  computed: {
    commissionChanged() {
      if (!this.commission) return true
      return Number(String(this.commissionInput).replace(',', '.')) !== Number(this.commission.commissionPercent)
    }
  },
  created() {
    this.loadCommission()
  },
  methods: {
    formatDateTime,
    commissionRule,
    handleError(e, fallback) {
      if (e && e.status === 403) {
        this.forbidden = true
        return ''
      }
      return (e && e.message) || fallback
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
