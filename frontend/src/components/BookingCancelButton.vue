<template>
  <div v-if="booking && booking.canCancel" class="booking-cancel">
    <v-btn outlined small color="error" class="text-none" :loading="busy" @click="open = true">Cancel booking</v-btn>
    <v-dialog v-model="open" max-width="460">
      <v-card class="rounded-xl">
        <v-card-title class="text-h6">Cancel this booking?</v-card-title>
        <v-card-text>
          <p class="mb-0">{{ refundNotice }}</p>
          <v-alert v-if="error" type="error" dense outlined class="mt-4 mb-0 rounded-lg">{{ error }}</v-alert>
        </v-card-text>
        <v-card-actions>
          <v-btn text class="text-none" :disabled="busy" @click="open = false">Keep booking</v-btn>
          <v-spacer />
          <v-btn depressed color="error" class="text-none" :loading="busy" @click="confirm">Cancel booking</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import { cancellationOutcomeMessage, readBookingFee } from '@/utils/bookingFee'
import { formatZar } from '@/utils/price'

/**
 * Customer self cancel of a pay in store booking. The server decides the refund (BR-4, BR-5); the dialog only
 * tells the customer beforehand what the server reported (`refundableIfCancelledNow`). Emits `cancelled` with the
 * server's answer and the outcome message.
 */
export default {
  name: 'BookingCancelButton',
  props: {
    booking: { type: Object, default: null },
    /** async () => server answer of the cancel endpoint */
    cancel: { type: Function, required: true }
  },
  data() {
    return { open: false, busy: false, error: '' }
  },
  computed: {
    fee() {
      return readBookingFee(this.booking)
    },
    refundNotice() {
      const fee = this.fee
      if (!fee || !['paid', 'awaiting_payment'].includes(fee.status)) return 'Your time will be freed for someone else.'
      if (fee.status === 'awaiting_payment') return 'Your booking fee has not been paid, so nothing is charged.'
      return fee.refundableIfCancelledNow
        ? `Your ${formatZar(fee.feeZar)} booking fee will be refunded.`
        : `Your ${formatZar(fee.feeZar)} booking fee will not be refunded because the booking starts in less than 24 hours.`
    }
  },
  methods: {
    async confirm() {
      this.busy = true
      this.error = ''
      try {
        const res = await this.cancel()
        const fee = readBookingFee(res)
        this.open = false
        this.$emit('cancelled', {
          result: res,
          message: cancellationOutcomeMessage(res && res.bookingFeeOutcome, fee ? fee.feeZar : '')
        })
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not cancel the booking.'
      } finally {
        this.busy = false
      }
    }
  }
}
</script>
