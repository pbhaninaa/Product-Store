<template>
  <div>
    <v-alert v-if="user && !merchantIsSalon" type="warning" dense outlined class="rounded-lg mb-6">
      Bookings are available when the business type is <strong>Items sold &amp; services</strong> or <strong>Services</strong> under
      <strong>Store</strong>, then reload.
    </v-alert>

    <template v-else-if="user">
      <v-card class="admin-card pa-4 pa-md-5 mb-4" elevation="3" rounded="xl">
        <div class="d-flex flex-column flex-md-row align-start">
          <v-avatar color="primary" size="48" class="mb-3 mb-md-0 mr-md-4">
            <v-icon dark>event_note</v-icon>
          </v-avatar>
          <div>
            <div class="text-h6 font-weight-bold mb-1">Bookings</div>
            <p class="text-body-2 text--secondary mb-0">
              Customer appointments from your public booking page. <strong>Orders</strong> remains the place for product
              checkouts when you run <strong>Items sold &amp; services</strong>.
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
          Service appointments appear here. Use filters to find specific bookings. Bookings <strong>paid online</strong> confirm
          automatically once paid. For <strong>Cash</strong>, enter the customer’s handover code.
          <template v-if="hasBookingsAwaitingEftReview">
            Some older <strong>Manual EFT</strong> bookings still need your review: approve or reject their proofs from
            the row actions.
          </template>
        </p>

        <v-alert v-if="actionError && !bookingCashDialogOpen" type="error" dense outlined class="mb-4 rounded-lg">{{
          actionError
        }}</v-alert>

        <v-progress-linear v-if="listRefreshing" indeterminate height="3" rounded class="mb-2" />

        <v-skeleton-loader
          v-if="loading && !rows.length"
          type="table-heading, table-row-divider@4"
          class="rounded-lg"
        />

        <div v-else-if="!loading && !rows.length" class="muted-panel rounded-lg pa-6 text-center">
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
              v-if="hasLegacyEftBookings"
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

          <div v-else class="bookings-table-scroll">
            <v-data-table
              :headers="headers"
              :items="filteredRows"
              :items-per-page="12"
              :mobile-breakpoint="960"
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
              <div class="text-caption text--secondary">Booking {{ shortRef(item.id) }}</div>
            </template>
            <template v-slot:[`item.status`]="{ item }">
              <v-chip small label outlined :color="statusColor(item.status)" class="text-none">
                {{ item.noShow ? 'No show' : statusDisplay(item.status) }}
              </v-chip>
              <div v-if="awaitingInAppPayment(item)" class="text-caption text--secondary mt-1">
                Awaiting online payment
              </div>
            </template>
            <template v-slot:[`item.payment`]="{ item }">
              <span class="text-body-2">{{ paymentMethodLabel(item) }}</span>
              <div v-if="amountDueLabel(item.amountDueZar)" class="text-caption font-weight-bold mt-1 amount-due">
                {{ amountDueLabel(item.amountDueZar) }}
              </div>
              <div v-if="item.bookingFeeStatus" class="text-caption text--secondary">
                {{ bookingFeeStatusLabel(item.bookingFeeStatus, item.bookingFeeReason) }}
              </div>
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
                    v-if="showConfirmCashBooking(item) && can('CONFIRM_PAYMENTS')"
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
                    v-if="showStartBooking(item)"
                    @click="setStatus(item, 'in_progress')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="primary">play_arrow</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Start service</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="showCompleteBooking(item)"
                    @click="setStatus(item, 'completed')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="success">done_all</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Complete service</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="showEftPaymentReview(item) && can('CONFIRM_PAYMENTS')"
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
                    v-if="showAssignBooking(item)"
                    @click="openAssignDialog(item)"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="primary">badge</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">
                        {{ item.completedByEmployeeId ? 'Change who did it (payroll)' : 'Assign to team member' }}
                      </v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-list-item
                    v-if="showEftPaymentReview(item) && can('CONFIRM_PAYMENTS')"
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
                  <v-list-item
                    v-if="showNoShow(item) && can('CANCEL_BOOKINGS')"
                    @click="openConfirm(item, 'no-show')"
                    :disabled="actionId === item.id"
                  >
                    <v-list-item-icon class="mr-2">
                      <v-icon small color="error">person_off</v-icon>
                    </v-list-item-icon>
                    <v-list-item-content>
                      <v-list-item-title class="text-body-2">Mark as no show</v-list-item-title>
                    </v-list-item-content>
                  </v-list-item>
                  <v-divider v-if="bookingIsStrictlyPending(item) && (can('CANCEL_BOOKINGS') || can('DELETE_RECORDS'))" />
                  <v-list-item
                    v-if="bookingIsStrictlyPending(item) && can('CANCEL_BOOKINGS')"
                    @click="openConfirm(item, 'cancel')"
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
                    v-if="bookingIsStrictlyPending(item) && can('DELETE_RECORDS')"
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
          </div>
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
          <p v-if="amountDueLabel(bookingCashTarget.amountDueZar)" class="text-h6 mb-2 amount-due">
            {{ amountDueLabel(bookingCashTarget.amountDueZar) }}
          </p>
          <p v-if="bookingCashTarget.bookingFeeStatus === 'paid'" class="text-body-2 text--secondary mb-2">
            The booking fee of {{ formatZar(bookingCashTarget.bookingFeeZar) }} was paid online. Collect only the amount due.
          </p>
          <p class="text-body-2 text--secondary mb-4">
            Enter the <strong>handover code</strong> the customer received when they booked. If it matches, this booking
            is marked <strong>Paid</strong>.
          </p>
          <v-form ref="bookingCashForm" @submit.prevent>
          <v-text-field
            v-model="bookingCashCodeInput"
            outlined
            dense
            hide-details="auto"
            label="Customer handover code *"
            :rules="cashCodeRules"
            maxlength="12"
            placeholder="e.g. 6 digits"
            class="rounded-lg mb-2"
            autocomplete="one-time-code"
            inputmode="numeric"
            @keyup.enter="submitBookingCashConfirm"
          />
          </v-form>
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

      <v-dialog
        v-model="bookingStartDialogOpen"
        max-width="440"
        content-class="rounded-xl"
        :persistent="Boolean(actionId && actionKind === 'start')"
        @click:outside="closeBookingStartDialogIfIdle"
        @keydown.esc="closeBookingStartDialogIfIdle"
      >
        <v-card v-if="bookingStartTarget" class="pa-8 rounded-xl">
          <h2 class="text-h6 font-weight-bold mb-3">
            {{ bookingStartStatus === 'completed' ? 'Complete service' : 'Start service' }}
          </h2>
          <p class="text-body-2 text--secondary mb-4">
            Ask {{ bookingStartTarget.customerName || 'the customer' }} for the <strong>handover code</strong> on their
            booking, then enter it below. The service can only start with the right code.
            <template v-if="startTakesCashPayment(bookingStartTarget)">
              Take the cash first: the booking is also marked <strong>Paid</strong>.
            </template>
          </p>
          <v-form ref="bookingStartForm" @submit.prevent>
            <v-text-field
              v-model="bookingStartCodeInput"
              outlined
              dense
              hide-details="auto"
              label="Customer handover code *"
              :rules="handoverCodeRules"
              maxlength="12"
              placeholder="6 digits"
              class="rounded-lg mb-2"
              autocomplete="one-time-code"
              inputmode="numeric"
              @keyup.enter="submitBookingStart"
            />
          </v-form>
          <v-alert v-if="actionError" type="error" dense outlined class="mt-2 mb-0 rounded-lg">{{ actionError }}</v-alert>
          <div class="d-flex flex-wrap justify-end mt-6" style="gap: 10px">
            <v-btn text class="text-none" :disabled="Boolean(actionId)" @click="closeBookingStartDialogIfIdle">
              Back
            </v-btn>
            <v-btn
              depressed
              color="primary"
              class="text-none white--text font-weight-bold"
              :loading="Boolean(actionId && actionKind === 'start')"
              @click="submitBookingStart"
            >
              <v-icon left color="white" small>play_arrow</v-icon>
              {{ bookingStartStatus === 'completed' ? 'Complete service' : 'Start service' }}
            </v-btn>
          </div>
        </v-card>
      </v-dialog>

      <v-dialog v-model="confirmDialogOpen" max-width="460" content-class="rounded-xl" :persistent="Boolean(actionId)">
        <v-card v-if="confirmTarget" class="pa-8 rounded-xl">
          <h2 class="text-h6 font-weight-bold mb-3">{{ confirmKind === 'no-show' ? 'Mark as no show?' : 'Cancel this booking?' }}</h2>
          <p class="text-body-2 mb-4">{{ confirmText }}</p>
          <v-alert v-if="actionError" type="error" dense outlined class="mb-4 rounded-lg">{{ actionError }}</v-alert>
          <div class="d-flex">
            <v-btn text class="text-none" :disabled="Boolean(actionId)" @click="confirmDialogOpen = false">Go back</v-btn>
            <v-spacer />
            <v-btn depressed color="error" class="text-none" :loading="Boolean(actionId)" @click="runConfirm">
              {{ confirmKind === 'no-show' ? 'Mark as no show' : 'Cancel booking' }}
            </v-btn>
          </div>
        </v-card>
      </v-dialog>

      <v-dialog v-model="assignDialogOpen" max-width="440" :persistent="Boolean(actionId)">
        <v-card v-if="assignTarget" class="pa-5 rounded-xl">
          <div class="text-h6 font-weight-bold mb-1">Who did this booking?</div>
          <p class="text-body-2 text--secondary mb-4">
            {{ assignTarget.serviceName }} for {{ assignTarget.customerName }}. It will count towards this person's
            pay under Team &amp; Payroll.
          </p>
          <v-select
            v-model="assignEmployeeId"
            :items="assignTeam"
            item-text="displayName"
            item-value="id"
            label="Team member *"
            outlined
            dense
            :loading="assignTeamLoading"
            no-data-text="Add a team member under Team & Payroll first."
          />
          <v-alert v-if="actionError" type="error" dense outlined class="mt-2 mb-0 rounded-lg">{{ actionError }}</v-alert>
          <div class="d-flex flex-wrap justify-end mt-4" style="gap: 10px">
            <v-btn text class="text-none" :disabled="Boolean(actionId)" @click="assignDialogOpen = false">Cancel</v-btn>
            <v-btn
              depressed
              color="primary"
              class="text-none font-weight-bold"
              :disabled="!assignEmployeeId"
              :loading="Boolean(actionId && actionKind === 'assign')"
              @click="submitAssign"
            >
              Save
            </v-btn>
          </div>
        </v-card>
      </v-dialog>
    </template>
  </div>
