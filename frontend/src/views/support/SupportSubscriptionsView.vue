<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">
      {{ error }}
    </v-alert>

    <v-alert type="info" dense outlined class="mb-4 rounded-lg">
      New merchants receive a one-time 7-day Free Trial (UTC) from store creation. After expiry, subscriptions
      activate only via verified PayFast hosted checkout (Card or Instant EFT). Support cannot reset or reissue trials.
      Legacy EFT proofs and platform banking are read-only history.
    </v-alert>

    <v-tabs v-model="tab" background-color="transparent" show-arrows class="mb-4">
      <v-tab class="text-none font-weight-bold">Legacy proofs (read-only)</v-tab>
      <v-tab class="text-none font-weight-bold">Plans</v-tab>
      <v-tab class="text-none font-weight-bold">Merchants</v-tab>
      <v-tab class="text-none font-weight-bold">Banking (read-only)</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="sc-toolbar">
            <div class="card-label mb-0">Legacy payment proofs (read-only)</div>
            <div class="sc-toolbar__end">
              <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
            </div>
          </div>
          <p class="text-caption text--secondary mb-3">
            Approve / reject is retired. Merchants must pay with PayFast. You can still open historical PDFs.
          </p>
          <v-data-table
            :headers="proofHeaders"
            :items="proofs"
            :items-per-page="10"
            :loading="loading"
            loading-text="Loading proofs…"
            class="elevation-0"
            no-data-text="No legacy proofs on file."
          >
            <template v-slot:[`item.expectedFee`]="{ item }">R {{ formatMoney(item.expectedFee) }}</template>
            <template v-slot:[`item.autoPassed`]="{ item }">
              <v-chip x-small :color="item.autoPassed ? 'success' : 'grey lighten-2'" label>
                {{ item.autoPassed ? 'Auto-check passed' : 'Not auto-checked' }}
              </v-chip>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn small text color="primary" class="text-none" @click="openProof(item)">PDF</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-tab-item>

      <v-tab-item>
        <p v-if="!canEditPlans" class="text-caption text--secondary mb-3">
          Read-only: editing plans needs the <strong>Manage plans</strong> permission.
        </p>
        <v-row v-if="!plans.length && loading">
          <v-col v-for="n in 3" :key="n" cols="12" md="4" class="d-flex">
            <v-card class="admin-card pa-4 sc-card" elevation="3" rounded="xl">
              <v-skeleton-loader type="heading, text@4, actions" />
            </v-card>
          </v-col>
        </v-row>
        <v-row v-else>
          <v-col v-for="plan in plans" :key="plan.tier" cols="12" md="4" class="d-flex">
            <v-card class="admin-card pa-4 sc-card" elevation="3" rounded="xl">
              <div class="card-label mb-3 sc-clamp-1">{{ plan.tier }}</div>
              <v-form :ref="`planForm-${plan.tier}`" class="sc-card__body" @submit.prevent>
                <v-text-field
                  v-for="field in planFields"
                  :key="field.key"
                  v-model.number="plan[field.key]"
                  type="number"
                  outlined
                  dense
                  hide-details="auto"
                  :label="field.label"
                  :rules="field.rules"
                  :readonly="!canEditPlans"
                  class="mb-3"
                />
                <v-checkbox
                  v-for="flag in planFlags"
                  :key="flag.key"
                  v-model="plan[flag.key]"
                  dense
                  hide-details
                  :readonly="!canEditPlans"
                  :label="flag.label"
                  class="mt-0"
                />
              </v-form>
              <div class="sc-card__actions pt-4">
                <v-btn
                  block
                  color="primary"
                  class="text-none font-weight-bold"
                  :disabled="!canEditPlans"
                  :loading="savingPlan === plan.tier"
                  @click="savePlan(plan)"
                >
                  Save {{ plan.tier }}
                </v-btn>
              </div>
            </v-card>
          </v-col>
        </v-row>
      </v-tab-item>

      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="sc-toolbar">
            <div class="card-label mb-0">Merchant subscriptions</div>
            <div class="sc-toolbar__end">
              <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
            </div>
          </div>
          <p class="text-caption text--secondary mb-3">
            Manual force-activate is retired. Paid periods renew only after a verified PayFast notification.
          </p>
          <v-data-table
            :headers="subHeaders"
            :items="subs"
            :items-per-page="15"
            :loading="loading"
            loading-text="Loading subscriptions…"
            class="elevation-0"
            no-data-text="No merchants yet."
          >
            <template v-slot:[`item.valid`]="{ item }">
              <v-chip x-small :color="item.valid ? 'success' : 'grey'" label>
                {{ item.onTrial ? 'Free Trial' : item.valid ? 'Active' : 'Inactive' }}
              </v-chip>
            </template>
            <template v-slot:[`item.daysRemaining`]="{ item }">
              {{ item.onTrial ? item.daysRemaining : '—' }}
            </template>
            <template v-slot:[`item.trialEndAt`]="{ item }">
              {{ item.trialEndAt ? String(item.trialEndAt).slice(0, 10) : '—' }}
            </template>
            <template v-slot:[`item.peachPaymentMethod`]="{ item }">
              {{ item.peachPaymentMethod || '—' }}
            </template>
          </v-data-table>
        </v-card>
      </v-tab-item>

      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl" style="max-width: 520px">
          <div class="card-label mb-3">Platform banking (legacy, read-only)</div>
          <v-chip small class="mb-4" :color="banking.configured ? 'success' : 'warning'" label>
            {{ banking.configured ? 'Historical record present' : 'Not configured' }}
          </v-chip>
          <v-text-field
            :value="banking.bankName"
            outlined
            dense
            hide-details="auto"
            label="Bank name"
            readonly
            class="mb-3"
          />
          <v-text-field
            :value="banking.accountName"
            outlined
            dense
            hide-details="auto"
            label="Account name"
            readonly
            class="mb-3"
          />
          <v-text-field
            :value="banking.accountNumber"
            outlined
            dense
            hide-details="auto"
            label="Account number"
            readonly
            class="mb-3"
          />
          <v-text-field
            :value="banking.branchCode"
            outlined
            dense
            hide-details="auto"
            label="Branch code"
            readonly
            class="mb-3"
          />
          <v-textarea
            :value="banking.referenceHint"
            outlined
            dense
            hide-details="auto"
            label="Reference hint"
            rows="2"
            readonly
            class="mb-3"
          />
          <v-text-field
            :value="banking.paymentLink"
            outlined
            dense
            hide-details="auto"
            label="Payment link (optional)"
            readonly
            class="mb-2"
          />
          <p class="text-caption text--secondary mb-0">
            Banking updates are disabled. Merchants pay subscription fees via PayFast only.
          </p>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script>
