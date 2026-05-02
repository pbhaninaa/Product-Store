<template>
  <div>
    <v-alert v-if="user && !merchantIsSalon" type="warning" outlined class="rounded-lg mb-6">
      Bookings are available when the store type is <strong>Salon + store</strong> or <strong>Salon only</strong> under
      <strong>Store</strong>, then reload.
    </v-alert>

    <template v-else-if="user">
      <v-card class="admin-card pa-4 pa-md-5 mb-4" elevation="3" rounded="xl">
        <div class="d-flex flex-column flex-md-row align-start">
          <v-avatar color="primary" size="48" class="mb-3 mb-md-0 mr-md-4">
            <v-icon dark>event_note</v-icon>
          </v-avatar>
          <div>
            <div class="text-h6 font-weight-bold mb-1">Salon bookings</div>
            <p class="text-body-2 text--secondary mb-0">
              Customer appointments from your public booking page. <strong>Orders</strong> remains the place for product
              checkouts when you run <strong>Salon + store</strong>.
            </p>
          </div>
        </div>
      </v-card>

      <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
        <div class="d-flex align-center flex-wrap mb-2">
          <div class="card-label mb-0">Upcoming and past</div>
          <div v-if="!loading && rows.length && filteredRows.length" class="text-caption text--secondary ml-sm-2 mt-1 mt-sm-0">
            {{ filteredRows.length }} booking{{ filteredRows.length === 1 ? '' : 's' }}
            <span v-if="filteredRows.length !== rows.length" class="text--disabled">({{ rows.length }} total)</span>
          </div>
          <v-spacer />
          <v-progress-circular v-if="loading" indeterminate size="22" width="2" color="accent" />
        </div>
        <p class="text-caption text--secondary mb-4">
          Salon appointments appear here. Use filters to find specific bookings. For <strong>EFT</strong>, customers
          upload proof; confirm payments after verifying transfers.
        </p>

        <v-alert v-if="actionError && !bookingCashDialogOpen" type="error" dense outlined class="mb-4 rounded-lg">{{
          actionError
        }}</v-alert>

        <v-progress-linear v-if="listRefreshing" indeterminate height="3" rounded class="mb-2" />

        <div v-if="!loading && !rows.length" class="muted-panel rounded-lg pa-8 text-center">
          <v-icon size="40" color="secondary" class="mb-3">event_busy</v-icon>
          <div class="text-subtitle-1 font-weight-bold mb-1">No bookings yet</div>
          <div class="text-body-2 text--secondary">When customers book a slot, it will show up here.</div>
        </div>

        <template v-else-if="rows.length">
          <div class="bookings-toolbar mb-4">
            <v-text-field
              v-model.trim="search"
              dense
              outlined
              hide-details
              clearable
              label="Search bookings"
              placeholder="Name, email, service, phone…"
              prepend-inner-icon="search"
              class="bookings-search rounded-lg"
            />
            <v-select
              v-model="statusFilter"
              :items="statusItems"
              item-text="text"
              item-value="value"
              dense
              outlined
              hide-details
              clearable
              label="Status"
              prepend-inner-icon="flag"
              class="bookings-filter rounded-lg"
            />
            <v-select
              v-model="paymentMethodFilter"
              :items="paymentMethodItems"
              item-text="text"
              item-value="value"
              dense
              outlined
              hide-details
              clearable
              label="Payment"
              prepend-inner-icon="payment"
              class="bookings-filter rounded-lg"
            />
            <v-select
              v-model="verificationFilter"
              :items="verificationItems"
              item-text="text"
              item-value="value"
              dense
              outlined
              hide-details
              clearable
              label="Verification"
              prepend-inner-icon="verified"
              class="bookings-filter rounded-lg"
            />
          </div>

          <div v-if="!filteredRows.length" class="muted-panel rounded-lg pa-6 text-center mb-4">
            <v-icon size="36" color="secondary" class="mb-2">manage_search</v-icon>
            <div class="text-subtitle-2 font-weight-bold mb-1">No matching bookings</div>
            <div class="text-body-2 text--secondary mb-0">Try different search words or clear the filters.</div>
          </div>

          <v-data-table
            v-else
            :headers="headers"
            :items="filteredRows"
            :items-per-page="12"
            class="rounded-lg elevation-0 bookings-table"
            no-data-text="No rows match your filters."
          >
            <template v-slot:[`item.startLabel`]="{ item }">
              <div class="font-weight-medium">{{ item.startLabel }}</div>
              <div class="text-caption text--secondary">{{ item.endLabel }}</div>
            </template>
            <template v-slot:[`item.customer`]="{ item }">
              <div class="font-weight-medium">{{ item.customerName }}</div>
              <div class="text-caption text--secondary">{{ item.contactLabel }}</div>
            </template>
            <template v-slot:[`item.status`]="{ item }">
              <v-chip small label outlined :color="statusColor(item.status)" class="text-none">
                {{ statusDisplay(item.status) }}
              </v-chip>
            </template>
            <template v-slot:[`item.payment`]="{ item }">
              <span class="text-body-2">{{ paymentMethodLabel(item) }}</span>
              <div v-if="item.paymentProofUrl" class="mt-1">
                <a :href="item.paymentProofUrl" target="_blank" rel="noopener" class="text-caption primary--text"
                  >View proof</a
                >
              </div>
            </template>
            <template v-slot:[`item.verification`]="{ item }">
              <span class="text-body-2">{{ verificationLabel(item) }}</span>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-menu offset-y left>
                <template #activator="{ on, attrs }">
                  <v-btn icon small v-bind="attrs" v-on="on">
                    <v-icon small>more_vert</v-icon>
                  </v-btn>
                </template>
                <v-list dense class="py-0">
                  <v-list-item
                    v-if="showConfirmCashBooking(item)"
                    @click="openBookingCashDialog(item)"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="success">verified</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Confirm payment</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="showConfirmBooking(item)"
                    @click="setStatus(item, 'confirmed')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="success">verified</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Confirm booking</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="showEftPaymentReview(item)"
                    @click="decideEft(item, 'approve')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="success">check_circle</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Approve EFT</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="showEftPaymentReview(item)"
                    @click="decideEft(item, 'reject')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="error">cancel</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Reject EFT</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-divider v-if="bookingIsStrictlyPending(item)" />
                  <v-list-item
                    v-if="bookingIsStrictlyPending(item)"
                    @click="setStatus(item, 'cancelled')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="error">cancel</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Cancel booking</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="bookingIsStrictlyPending(item)"
                    @click="openDeleteBookingDialog(item)"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="error">delete_forever</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Delete permanently</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                </v-list>
              </v-menu>
            </template>
          </v-data-table>
        </template>
      </v-card>

      <v-dialog
        v-model="deleteBookingDialogOpen"
        max-width="480"
        content-class="rounded-xl"
        :persistent="Boolean(actionId && actionKind === 'delete')"
        @click:outside="closeDeleteBookingDialogIfIdle"
      >
        <v-card v-if="deleteBookingTarget" class="pa-8 rounded-xl">
          <h2 class="text-h6 font-weight-bold mb-3">Delete booking permanently?</h2>
          <p class="text-body-2 text--secondary mb-2">{{ deleteBookingTarget.customerName }}</p>
          <p class="text-body-2 mb-6">
            This removes the row from your database. Only possible while the booking is still <strong>pending</strong>.
          </p>
          <div class="d-flex flex-wrap justify-end" style="gap: 10px">
            <v-btn text class="text-none" :disabled="Boolean(actionId)" @click="closeDeleteBookingDialogIfIdle">
              Back
            </v-btn>
            <v-btn
              depressed
              color="error"
              class="text-none white--text font-weight-bold"
              :loading="Boolean(actionId && actionKind === 'delete')"
              @click="confirmDeleteBookingPermanent"
            >
              Delete permanently
            </v-btn>
          </div>
        </v-card>
      </v-dialog>

      <v-dialog
        v-model="bookingCashDialogOpen"
        max-width="440"
        content-class="rounded-xl"
        :persistent="Boolean(actionId && actionKind === 'cash-confirm')"
        @click:outside="closeBookingCashDialogIfIdle"
      >
        <v-card v-if="bookingCashTarget" class="pa-8 rounded-xl">
          <h2 class="text-h6 font-weight-bold mb-3">Confirm cash payment</h2>
          <p class="text-body-2 text--secondary mb-4">
            Enter the <strong>payment code</strong> the customer received when they booked. If it matches, this booking
            is marked <strong>Paid</strong>.
          </p>
          <v-text-field
            v-model="bookingCashCodeInput"
            outlined
            dense
            hide-details="auto"
            label="Customer payment code"
            placeholder="e.g. 6 digits"
            class="rounded-lg mb-2"
            autocomplete="one-time-code"
            inputmode="numeric"
            @keyup.enter="submitBookingCashConfirm"
          />
          <v-alert v-if="actionError" type="error" dense outlined class="mt-2 mb-0 rounded-lg">{{ actionError }}</v-alert>
          <div class="d-flex flex-wrap justify-end mt-6" style="gap: 10px">
            <v-btn text class="text-none" :disabled="Boolean(actionId)" @click="closeBookingCashDialogIfIdle">
              Back
            </v-btn>
            <v-btn
              depressed
              color="success"
              class="text-none white--text font-weight-bold"
              :loading="Boolean(actionId && actionKind === 'cash-confirm')"
              @click="submitBookingCashConfirm"
            >
              Confirm payment
            </v-btn>
          </div>
        </v-card>
      </v-dialog>
    </template>
  </div>
