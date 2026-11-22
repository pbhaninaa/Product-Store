<template>
  <v-container class="page-narrow py-8 px-3 px-sm-4">
    <div class="d-flex align-center mb-2">
      <h1 class="text-h4 font-weight-bold mb-0">Your activity</h1>
      <v-spacer />
      <v-btn text class="text-none" :to="{ name: 'client-hub' }">Hub</v-btn>
    </div>
    <p class="text-body-2 text--secondary mb-6">Orders and service bookings on your customer account.</p>
    <v-alert v-if="error" type="error" dense outlined class="mb-4">{{ error }}</v-alert>
    <v-card class="pa-4 rounded-xl mb-6" outlined>
      <div class="d-flex align-center mb-3">
        <div class="font-weight-bold">Referral code</div>
        <v-spacer />
        <v-chip v-if="referralCode" label outlined>{{ referralCode }}</v-chip>
      </div>
      <p class="text-caption text--secondary mb-0">Share this code. First paid merchant subscription pays 15% commission.</p>
    </v-card>
    <v-card class="pa-4 rounded-xl mb-6" outlined>
      <div class="font-weight-bold mb-3">Orders</div>
      <v-skeleton-loader v-if="loading" type="list-item-two-line@2" />
      <div v-else-if="!orders.length" class="text-body-2 text--secondary">No orders yet.</div>
      <div v-for="o in orders" :key="o.id" class="py-3 history-row">
        <div class="d-flex align-start">
          <div class="min-width-0 pr-3">
            <div class="font-weight-medium text-truncate">{{ o.merchantName || 'Store' }} · Order {{ shortRef(o.id) }}</div>
            <div class="text-caption text--secondary">{{ orderStatusLabel(o.status) }}<template v-if="o.status === 'paid'"> · {{ fulfillmentLabel(o.fulfillmentStatus) }}</template></div>
            <div v-if="o.cashPaymentCode" class="text-caption mt-1">
              Payment code: <strong class="payment-code">{{ o.cashPaymentCode }}</strong>
              <span class="text--secondary"> · give this to staff when you pay</span>
            </div>
          </div>
          <v-spacer />
          <div class="text-body-2 font-weight-medium text-no-wrap">{{ formatZar(o.totalZar) }}</div>
        </div>
        <div class="d-flex flex-wrap mt-2">
          <v-btn
            v-if="o.merchantSlug"
            text
            small
            class="text-none px-0 mr-4"
            :to="{ name: 'merchant-home', params: { merchantSlug: o.merchantSlug } }"
          >
            View store
          </v-btn>
          <v-btn
            v-if="o.canPayNow"
            small
            depressed
            color="primary"
            class="text-none font-weight-bold"
            :loading="payingId === o.id"
            @click="payOrder(o)"
          >
            Pay now
          </v-btn>
        </div>
      </div>
    </v-card>
    <v-card class="pa-4 rounded-xl" outlined>
      <div class="font-weight-bold mb-3">Bookings</div>
      <v-skeleton-loader v-if="loading" type="list-item-two-line@2" />
      <div v-else-if="!bookings.length" class="text-body-2 text--secondary">No bookings yet.</div>
      <div v-for="b in bookings" :key="b.id" class="py-3 history-row">
        <div class="d-flex align-start">
          <div class="min-width-0 pr-3">
            <div class="font-weight-medium text-truncate">{{ b.merchantName || 'Provider' }} · Booking {{ shortRef(b.id) }}</div>
            <div class="text-caption text--secondary">
              {{ b.serviceName || 'Service' }} · {{ bookingStatusLabel(b.status) }}
            </div>
            <div v-if="b.cashPaymentCode" class="text-caption mt-1">
              Payment code: <strong class="payment-code">{{ b.cashPaymentCode }}</strong>
              <span class="text--secondary"> · give this to staff when you pay</span>
            </div>
          </div>
          <v-spacer />
          <div class="text-caption">{{ formatDateTime(b.startAt) }}</div>
        </div>
        <div class="d-flex flex-wrap mt-2">
          <v-btn
            v-if="b.merchantSlug"
            text
            small
            class="text-none px-0 mr-4"
            :to="salonLink(b)"
          >
            View provider
          </v-btn>
          <v-btn
            v-if="b.canPayNow"
            small
            depressed
            color="primary"
            class="text-none font-weight-bold"
            :loading="payingId === b.id"
            @click="payBooking(b)"
          >
            Pay now
          </v-btn>
        </div>
      </div>
    </v-card>
  </v-container>
</template>

<script>
import { apiFetch } from '@/services/api'
import { getSessionUser, isClientUser } from '@/services/auth'
import { startClientBookingPayFast, startClientOrderPayFast } from '@/services/clientNearby'
import { startHostedCheckout } from '@/utils/payFastCheckout'
import { bookingStatusLabel, formatDateTime, fulfillmentLabel, orderStatusLabel, shortRef } from '@/utils/displayLabels'
import { formatZar } from '@/utils/price'

export default {
  name: 'ClientHistoryView',
  data() {
    return { orders: [], bookings: [], referralCode: '', error: '', payingId: '', loading: true }
  },
  async created() {
    const u = getSessionUser()
    if (!isClientUser(u)) {
      this.$router.replace({ name: 'client-login', query: { next: this.$route.fullPath } }).catch(() => {})
      return
    }
    try {
      const [orders, bookings, code] = await Promise.all([
        apiFetch('/api/clients/me/orders', { auth: true }),
        apiFetch('/api/clients/me/bookings', { auth: true }),
        apiFetch('/api/referrals/my-code', { auth: true })
      ])
      this.orders = Array.isArray(orders) ? orders : []
      this.bookings = Array.isArray(bookings) ? bookings : []
      this.referralCode = (code && code.referralCode) || ''
    } catch (e) {
      this.error = (e && e.message) || 'Could not load history.'
    } finally {
      this.loading = false
    }
  },
  methods: {
    bookingStatusLabel,
    formatDateTime,
    formatZar,
    shortRef,
    fulfillmentLabel,
    orderStatusLabel,
    salonLink(b) {
      if (b.serviceId) {
        return {
          name: 'merchant-salon-book',
          params: { merchantSlug: b.merchantSlug, serviceId: b.serviceId }
        }
      }
      return { name: 'merchant-salon-services', params: { merchantSlug: b.merchantSlug } }
    },
    async payOrder(o) {
      this.error = ''
      this.payingId = o.id
      try {
        const session = await startClientOrderPayFast(o.id)
        if (!startHostedCheckout(session)) this.error = 'Could not start payment.'
      } catch (e) {
        this.error = (e && e.message) || 'Could not start payment.'
      } finally {
        this.payingId = ''
      }
    },
    async payBooking(b) {
      this.error = ''
      this.payingId = b.id
      try {
        const session = await startClientBookingPayFast(b.id)
        if (!startHostedCheckout(session)) this.error = 'Could not start payment.'
      } catch (e) {
        this.error = (e && e.message) || 'Could not start payment.'
      } finally {
        this.payingId = ''
      }
    }
  }
}
</script>

<style scoped>
.history-row + .history-row {
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}
.payment-code {
  font-family: monospace;
  letter-spacing: 0.08em;
}
</style>
