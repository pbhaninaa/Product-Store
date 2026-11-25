<template>
  <div>
    <page-hero
      kicker="Your activity"
      kicker-icon="local_shipping"
      title="Track order or booking"
      lead="Enter the reference from your confirmation email or receipt, plus the same email you used at checkout."
      container-class="page-narrow"
    />

    <v-container class="admin-body page-narrow pb-12 pb-md-16 px-3 px-sm-4 track-page">
      <v-card class="admin-card pa-6 pa-md-8 rounded-xl mb-6" elevation="3">
        <v-radio-group v-model="kind" row hide-details class="mt-0 mb-4">
          <v-radio label="Product order" value="order" />
          <v-radio label="Service booking" value="booking" />
        </v-radio-group>
        <v-form ref="lookupForm" @submit.prevent>
          <v-text-field
            v-model="lookupId"
            outlined
            label="Order or booking reference *"
            hide-details="auto"
            class="rounded-lg mb-4"
            :rules="[rules.required('Reference'), lookupIdRule]"
          />
          <v-text-field
            v-model="email"
            outlined
            type="email"
            label="Email used at checkout *"
            hide-details="auto"
            class="rounded-lg mb-6"
            :rules="[rules.required('Email'), rules.email()]"
          />
        </v-form>
        <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">{{ error }}</v-alert>
        <v-btn
          color="primary"
          depressed
          large
          class="text-none font-weight-bold"
          :loading="loading"
          @click="submitLookup"
        >
          Look up
        </v-btn>
      </v-card>

      <v-card v-if="result" class="admin-card pa-6 pa-md-8 rounded-xl" elevation="3">
        <div class="d-flex flex-wrap align-center mb-4">
          <v-chip small label outlined :color="statusColor" class="text-none mr-3 mb-2">{{ statusLabel }}</v-chip>
          <div class="text-caption text--secondary mb-2">Reference {{ shortRef(result.id) }}</div>
        </div>
        <p class="text-body-1 font-weight-medium mb-1">{{ result.customerName || result.customer_name }}</p>
        <p v-if="kind === 'order'" class="text-body-2 text--secondary mb-4">
          {{ formatZar(result.totalZar || result.total_zar) }}
          ·
          {{ (result.deliveryType || result.delivery_type) === 'delivery' ? 'Delivery' : 'Pickup' }}
        </p>
        <p v-else class="text-body-2 text--secondary mb-4">
          {{ result.serviceName || 'Service booking' }}
          <span v-if="result.startAt"> · {{ formatWhen(result.startAt) }}</span>
        </p>

        <ul v-if="kind === 'order' && orderLines.length" class="mb-6 pl-4">
          <li v-for="(line, i) in orderLines" :key="i" class="text-body-2 mb-1">
            {{ lineName(line) }} × {{ line.quantity }}
          </li>
        </ul>

        <template v-if="kind === 'booking'">
          <booking-payment-summary :booking="result" class="mb-4" />
          <v-alert v-if="result.canPayBookingFee" type="warning" dense outlined class="mb-4 rounded-lg">
            Your booking fee is not paid yet. Pay it within 2 hours of booking, or the booking is cancelled.
          </v-alert>
          <v-alert v-if="cancelMessage" type="success" dense outlined class="mb-4 rounded-lg">{{ cancelMessage }}</v-alert>
          <booking-cancel-button :booking="result" :cancel="cancelBooking" class="mb-4" @cancelled="onCancelled" />
        </template>

        <v-alert v-if="handoverCode" type="info" dense outlined class="mb-6 rounded-lg">
          Your handover code: <strong class="handover-code">{{ handoverCode }}</strong>
          <div class="text-body-2 mt-1">{{ handoverCodeHint }}</div>
        </v-alert>

        <template v-if="canRate">
          <div class="card-label mb-2">Rate this provider</div>
          <v-form ref="reviewForm" @submit.prevent>
            <v-rating v-model="rating" color="amber" background-color="grey lighten-1" hover class="mb-3" />
            <p v-if="ratingError" class="text-caption error--text mt-n2 mb-3">{{ ratingError }}</p>
            <v-textarea
              v-model="comment"
              outlined
              dense
              rows="3"
              label="Comment (optional)"
              class="mb-4 rounded-lg"
              :rules="[rules.maxLength(LIMITS.REVIEW_MAX, 'Comment')]"
            />
          </v-form>
          <v-alert v-if="reviewError" type="error" dense outlined class="mb-4 rounded-lg">{{ reviewError }}</v-alert>
          <v-btn color="primary" class="text-none font-weight-bold" :loading="reviewSending" @click="sendReview">
            Submit review
          </v-btn>
        </template>
        <v-alert v-else-if="alreadyRated" type="success" dense outlined class="mb-0 rounded-lg">
          Thanks, you already rated this {{ kind === 'booking' ? 'booking' : 'order' }}.
        </v-alert>
      </v-card>
    </v-container>
  </div>
