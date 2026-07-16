<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">
      {{ error }}
    </v-alert>

    <v-tabs v-model="tab" background-color="transparent" class="mb-4">
      <v-tab class="text-none font-weight-bold">Proofs</v-tab>
      <v-tab class="text-none font-weight-bold">Plans</v-tab>
      <v-tab class="text-none font-weight-bold">Merchants</v-tab>
      <v-tab class="text-none font-weight-bold">Banking</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="d-flex align-center mb-3">
            <div class="card-label mb-0">Pending payment proofs</div>
            <v-spacer />
            <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
          </div>
          <v-data-table
            :headers="proofHeaders"
            :items="pending"
            :items-per-page="10"
            class="elevation-0"
            no-data-text="No proofs waiting for review."
          >
            <template v-slot:[`item.expectedFee`]="{ item }">R {{ formatMoney(item.expectedFee) }}</template>
            <template v-slot:[`item.autoPassed`]="{ item }">
              <v-chip x-small :color="item.autoPassed ? 'success' : 'warning'" label>
                {{ item.autoPassed ? 'Auto OK' : 'Review' }}
              </v-chip>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn small text color="primary" class="text-none" @click="openProof(item)">PDF</v-btn>
              <v-btn
                small
                text
                color="success"
                class="text-none"
                :loading="acting === item.tenantId + ':ok'"
                @click="approve(item)"
              >
                Approve
              </v-btn>
              <v-btn
                small
                text
                color="error"
                class="text-none"
                :loading="acting === item.tenantId + ':no'"
                @click="reject(item)"
              >
                Reject
              </v-btn>
            </template>
          </v-data-table>
          <p v-if="pending.length" class="text-caption text--secondary mt-2 mb-0">
            Auto summary shows parser hints — still verify reference and amount before approve.
          </p>
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
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn
                small
                text
                color="primary"
                class="text-none"
                :loading="acting === item.tenantId + ':act'"
                @click="activate(item)"
              >
                Force activate
              </v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-tab-item>

      <v-tab-item>
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl" style="max-width: 520px">
          <div class="card-label mb-3">Platform banking (EFT)</div>
          <v-chip small class="mb-4" :color="banking.configured ? 'success' : 'warning'" label>
            {{ banking.configured ? 'Configured' : 'Not configured' }}
          </v-chip>
          <v-text-field v-model="banking.bankName" outlined dense hide-details="auto" label="Bank name" class="mb-3" />
          <v-text-field
            v-model="banking.accountName"
            outlined
            dense
            hide-details="auto"
            label="Account name"
            class="mb-3"
          />
          <v-text-field
            v-model="banking.accountNumber"
            outlined
            dense
            hide-details="auto"
            label="Account number"
            class="mb-3"
          />
          <v-text-field
            v-model="banking.branchCode"
            outlined
            dense
            hide-details="auto"
            label="Branch code"
            class="mb-3"
          />
          <v-textarea
            v-model="banking.referenceHint"
            outlined
            dense
            hide-details="auto"
            label="Reference hint"
            rows="2"
            class="mb-3"
          />
          <v-text-field
            v-model="banking.paymentLink"
            outlined
            dense
            hide-details="auto"
            label="Payment link (optional)"
            class="mb-4"
          />
          <v-btn
            block
            depressed
            color="primary"
            class="text-none font-weight-bold"
            :loading="savingBank"
            @click="saveBanking"
          >
            Save banking
          </v-btn>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script>
import {
  approveSubscriptionProof,
  fetchMerchantSubscriptions,
  fetchPendingSubscriptionProofs,
  fetchSupportPlans,
  fetchSupportPlatformBanking,
  forceActivateSubscription,
  rejectSubscriptionProof,
  subscriptionProofFileUrl,
  updateSupportPlan,
  updateSupportPlatformBanking
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
  inject: ['supportDialog'],
  data() {
    return {
      tab: 0,
      loading: false,
      savingBank: false,
      savingPlan: null,
      error: '',
      pending: [],
      plans: [],
      subs: [],
      acting: null,
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
        { text: '', value: 'actions', sortable: false }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
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
    async saveBanking() {
      this.savingBank = true
      this.error = ''
      try {
        this.banking = await updateSupportPlatformBanking(this.banking)
      } catch (e) {
        this.error = (e && e.message) || 'Could not save banking'
      } finally {
        this.savingBank = false
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
    },
    async approve(item) {
      this.acting = item.tenantId + ':ok'
      try {
        await approveSubscriptionProof(item.tenantId)
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Approve failed'
      } finally {
        this.acting = null
      }
    },
    async reject(item) {
      if (!this.supportDialog) return
      let note = ''
      try {
        note = await this.supportDialog.prompt({
          title: 'Reject payment proof',
          message: `Reject proof for ${item.tenantName || item.tenantId}.`,
          inputLabel: 'Rejection note (optional)',
          confirmLabel: 'Reject',
          tone: 'danger',
          confirmColor: 'error'
        })
      } catch {
        return
      }
      this.acting = item.tenantId + ':no'
      try {
        await rejectSubscriptionProof(item.tenantId, note || '')
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Reject failed'
      } finally {
        this.acting = null
      }
    },
    async activate(item) {
      if (!this.supportDialog) return
      const tiers = ['STARTER', 'STANDARD', 'PREMIUM']
      const defaultTier = tiers.includes(String(item.planTier || '').toUpperCase())
        ? String(item.planTier).toUpperCase()
        : 'STARTER'
      let tier
      try {
        tier = await this.supportDialog.select({
          title: 'Force-activate subscription',
          message: `Activate billing for ${item.tenantName || item.tenantId}.`,
          inputLabel: 'Plan tier',
          items: tiers,
          value: defaultTier,
          confirmLabel: 'Activate',
          tone: 'default'
        })
      } catch {
        return
      }
      if (!tier) return
      this.acting = item.tenantId + ':act'
      try {
        await forceActivateSubscription(item.tenantId, tier.trim().toUpperCase())
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Activate failed (platform admin only)'
      } finally {
        this.acting = null
      }
    }
  }
}
</script>