</template>

<script>
import { matchesPaymentMethodFilter, inAppPaymentLabel } from '@/utils/inAppPayment'
import { fetchAdminStoreSettings } from '@/services/adminApi'
import { isSalonShopType } from '@/services/shopType'
import { rules, validateForm } from '@/utils/validation'
import { LEGACY_EFT_LABEL, shortRef } from '@/utils/displayLabels'
import {
  bookingStatusNeedsHandoverCode,
  canStartUnpaidCashBooking,
  HANDOVER_CODE_LABEL,
  normalizeHandoverCode
} from '@/utils/handoverCode'
import adminAccessMixin from './mixins/adminAccessMixin'
import { amountDueLabel, bookingFeeStatusLabel, cancelConfirmText, noShowConfirmText } from '@/utils/bookingFee'
import { formatZar } from '@/utils/price'
import { assignJobToEmployee, fetchTeam } from '@/services/teamApi'
import {
  bookingAwaitingInAppPayment,
  canMerchantConfirmBooking,
  isLegacyEftRow,
  legacyEftAwaitsReview
} from './adminRules'
import {
  deleteAdminSalonBooking,
  fetchAdminSalonBookings,
  postAdminSalonBookingCashConfirm,
  postAdminSalonBookingEftPaymentDecision,
  postAdminSalonBookingNoShow,
  updateAdminSalonBookingStatus
} from '@/services/salonAdmin'

