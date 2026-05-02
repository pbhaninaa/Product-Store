<template>
  <div>
    <v-row v-if="user" class="mt-0 mt-sm-2">
      <v-col cols="12">
        <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="d-flex align-center flex-wrap mb-2">
            <div class="card-label mb-0">Orders</div>
            <div
              v-if="!ordersLoading && orders.length && filteredOrdersForAdmin.length"
              class="text-caption text--secondary ml-sm-2 mt-1 mt-sm-0"
            >
              {{ filteredOrdersForAdmin.length }} order{{ filteredOrdersForAdmin.length === 1 ? '' : 's' }}
              <span v-if="filteredOrdersForAdmin.length !== orders.length" class="text--disabled">
                ({{ orders.length }} total)
              </span>
            </div>
            <v-spacer />
            <v-progress-circular v-if="ordersLoading" indeterminate size="22" width="2" color="accent" />
          </div>
          <p class="text-caption text--secondary mb-4">
            <strong>Product orders</strong> (shop checkout) appear here — not salon appointments; those live under
            <strong>Salon → Bookings</strong>. For <strong>EFT</strong>, customers upload proof after paying; if the
            reference does not auto-match, use <strong>Confirm payment</strong> after you verify their transfer.
          </p>

          <v-alert v-if="ordersActionError" type="error" dense outlined class="mb-4 rounded-lg">
            {{ ordersActionError }}
          </v-alert>

          <div v-if="!ordersLoading && orders.length === 0" class="muted-panel rounded-lg pa-8 text-center">
            <v-icon size="40" color="secondary" class="mb-3">receipt_long</v-icon>
            <div class="text-subtitle-1 font-weight-bold mb-1">No orders yet</div>
            <div class="text-body-2 text--secondary">Customer orders will appear here.</div>
          </div>

          <template v-else-if="orders.length">
            <div class="orders-toolbar mb-4">
              <v-text-field
                v-model="ordersSearch"
                outlined
                dense
                hide-details
                clearable
                prepend-inner-icon="search"
                label="Search orders"
                placeholder="Name, email, order ID, product…"
                class="orders-search-field rounded-lg"
                aria-label="Search orders"
              />
              <v-select
                v-model="ordersStatusFilter"
                :items="ordersStatusFilterItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Status"
                prepend-inner-icon="flag"
                class="orders-filter-select rounded-lg"
              />
              <v-select
                v-model="ordersPaymentMethodFilter"
                :items="ordersPaymentMethodFilterItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Payment"
                prepend-inner-icon="payment"
                class="orders-filter-select rounded-lg"
              />
              <v-select
                v-model="ordersPaymentVerificationFilter"
                :items="ordersPaymentVerificationFilterItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Verification"
                prepend-inner-icon="verified"
                class="orders-filter-select rounded-lg"
              />
              <v-select
                v-model="ordersDeliveryFilter"
                :items="ordersDeliveryFilterItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Delivery"
                prepend-inner-icon="local_shipping"
                class="orders-filter-select rounded-lg"
              />
            </div>

            <div v-if="!filteredOrdersForAdmin.length" class="muted-panel rounded-lg pa-6 text-center mb-4">
              <v-icon size="36" color="secondary" class="mb-2">manage_search</v-icon>
              <div class="text-subtitle-2 font-weight-bold mb-1">No matching orders</div>
              <div class="text-body-2 text--secondary mb-0">
                Try different search words or clear the filters.
              </div>
            </div>

            <v-data-table
              v-else
              :headers="headers"
              :items="filteredOrdersForAdmin"
              :items-per-page="ordersPerPage"
              :page.sync="ordersPage"
              class="rounded-lg elevation-0 orders-table"
              no-data-text="No orders match your filters."
              hide-default-footer
            >
              <template v-slot:[`item.customer`]="{ item }">
                <div class="font-weight-medium">{{ item.customer_name }}</div>
                <div class="text-caption text--secondary">{{ item.customer_email }}</div>
              </template>
              <template v-slot:[`item.total`]="{ item }">
                <div class="font-weight-bold">{{ formatZar(item.total_zar) }}</div>
                <div class="text-caption text--secondary">{{ formatWhen(item.created_at || item.createdAt) }}</div>
              </template>
              <template v-slot:[`item.items`]="{ item }">
                <div class="text-body-2">
                  {{ (item.order_items || []).length }} item{{
                    (item.order_items || []).length === 1 ? '' : 's'
                  }}
                </div>
                <div class="text-caption text--secondary">
                  {{ item.delivery_type === 'delivery' ? 'Delivery' : 'Pickup' }}
                </div>
              </template>
              <template v-slot:[`item.payment`]="{ item }">
                <div class="text-body-2">
                  {{ (item.payment_method || item.paymentMethod) === 'eft' ? 'EFT' : 'Cash in store' }}
                </div>
                <div v-if="item.payment_proof_url" class="mt-1">
                  <a
                    :href="item.payment_proof_url"
                    target="_blank"
                    rel="noopener"
                    class="text-caption primary--text"
                  >
                    View proof
                  </a>
                </div>
              </template>
              <template v-slot:[`item.verification`]="{ item }">
                <span class="text-body-2">{{ verificationLabel(item) }}</span>
              </template>
              <template v-slot:[`item.status`]="{ item }">
                <v-chip
                  v-if="orderCancelled(item)"
                  small
                  label
                  outlined
                  color="secondary"
                  class="text-none"
                >
                  Cancelled
                </v-chip>
                <v-chip
                  v-else
                  small
                  label
                  outlined
                  :color="orderIsPaid(item) ? 'success' : 'warning'"
                  class="text-none"
                >
                  {{ orderIsPaid(item) ? 'Paid' : 'Pending' }}
                </v-chip>
              </template>
              <template v-slot:[`item.actions`]="{ item }">
                <v-menu offset-y left>
                  <template #activator="{ on, attrs }">
                    <v-btn icon small v-bind="attrs" v-on="on">
                      <v-icon small>more_vert</v-icon>
                    </v-btn>
                  </template>
                  <v-list dense class="py-0">
                    <v-list-item @click="openInvoicePrint(item)">
                      <v-list-item-icon class="mr-2">
                        <v-icon small color="primary">print</v-icon>
                      </v-list-item-icon>
                      <v-list-item-content>
                        <v-list-item-title class="text-body-2">Print invoice</v-list-item-title>
                      </v-list-item-content>
                    </v-list-item>
                    <v-list-item
                      v-if="showMerchantConfirmOrderPayment(item)"
                      @click="confirmPayment(item)"
                      :disabled="confirmingId === item.id"
                    >
                      <v-list-item-icon class="mr-2">
                        <v-icon small color="success">verified</v-icon>
                      </v-list-item-icon>
                      <v-list-item-content>
                        <v-list-item-title class="text-body-2">Confirm payment</v-list-item-title>
                      </v-list-item-content>
                    </v-list-item>
                    <v-divider v-if="orderIsStrictlyPending(item)" />
                    <v-list-item
                      v-if="orderIsStrictlyPending(item)"
                      @click="openCancelOrderDialog(item)"
                      :disabled="cancellingOrderId === item.id"
                    >
                      <v-list-item-icon class="mr-2">
                        <v-icon small color="error">cancel</v-icon>
                      </v-list-item-icon>
                      <v-list-item-content>
                        <v-list-item-title class="text-body-2">Cancel order</v-list-item-title>
                      </v-list-item-content>
                    </v-list-item>
                    <v-list-item
                      v-if="orderIsStrictlyPending(item)"
                      @click="openDeleteOrderDialog(item)"
                      :disabled="deletingOrderId === item.id"
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

            <div
              v-if="filteredOrdersForAdmin.length"
              class="orders-pagination-row d-flex flex-column flex-sm-row align-center justify-space-between gap-3 mt-4"
            >
              <v-select
                v-model="ordersPerPage"
                :items="ordersPerPageOptions"
                label="Orders per page"
                outlined
                dense
                hide-details
                class="pagination-size-select rounded-lg mb-0"
              />
              <v-pagination
                v-if="ordersPageCount > 1"
                v-model="ordersPage"
                :length="ordersPageCount"
                :total-visible="paginationVisible"
                color="primary"
                class="admin-pagination flex-grow-1 justify-sm-end"
              />
            </div>
          </template>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog
      v-model="cancelOrderDialogOpen"
      max-width="460"
      content-class="rounded-xl"
      :persistent="Boolean(cancellingOrderId)"
    >
      <v-card v-if="cancelOrderTarget" class="pa-8 rounded-xl">
        <h2 class="text-h6 font-weight-bold mb-3">Cancel this order?</h2>
        <p class="text-body-2 text--secondary mb-2">
          {{ cancelOrderTarget.customer_name }} · {{ formatZar(cancelOrderTarget.total_zar) }}
        </p>
        <p class="text-body-2 mb-6">
          Payment has not been confirmed. Cancelling releases these items for other customers. You cannot undo this,
          but the order stays in the list as <strong>Cancelled</strong> for your records.
        </p>
        <div class="d-flex flex-wrap justify-end" style="gap: 10px">
          <v-btn text class="text-none" :disabled="Boolean(cancellingOrderId)" @click="cancelOrderDialogOpen = false">
            Back
          </v-btn>
          <v-btn
            depressed
            color="error"
            class="text-none white--text font-weight-bold"
            :loading="Boolean(cancellingOrderId)"
            @click="confirmCancelOrder"
          >
            <v-icon left color="white" small>cancel</v-icon>
            Cancel order
          </v-btn>
        </div>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="deleteOrderDialogOpen"
      max-width="480"
      content-class="rounded-xl"
      :persistent="Boolean(deletingOrderId)"
      @click:outside="closeDeleteOrderDialogIfIdle"
    >
      <v-card v-if="deleteOrderTarget" class="pa-8 rounded-xl">
        <h2 class="text-h6 font-weight-bold mb-3">Delete order from database?</h2>
        <p class="text-body-2 text--secondary mb-2">
          {{ deleteOrderTarget.customer_name }} · {{ formatZar(deleteOrderTarget.total_zar) }}
        </p>
        <p class="text-body-2 font-weight-bold font-mono mb-1">{{ displayOrderRef(deleteOrderTarget) }}</p>
        <p class="text-body-2 text--secondary font-mono mb-4" style="font-size: 0.8125rem">
          {{ deleteOrderTarget.id }}
        </p>
        <p class="text-body-2 mb-6">
          This will <strong>permanently delete</strong> this order and all its line items. The row is removed from your
          database to save storage. This cannot be undone.
        </p>
        <div class="d-flex flex-wrap justify-end" style="gap: 10px">
          <v-btn text class="text-none" :disabled="Boolean(deletingOrderId)" @click="closeDeleteOrderDialogIfIdle">
            Back
          </v-btn>
          <v-btn
            depressed
            color="error"
            class="text-none white--text font-weight-bold"
            :loading="Boolean(deletingOrderId)"
            @click="confirmDeleteOrderPermanent"
          >
            <v-icon left color="white" small>delete_forever</v-icon>
            Delete permanently
          </v-btn>
        </div>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="orderCashPaymentDialogOpen"
      max-width="440"
      content-class="rounded-xl"
      :persistent="Boolean(confirmingId)"
      @click:outside="closeOrderCashPaymentDialogIfIdle"
    >
      <v-card v-if="orderCashPaymentTarget" class="pa-8 rounded-xl">
        <h2 class="text-h6 font-weight-bold mb-3">Confirm cash payment</h2>
        <p class="text-body-2 text--secondary mb-4">
          Ask the customer for the <strong>payment code</strong> shown on their order confirmation, then enter it
          below. If it matches, the order is marked <strong>Paid</strong>.
        </p>
        <v-text-field
          v-model="orderCashCodeInput"
          outlined
          dense
          hide-details="auto"
          label="Customer payment code"
          placeholder="e.g. 6 digits"
          class="rounded-lg mb-2"
          autocomplete="one-time-code"
          inputmode="numeric"
          @keyup.enter="submitOrderCashPaymentConfirm"
        />
        <v-alert v-if="ordersActionError" type="error" dense outlined class="mt-2 mb-0 rounded-lg">{{
          ordersActionError
        }}</v-alert>
        <div class="d-flex flex-wrap justify-end mt-6" style="gap: 10px">
          <v-btn text class="text-none" :disabled="Boolean(confirmingId)" @click="closeOrderCashPaymentDialogIfIdle">
            Back
          </v-btn>
          <v-btn
            depressed
            color="success"
            class="text-none white--text font-weight-bold"
            :loading="Boolean(confirmingId)"
            @click="submitOrderCashPaymentConfirm"
          >
            <v-icon left color="white" small>verified</v-icon>
            Confirm payment
          </v-btn>
        </div>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminOrdersView',
  mixins: [adminModuleMixin],
  computed: {
    headers() {
      return [
        { text: 'Customer', value: 'customer', sortable: false },
        { text: 'Total', value: 'total', sortable: false },
        { text: 'Items', value: 'items', sortable: false },
        { text: 'Payment', value: 'payment', sortable: false },
        { text: 'Verification', value: 'verification', sortable: false },
        { text: 'Status', value: 'status', sortable: false },
        { text: '', value: 'actions', sortable: false, width: 1 }
      ]
    },
    ordersPaymentVerificationFilterItems() {
      return [
        { text: 'Awaiting proof', value: 'awaiting_proof' },
        { text: 'Auto-verified', value: 'auto_verified' },
        { text: 'Manual review', value: 'manual_pending' },
        { text: 'Manual rejected', value: 'manual_rejected' }
      ]
    }
  },
  methods: {
    verificationLabel(item) {
      const v = String(item.payment_verification_state || '').toLowerCase()
      const map = {
        not_applicable: '—',
        awaiting_proof: 'Awaiting proof',
        auto_verified: 'Auto-verified',
        manual_pending: 'Manual review',
        manual_rejected: 'Rejected'
      }
      return map[v] || (v ? v : '—')
    }
  }
}
</script>

<style scoped>
.orders-toolbar {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  margin-bottom: 1rem;
}

@media (min-width: 600px) {
  .orders-toolbar {
    grid-template-columns: 2fr repeat(4, 1fr);
  }
}

.orders-table ::v-deep th {
  font-size: 0.6875rem !important;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.55) !important;
}

.muted-panel {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  background: rgba(248, 250, 252, 0.9);
}

.pagination-size-select {
  max-width: 200px;
}
</style>
