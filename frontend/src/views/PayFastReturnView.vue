<template>
  <div>
    <v-container class="py-12 px-3 px-sm-4">
      <v-row justify="center">
        <v-col cols="12" sm="10" md="7" lg="5">
          <v-card class="pa-8 rounded-xl" elevation="3">
            <div class="d-flex align-center mb-4">
              <v-avatar :color="statusColor" size="44" class="mr-4">
                <v-icon color="white">{{ statusIcon }}</v-icon>
              </v-avatar>
              <div>
                <div class="text-overline text--secondary mb-0">PayFast payment</div>
                <h1 class="text-h5 font-weight-bold mb-0">Payment {{ statusLabel }}</h1>
              </div>
            </div>

            <v-progress-linear v-if="polling" indeterminate color="primary" class="mb-4" />

            <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">
              {{ error }}
            </v-alert>
            <v-alert v-else-if="cancelled" type="warning" dense outlined class="mb-4 rounded-lg">
              Payment was cancelled. You can try again from checkout.
            </v-alert>
            <v-alert v-else-if="paid" type="success" dense outlined class="mb-4 rounded-lg">
              Your PayFast payment was received. This {{ kindLabel }} is now marked as paid.
            </v-alert>
            <v-alert v-else type="info" dense outlined class="mb-4 rounded-lg">
              We are waiting for confirmation from PayFast. This usually takes a few seconds — leave this page open.
            </v-alert>

            <v-btn v-if="homePath" block depressed color="primary" class="text-none font-weight-bold btn-amber" :to="homePath">
              Continue
            </v-btn>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import { fetchOrderPeachStatus } from '@/services/publicStore'
import { fetchBookingPeachStatus } from '@/services/salonPublic'

export default {
  name: 'PayFastReturnView',
  data() {
    return {
      polling: false,
      error: '',
      paid: false,
      status: '',
      pollTimer: null,
      attempts: 0
    }
  },
  computed: {
    merchantSlug() {
      return String(this.$route.params.merchantSlug || this.$route.query.merchant || '').trim()
    },
    kind() {
      return String(this.$route.query.kind || 'order').trim().toLowerCase()
    },
    entityId() {
      return String(this.$route.query.id || this.$route.query.paymentId || '').trim()
    },
    customerEmail() {
      return String(this.$route.query.email || '').trim()
    },
    cancelled() {
      return String(this.$route.query.cancelled || '') === '1'
    },
    kindLabel() {
      if (this.kind === 'booking') return 'booking'
      if (this.kind === 'subscription') return 'subscription'
      return 'order'
    },
    statusLabel() {
      if (this.cancelled) return 'cancelled'
      if (this.paid) return 'successful'
      if (this.polling) return 'processing'
      return 'update'
    },
    statusColor() {
      if (this.paid) return 'success'
      if (this.error || this.cancelled) return 'error'
      return 'primary'
    },
    statusIcon() {
      if (this.paid) return 'check'
      if (this.error || this.cancelled) return 'error_outline'
      return 'hourglass_empty'
    },
    homePath() {
      if (this.kind === 'subscription' && this.merchantSlug) {
        return { name: 'merchant-admin-subscription', params: { merchantSlug: this.merchantSlug } }
      }
      if (!this.merchantSlug) return { name: 'client-history' }
      if (this.kind === 'booking') {
        return { name: 'merchant-track', params: { merchantSlug: this.merchantSlug } }
      }
      return { name: 'merchant-track', params: { merchantSlug: this.merchantSlug } }
    }
  },
  mounted() {
    if (this.kind === 'subscription') {
      this.paid = !this.cancelled
      return
    }
    if (!this.cancelled) this.startPolling()
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      this.polling = false
    },
    async fetchStatus() {
      if (!this.merchantSlug || !this.entityId) {
        this.error = 'Missing payment reference.'
        this.stopPolling()
        return
      }
      if (!this.customerEmail) {
        this.status = 'pending'
        return
      }
      try {
        const res =
          this.kind === 'booking'
            ? await fetchBookingPeachStatus(this.merchantSlug, this.entityId, this.customerEmail)
            : await fetchOrderPeachStatus(this.merchantSlug, this.entityId, this.customerEmail)
        this.status = String((res && res.status) || '')
        this.paid = Boolean(res && res.paid)
        if (this.paid) this.stopPolling()
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not verify payment status.'
        this.stopPolling()
      }
    },
    startPolling() {
      this.stopPolling()
      this.polling = true
      this.attempts = 0
      void this.fetchStatus()
      this.pollTimer = setInterval(() => {
        this.attempts += 1
        void this.fetchStatus()
        if (this.attempts >= 20) this.stopPolling()
      }, 2000)
    }
  }
}
</script>