export default {
  name: 'AdminSalonBookingsView',
  mixins: [adminAccessMixin],
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
        { text: 'In progress', value: 'in_progress' },
        { text: 'Completed', value: 'completed' },
        { text: 'Cancelled', value: 'cancelled' }
      ],
      verificationItems: [
        { text: 'Awaiting proof', value: 'awaiting_proof' },
        { text: 'Verified automatically', value: 'auto_verified' },
        { text: 'Manual review', value: 'manual_pending' },
        { text: 'Approved (manual)', value: 'manual_approved' },
        { text: 'Rejected', value: 'manual_rejected' }
      ],
      confirmDialogOpen: false,
      confirmTarget: null,
      confirmKind: '',
      bookingCashDialogOpen: false,
      bookingCashTarget: null,
      bookingCashCodeInput: '',
      actionId: '',
      actionKind: '',
      actionError: '',
      listRefreshing: false,
      deleteBookingDialogOpen: false,
      deleteBookingTarget: null,
      assignDialogOpen: false,
      assignTarget: null,
      assignEmployeeId: '',
      assignTeam: [],
      assignTeamLoading: false,
      cashCodeRules: [rules.required('Handover code'), rules.digits({ min: 6, max: 6, label: 'Handover code' })],
      bookingStartDialogOpen: false,
      bookingStartTarget: null,
      bookingStartStatus: 'in_progress',
      bookingStartCodeInput: '',
      handoverCodeRules: [
        rules.required(HANDOVER_CODE_LABEL),
        rules.digits({ min: 6, max: 6, label: HANDOVER_CODE_LABEL })
      ]
    }
  },
  computed: {
    confirmText() {
      const item = this.confirmTarget
      if (!item) return ''
      return this.confirmKind === 'no-show' ? noShowConfirmText(item) : cancelConfirmText(item)
    },
    user() {
      return this.adminSession && this.adminSession.user ? this.adminSession.user : null
    },
    headers() {
      return [
        { text: 'When', value: 'startLabel', sortable: false },
        { text: 'Service', value: 'serviceName' },
        { text: 'Customer', value: 'customer', sortable: false },
        { text: 'Payment', value: 'payment', sortable: false },
        // Proof verification only ever applied to the retired Manual EFT.
        ...(this.hasLegacyEftBookings ? [{ text: 'Verification', value: 'verification', sortable: false }] : []),
        { text: 'Status', value: 'status', sortable: false },
        { text: '', value: 'actions', sortable: false, width: 1 }
      ]
    },
    /** Bookings paid by the retired Manual EFT are still listed; their filters and columns only show when present. */
    hasLegacyEftBookings() {
      return (this.rows || []).some(isLegacyEftRow)
    },
    hasBookingsAwaitingEftReview() {
      return (this.rows || []).some((r) => this.showEftPaymentReview(r))
    },
    paymentMethodItems() {
      return [
        { text: 'Cash', value: 'cash_store' },
        ...(this.hasLegacyEftBookings ? [{ text: LEGACY_EFT_LABEL, value: 'eft' }] : []),
        { text: 'Online (card or Instant EFT)', value: 'online' }
      ]
    },
    filteredRows() {
      const q = String(this.search || '').trim().toLowerCase()
      const st = String(this.statusFilter || '').trim().toLowerCase()
      const pm = String(this.paymentMethodFilter || '').trim().toLowerCase()
      const vf = String(this.verificationFilter || '').trim().toLowerCase()
      return (this.rows || []).filter((r) => {
        if (st && String(r.status || '').toLowerCase() !== st) return false
        if (pm && !matchesPaymentMethodFilter(r.clientPaymentMethod, pm)) return false
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
    shortRef,
    formatZar,
    amountDueLabel,
    bookingFeeStatusLabel,
    /** No show: pending or paid bookings whose start time has passed. */
    showNoShow(item) {
      const s = String(item.status || '').toLowerCase()
      if (s !== 'pending' && s !== 'confirmed') return false
      const start = new Date(item.startAt).getTime()
      return Number.isFinite(start) && start <= Date.now()
    },
    openConfirm(item, kind) {
      this.actionError = ''
      this.confirmTarget = item
      this.confirmKind = kind
      this.confirmDialogOpen = true
    },
    async runConfirm() {
      const item = this.confirmTarget
      if (!item) return
      if (this.confirmKind === 'cancel') {
        await this.setStatus(item, 'cancelled')
        if (!this.actionError) this.confirmDialogOpen = false
        return
      }
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = 'no-show'
      try {
        await postAdminSalonBookingNoShow(this.$route, item.id)
        this.confirmDialogOpen = false
        await this.bootstrap()
      } catch (e) {
        this.actionError = this.friendlyActionError(e, 'Could not mark this booking as a no show.')
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    },
    statusColor(status) {
      const s = String(status || '').toLowerCase()
      if (s === 'completed') return 'success'
      if (s === 'confirmed') return 'success'
      if (s === 'in_progress') return 'primary'
      if (s === 'cancelled') return 'secondary'
      return 'warning'
    },
    paymentMethodLabel(item) {
      const m = String(item.clientPaymentMethod || '').toLowerCase()
      if (m === 'cash_store') return 'Cash'
      const inApp = inAppPaymentLabel(m, item.onlinePaymentChannel)
      if (inApp) return inApp
      if (m === 'eft') return LEGACY_EFT_LABEL
      return 'Not set'
    },
    verificationLabel(item) {
      const v = String(item.paymentVerificationState || '').toLowerCase()
      const map = {
        not_applicable: 'Not needed',
        awaiting_proof: 'Awaiting proof',
        auto_verified: 'Verified automatically',
        manual_pending: 'Manual review',
        manual_approved: 'Approved (manual)',
        manual_rejected: 'Rejected'
      }
      return map[v] || (v ? v : 'Not set')
    },
    statusDisplay(status) {
      const s = String(status || '').toLowerCase()
      if (s === 'confirmed') return 'Paid'
      if (s === 'in_progress') return 'In progress'
      if (s === 'completed') return 'Completed'
      if (s === 'pending') return 'Pending'
      if (s === 'cancelled') return 'Cancelled'
      return s || 'Not set'
    },
    bookingIsStrictlyPending(item) {
      return String(item.status || '').toLowerCase() === 'pending'
    },
    /** Paid bookings start with the customer's handover code; unpaid cash ones too, which also takes the payment. */
    showStartBooking(item) {
      if (String(item.status || '').toLowerCase() === 'confirmed') return true
      return canStartUnpaidCashBooking(item) && this.can('CONFIRM_PAYMENTS')
    },
    startTakesCashPayment(item) {
      return canStartUnpaidCashBooking(item)
    },
    showCompleteBooking(item) {
      const s = String(item.status || '').toLowerCase()
      return s === 'in_progress' || s === 'confirmed'
    },
    showConfirmCashBooking(item) {
      return (
        this.bookingIsStrictlyPending(item) && String(item.clientPaymentMethod || '').toLowerCase() === 'cash_store'
      )
    },
    /** Never for unpaid online bookings: those confirm themselves when the payment arrives. */
    showConfirmBooking(item) {
      return canMerchantConfirmBooking(item)
    },
    awaitingInAppPayment(item) {
      return bookingAwaitingInAppPayment(item)
    },
    friendlyActionError(e, fallback) {
      const code = String((e && e.code) || '')
      if (code === 'in_app_payment_not_received') {
        return 'This booking has not been paid yet. It confirms automatically once the customer’s online payment arrives.'
      }
      if (code === 'payment_verification_required') {
        return 'Confirm the payment first (cash code, or review the Manual EFT proof), then the booking is confirmed.'
      }
      if (code === 'forbidden') return 'You do not have access to change this booking.'
      return (e && e.message) || fallback
    },
    /** Approve / reject stays available for legacy Manual EFT proofs that were waiting when EFT was removed. */
    showEftPaymentReview(item) {
      return String(item.status || '').toLowerCase() === 'pending' && legacyEftAwaitsReview(item)
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
            serviceName: String(b.serviceName || 'None'),
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
            cashPaymentCode: String(b.cashPaymentCode || ''),
            completedByEmployeeId: String(b.completedByEmployeeId || ''),
            startAt: b.startAt,
            amountDueZar: String(b.amountDueZar || ''),
            bookingFeeZar: String(b.bookingFeeZar || ''),
            bookingFeeStatus: String(b.bookingFeeStatus || ''),
            bookingFeeReason: String(b.bookingFeeReason || ''),
            noShow: Boolean(b.noShow)
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
    /** Needs payroll access (the team list and assign-job are payroll APIs), and only once the booking is paid. */
    showAssignBooking(item) {
      if (!item || !this.can('TEAM_PAYROLL')) return false
      return ['confirmed', 'in_progress', 'completed'].includes(item.status)
    },
    async openAssignDialog(item) {
      this.actionError = ''
      this.assignTarget = item
      this.assignEmployeeId = item.completedByEmployeeId || ''
      this.assignDialogOpen = true
      this.assignTeamLoading = true
      try {
        const res = await fetchTeam(this.$route)
        this.assignTeam = ((res && res.team) || []).filter((t) => t && t.active)
      } catch (e) {
        this.assignTeam = []
        this.actionError = (e && e.message) || 'Could not load your team.'
      } finally {
        this.assignTeamLoading = false
      }
    },
    async submitAssign() {
      const item = this.assignTarget
      if (!item || !this.assignEmployeeId) return
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = 'assign'
      try {
        await assignJobToEmployee(this.$route, {
          jobId: item.id,
          jobType: 'SALON_BOOKING',
          employeeId: this.assignEmployeeId
        })
        this.assignDialogOpen = false
        await this.bootstrap()
      } catch (e) {
        this.actionError = (e && e.message) || 'Could not assign this booking.'
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    },
    openBookingCashDialog(item) {
      this.actionError = ''
      this.bookingCashTarget = item
      this.bookingCashCodeInput = ''
      this.bookingCashDialogOpen = true
      this.$nextTick(() => {
        if (this.$refs.bookingCashForm) this.$refs.bookingCashForm.resetValidation()
      })
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
      if (!validateForm(this.$refs.bookingCashForm)) return
      const code = String(this.bookingCashCodeInput || '').trim()
      if (!code) {
        this.actionError = 'Enter the handover code the customer was given when they booked.'
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
    openBookingStartDialog(item, status) {
      this.actionError = ''
      this.bookingStartTarget = item
      this.bookingStartStatus = status
      this.bookingStartCodeInput = ''
      this.bookingStartDialogOpen = true
      this.$nextTick(() => {
        if (this.$refs.bookingStartForm) this.$refs.bookingStartForm.resetValidation()
      })
    },
    closeBookingStartDialogIfIdle() {
      if (this.actionId && this.actionKind === 'start') return
      this.actionError = ''
      this.bookingStartDialogOpen = false
      this.bookingStartTarget = null
      this.bookingStartCodeInput = ''
    },
    async submitBookingStart() {
      const item = this.bookingStartTarget
      if (!item || !item.id) return
      if (!validateForm(this.$refs.bookingStartForm)) return
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = 'start'
      try {
        await updateAdminSalonBookingStatus(
          this.$route,
          item.id,
          this.bookingStartStatus || 'in_progress',
          normalizeHandoverCode(this.bookingStartCodeInput)
        )
        this.bookingStartDialogOpen = false
        this.bookingStartTarget = null
        this.bookingStartCodeInput = ''
        await this.bootstrap()
      } catch (e) {
        this.actionError = this.friendlyActionError(e, 'Could not start this service.')
      } finally {
        this.actionId = ''
        this.actionKind = ''
      }
    },
    async setStatus(item, status) {
      if (status === 'cancelled' && !this.bookingIsStrictlyPending(item)) return
      if (bookingStatusNeedsHandoverCode(item.status, status)) {
        this.openBookingStartDialog(item, status)
        return
      }
      this.actionError = ''
      this.actionId = item.id
      this.actionKind = status === 'cancelled' ? 'cancel' : 'confirm'
      try {
        await updateAdminSalonBookingStatus(this.$route, item.id, status)
        await this.bootstrap()
      } catch (e) {
        this.actionError = this.friendlyActionError(e, 'Update failed.')
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
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 1rem;
}

@media (min-width: 600px) and (max-width: 959px) {
  .bookings-toolbar {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }

  .bookings-toolbar > *:first-child {
    grid-column: 1 / -1;
  }
}

@media (min-width: 960px) {
  .bookings-toolbar {
    display: grid;
    grid-template-columns: 2fr repeat(3, 1fr);
    gap: 12px;
  }
}

.bookings-table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 0 -4px;
  padding: 0 4px;
}

@media (min-width: 960px) {
  .bookings-table {
    min-width: 720px;
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
