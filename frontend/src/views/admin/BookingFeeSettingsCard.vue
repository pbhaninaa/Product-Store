<template>
  <v-card class="admin-card admin-card--settings pa-4 pa-sm-6" elevation="3" rounded="xl">
    <div class="card-label mb-2">Booking fee</div>
    <p class="text-body-2 text--secondary mb-4">
      Customers who book with cash at the shop pay this part of the price online when they book. The rest is paid in
      cash at the shop. Customers who pay the full price online never pay a booking fee. A fee of 0% means no fee.
    </p>
    <p class="text-body-2 text--secondary mb-4">
      Refund rules: cancelled at least 24 hours before the start, the fee is refunded. Cancelled later, or a no show,
      you keep the fee. If you cancel a booking, the fee is always refunded. A booking that is not paid within 2 hours
      is cancelled and the time is freed.
    </p>
    <v-alert v-if="!canEdit" type="info" dense outlined class="mb-4 rounded-lg">
      Only the store owner or staff allowed to change store settings can change the booking fee.
    </v-alert>
    <v-form ref="form" :disabled="!canEdit" @submit.prevent="save">
      <v-text-field
        v-model="percentDraft"
        outlined
        label="Booking fee"
        suffix="%"
        inputmode="numeric"
        hint="A whole number from 0% to 50%."
        persistent-hint
        :rules="[percentRule]"
        :loading="loading"
        :disabled="saving || loading"
        class="rounded-lg"
      />
      <p v-if="example" class="text-body-2 mt-3 mb-0">
        Example: on a {{ example.total }} booking the customer pays {{ example.fee }} online and {{ example.remainder }}
        at the shop.
      </p>
      <v-btn
        v-if="canEdit"
        block
        depressed
        color="primary"
        class="text-none font-weight-bold btn-amber mt-4"
        :loading="saving"
        :disabled="loading"
        type="submit"
      >
        Save booking fee
      </v-btn>
    </v-form>
    <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>
    <v-alert v-if="saved" type="success" dense outlined class="mt-4 rounded-lg">
      Booking fee saved. It applies to new cash bookings; existing bookings keep their fee.
    </v-alert>
  </v-card>
</template>

<script>
import adminAccessMixin from './mixins/adminAccessMixin'
import { fetchAdminStoreSettings, updateAdminBookingFee } from '@/services/adminApi'
import { bookingFeePercentError, bookingFeePreview } from '@/utils/bookingFee'
import { formatZar } from '@/utils/price'

export default {
  name: 'BookingFeeSettingsCard',
  mixins: [adminAccessMixin],
  data() {
    return {
      percentDraft: '0',
      loading: false,
      saving: false,
      error: '',
      saved: false
    }
  },
  computed: {
    canEdit() {
      return this.can('STORE_SETTINGS')
    },
    example() {
      const preview = bookingFeePreview('1000.00', Number(this.percentDraft))
      if (bookingFeePercentError(this.percentDraft) || !preview) return null
      return {
        total: formatZar(preview.totalZar),
        fee: formatZar(preview.feeZar),
        remainder: formatZar(preview.remainderZar)
      }
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    percentRule(v) {
      return bookingFeePercentError(v) || true
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        const p = Number(s && s.bookingFeePercent)
        this.percentDraft = String(Number.isInteger(p) ? p : 0)
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not load the booking fee.'
      } finally {
        this.loading = false
      }
    },
    async save() {
      this.saved = false
      this.error = bookingFeePercentError(this.percentDraft)
      if (this.error) return
      this.saving = true
      try {
        const res = await updateAdminBookingFee(this.$route, this.percentDraft.trim())
        this.percentDraft = String(res && res.bookingFeePercent != null ? res.bookingFeePercent : this.percentDraft.trim())
        this.saved = true
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not save the booking fee.'
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
