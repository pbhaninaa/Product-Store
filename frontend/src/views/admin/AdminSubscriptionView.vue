<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">
      {{ error }}
    </v-alert>
    <v-progress-linear v-if="loading" indeterminate height="3" class="mb-4" />

    <template v-if="status && !loading">
      <v-alert v-if="status.valid && status.onTrial && !pendingUpgrade" type="info" dense outlined class="mb-4 rounded-lg">
        First month free — your plan is active until {{ status.periodEnd || '-' }}. Payment starts on the next period.
      </v-alert>
      <v-alert v-else-if="status.valid && !pendingUpgrade" type="success" dense outlined class="mb-4 rounded-lg">
        Your plan is active until {{ status.periodEnd || '-' }}. Features unlocked for this period are shown below.
      </v-alert>
      <v-alert v-else-if="status.trialEligible" type="info" dense outlined class="mb-4 rounded-lg">
        Select a plan to start your free first month — no payment required until the trial ends.
      </v-alert>
      <v-alert v-else-if="needsPayment && !status.peachConfigured" type="warning" dense outlined class="mb-4 rounded-lg">
        Peach checkout is not configured yet. Contact support before renewing or upgrading.
      </v-alert>
      <v-alert v-else-if="status.paymentProofPendingReview" type="info" dense outlined class="mb-4 rounded-lg">
        A legacy EFT proof is waiting for support review.
        <span v-if="status.paymentProofAutoSummary" class="d-block text-caption mt-1">{{ status.paymentProofAutoSummary }}</span>
      </v-alert>
      <v-alert v-else-if="status.paymentProofStatus === 'REJECTED'" type="warning" dense outlined class="mb-4 rounded-lg">
        Legacy EFT proof was not accepted. {{ status.paymentProofRejectionNote || 'Contact support if you need help.' }}
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
                {{ status.valid ? (status.onTrial ? 'Free trial' : 'Active') : 'Not active' }}
              </v-chip>
            </div>
            <div v-if="status.periodStart && status.periodEnd" class="text-caption text--secondary mb-2">
              Period: {{ status.periodStart }} to {{ status.periodEnd }}
            </div>
            <div class="text-caption text--secondary mb-4">Payment: {{ paymentStatusLabel }}</div>
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
            v-if="needsPayment"
            class="admin-card pa-4 pa-sm-6 mb-6"
            elevation="3"
            rounded="xl"
          >
            <div class="card-label mb-2">{{ pendingUpgrade ? 'Pay upgrade total' : 'Pay for this billing period' }}</div>
            <p class="text-body-2 text--secondary mb-2">
              Pay <strong>R {{ formatMoney(amountDue) }}</strong> securely with Peach Payments.
            </p>
            <p class="text-caption text--secondary mb-4">
              Choose Card or Instant EFT. Access activates automatically after payment.
            </p>
            <v-radio-group v-model="peachPaymentMethod" row hide-details class="mt-0 mb-4">
              <v-radio label="Card" value="CARD" />
              <v-radio label="Instant EFT" value="EFT" />
            </v-radio-group>
            <v-btn
              depressed
              color="primary"
              class="text-none font-weight-bold mr-2 mb-2"
              :loading="checkoutStarting"
              :disabled="!isOwner || !status.peachConfigured"
              @click="startPeachCheckout"
            >
              Continue to Peach
            </v-btn>
          </v-card>

          <v-card
            v-else-if="status.trialEligible"
            class="admin-card pa-4 pa-sm-6 mb-6"
            elevation="3"
            rounded="xl"
          >
            <div class="card-label mb-2">First month free</div>
            <p class="text-body-2 text--secondary mb-4">
              No payment is required for your first billing period. Choose a plan below to activate your free trial
              immediately.
            </p>
          </v-card>

          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="card-label mb-2">Choose a plan</div>
            <p class="text-caption text--secondary mb-4">
              <template v-if="status.trialEligible">
                First month free: pick Starter / Standard / Premium to unlock features immediately.
              </template>
              <template v-else>
                Pick a plan, then pay the period fee with Peach Hosted Checkout (card or instant EFT).
              </template>
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
                  <div class="text-h6 primary--text mb-1">R {{ formatMoney(p.subscriptionFee) }}</div>
                  <div v-if="status.trialEligible || status.onTrial" class="text-caption success--text mb-3">
                    First month free
                  </div>
                  <div v-else class="mb-3" />
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
                    {{
                      !isOwner
                        ? 'Owner only'
                        : status.planTier === p.tier && status.valid
                          ? 'Current'
                          : status.trialEligible
                            ? 'Start free month'
                            : status.planTier === p.tier
                              ? 'Selected'
                              : 'Select'
                    }}
                  </v-btn>
                </v-card>
              </v-col>
            </v-row>
          </v-card>
        </v-col>
      </v-row>
    </template>

  </div>
</template>

<script>
import {
  chooseSubscriptionPlan,
  fetchSubscriptionPlans,
  fetchSubscriptionStatus,
  startSubscriptionPeachCheckout
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
      checkoutStarting: false,
      peachPaymentMethod: 'CARD'
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
    needsPayment() {
      return Boolean(this.status && (this.status.needsPayment ?? this.status.needsPaymentProofUpload))
    },
    paymentStatusLabel() {
      const ps = (this.status && this.status.paymentProofStatus) || 'NONE'
      if (ps === 'PENDING') return 'Legacy EFT review pending'
      if (ps === 'REJECTED') return 'Legacy EFT rejected'
      if (this.status && this.status.valid) {
        if (this.status.onTrial) return 'Free trial'
        if (this.status.peachPaymentMethod === 'CARD') return 'PEACH · CARD'
        if (this.status.peachPaymentMethod === 'EFT') return 'PEACH · INSTANT EFT'
        return 'Paid'
      }
      return 'Payment required'
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
        if (this.needsPayment && (!this.status.valid || this.pendingUpgrade) && this.status.peachConfigured) {
          await this.startPeachCheckout()
        }
      } catch (e) {
        this.error = (e && e.message) || 'Could not select plan'
      } finally {
        this.choosing = null
      }
    },
    async startPeachCheckout() {
      if (!this.isOwner || !this.status || !this.status.peachConfigured) return
      this.checkoutStarting = true
      this.error = ''
      try {
        const checkout = await startSubscriptionPeachCheckout(this.$route, this.peachPaymentMethod)
        if (!checkout || !checkout.redirectUrl) throw new Error('Peach did not return a checkout URL.')
        window.location.href = checkout.redirectUrl
      } catch (e) {
        this.error = (e && e.message) || 'Could not start Peach checkout'
      } finally {
        this.checkoutStarting = false
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
