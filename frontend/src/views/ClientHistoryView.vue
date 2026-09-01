<template>
  <v-container class="py-10 px-3 px-sm-4">
    <div class="d-flex align-center mb-2">
      <h1 class="text-h4 font-weight-bold mb-0">Your activity</h1>
      <v-spacer />
      <v-btn text class="text-none" to="/">Hub</v-btn>
    </div>
    <p class="text-body-2 text--secondary mb-6">Orders and salon bookings on your customer account.</p>
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
      <div v-if="!orders.length" class="text-body-2 text--secondary">No orders yet.</div>
      <div v-for="o in orders" :key="o.id" class="py-3 history-row">
        <div class="d-flex align-start">
          <div>
            <div class="font-weight-medium">{{ o.merchantSlug || 'Store' }}</div>
            <div class="text-caption text--secondary">{{ o.status }} ù {{ o.fulfillmentStatus }}</div>
          </div>
          <v-spacer />
          <div class="text-body-2 font-weight-medium">R {{ o.totalZar }}</div>
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
      <div v-if="!bookings.length" class="text-body-2 text--secondary">No bookings yet.</div>
      <div v-for="b in bookings" :key="b.id" class="py-3 history-row">
        <div class="d-flex align-start">
          <div>
            <div class="font-weight-medium">{{ b.merchantSlug || 'Salon' }}</div>
            <div class="text-caption text--secondary">
              {{ b.serviceName || 'Service' }} ù {{ b.status }}
            </div>
          </div>
          <v-spacer />
          <div class="text-caption">{{ b.startAt }}</div>
        </div>
        <div class="d-flex flex-wrap mt-2">
          <v-btn
            v-if="b.merchantSlug"
            text
            small
            class="text-none px-0 mr-4"
            :to="salonLink(b)"
          >
            View salon
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

export default {
  name: 'ClientHistoryView',
  data() {
    return { orders: [], bookings: [], referralCode: '', error: '', payingId: '' }
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
    }
  },
  methods: {
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
</style>
