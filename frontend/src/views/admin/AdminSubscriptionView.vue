<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">
      {{ error }}
    </v-alert>
    <v-progress-linear v-if="loading" indeterminate height="3" class="mb-4" />

    <template v-if="status && !loading">
      <v-alert v-if="status.valid && !pendingUpgrade" type="success" dense outlined class="mb-4 rounded-lg">
        Your plan is active until {{ status.periodEnd || '-' }}. Features unlocked for this period are shown below.
      </v-alert>
      <v-alert v-else-if="!status.platformBankingConfigured" type="warning" dense outlined class="mb-4 rounded-lg">
        Platform banking is not configured yet. Support must set bank details before you can pay.
      </v-alert>
      <v-alert v-else-if="status.paymentProofPendingReview" type="info" dense outlined class="mb-4 rounded-lg">
        Payment proof received - waiting for support review.
        <span v-if="status.paymentProofAutoSummary" class="d-block text-caption mt-1">{{ status.paymentProofAutoSummary }}</span>
      </v-alert>
      <v-alert v-else-if="status.paymentProofStatus === 'REJECTED'" type="warning" dense outlined class="mb-4 rounded-lg">
        Proof not accepted. {{ status.paymentProofRejectionNote || 'Please upload a clear PDF and try again.' }}
      </v-alert>

      <v-row>
        <v-col cols="12" md="4">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="card-label mb-3">Current status</div>
            <div class="text-body-2 mb-2">
              <span class="text--secondary">Plan:</span>
              <strong class="ml-1">{{ tierLabel(status.planTier) || 'Not selected' }}</strong>
            </div>
            <div class="mb-2">
              <v-chip small label :color="status.valid ? 'success' : 'warning'" outlined>
                {{ status.valid ? 'Active' : 'Not active' }}
              </v-chip>
            </div>
            <div v-if="status.periodStart && status.periodEnd" class="text-caption text--secondary mb-2">
              Period: {{ status.periodStart }} to {{ status.periodEnd }}
            </div>
            <div class="text-caption text--secondary mb-4">Proof: {{ proofStatusLabel }}</div>
            <div class="card-label mb-2">Included now</div>
            <div v-for="row in activeFeatureRows" :key="row.key" class="d-flex align-start mb-2">
              <v-icon small :color="row.on ? 'success' : 'grey'" class="mr-2 mt-0">
                {{ row.on ? 'check_circle' : 'cancel' }}
              </v-icon>
              <div>
                <div class="text-body-2">{{ row.title }}</div>
                <div class="text-caption text--secondary">{{ row.subtitle }}</div>
              </div>
            </div>
          </v-card>
        </v-col>

        <v-col cols="12" md="8">
          <v-card
            v-if="status.planTier && (!status.valid || pendingUpgrade)"
            class="admin-card pa-4 pa-sm-6 mb-6"
            elevation="3"
            rounded="xl"
          >
            <div class="card-label mb-2">{{ pendingUpgrade ? 'Pay upgrade total' : 'Pay for this billing period' }}</div>
            <p class="text-body-2 text--secondary mb-2">
              Transfer
              <strong>R {{ formatMoney(amountDue) }}</strong>
              using this reference:
              <v-chip small label color="primary" outlined class="ml-1">{{ status.mandatoryPaymentReference || '-' }}</v-chip>
            </p>
            <p class="text-caption text--secondary mb-4">
              {{ status.billingPeriodDays || 30 }}-day plan fee. PDF proof must show the amount, today's date, and this
              payment reference. Auto-verify activates access; otherwise support reviews.
            </p>
            <v-btn
              depressed
              color="primary"
              class="text-none font-weight-bold mr-2 mb-2"
              :disabled="!isOwner || !status.platformBankingConfigured"
              @click="paymentDialog = true"
            >
              Bank details &amp; upload proof
            </v-btn>
          </v-card>

          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="card-label mb-2">Choose a plan</div>
            <p class="text-caption text--secondary mb-4">
              Starter / Standard / Premium match Wheel Hub Silver / Gold / Platinum: pick a plan, pay the period fee by EFT,
              upload PDF proof.
            </p>
            <v-row dense>
              <v-col v-for="p in plans" :key="p.tier" cols="12" sm="4">
                <v-card
                  outlined
                  class="pa-4 h-100"
                  :class="{ 'plan-selected': status.planTier === p.tier }"
                  rounded="lg"
                >
                  <div class="text-subtitle-1 font-weight-bold mb-1">{{ tierLabel(p.tier) }}</div>
                  <div class="text-h6 primary--text mb-3">R {{ formatMoney(p.subscriptionFee) }}</div>
                  <div class="text-caption mb-1" v-for="f in planFeatureLines(p)" :key="f">- {{ f }}</div>
                  <v-btn
                    block
                    depressed
                    class="text-none font-weight-bold mt-4"
                    color="tertiary"
                    :loading="choosing === p.tier"
                    :disabled="!!choosing || !isOwner"
                    @click="selectPlan(p.tier)"
                  >
                    {{ !isOwner ? 'Owner only' : status.planTier === p.tier ? 'Selected' : 'Select' }}
                  </v-btn>
                </v-card>
              </v-col>
            </v-row>
          </v-card>
        </v-col>
      </v-row>
    </template>

    <v-dialog v-model="paymentDialog" max-width="520" scrollable>
      <v-card class="pa-4 pa-sm-6" rounded="xl">
        <div class="card-label mb-3">Platform bank details</div>
        <v-progress-linear v-if="bankingLoading" indeterminate height="3" class="mb-3" />
        <template v-if="banking">
          <v-alert v-if="banking.configured === false" type="warning" dense outlined class="mb-3 rounded-lg">
            Platform banking is not ready. Contact support.
          </v-alert>
          <div class="text-body-2 mb-1"><span class="text--secondary">Bank:</span> {{ banking.bankName || '-' }}</div>
          <div class="text-body-2 mb-1"><span class="text--secondary">Account name:</span> {{ banking.accountName || '-' }}</div>
          <div class="text-body-2 mb-1"><span class="text--secondary">Account:</span> {{ banking.accountNumber || '-' }}</div>
          <div class="text-body-2 mb-1"><span class="text--secondary">Branch:</span> {{ banking.branchCode || '-' }}</div>
          <div v-if="banking.paymentLink" class="text-body-2 mb-1">
            <span class="text--secondary">Pay link:</span>
            <a :href="banking.paymentLink" target="_blank" rel="noopener">Open payment link</a>
          </div>
          <div class="text-body-2 mb-3">
            <span class="text--secondary">Reference:</span>
            <strong>{{ status && status.mandatoryPaymentReference }}</strong>
          </div>
          <div class="text-body-2 mb-4">
            <span class="text--secondary">Amount:</span>
            <strong>R {{ formatMoney(amountDue) }}</strong>
          </div>
          <p v-if="banking.referenceHint" class="text-caption text--secondary mb-4">{{ banking.referenceHint }}</p>
        </template>
        <v-alert v-else-if="!bankingLoading" type="error" dense outlined class="mb-3 rounded-lg">
          Could not load bank details (owner login required).
        </v-alert>
        <v-file-input
          v-model="proofFile"
          accept="application/pdf,.pdf"
          outlined
          dense
          hide-details="auto"
          label="Payment proof (PDF)"
          prepend-icon="picture_as_pdf"
          class="rounded-lg mb-4"
          :disabled="!isOwner"
        />
        <v-alert v-if="uploadError" type="error" dense outlined class="mb-3 rounded-lg">{{ uploadError }}</v-alert>
        <div class="d-flex justify-end">
          <v-btn text class="text-none mr-2" @click="paymentDialog = false">Close</v-btn>
          <v-btn
            depressed
            color="primary"
            class="text-none font-weight-bold"
            :loading="uploading"
            :disabled="!proofFile || !isOwner || (banking && banking.configured === false)"
            @click="uploadProof"
          >
            Upload proof
          </v-btn>
        </div>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import {
  chooseSubscriptionPlan,
  fetchPlatformBanking,
  fetchSubscriptionPlans,
  fetchSubscriptionStatus,
  uploadSubscriptionPaymentProof
} from '@/services/subscriptionApi'

