<template>
  <div>
    <page-hero
      kicker="My account"
      kicker-icon="receipt_long"
      title="Your activity"
      lead="Orders and service bookings on your customer account."
      container-class="page-narrow"
    />

    <v-container class="admin-body page-narrow pb-12 px-3 px-sm-4">
      <v-alert v-if="error" type="error" dense outlined class="mb-4">{{ error }}</v-alert>

      <v-card class="admin-card pa-4 pa-sm-6 mb-6" elevation="3" rounded="xl">
        <div class="d-flex align-center flex-wrap">
          <div class="mr-4 min-width-0">
            <div class="card-label mb-1">Referral code</div>
            <p class="text-body-2 text--secondary mb-0">
              Share this code. First paid merchant subscription pays 15% commission.
            </p>
          </div>
          <v-spacer />
          <v-chip v-if="referralCode" label outlined class="mt-2 mt-sm-0 font-weight-bold">{{ referralCode }}</v-chip>
        </div>
      </v-card>

      <v-card class="admin-card pa-4 pa-sm-6 mb-6" elevation="3" rounded="xl">
        <div class="card-label mb-3">Orders</div>
        <v-skeleton-loader v-if="loading" type="list-item-two-line@2" />
        <empty-state
          v-else-if="!orders.length"
          icon="shopping_bag"
          title="No orders yet."
          text="Items you buy from a provider show up here."
        />
        <div v-for="o in orders" :key="o.id" class="py-3 history-row">
          <div class="d-flex align-start">
            <div class="min-width-0 pr-3">
              <div class="font-weight-medium text-truncate">{{ o.merchantName || 'Store' }} · Order {{ shortRef(o.id) }}</div>
              <div class="text-caption text--secondary">{{ orderStatusLabel(o.status) }}<template v-if="o.status === 'paid'"> · {{ fulfillmentLabel(o.fulfillmentStatus) }}</template></div>
              <div v-if="handoverCodeOf(o)" class="text-caption mt-1">
                Handover code: <strong class="payment-code">{{ handoverCodeOf(o) }}</strong>
                <span class="text--secondary"> · show this to staff when you collect or your delivery arrives</span>
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

      <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
        <div class="card-label mb-3">Bookings</div>
        <v-skeleton-loader v-if="loading" type="list-item-two-line@2" />
        <empty-state
          v-else-if="!bookings.length"
          icon="event_available"
          title="No bookings yet."
          text="Services you book with a provider show up here."
        />
        <div v-for="b in bookings" :key="b.id" class="py-3 history-row">
          <div class="d-flex align-start">
            <div class="min-width-0 pr-3">
              <div class="font-weight-medium text-truncate">{{ b.merchantName || 'Provider' }} · Booking {{ shortRef(b.id) }}</div>
              <div class="text-caption text--secondary">
                {{ b.serviceName || 'Service' }} · {{ bookingStatusLabel(b.status) }}
              </div>
              <div v-if="handoverCodeOf(b)" class="text-caption mt-1">
                Handover code: <strong class="payment-code">{{ handoverCodeOf(b) }}</strong>
                <span class="text--secondary"> · show this to staff when your service starts</span>
              </div>
            </div>
            <v-spacer />
            <div class="text-caption">{{ formatDateTime(b.startAt) }}</div>
          </div>
          <booking-payment-summary :booking="b" class="mt-2" />
          <v-alert v-if="cancelMessages[b.id]" type="success" dense outlined class="mt-2 mb-0 rounded-lg">
            {{ cancelMessages[b.id] }}
          </v-alert>
          <div class="d-flex flex-wrap align-center mt-2">
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
              {{ b.canPayBookingFee ? 'Pay booking fee' : 'Pay now' }}
            </v-btn>
            <booking-cancel-button
              :booking="b"
              :cancel="() => cancelClientBooking(b.id)"
              class="ml-4"
              @cancelled="(e) => onCancelled(b, e)"
            />
          </div>
        </div>
      </v-card>
    </v-container>
  </div>
</template>

<script>
import EmptyState from '@/components/layout/EmptyState.vue'
import PageHero from '@/components/layout/PageHero.vue'
import { apiFetch } from '@/services/api'
import { getSessionUser, isClientUser } from '@/services/auth'
import {
  cancelClientBooking,
  startClientBookingOnlinePayment,
  startClientOrderOnlinePayment
} from '@/services/clientNearby'
import BookingPaymentSummary from '@/components/BookingPaymentSummary.vue'
import BookingCancelButton from '@/components/BookingCancelButton.vue'
import { startOnlinePayment } from '@/utils/onlinePayment'
import { bookingStatusLabel, formatDateTime, fulfillmentLabel, orderStatusLabel, shortRef } from '@/utils/displayLabels'
import { formatZar } from '@/utils/price'
import { readHandoverCode } from '@/utils/handoverCode'

export default {
  name: 'ClientHistoryView',
  components: { BookingPaymentSummary, BookingCancelButton, EmptyState, PageHero },
  data() {
    return { orders: [], bookings: [], referralCode: '', error: '', payingId: '', loading: true, cancelMessages: {} }
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
    cancelClientBooking,
    onCancelled(b, { result, message }) {
      this.cancelMessages = { ...this.cancelMessages, [b.id]: message }
      this.bookings = this.bookings.map((row) => (row.id === b.id ? { ...row, ...result, canPayNow: false } : row))
    },
    handoverCodeOf(row) {
      return readHandoverCode(row)
    },
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
        const session = await startClientOrderOnlinePayment(o.id)
        if (!startOnlinePayment(session)) this.error = 'Could not start payment.'
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
        const session = await startClientBookingOnlinePayment(b.id)
        if (!startOnlinePayment(session)) this.error = 'Could not start payment.'
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
