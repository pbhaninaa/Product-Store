<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">
      {{ error }}
    </v-alert>

    <v-alert type="info" dense outlined class="mb-4 rounded-lg">
      Merchant subscriptions activate only via verified Peach Hosted Checkout (Card or Instant EFT).
      Legacy EFT proofs and platform banking are read-only history.
    </v-alert>

    <v-tabs v-model="tab" background-color="transparent" class="mb-4">
      <v-tab class="text-none font-weight-bold">Legacy proofs</v-tab>
      <v-tab class="text-none font-weight-bold">Plans</v-tab>
      <v-tab class="text-none font-weight-bold">Merchants</v-tab>
      <v-tab class="text-none font-weight-bold">Banking (read-only)</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="d-flex align-center mb-3">
            <div class="card-label mb-0">Legacy payment proofs (read-only)</div>
            <v-spacer />
            <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
          </div>
          <p class="text-caption text--secondary mb-3">
            Approve / reject is retired. Merchants must pay with Peach. You can still open historical PDFs.
          </p>
          <v-data-table
            :headers="proofHeaders"
            :items="pending"
            :items-per-page="10"
            class="elevation-0"
            no-data-text="No legacy proofs on file."
          >
            <template v-slot:[`item.expectedFee`]="{ item }">R {{ formatMoney(item.expectedFee) }}</template>
            <template v-slot:[`item.autoPassed`]="{ item }">
              <v-chip x-small :color="item.autoPassed ? 'success' : 'warning'" label>
                {{ item.autoPassed ? 'Auto OK' : 'Review' }}
              </v-chip>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn small text color="primary" class="text-none" @click="openProof(item)">PDF</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-tab-item>

      <v-tab-item>
        <v-row>
          <v-col v-for="plan in plans" :key="plan.tier" cols="12" md="4">
            <v-card class="admin-card pa-4" elevation="3" rounded="xl">
              <div class="card-label mb-2">{{ plan.tier }}</div>
              <v-text-field
                v-model.number="plan.subscriptionFee"
                type="number"
                outlined
                dense
                label="Fee (ZAR)"
                class="mb-2"
              />
              <v-text-field
                v-model.number="plan.billingPeriodDays"
                type="number"
                outlined
                dense
                label="Period days"
                class="mb-2"
              />
              <v-text-field
                v-model.number="plan.maxEmployees"
                type="number"
                outlined
                dense
                label="Max employees (-1 unlimited)"
                class="mb-2"
              />
              <v-text-field
                v-model.number="plan.maxProducts"
                type="number"
                outlined
                dense
                label="Max products (-1 unlimited)"
                class="mb-2"
              />
              <v-checkbox v-model="plan.featureInsights" dense hide-details label="Insights" />
              <v-checkbox v-model="plan.featureEmailAlerts" dense hide-details label="Email alerts" />
              <v-checkbox v-model="plan.featureWhatsapp" dense hide-details label="WhatsApp" />
              <v-checkbox v-model="plan.featurePayroll" dense hide-details label="Payroll" class="mb-3" />
              <v-btn
                block
                color="primary"
                class="text-none font-weight-bold"
                :loading="savingPlan === plan.tier"
                @click="savePlan(plan)"
              >
                Save {{ plan.tier }}
              </v-btn>
            </v-card>
          </v-col>
        </v-row>
      </v-tab-item>

      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="d-flex align-center mb-3">
            <div class="card-label mb-0">Merchant subscriptions</div>
            <v-spacer />
            <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
          </div>
          <p class="text-caption text--secondary mb-3">
            Manual force-activate is retired. Paid periods renew only after a verified Peach callback.
          </p>
          <v-data-table
            :headers="subHeaders"
            :items="subs"
            :items-per-page="15"
            class="elevation-0"
            no-data-text="No merchants yet."
          >
            <template v-slot:[`item.valid`]="{ item }">
              <v-chip x-small :color="item.valid ? 'success' : 'grey'" label>
                {{ item.valid ? 'Active' : 'Inactive' }}
              </v-chip>
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
            Banking updates are disabled. Merchants pay subscription fees via Peach only.
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

function authToken() {
  try {
    return localStorage.getItem('ps_token') || ''
  } catch {
    return ''
  }
}

export default {
  name: 'SupportSubscriptionsView',
  data() {
    return {
      tab: 0,
      loading: false,
      savingPlan: null,
      error: '',
      pending: [],
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
        { text: 'Period end', value: 'periodEnd' },
        { text: 'Proof', value: 'paymentProofStatus' },
        { text: 'Peach', value: 'peachPaymentMethod' }
      ]
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
        this.pending = (proofs && proofs.pending) || []
        this.plans = ((plansRes && plansRes.plans) || []).map((p) => ({ ...p }))
        this.subs = (subsRes && subsRes.subscriptions) || []
        const tenantId = String((this.$route.query && this.$route.query.tenantId) || '').trim()
        if (tenantId) {
          this.pending = this.pending.slice().sort((a, b) => {
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
