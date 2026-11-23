<template>
  <div class="support-page">
    <div class="sc-page-head">
      <div class="release-head">
        <v-btn text small class="text-none px-1 mb-1" :to="{ name: 'support-payouts' }">
          <v-icon left small>arrow_back</v-icon>
          Payouts
        </v-btn>
        <div class="text-h5 font-weight-bold release-head__title">
          Release payout<template v-if="merchant">: {{ merchant.name || merchant.slug }}</template>
        </div>
        <p class="text-body-2 text--secondary mb-0">
          Pay the store outside the app first. This screen only records the payout; it does not move money.
        </p>
      </div>
      <div class="sc-page-head__actions">
        <v-btn class="text-none font-weight-bold" color="primary" outlined :loading="loading" :disabled="submitting" @click="load">
          <v-icon left small>refresh</v-icon>
          Refresh
        </v-btn>
      </div>
    </div>

    <v-alert v-if="forbidden" type="warning" outlined dense class="rounded-lg mb-4">
      Only platform admins can release payouts.
    </v-alert>
    <v-alert v-else-if="error" type="error" dense outlined class="mb-4 rounded-lg">{{ error }}</v-alert>

    <v-alert v-if="staleMessage" type="warning" outlined class="rounded-lg mb-4" role="alert">
      <div class="d-flex flex-wrap align-center sc-gap">
        <span class="font-weight-medium">{{ staleMessage }}</span>
        <v-btn small depressed color="primary" class="text-none font-weight-bold ml-sm-auto" :loading="loading" @click="refreshAfterConflict">
          Refresh
        </v-btn>
      </div>
    </v-alert>

    <v-alert v-if="released" type="success" outlined class="rounded-lg mb-4" role="status">
      <div class="font-weight-bold mb-1">Payout recorded</div>
      <div class="text-body-2">
        {{ formatCents(released.netCents) }} to {{ released.merchantName || (merchant && merchant.name) }},
        reference {{ released.reference }}, covering {{ paymentsLabel(released.paymentCount) }}.
      </div>
      <div class="d-flex flex-wrap sc-gap mt-3">
        <v-btn small outlined color="primary" class="text-none font-weight-bold" :to="{ name: 'support-payouts' }">Back to payouts</v-btn>
        <v-btn small text color="primary" class="text-none" :to="{ name: 'support-payout-history' }">Payout history</v-btn>
      </div>
    </v-alert>

    <v-skeleton-loader v-if="loading && !preview" type="card, table" class="rounded-xl" />

    <template v-if="preview && !forbidden">
      <v-row dense>
        <v-col cols="12" md="5" class="d-flex">
          <v-card class="admin-card pa-4 sc-card" elevation="3" rounded="xl">
            <div class="card-label mb-3">Bank details</div>
            <v-alert v-if="missingBankText" type="warning" dense outlined class="rounded-lg text-body-2">
              {{ missingBankText }}
            </v-alert>
            <dl class="bank-list">
              <template v-for="row in bankRows">
                <dt :key="`${row.key}-label`" class="text-caption text--secondary">{{ row.label }}</dt>
                <dd :key="`${row.key}-value`" class="text-body-1 mb-2" :class="{ 'text--secondary': !row.value }">
                  {{ row.value || 'Not set' }}
                </dd>
              </template>
            </dl>
          </v-card>
        </v-col>

        <v-col cols="12" md="7" class="d-flex">
          <v-card class="admin-card pa-4 sc-card" elevation="3" rounded="xl">
            <div class="card-label mb-3">Mark released</div>
            <v-alert v-if="!payments.length" type="info" dense outlined class="rounded-lg mb-0">
              Nothing is owed to this store right now.
            </v-alert>
            <v-form v-else ref="releaseForm" @submit.prevent="release">
              <v-simple-table dense class="release-sum mb-3">
                <tbody>
                  <tr>
                    <td>Gross ({{ paymentsLabel(summary.paymentCount) }})</td>
                    <td class="text-right">{{ formatCents(summary.grossCents) }}</td>
                  </tr>
                  <tr>
                    <td>
                      Paystack fees captured
                      <v-chip v-if="summary.unknownFeeCount > 0" x-small label color="orange lighten-5" text-color="deep-orange darken-3" class="font-weight-bold ml-1">
                        {{ unknownFeeLabel(summary.unknownFeeCount) }}
                      </v-chip>
                    </td>
                    <td class="text-right">{{ formatCents(summary.knownFeeCents) }}</td>
                  </tr>
                  <tr>
                    <td>Commission ({{ formatPercent(preview.commissionPercent) }})</td>
                    <td class="text-right">{{ formatCents(summary.commissionCents) }}</td>
                  </tr>
                </tbody>
              </v-simple-table>

              <v-text-field
                v-model="feeText"
                outlined
                dense
                hide-details="auto"
                label="Fee total used *"
                prefix="R"
                inputmode="decimal"
                class="mb-3"
                :hint="feeHint"
                persistent-hint
                :rules="[feeRule]"
                :disabled="submitting"
              />

              <div class="release-net rounded-lg pa-3 mb-3" aria-live="polite">
                <div class="text-caption text--secondary">Net to pay the store</div>
                <div class="text-h5 font-weight-bold">{{ netCents == null ? 'Enter the fee total' : formatCents(netCents) }}</div>
              </div>

              <v-text-field
                v-model="reference"
                outlined
                dense
                hide-details="auto"
                label="Payment reference *"
                hint="The reference of your bank or Paystack payment to the store"
                persistent-hint
                class="mb-3"
                :counter="referenceMax"
                :rules="[referenceRule]"
                :disabled="submitting"
              />
              <v-textarea
                v-model="note"
                outlined
                dense
                hide-details="auto"
                label="Note (internal, not shown to the store)"
                rows="2"
                auto-grow
                class="mb-3"
                :counter="noteMax"
                :rules="[noteRule]"
                :disabled="submitting"
              />
              <v-btn
                type="submit"
                color="primary"
                depressed
                block
                class="text-none font-weight-bold"
                :loading="submitting"
                :disabled="submitting || loading || Boolean(staleMessage)"
              >
                Mark released
              </v-btn>
            </v-form>
          </v-card>
        </v-col>
      </v-row>

      <v-card class="admin-card pa-4 mt-4" elevation="3" rounded="xl">
        <div class="sc-toolbar">
          <div class="card-label mb-0">Payments covered</div>
          <div class="sc-toolbar__end text-caption text--secondary">{{ paymentsLabel(payments.length) }}, all dates</div>
        </div>
        <v-data-table
          :headers="headers"
          :items="payments"
          :items-per-page="25"
          item-key="attemptId"
          no-data-text="No payments are owed."
          class="elevation-0"
        >
          <template v-slot:[`item.paidAt`]="{ item }">{{ formatDateTime(item.paidAt) }}</template>
          <template v-slot:[`item.kind`]="{ item }">{{ paymentKindLabel(item.kind) }}</template>
          <template v-slot:[`item.recordRef`]="{ item }">
            <span class="sc-code">{{ item.recordRef || shortRef(item.recordId) }}</span>
          </template>
          <template v-slot:[`item.grossCents`]="{ item }">{{ formatCents(item.grossCents) }}</template>
          <template v-slot:[`item.feeCents`]="{ item }">
            <v-chip v-if="item.feeCents == null" x-small label color="orange lighten-5" text-color="deep-orange darken-3" class="font-weight-bold">
              Fee unknown
            </v-chip>
            <template v-else>{{ formatCents(item.feeCents) }}</template>
          </template>
          <template v-slot:[`item.commissionCents`]="{ item }">{{ formatCents(item.commissionCents) }}</template>
        </v-data-table>
      </v-card>
    </template>
  </div>
