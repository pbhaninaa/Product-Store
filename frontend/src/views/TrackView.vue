<template>
  <v-container class="py-10 py-md-16 px-3 px-sm-4 track-page">
    <div class="section-kicker mb-2">Your activity</div>
    <h1 class="text-h4 font-weight-bold mb-2">Track order or booking</h1>
    <p class="text-body-2 text--secondary mb-8" style="max-width: 40rem">
      Enter the reference from your confirmation email or receipt, plus the same email you used at checkout.
    </p>

    <v-card class="pa-6 pa-md-8 rounded-xl mb-8" elevation="2">
      <v-radio-group v-model="kind" row hide-details class="mt-0 mb-4">
        <v-radio label="Product order" value="order" />
        <v-radio label="Salon booking" value="booking" />
      </v-radio-group>
      <v-text-field
        v-model="lookupId"
        outlined
        label="Order or booking ID"
        hide-details="auto"
        class="rounded-lg mb-4"
      />
      <v-text-field
        v-model="email"
        outlined
        type="email"
        label="Email used at checkout"
        hide-details="auto"
        class="rounded-lg mb-6"
      />
      <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg">{{ error }}</v-alert>
      <v-btn
        color="primary"
        depressed
        large
        class="text-none font-weight-bold"
        :loading="loading"
        :disabled="!canLookup"
        @click="lookup"
      >
        Look up
      </v-btn>
    </v-card>

    <v-card v-if="result" class="pa-6 pa-md-8 rounded-xl" elevation="2">
      <div class="d-flex flex-wrap align-center mb-4">
        <v-chip small label outlined :color="statusColor" class="text-none mr-3 mb-2">{{ statusLabel }}</v-chip>
        <div class="text-caption text--secondary font-mono mb-2">{{ result.id }}</div>
      </div>
      <p class="text-body-1 font-weight-medium mb-1">{{ result.customerName || result.customer_name }}</p>
      <p v-if="kind === 'order'" class="text-body-2 text--secondary mb-4">
        {{ formatZar(result.totalZar || result.total_zar) }}
        ·
        {{ (result.deliveryType || result.delivery_type) === 'delivery' ? 'Delivery' : 'Pickup' }}
      </p>
      <p v-else class="text-body-2 text--secondary mb-4">
        {{ result.serviceName || 'Salon booking' }}
        <span v-if="result.startAt"> · {{ formatWhen(result.startAt) }}</span>
      </p>

      <ul v-if="kind === 'order' && orderLines.length" class="mb-6 pl-4">
        <li v-for="(line, i) in orderLines" :key="i" class="text-body-2 mb-1">
          {{ lineName(line) }} × {{ line.quantity }}
        </li>
      </ul>

      <v-alert v-if="result.cashPaymentCode" type="info" dense outlined class="mb-6 rounded-lg">
        Give this payment code to staff: <strong>{{ result.cashPaymentCode }}</strong>
      </v-alert>

      <template v-if="canRate">
        <div class="card-label mb-2">Rate this shop</div>
        <v-rating v-model="rating" color="amber" background-color="grey lighten-1" hover class="mb-3" />
        <v-textarea v-model="comment" outlined dense rows="3" label="Comment (optional)" class="mb-4 rounded-lg" />
        <v-alert v-if="reviewError" type="error" dense outlined class="mb-4 rounded-lg">{{ reviewError }}</v-alert>
        <v-btn color="primary" class="text-none font-weight-bold" :loading="reviewSending" @click="sendReview">
          Submit review
        </v-btn>
      </template>
      <v-alert v-else-if="alreadyRated" type="success" dense outlined class="mb-0 rounded-lg">
        Thanks — you already rated this {{ kind === 'booking' ? 'booking' : 'order' }}.
      </v-alert>
    </v-card>
  </v-container>
</template>

<script>
import { lookupPublicOrder, fetchReviewRated, submitPublicReview } from '@/services/publicStore'
import { lookupPublicBooking } from '@/services/salonPublic'
import { formatZar } from '@/utils/price'

const STORAGE_KEY = 'productstore_last_lookup'

export default {
  name: 'TrackView',
  data() {
    return {
      kind: 'order',
      lookupId: '',
      email: '',
      loading: false,
      error: '',
      result: null,
      alreadyRated: false,
      rating: 5,
      comment: '',
      reviewSending: false,
      reviewError: ''
    }
  },
  computed: {
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    canLookup() {
      return this.lookupId.trim().length >= 8 && this.email.trim().includes('@')
    },
    orderLines() {
      if (!this.result) return []
      return this.result.order_items || this.result.items || []
    },
    statusLabel() {
      if (!this.result) return ''
      if (this.kind === 'booking') {
        const s = String(this.result.status || '').toLowerCase()
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
    if (q.id) this.lookupId = String(q.id)
    if (q.email) this.email = String(q.email)
    if (!this.lookupId || !this.email) {
      try {
        const raw = localStorage.getItem(STORAGE_KEY)
        const saved = raw ? JSON.parse(raw) : null
        if (saved && saved.slug === this.merchantSlug) {
          if (!this.kind && saved.kind) this.kind = saved.kind
          if (!this.lookupId && saved.id) this.lookupId = saved.id
          if (!this.email && saved.email) this.email = saved.email
        }
      } catch {
        // ignore
      }
    }
    if (this.canLookup) this.lookup()
  },
  methods: {
    formatZar,
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
    async lookup() {
      if (!this.canLookup) return
      this.loading = true
      this.error = ''
      this.result = null
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