export default {
  name: 'AdminSubscriptionView',
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return {
      loading: false,
      error: '',
      status: null,
      plans: [],
      choosing: null,
      paymentDialog: false,
      banking: null,
      bankingLoading: false,
      proofFile: null,
      uploading: false,
      uploadError: ''
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    },
    isOwner() {
      const roles = (this.user && this.user.roles) || []
      return roles.includes('MERCHANT_OWNER')
    },
    pendingUpgrade() {
      const s = this.status
      if (!s || !s.valid || !s.planTier || !s.billedPlanTier) return false
      return s.planTier !== s.billedPlanTier
    },
    amountDue() {
      const s = this.status
      if (!s) return 0
      return Number(s.grandTotalDue ?? s.amountDueThisPeriod ?? s.subscriptionFee) || 0
    },
    proofStatusLabel() {
      const ps = (this.status && this.status.paymentProofStatus) || 'NONE'
      if (ps === 'PENDING') return 'Pending review'
      if (ps === 'APPROVED') return 'Approved'
      if (ps === 'REJECTED') return 'Rejected'
      return 'None'
    },
    activeFeatureRows() {
      const f = (this.status && this.status.features) || {}
      return [
        {
          key: 'insights',
          title: 'Insights',
          subtitle: 'Sales charts and performance views.',
          on: !!f.insights
        },
        {
          key: 'email',
          title: 'Email alerts',
          subtitle: 'Order and booking emails to your store contact.',
          on: !!f.emailAlerts
        },
        {
          key: 'whatsapp',
          title: 'WhatsApp',
          subtitle: 'Twilio WhatsApp alerts (Premium).',
          on: !!f.whatsapp
        },
        {
          key: 'payroll',
          title: 'Team payroll',
          subtitle: 'Payment calculations and mark-paid for login team.',
          on: !!f.payroll
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
    paymentDialog(open) {
      if (open) this.loadBanking()
    }
  },
  methods: {
    tierLabel(tier) {
      if (tier === 'STARTER') return 'Starter'
      if (tier === 'STANDARD') return 'Standard'
      if (tier === 'PREMIUM') return 'Premium'
      return tier || ''
    },
    formatMoney(n) {
      const v = Number(n)
      if (!Number.isFinite(v)) return '0.00'
      return v.toFixed(2)
    },
    planFeatureLines(p) {
      const lines = []
      lines.push(p.featureInsights ? 'Insights' : 'No Insights')
      lines.push(p.featureEmailAlerts ? 'Email alerts' : 'No email alerts')
      lines.push(p.featureWhatsapp ? 'WhatsApp' : 'No WhatsApp')
      lines.push(p.featurePayroll ? 'Team payroll' : 'No payroll')
      if (p.maxEmployees < 0) lines.push('Unlimited team logins')
      else lines.push(`Up to ${p.maxEmployees} team login${p.maxEmployees === 1 ? '' : 's'}`)
      if (p.maxProducts < 0) lines.push('Unlimited products')
      else lines.push(`Up to ${p.maxProducts} products`)
      return lines
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const [st, pl] = await Promise.all([
          fetchSubscriptionStatus(this.$route),
          fetchSubscriptionPlans(this.$route)
        ])
        this.status = st
        this.plans = (pl && pl.plans) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load subscription'
      } finally {
        this.loading = false
      }
    },
    async selectPlan(tier) {
      this.choosing = tier
      this.error = ''
      try {
        this.status = await chooseSubscriptionPlan(this.$route, tier)
        this.$root.$emit('merchant-subscription-updated')
        if ((!this.status.valid || this.pendingUpgrade) && this.status.platformBankingConfigured) {
          this.paymentDialog = true
        }
      } catch (e) {
        this.error = (e && e.message) || 'Could not select plan'
      } finally {
        this.choosing = null
      }
    },
    async loadBanking() {
      this.bankingLoading = true
      try {
        this.banking = await fetchPlatformBanking(this.$route)
      } catch (e) {
        this.banking = null
        this.uploadError = (e && e.message) || 'Could not load banking'
      } finally {
        this.bankingLoading = false
      }
    },
    async uploadProof() {
      if (!this.proofFile) return
      this.uploading = true
      this.uploadError = ''
      try {
        const file = Array.isArray(this.proofFile) ? this.proofFile[0] : this.proofFile
        this.status = await uploadSubscriptionPaymentProof(this.$route, file)
        this.proofFile = null
        this.$root.$emit('merchant-subscription-updated')
        if (this.status.valid && !this.pendingUpgrade) this.paymentDialog = false
      } catch (e) {
        this.uploadError = (e && e.message) || 'Upload failed'
      } finally {
        this.uploading = false
      }
    }
  }
}
</script>

<style scoped>
.plan-selected {
  border-color: var(--v-primary-base, #1976d2) !important;
  box-shadow: 0 0 0 1px rgba(25, 118, 210, 0.35);
}
</style>
