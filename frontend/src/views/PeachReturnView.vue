<template>
  <div>
    <v-container class="py-8 py-md-12 px-3 px-sm-4">
      <v-row justify="center">
        <v-col cols="12" sm="10" md="7" lg="5">
          <v-card class="pa-6 pa-sm-8 rounded-xl" elevation="3">
            <div class="d-flex align-center mb-4">
              <v-avatar :color="statusColor" size="44" class="mr-4">
                <v-icon color="white">{{ statusIcon }}</v-icon>
              </v-avatar>
              <div>
                <div class="text-overline text--secondary mb-0">Peach payment</div>
                <h1 class="text-h5 font-weight-bold mb-0">Payment {{ statusLabel }}</h1>
              </div>
            </div>

            <v-progress-linear v-if="polling" indeterminate color="primary" class="mb-4" />

            <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">
              {{ error }}
            </v-alert>
            <v-alert v-else-if="cancelled" type="warning" dense outlined class="mb-4 rounded-lg">
              Payment was cancelled and nothing was charged. You can try again from checkout.
            </v-alert>
            <v-alert v-else-if="paid" type="success" dense outlined class="mb-4 rounded-lg">
              Your Peach payment was received. This {{ kindLabel }} is now marked as paid.
            </v-alert>
            <v-alert v-else-if="timedOut" type="warning" dense outlined class="mb-4 rounded-lg">
              We haven’t received confirmation yet. If you completed the payment it can take a little longer — tap
              <strong>Check again</strong> in a moment.
            </v-alert>
            <v-alert v-else type="info" dense outlined class="mb-4 rounded-lg">
              We are waiting for confirmation from Peach. This usually takes a few seconds — leave this page open.
            </v-alert>

            <div class="d-flex flex-column flex-sm-row" style="gap: 12px">
              <v-btn
                v-if="canRetry"
                outlined
                color="primary"
                class="text-none font-weight-bold flex-grow-1"
                @click="startPolling"
              >
                <v-icon left small>refresh</v-icon>
                Check again
              </v-btn>
              <v-btn
                v-if="homePath"
                depressed
                color="primary"
                class="text-none font-weight-bold btn-amber flex-grow-1"
                :to="homePath"
              >
                Back to store
              </v-btn>
            </div>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import { fetchOrderPeachStatus } from '@/services/publicStore'
import { fetchBookingPeachStatus } from '@/services/salonPublic'
import { paymentReturnPolling } from '@/utils/paymentReturnPolling'

export default {
  name: 'PeachReturnView',
  mixins: [paymentReturnPolling],
  computed: {
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    kind() {
      return String(this.$route.query.kind || 'order').trim().toLowerCase()
    },
    entityId() {
      return String(this.$route.query.id || '').trim()
    },
    customerEmail() {
      return String(this.$route.query.email || '').trim()
    },
    cancelled() {
      const q = this.$route.query || {}
      return String(q.cancelled || '') === '1' || String(q.status || '').toLowerCase() === 'cancelled'
    },
    kindLabel() {
      return this.kind === 'booking' ? 'booking' : 'order'
    },
    canRetry() {
      return !this.cancelled && !this.paid && !this.polling && (this.timedOut || Boolean(this.error))
    },
    statusLabel() {
      if (this.cancelled) return 'cancelled'
      if (this.paid) return 'successful'
      if (this.polling || this.timedOut) return 'processing'
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
      if (!this.merchantSlug) return null
      return { name: 'merchant-home', params: { merchantSlug: this.merchantSlug } }
    }
  },
  mounted() {
    if (!this.cancelled) this.startPolling()
  },
  methods: {
    /** Polled by the mixin; resolves true once the server confirms payment. */
    async checkPaid() {
      if (!this.merchantSlug || !this.entityId) throw new Error('Missing payment reference.')
      // Email may be omitted on shopperResultUrl; keep showing the waiting state.
      if (!this.customerEmail) return false
      const res =
        this.kind === 'booking'
          ? await fetchBookingPeachStatus(this.merchantSlug, this.entityId, this.customerEmail)
          : await fetchOrderPeachStatus(this.merchantSlug, this.entityId, this.customerEmail)
      return Boolean(res && res.paid)
    }
  }
}
</script>
