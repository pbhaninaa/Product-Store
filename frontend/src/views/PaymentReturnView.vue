<template>
  <div>
    <v-container class="py-8 py-md-12 px-3 px-sm-4">
      <v-row justify="center">
        <v-col cols="12" sm="10" md="7" lg="5">
          <v-card class="pa-6 pa-sm-8 rounded-xl" elevation="3">
            <div class="d-flex align-center mb-4">
              <v-avatar :color="statusColor" size="44" class="mr-4">
                <v-icon color="white" aria-hidden="true">{{ statusIcon }}</v-icon>
              </v-avatar>
              <div>
                <div class="text-overline text--secondary mb-0">Online payment</div>
                <h1 class="text-h5 font-weight-bold mb-0">Payment {{ statusLabel }}</h1>
              </div>
            </div>

            <v-progress-linear v-if="polling" indeterminate color="primary" class="mb-4" aria-label="Checking payment" />

            <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">
              {{ error }}
            </v-alert>
            <v-alert v-else-if="cancelled" type="warning" dense outlined class="mb-4 rounded-lg">
              The payment was not completed.
              <template v-if="ret.kind === 'subscription'">You can try again from your subscription page.</template>
              <template v-else>You can try again from checkout.</template>
              If money left your account, contact {{ ret.kind === 'subscription' ? 'support' : 'the store' }}<template
                v-if="ret.reference"
              > and quote the payment reference below</template>.
              <span v-if="ret.reference" class="d-block mt-2">
                Payment reference: <strong class="payment-reference">{{ ret.reference }}</strong>
              </span>
            </v-alert>
            <v-alert v-else-if="paid" type="success" dense outlined class="mb-4 rounded-lg">
              <template v-if="ret.kind === 'subscription'">
                Your payment was received and your subscription is active.
              </template>
              <template v-else-if="bookingFee">
                Your booking fee of {{ formatZar(bookingFee.feeZar) }} was received and your booking is confirmed.
                Pay {{ formatZar(bookingFee.remainderZar) }} in cash at the shop.
                <span v-if="handoverCode" class="d-block mt-2">
                  Your handover code: <strong class="handover-code">{{ handoverCode }}</strong>
                  <span class="d-block text-body-2 mt-1">Show it when you arrive. Staff enter it when you pay the rest.</span>
                </span>
              </template>
              <template v-else>
                Your payment was received. This {{ ret.kind }} is now marked as paid.
                <span v-if="handoverCode" class="d-block mt-2">
                  Your handover code: <strong class="handover-code">{{ handoverCode }}</strong>
                  <span class="d-block text-body-2 mt-1">{{ handoverCodeHint }}</span>
                </span>
              </template>
            </v-alert>
            <v-alert v-else-if="timedOut" type="warning" dense outlined class="mb-4 rounded-lg">
              We haven’t received confirmation yet. If you completed the payment it can take a little longer. Tap
              <strong>Check again</strong> in a moment.
              <template v-if="needsLookupHint">
                You can also look it up on the tracking page with your order number and email.
              </template>
              <span v-if="ret.reference" class="d-block mt-2">
                Payment reference: <strong class="payment-reference">{{ ret.reference }}</strong>
              </span>
            </v-alert>
            <v-alert v-else type="info" dense outlined class="mb-4 rounded-lg">
              We are waiting for confirmation of your payment. This usually takes a few seconds, so leave this page
              open.
            </v-alert>

            <div class="d-flex flex-column flex-sm-row" style="gap: 12px">
              <v-btn
                v-if="canRetry"
                outlined
                color="primary"
                class="text-none font-weight-bold flex-grow-1"
                @click="startPolling"
              >
                <v-icon left small aria-hidden="true">refresh</v-icon>
                Check again
              </v-btn>
              <v-btn
                depressed
                color="primary"
                class="text-none font-weight-bold btn-amber flex-grow-1"
                :to="homePath"
              >
                Continue
              </v-btn>
            </div>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import { fetchOrderPaymentStatus } from '@/services/publicStore'
import { fetchBookingPaymentStatus } from '@/services/salonPublic'
import {
  fetchSubscriptionPaymentStatus,
  fetchSubscriptionStatusForSlug,
  isSubscriptionPaid
} from '@/services/subscriptionApi'
import {
  checkOrderOrBookingReturn,
  checkSubscriptionReturn,
  hasStatusLookup,
  isPaymentReturnIncomplete,
  readPaymentReturn
} from '@/utils/paymentReturn'
import { paymentReturnPolling } from '@/utils/paymentReturnPolling'
import { handoverCodeHint } from '@/utils/handoverCode'
import { formatZar } from '@/utils/price'

export default {
  name: 'PaymentReturnView',
  mixins: [paymentReturnPolling],
  data() {
    return {
      /** The server reported the checkout as abandoned or failed (Paystack has no cancel URL). */
      serverCancelled: false,
      /** Shown once an order or booking is paid; staff ask for it at handover or service start. */
      handoverCode: '',
      /** Set when the payment was a cash booking's booking fee. */
      bookingFee: null
    }
  },
  computed: {
    handoverCodeHint() {
      return handoverCodeHint({ kind: this.ret.kind, paymentMethod: 'online' })
    },
    ret() {
      return readPaymentReturn(this.$route.params, this.$route.query)
    },
    cancelled() {
      return this.ret.cancelled || this.serverCancelled
    },
    needsLookupHint() {
      return this.ret.kind !== 'subscription' && !hasStatusLookup(this.ret)
    },
    canRetry() {
      return !this.cancelled && !this.paid && !this.polling && (this.timedOut || Boolean(this.error))
    },
    statusLabel() {
      if (this.cancelled) return 'not completed'
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
      const slug = this.ret.merchantSlug
      if (this.ret.kind === 'subscription' && slug) {
        return { name: 'merchant-admin-subscription', params: { merchantSlug: slug } }
      }
      if (!slug) return { name: 'client-history' }
      return { name: 'merchant-track', params: { merchantSlug: slug } }
    }
  },
  mounted() {
    if (!this.ret.cancelled) this.startPolling()
  },
  methods: {
    formatZar,
    /**
     * Polled by the mixin; resolves true once the server confirms payment. When the server reports the
     * checkout as abandoned or failed, polling stops and the cancelled state is shown.
     */
    async checkPaid() {
      const ret = this.ret
      if (isPaymentReturnIncomplete(ret)) throw new Error('Missing payment reference.')
      let outcome
      if (ret.kind === 'subscription') {
        outcome = await checkSubscriptionReturn(ret, {
          fetchPaymentStatus: fetchSubscriptionPaymentStatus,
          fetchPlanStatus: fetchSubscriptionStatusForSlug,
          isPlanPaid: isSubscriptionPaid
        })
      } else {
        // Uses the Paystack reference; without it (or an older link's email) this keeps waiting and shows a hint.
        outcome = await checkOrderOrBookingReturn(ret, {
          fetchOrderStatus: fetchOrderPaymentStatus,
          fetchBookingStatus: fetchBookingPaymentStatus
        })
      }
      if (outcome.cancelled) {
        this.serverCancelled = true
        this.stopPolling()
        return false
      }
      this.handoverCode = outcome.handoverCode || ''
      this.bookingFee = outcome.bookingFee || null
      return outcome.paid
    }
  }
}
</script>

<style scoped>
.payment-reference {
  word-break: break-all;
}
</style>
