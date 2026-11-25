<template>
  <div v-if="lines.length" class="booking-payment-summary">
    <div v-if="feeStatus" class="text-caption text--secondary mb-1">{{ feeStatus }}</div>
    <table class="booking-payment-summary__table">
      <tbody>
        <tr
          v-for="line in lines"
          :key="line.kind"
          :class="{ 'font-weight-bold': line.kind === 'total' }"
        >
          <td class="pr-4">{{ line.label }}</td>
          <td class="text-right">{{ line.amount }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import { bookingFeeStatusLabel, readBookingFee, receiptLines } from '@/utils/bookingFee'

/**
 * Receipt lines of a booking (FR-7): booking fee paid online, amount paid or due at the shop, refunded or kept fee,
 * and the total. `booking` is any API booking that carries `receiptLines` (and `bookingFee` when it has one).
 */
export default {
  name: 'BookingPaymentSummary',
  props: {
    booking: { type: Object, default: null }
  },
  computed: {
    lines() {
      return receiptLines(this.booking)
    },
    feeStatus() {
      const fee = readBookingFee(this.booking)
      return fee ? bookingFeeStatusLabel(fee.status, fee.reason) : ''
    }
  }
}
</script>

<style scoped>
.booking-payment-summary__table {
  border-collapse: collapse;
  font-size: 0.875rem;
}
.booking-payment-summary__table td {
  padding: 2px 0;
}
</style>