import {
  fetchMerchantSubscriptions,
  fetchPendingSubscriptionProofs,
  fetchSupportPlans,
  fetchSupportPlatformBanking,
  subscriptionProofFileUrl,
  updateSupportPlan
} from '@/services/supportApi'
import { rules, validateForm } from '@/utils/validation'

function authToken() {
  try {
    return localStorage.getItem('ps_token') || ''
  } catch {
    return ''
  }
}

export default {
  name: 'SupportSubscriptionsView',
  inject: {
    supportAccess: { default: null }
  },
  data() {
    return {
      tab: 0,
      loading: false,
      savingPlan: null,
      error: '',
      proofs: [],
      planFields: [
        {
          key: 'subscriptionFee',
          label: 'Fee (ZAR) *',
          rules: [rules.required('Fee'), rules.money({ min: 0 })]
        },
        {
          key: 'billingPeriodDays',
          label: 'Period days *',
          rules: [rules.required('Period days'), rules.integer({ min: 1, max: 366, label: 'Period days' })]
        },
        {
          key: 'maxEmployees',
          label: 'Max employees (-1 unlimited) *',
          rules: [rules.required('Max employees'), rules.integer({ min: -1, max: 100000, label: 'Max employees' })]
        },
        {
          key: 'maxProducts',
          label: 'Max products (-1 unlimited) *',
          rules: [rules.required('Max products'), rules.integer({ min: -1, max: 1000000, label: 'Max products' })]
        }
      ],
      planFlags: [
        { key: 'featureInsights', label: 'Insights' },
        { key: 'featureEmailAlerts', label: 'Email alerts' },
        { key: 'featureWhatsapp', label: 'WhatsApp' },
        { key: 'featurePayroll', label: 'Payroll' }
      ],
      plans: [],
      subs: [],
      banking: {
        bankName: '',
        accountName: '',
        accountNumber: '',
        branchCode: '',
        referenceHint: '',
        paymentLink: '',
        configured: false
      },
      proofHeaders: [
        { text: 'Merchant', value: 'name' },
        { text: 'Slug', value: 'slug' },
        { text: 'Plan', value: 'planTier' },
        { text: 'Fee', value: 'expectedFee' },
        { text: 'Reference', value: 'mandatoryPaymentReference' },
        { text: 'Auto', value: 'autoPassed' },
        { text: '', value: 'actions', sortable: false }
      ],
      subHeaders: [
        { text: 'Merchant', value: 'name' },
        { text: 'Slug', value: 'slug' },
        { text: 'Plan', value: 'planTier' },
        { text: 'Billed', value: 'billedPlanTier' },
        { text: 'Status', value: 'valid' },
        { text: 'Trial end (UTC)', value: 'trialEndAt' },
        { text: 'Days left', value: 'daysRemaining' },
        { text: 'Period end', value: 'periodEnd' },
        { text: 'Proof', value: 'paymentProofStatus' },
        { text: 'PayFast', value: 'peachPaymentMethod' }
      ]
    }
  },
  computed: {
    canEditPlans() {
      // PUT /api/support/subscriptions/plans/{tier} requires MANAGE_PLANS (platform admins have all).
      return Boolean(this.supportAccess && this.supportAccess.can('MANAGE_PLANS'))
    }
  },
  created() {
    this.applyNotificationQuery()
    this.load()
  },
  watch: {
    '$route.query': {
      deep: true,
      handler() {
        this.applyNotificationQuery()
      }
    }
  },
  methods: {
    applyNotificationQuery() {
      const tab = String((this.$route.query && this.$route.query.tab) || '').trim().toLowerCase()
      if (tab === 'proofs') this.tab = 0
      else if (tab === 'plans') this.tab = 1
      else if (tab === 'merchants') this.tab = 2
      else if (tab === 'banking') this.tab = 3
    },
    formatMoney(n) {
      const v = Number(n)
      if (!Number.isFinite(v)) return '0.00'
      return v.toFixed(2)
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const [proofs, bank, plansRes, subsRes] = await Promise.all([
          fetchPendingSubscriptionProofs(),
          fetchSupportPlatformBanking(),
          fetchSupportPlans(),
          fetchMerchantSubscriptions()
        ])
        this.proofs = (proofs && proofs.pending) || []
        this.plans = ((plansRes && plansRes.plans) || []).map((p) => ({ ...p }))
        this.subs = (subsRes && subsRes.subscriptions) || []
        const tenantId = String((this.$route.query && this.$route.query.tenantId) || '').trim()
        if (tenantId) {
          this.proofs = this.proofs.slice().sort((a, b) => {
            const aMatch = String(a.tenantId || '') === tenantId ? 0 : 1
            const bMatch = String(b.tenantId || '') === tenantId ? 0 : 1
            return aMatch - bMatch
          })
          this.subs = this.subs.slice().sort((a, b) => {
            const aMatch = String(a.tenantId || '') === tenantId ? 0 : 1
            const bMatch = String(b.tenantId || '') === tenantId ? 0 : 1
            return aMatch - bMatch
          })
        }
        this.banking = {
          bankName: bank.bankName || '',
          accountName: bank.accountName || '',
          accountNumber: bank.accountNumber || '',
          branchCode: bank.branchCode || '',
          referenceHint: bank.referenceHint || '',
          paymentLink: bank.paymentLink || '',
          configured: Boolean(bank.configured)
        }
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load subscriptions'
      } finally {
        this.loading = false
      }
    },
    async savePlan(plan) {
      const ref = this.$refs[`planForm-${plan.tier}`]
      if (!validateForm(Array.isArray(ref) ? ref[0] : ref)) return
      this.savingPlan = plan.tier
      this.error = ''
      try {
        const updated = await updateSupportPlan(plan.tier, plan)
        Object.assign(plan, updated)
      } catch (e) {
        this.error = (e && e.message) || 'Could not save plan'
      } finally {
        this.savingPlan = null
      }
    },
    async openProof(item) {
      try {
        const url = subscriptionProofFileUrl(item.tenantId)
        const token = authToken()
        const res = await fetch(url, {
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        })
        if (!res.ok) throw new Error('Could not open proof')
        const blob = await res.blob()
        const obj = URL.createObjectURL(blob)
        window.open(obj, '_blank', 'noopener')
      } catch (e) {
        this.error = (e && e.message) || 'Could not open proof'
      }
    }
  }
}
</script>
