<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">
      {{ error }}
    </v-alert>

    <v-row>
      <v-col cols="12" md="5">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="card-label mb-3">Platform banking (EFT)</div>
          <p class="text-caption text--secondary mb-4">
            Merchants pay subscription fees into this account (same flow as Wheel Hub platform banking).
          </p>
          <v-text-field v-model="banking.bankName" outlined dense hide-details="auto" label="Bank name" class="rounded-lg mb-3" />
          <v-text-field
            v-model="banking.accountName"
            outlined
            dense
            hide-details="auto"
            label="Account name"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model="banking.accountNumber"
            outlined
            dense
            hide-details="auto"
            label="Account number"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model="banking.branchCode"
            outlined
            dense
            hide-details="auto"
            label="Branch code"
            class="rounded-lg mb-3"
          />
          <v-textarea
            v-model="banking.referenceHint"
            outlined
            dense
            hide-details="auto"
            label="Reference hint"
            rows="2"
            class="rounded-lg mb-3"
          />
          <v-text-field
            v-model="banking.paymentLink"
            outlined
            dense
            hide-details="auto"
            label="Payment link (optional)"
            class="rounded-lg mb-4"
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
      </v-col>

      <v-col cols="12" md="7">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="d-flex align-center mb-3">
            <div class="card-label mb-0">Pending payment proofs</div>
            <v-spacer />
            <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
          </div>
          <v-progress-linear v-if="loading" indeterminate height="3" class="mb-3" />
          <v-data-table
            :headers="headers"
            :items="pending"
            :items-per-page="10"
            class="elevation-0"
            no-data-text="No proofs waiting for review."
          >
            <template v-slot:[`item.expectedFee`]="{ item }">
              R {{ formatMoney(item.expectedFee) }}
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn small text color="primary" class="text-none" @click="openProof(item)">PDF</v-btn>
              <v-btn small text color="success" class="text-none" :loading="acting === item.tenantId + ':ok'" @click="approve(item)">
                Approve
              </v-btn>
              <v-btn small text color="error" class="text-none" :loading="acting === item.tenantId + ':no'" @click="reject(item)">
                Reject
              </v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import {
  approveSubscriptionProof,
  fetchPendingSubscriptionProofs,
  fetchSupportPlatformBanking,
  rejectSubscriptionProof,
  subscriptionProofFileUrl,
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
  data() {
    return {
      loading: false,
      savingBank: false,
      error: '',
      pending: [],
      acting: null,
      banking: {
        bankName: '',
        accountName: '',
        accountNumber: '',
        branchCode: '',
        referenceHint: '',
        paymentLink: ''
      },
      headers: [
        { text: 'Merchant', value: 'name' },
        { text: 'Slug', value: 'slug' },
        { text: 'Plan', value: 'planTier' },
        { text: 'Fee', value: 'expectedFee' },
        { text: 'Reference', value: 'mandatoryPaymentReference' },
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
        const [proofs, bank] = await Promise.all([
          fetchPendingSubscriptionProofs(),
          fetchSupportPlatformBanking()
        ])
        this.pending = (proofs && proofs.pending) || []
        this.banking = {
          bankName: bank.bankName || '',
          accountName: bank.accountName || '',
          accountNumber: bank.accountNumber || '',
          branchCode: bank.branchCode || '',
          referenceHint: bank.referenceHint || '',
          paymentLink: bank.paymentLink || ''
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
      const note = window.prompt('Rejection note (optional):', '') || ''
      this.acting = item.tenantId + ':no'
      try {
        await rejectSubscriptionProof(item.tenantId, note)
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Reject failed'
      } finally {
        this.acting = null
      }
    }
  }
}
</script>