</template>

<script>
import { fetchPayoutOwedForMerchant, releasePayout } from '@/services/supportApi'
import { formatDateTime, shortRef } from '@/utils/displayLabels'
import {
  NOTE_MAX,
  REFERENCE_MAX,
  feeTotalRule,
  formatPercent,
  isStaleReleaseError,
  missingBankFields,
  missingBankMessage,
  paymentKindLabel,
  paymentsLabel,
  referenceRule,
  releaseNetCents,
  releasePayload,
  unknownFeeLabel
} from '@/utils/payouts'
import { centsToAmountInput, formatCents } from '@/utils/price'
import { rules, validateForm } from '@/utils/validation'

const BANK_ROWS = [
  { key: 'bankName', label: 'Bank' },
  { key: 'accountHolder', label: 'Account holder' },
  { key: 'accountNumber', label: 'Account number' },
  { key: 'branchCode', label: 'Branch code' }
]

export default {
  name: 'SupportPayoutReleaseView',
  inject: { supportDialog: { default: null } },
  data() {
    return {
      loading: false,
      submitting: false,
      error: '',
      forbidden: false,
      staleMessage: '',
      preview: null,
      released: null,
      feeText: '',
      reference: '',
      note: '',
      referenceMax: REFERENCE_MAX,
      noteMax: NOTE_MAX,
      noteRule: rules.maxLength(NOTE_MAX, 'Note'),
      referenceRule,
      headers: [
        { text: 'Paid', value: 'paidAt' },
        { text: 'Type', value: 'kind' },
        { text: 'Order or booking', value: 'recordRef', sortable: false },
        { text: 'Paystack reference', value: 'paymentReference', sortable: false },
        { text: 'Gross', value: 'grossCents', align: 'end' },
        { text: 'Fee', value: 'feeCents', align: 'end' },
        { text: 'Commission', value: 'commissionCents', align: 'end' }
      ]
    }
  },
  computed: {
    tenantId() {
      return String(this.$route.params.tenantId || '')
    },
    merchant() {
      return (this.preview && this.preview.merchant) || null
    },
    payments() {
      return (this.preview && this.preview.payments) || []
    },
    summary() {
      return (this.preview && this.preview.summary) || {}
    },
    bankRows() {
      const bank = (this.preview && this.preview.bankDetails) || {}
      return BANK_ROWS.map((r) => ({ ...r, value: String(bank[r.key] == null ? '' : bank[r.key]).trim() }))
    },
    missingBankText() {
      return missingBankMessage(missingBankFields(this.preview))
    },
    feeRule() {
      return feeTotalRule(this.summary)
    },
    feeHint() {
      const unknown = Number(this.summary.unknownFeeCount || 0)
      const base = `Prefilled with the captured fees (${formatCents(this.summary.knownFeeCents || 0)}).`
      return unknown > 0
        ? `${base} ${unknownFeeLabel(unknown)} counted as R0: enter the real total if you know it.`
        : `${base} Change it if Paystack charged a different total.`
    },
    netCents() {
      return releaseNetCents(this.summary, this.feeText)
    }
  },
  watch: {
    tenantId: {
      immediate: true,
      handler() {
        this.released = null
        this.reference = ''
        this.note = ''
        this.load()
      }
    }
  },
  methods: {
    formatCents,
    formatDateTime,
    formatPercent,
    paymentKindLabel,
    shortRef,
    paymentsLabel,
    unknownFeeLabel,
    async load() {
      this.loading = true
      this.error = ''
      try {
        const preview = await fetchPayoutOwedForMerchant(this.tenantId)
        this.preview = preview
        // The fee total follows the loaded payments; reference and note stay as typed.
        this.feeText = centsToAmountInput((preview && preview.summary && preview.summary.knownFeeCents) || 0)
        this.staleMessage = ''
        this.$nextTick(() => this.$refs.releaseForm && this.$refs.releaseForm.resetValidation())
      } catch (e) {
        if (e && e.status === 403) this.forbidden = true
        else this.error = (e && e.message) || 'Could not load this store.'
        this.preview = null
      } finally {
        this.loading = false
      }
    },
    refreshAfterConflict() {
      this.load()
    },
    async confirmRelease() {
      if (!this.supportDialog) return true
      const name = (this.merchant && (this.merchant.name || this.merchant.slug)) || 'this store'
      try {
        await this.supportDialog.confirm({
          title: 'Mark payout as released?',
          message: `Record ${formatCents(this.netCents)} paid to ${name} for ${paymentsLabel(this.payments.length)}. Only do this after you have paid the store.`,
          confirmLabel: 'Mark released'
        })
        return true
      } catch {
        return false
      }
    },
    async release() {
      if (this.submitting || !this.preview || !this.payments.length) return
      if (!validateForm(this.$refs.releaseForm)) return
      if (!(await this.confirmRelease())) return
      this.submitting = true
      this.error = ''
      this.staleMessage = ''
      try {
        const res = await releasePayout(
          releasePayload(this.preview, { feeText: this.feeText, reference: this.reference, note: this.note })
        )
        this.released = (res && res.payout) || { netCents: this.netCents, reference: this.reference.trim(), paymentCount: this.payments.length }
        this.reference = ''
        this.note = ''
        await this.load()
      } catch (e) {
        if (isStaleReleaseError(e)) this.staleMessage = e.message
        else if (e && e.status === 403) this.forbidden = true
        else this.error = (e && e.message) || 'Could not record the payout. Nothing was saved.'
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.release-head {
  min-width: 0;
}

.release-head__title {
  overflow-wrap: anywhere;
}

.bank-list {
  margin: 0;
}

.bank-list dd {
  margin-left: 0;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}

.release-sum td {
  padding-left: 0 !important;
  font-variant-numeric: tabular-nums;
}

.release-net {
  background: rgba(15, 23, 42, 0.04);
  font-variant-numeric: tabular-nums;
}
</style>