</template>

<script>
import { lookupPublicOrder, fetchReviewRated, submitPublicReview } from '@/services/publicStore'
import { cancelPublicBooking, lookupPublicBooking } from '@/services/salonPublic'
import BookingPaymentSummary from '@/components/BookingPaymentSummary.vue'
import BookingCancelButton from '@/components/BookingCancelButton.vue'
import PageHero from '@/components/layout/PageHero.vue'
import { formatZar } from '@/utils/price'
import { shortRef } from '@/utils/displayLabels'
import { handoverCodeHint, readHandoverCode } from '@/utils/handoverCode'
import { LIMITS, isBlank, rules, validateForm } from '@/utils/validation'

const STORAGE_KEY = 'productstore_last_lookup'
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** The 8-character reference from the confirmation (e.g. A1B2C3D4); a full id from an old link also works. */
function lookupIdRule(v) {
  const s = String(v || '').trim()
  const short = s.replace(/[\s#-]+/g, '')
  return (
    isBlank(v) ||
    UUID_RE.test(s) ||
    /^[0-9a-f]{8}$/i.test(short) ||
    'Enter the 8 character reference from your confirmation, e.g. A1B2C3D4'
  )
}

export default {
  name: 'TrackView',
  components: { BookingPaymentSummary, BookingCancelButton, PageHero },
  data() {
    return {
      kind: 'order',
      lookupId: '',
      email: '',
      loading: false,
      error: '',
      result: null,
      cancelMessage: '',
      alreadyRated: false,
      rating: 5,
      comment: '',
      reviewSending: false,
      reviewError: '',
      ratingTouched: false,
      rules,
      LIMITS,
      lookupIdRule
    }
  },
  computed: {
    handoverCode() {
      return readHandoverCode(this.result)
    },
    handoverCodeHint() {
      const r = this.result || {}
      return handoverCodeHint({
        kind: this.kind,
        paymentMethod: r.paymentMethod || r.payment_method || r.clientPaymentMethod,
        status: r.status
      })
    },
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    canLookup() {
      return lookupIdRule(this.lookupId) === true && !isBlank(this.lookupId) && rules.email()(this.email) === true && !isBlank(this.email)
    },
    ratingError() {
      if (!this.ratingTouched) return ''
      const r = Number(this.rating)
      return Number.isInteger(r) && r >= 1 && r <= 5 ? '' : 'Choose a rating from 1 to 5 stars'
    },
    orderLines() {
      if (!this.result) return []
      return this.result.order_items || this.result.items || []
    },
    statusLabel() {
      if (!this.result) return ''
      if (this.kind === 'booking') {
        const s = String(this.result.status || '').toLowerCase()
        const fee = this.result.bookingFee
        if (s === 'pending' && fee && fee.status === 'paid') return 'Confirmed, pay the rest at the shop'
        if (this.result.noShow) return 'Missed (no show)'
        const map = {
          pending: 'Pending payment',
          confirmed: 'Paid / confirmed',
          in_progress: 'In progress',
          completed: 'Completed',
          cancelled: 'Cancelled'
        }
        return map[s] || s
      }
      const st = String(this.result.status || '').toLowerCase()
      if (st === 'cancelled') return 'Cancelled'
      if (st !== 'paid') return 'Pending payment'
      const f = String(this.result.fulfillmentStatus || this.result.fulfillment_status || 'processing').toLowerCase()
      const d = (this.result.deliveryType || this.result.delivery_type) === 'delivery'
      if (f === 'completed') return 'Completed'
      if (f === 'ready') return d ? 'Out for delivery' : 'Ready for pickup'
      return 'Processing'
    },
    statusColor() {
      const t = this.statusLabel.toLowerCase()
      if (t.includes('completed')) return 'success'
      if (t.includes('pending') || t.includes('cancelled')) return 'warning'
      return 'primary'
    },
    canRate() {
      if (!this.result || this.alreadyRated) return false
      if (this.kind === 'booking') return String(this.result.status || '').toLowerCase() === 'completed'
      return String(this.result.fulfillmentStatus || this.result.fulfillment_status || '').toLowerCase() === 'completed'
    }
  },
  created() {
    const q = this.$route.query || {}
    if (q.kind === 'booking' || q.kind === 'order') this.kind = q.kind
    if (q.id) this.lookupId = shortRef(q.id)
    if (q.email) this.email = String(q.email)
    if (!this.lookupId || !this.email) {
      try {
        const raw = localStorage.getItem(STORAGE_KEY)
        const saved = raw ? JSON.parse(raw) : null
        if (saved && saved.slug === this.merchantSlug) {
          if (!this.kind && saved.kind) this.kind = saved.kind
          if (!this.lookupId && saved.id) this.lookupId = shortRef(saved.id)
          if (!this.email && saved.email) this.email = saved.email
        }
      } catch {
        // ignore
      }
    }
    if (this.canLookup) this.lookup()
  },
  methods: {
    shortRef,
    formatZar,
    cancelBooking() {
      return cancelPublicBooking(this.merchantSlug, this.result.id, this.email.trim())
    },
    onCancelled({ result, message }) {
      this.cancelMessage = message
      this.result = { ...this.result, ...result }
    },
    formatWhen(iso) {
      if (!iso) return ''
      try {
        return new Date(iso).toLocaleString()
      } catch {
        return String(iso)
      }
    },
    lineName(line) {
      if (line.products && line.products.name) return line.products.name
      return 'Item'
    },
    persist() {
      try {
        localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({
            slug: this.merchantSlug,
            kind: this.kind,
            id: this.lookupId.trim(),
            email: this.email.trim()
          })
        )
      } catch {
        // ignore
      }
    },
    submitLookup() {
      if (!validateForm(this.$refs.lookupForm)) return
      this.lookup()
    },
    async lookup() {
      if (!this.canLookup) return
      this.loading = true
      this.error = ''
      this.result = null
      this.cancelMessage = ''
      this.alreadyRated = false
      this.reviewError = ''
      try {
        this.result =
          this.kind === 'booking'
            ? await lookupPublicBooking(this.merchantSlug, this.lookupId.trim(), this.email.trim())
            : await lookupPublicOrder(this.merchantSlug, this.lookupId.trim(), this.email.trim())
        this.persist()
        this.alreadyRated = await fetchReviewRated(this.merchantSlug, this.kind, this.result.id)
        this.$router
          .replace({
            query: { kind: this.kind, id: this.lookupId.trim(), email: this.email.trim() }
          })
          .catch(() => {})
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not find that order. Check the ID and email.'
      } finally {
        this.loading = false
      }
    },
    async sendReview() {
      if (!this.canRate) return
      this.ratingTouched = true
      const formOk = validateForm(this.$refs.reviewForm)
      if (!formOk || this.ratingError) return
      this.reviewSending = true
      this.reviewError = ''
      try {
        await submitPublicReview(this.merchantSlug, {
          kind: this.kind,
          id: this.result.id,
          customerEmail: this.email.trim(),
          rating: this.rating,
          comment: this.comment
        })
        this.alreadyRated = true
      } catch (e) {
        this.reviewError = e && e.message ? e.message : 'Could not submit review.'
      } finally {
        this.reviewSending = false
      }
    }
  }
}
</script>