</template>

<script>
import { fetchAdminStoreSettings } from '@/services/adminApi'
import { isSalonShopType } from '@/services/shopType'
import {
  deleteAdminSalonBooking,
  fetchAdminSalonBookings,
  postAdminSalonBookingCashConfirm,
  postAdminSalonBookingEftPaymentDecision,
  updateAdminSalonBookingStatus
} from '@/services/salonAdmin'

export default {
  name: 'AdminSalonBookingsView',
  inject: {
    adminSession: { default: null }
  },
  data() {
    return {
      merchantIsSalon: false,
      loading: false,
      rows: [],
      search: '',
      statusFilter: '',
      paymentMethodFilter: '',
      verificationFilter: '',
      statusItems: [
        { text: 'Pending', value: 'pending' },
        { text: 'Paid', value: 'confirmed' },
        { text: 'Cancelled', value: 'cancelled' }
      ],
      paymentMethodItems: [
        { text: 'EFT', value: 'eft' },
        { text: 'Pay in store', value: 'cash_store' }
      ],
      verificationItems: [
        { text: 'Awaiting proof', value: 'awaiting_proof' },
        { text: 'Auto-verified', value: 'auto_verified' },
        { text: 'Manual review', value: 'manual_pending' },
        { text: 'Approved (manual)', value: 'manual_approved' },
        { text: 'Rejected', value: 'manual_rejected' }
      ],
      bookingCashDialogOpen: false,
      bookingCashTarget: null,
      bookingCashCodeInput: '',
      actionId: '',
      actionKind: '',
      actionError: '',
      listRefreshing: false,
      deleteBookingDialogOpen: false,
      deleteBookingTarget: null
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user ? this.adminSession.user : null
    },
    headers() {
      return [
        { text: 'When', value: 'startLabel', sortable: false },
        { text: 'Service', value: 'serviceName' },
        { text: 'Customer', value: 'customer', sortable: false },
        { text: 'Payment', value: 'payment', sortable: false },
        { text: 'Verification', value: 'verification', sortable: false },
        { text: 'Status', value: 'status', sortable: false },
        { text: '', value: 'actions', sortable: false, width: 1 }
      ]
    },
    filteredRows() {
      const q = String(this.search || '').trim().toLowerCase()
      const st = String(this.statusFilter || '').trim().toLowerCase()
      const pm = String(this.paymentMethodFilter || '').trim().toLowerCase()
      const vf = String(this.verificationFilter || '').trim().toLowerCase()
      return (this.rows || []).filter((r) => {
        if (st && String(r.status || '').toLowerCase() !== st) return false
        if (pm && String(r.clientPaymentMethod || '').toLowerCase() !== pm) return false
        if (vf && String(r.paymentVerificationState || '').toLowerCase() !== vf) return false
        if (!q) return true
        const hay = [
          r.serviceName,
          r.customerName,
          r.customerEmail,
          r.customerPhone,
          r.id,
          r.startLabel,
          r.contactLabel,
          r.clientPaymentMethod,
          r.paymentVerificationState,
          r.cashPaymentCode
        ]
          .join(' ')
          .toLowerCase()
        return hay.includes(q)
      })
    }
  },
  watch: {
    user(u) {
      if (u) this.bootstrap()
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.bootstrap()
    }
  },
  created() {
    if (this.user) this.bootstrap()
  },
  methods: {
    statusColor(status) {
      const s = String(status || '').toLowerCase()
      if (s === 'confirmed') return 'success'
      if (s === 'cancelled') return 'secondary'
      return 'warning'
    },
    paymentMethodLabel(item) {
      const m = String(item.clientPaymentMethod || '').toLowerCase()
      if (m === 'cash_store') return 'Pay in store'
      if (m === 'eft') return 'EFT'
      return '\u2014'
    },
    verificationLabel(item) {
      const v = String(item.paymentVerificationState || '').toLowerCase()
      const map = {
        not_applicable: '\u2014',
        awaiting_proof: 'Awaiting proof',
        auto_verified: 'Auto-verified',
        manual_pending: 'Manual review',
        manual_approved: 'Approved (manual)',
        manual_rejected: 'Rejected'
      }
      return map[v] || (v ? v : '\u2014')
    },
    statusDisplay(status) {
      const s = String(status || '').toLowerCase()
      if (s === 'confirmed') return 'Paid'
      if (s === 'pending') return 'Pending'
      if (s === 'cancelled') return 'Cancelled'
      return s || '\u2014'
    },
    bookingIsStrictlyPending(item) {
      return String(item.status || '').toLowerCase() === 'pending'
    },
    showConfirmCashBooking(item) {
      return (
        this.bookingIsStrictlyPending(item) && String(item.clientPaymentMethod || '').toLowerCase() === 'cash_store'
      )
    },
    showConfirmBooking(item) {
      if (!this.bookingIsStrictlyPending(item)) return false
      const pm = String(item.clientPaymentMethod || '').toLowerCase()
      if (pm === 'cash_store') return false
      const pv = String(item.paymentVerificationState || '').toLowerCase()
      if (pm === 'eft' && (pv === 'awaiting_proof' || pv === 'manual_pending')) return false
      return true
    },
    showEftPaymentReview(item) {
      if (String(item.status || '').toLowerCase() !== 'pending') return false
      const pm = String(item.clientPaymentMethod || '').toLowerCase()
      const pv = String(item.paymentVerificationState || '').toLowerCase()
      return pm === 'eft' && pv === 'manual_pending'
    },
    formatRange(isoStart, isoEnd) {
      try {
        const a = new Date(isoStart)
        const b = new Date(isoEnd)
        const dOpts = { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }
        const tOpts = { hour: '2-digit', minute: '2-digit' }
        const sameDay = a.toDateString() === b.toDateString()
        return {
          startLabel: `${a.toLocaleString(undefined, { ...dOpts, ...tOpts })}`,
          endLabel: sameDay
            ? `Until ${b.toLocaleTimeString(undefined, tOpts)}`
            : b.toLocaleString(undefined, { ...dOpts, ...tOpts })
        }
      } catch {
        return { startLabel: String(isoStart || ''), endLabel: String(isoEnd || '') }
      }
    },
    async bootstrap() {
      this.actionError = ''
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.merchantIsSalon = isSalonShopType(s.shopType)
      } catch {
        this.merchantIsSalon = false
      }
      if (!this.merchantIsSalon) {
        this.rows = []
        return
      }
      const silent = this.rows.length > 0
      if (silent) this.listRefreshing = true
      else this.loading = true
      try {
        const list = await fetchAdminSalonBookings(this.$route)
        this.rows = (Array.isArray(list) ? list : []).map((b) => {
          const range = this.formatRange(b.startAt, b.endAt)
          return {
            id: String(b.id || ''),
            serviceId: String(b.serviceId || ''),
            serviceName: String(b.serviceName || '\u2014'),
            customerName: String(b.customerName || ''),
            customerPhone: String(b.customerPhone || ''),
            customerEmail: String(b.customerEmail || ''),
            status: String(b.status || '').toLowerCase(),
            startLabel: range.startLabel,
            endLabel: range.endLabel,
            contactLabel: [b.customerPhone, b.customerEmail].filter(Boolean).join(' \u00b7 '),
            clientPaymentMethod: String(b.clientPaymentMethod || ''),
            paymentVerificationState: String(b.paymentVerificationState || ''),
            paymentReferenceDeclared: String(b.paymentReferenceDeclared || ''),
            paymentProofUrl: String(b.paymentProofUrl || ''),
            cashPaymentCode: String(b.cashPaymentCode || '')
          }
        })
      } catch (e) {
        this.actionError = (e && e.message) || 'Could not load bookings.'
        this.rows = []
      } finally {
        this.loading = false
        this.listRefreshing = false
        try {
          this.$root.$emit('merchant-admin-badges-refresh')
        } catch {
          // ignore
        }
      }
    },
    openBookingCashDialog(item) {
      this.actionError = ''
      this.bookingCashTarget = item
      this.bookingCashCodeInput = ''
      this.bookingCashDialogOpen = true
    },
    closeBookingCashDialogIfIdle() {
      if (this.actionId && this.actionKind === 'cash-confirm') return
      this.actionError = ''
      this.bookingCashDialogOpen = false
      this.bookingCashTarget = null
      this.bookingCashCodeInput = ''
    },
    async submitBookingCashConfirm() {
      const item = this.bookingCashTarget
      if (!item || !item.id) return
      const code = String(this.bookingCashCodeInput || '').trim()
      if (!code) {
        this.actionError = 'Enter the payment code the customer was given when they booked.'
        return
      }
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = 'cash-confirm'
      try {
        const res = await postAdminSalonBookingCashConfirm(this.$route, item.id, code)
        if (!res || res.ok !== true) {
          this.actionError =
            (res && res.reason === 'invalid_code_or_state'
              ? 'Invalid code or this booking is no longer pending.'
              : null) || 'Could not confirm this payment.'
          return
        }
        this.bookingCashDialogOpen = false
        this.bookingCashTarget = null
        this.bookingCashCodeInput = ''
        await this.bootstrap()
      } catch (e) {
        this.actionError = (e && e.message) || 'Could not confirm.'
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    },
    openDeleteBookingDialog(item) {
      if (!this.bookingIsStrictlyPending(item)) return
      this.actionError = ''
      this.deleteBookingTarget = item
      this.deleteBookingDialogOpen = true
    },
    closeDeleteBookingDialogIfIdle() {
      if (this.actionId && String(this.actionKind || '') === 'delete') return
      this.deleteBookingDialogOpen = false
      this.deleteBookingTarget = null
    },
    async confirmDeleteBookingPermanent() {
      if (!this.deleteBookingTarget || !this.deleteBookingTarget.id) return
      if (!this.bookingIsStrictlyPending(this.deleteBookingTarget)) {
        this.actionError = 'Only pending bookings can be deleted.'
        this.closeDeleteBookingDialogIfIdle()
        return
      }
      this.actionError = ''
      this.actionId = this.deleteBookingTarget.id
      this.actionKind = 'delete'
      try {
        const res = await deleteAdminSalonBooking(this.$route, this.deleteBookingTarget.id)
        if (!res || res.ok !== true) {
          this.actionError =
            (res && res.reason === 'not_deletable' ? 'This booking cannot be deleted.' : null) || 'Delete failed.'
          return
        }
        this.deleteBookingDialogOpen = false
        this.deleteBookingTarget = null
        await this.bootstrap()
      } catch (e) {
        this.actionError = (e && e.message) || 'Delete failed.'
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    },
    async setStatus(item, status) {
      if (status === 'cancelled' && !this.bookingIsStrictlyPending(item)) return
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = status === 'cancelled' ? 'cancel' : 'confirm'
      try {
        await updateAdminSalonBookingStatus(this.$route, item.id, status)
        await this.bootstrap()
      } catch (e) {
        this.actionError = (e && e.message) || 'Update failed.'
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    },
    async decideEft(item, decision) {
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = decision === 'approve' ? 'eft-approve' : 'eft-reject'
      try {
        await postAdminSalonBookingEftPaymentDecision(this.$route, item.id, decision)
        await this.bootstrap()
      } catch (e) {
        this.actionError = (e && e.message) || 'Update failed.'
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    }
  }
}
</script>

<style scoped>
.bookings-toolbar {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  margin-bottom: 1rem;
}

@media (min-width: 600px) {
  .bookings-toolbar {
    grid-template-columns: 2fr repeat(3, 1fr);
  }
}

.bookings-table ::v-deep th {
  font-size: 0.6875rem !important;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.55) !important;
}

.muted-panel {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  background: rgba(248, 250, 252, 0.9);
}
</style>
