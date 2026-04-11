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
                {{ ordersRangeFrom }}–{{ ordersRangeTo }} of {{ filteredOrdersForAdmin.length }}
                <span v-if="filteredOrdersForAdmin.length !== orders.length" class="text--disabled">
                  ({{ orders.length }} total)
                </span>
              </div>
              <v-spacer />
              <v-progress-circular v-if="ordersLoading" indeterminate size="22" width="2" color="accent" />
            </div>
            <p class="text-caption text--secondary mb-4">
              Totals and line prices are calculated on the server. For <strong>EFT</strong> orders, confirm payment only
              after the money reflects in your account. Customers cannot edit orders from the app.
            </p>

            <v-alert v-if="ordersActionError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ ordersActionError }}
            </v-alert>

            <div
              v-if="!ordersLoading && orders.length === 0"
              class="muted-panel rounded-lg pa-8 text-center"
            >
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
                  placeholder="Name, email, order ID, address, product…"
                  class="orders-search-field rounded-lg"
                  aria-label="Search orders"
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
              </div>

              <div
                v-if="!filteredOrdersForAdmin.length"
                class="muted-panel rounded-lg pa-6 text-center mb-4"
              >
                <v-icon size="36" color="secondary" class="mb-2">manage_search</v-icon>
                <div class="text-subtitle-2 font-weight-bold mb-1">No matching orders</div>
                <div class="text-body-2 text--secondary mb-0">
                  Try different search words or clear the filters.
                </div>
              </div>

              <v-expansion-panels v-else multiple class="orders-panels">
              <v-expansion-panel
                v-for="o in paginatedOrders"
                :key="o.id"
                class="rounded-lg mb-3"
                :class="{ 'order-panel--cancelled': orderCancelled(o) }"
              >
                <v-expansion-panel-header class="order-panel-header">
                  <div class="d-flex flex-column flex-sm-row align-start align-sm-center flex-grow-1 pr-2">
                    <div>
                      <div class="font-weight-bold text-subtitle-1">
                        {{ formatZar(o.total_zar) }}
                        <span class="text-caption text--secondary font-weight-regular ml-2">
                          {{ formatWhen(o.created_at) }}
                        </span>
                      </div>
                      <div class="text-body-2 font-weight-bold font-mono order-ref-line mb-1">
                        {{ displayOrderRef(o) }}
                      </div>
                      <div class="text-body-2 text--secondary order-customer-line">
                        {{ o.customer_name }} · {{ o.customer_email }}
                      </div>
                    </div>
                    <v-spacer />
                    <div class="d-flex flex-wrap align-center mt-2 mt-sm-0">
                      <v-chip small outlined class="mr-2 mb-1 text-none">
                        {{ o.delivery_type === 'delivery' ? 'Delivery' : 'Pickup' }}
                      </v-chip>
                      <v-chip small outlined class="mr-2 mb-1 text-none">
                        {{ o.payment_method === 'eft' ? 'EFT' : 'Cash in store' }}
                      </v-chip>
                      <v-chip
                        v-if="orderCancelled(o)"
                        small
                        class="mb-1 text-none white--text"
                        color="error"
                      >
                        Cancelled
                      </v-chip>
                      <template v-else>
                        <v-chip
                          small
                          class="mb-1 mr-2 text-none white--text"
                          :color="o.payment_confirmed ? 'success' : 'warning'"
                        >
                          <template v-if="o.payment_method === 'eft'">
                            {{ o.payment_confirmed ? 'EFT confirmed' : 'Awaiting EFT' }}
                          </template>
                          <template v-else>
                            {{ o.payment_confirmed ? 'Cash received' : 'Awaiting cash payment' }}
                          </template>
                        </v-chip>
                        <v-chip
                          small
                          outlined
                          class="mb-1 text-none"
                          :color="fulfillmentChipColor(o)"
                        >
                          {{ fulfillmentStatusLabel(o) }}
                        </v-chip>
                      </template>
                    </div>
                  </div>
                </v-expansion-panel-header>
                <v-expansion-panel-content class="order-panel-body">
                  <div class="text-caption text--secondary mb-1">Order number</div>
                  <div class="text-body-2 font-weight-bold font-mono mb-2">{{ displayOrderRef(o) }}</div>
                  <div class="text-caption text--secondary mb-1">Internal ID</div>
                  <div class="text-body-2 font-mono mb-4 text--secondary" style="font-size: 0.8125rem">{{ o.id }}</div>

                  <div v-if="!orderCancelled(o)" class="mb-4">
                    <div class="text-caption text--secondary mb-1">Fulfillment</div>
                    <v-select
                      v-if="o.payment_confirmed"
                      :value="effectiveOrderStatus(o)"
                      :items="orderFulfillmentSelectItems(o)"
                      outlined
                      dense
                      hide-details
                      item-text="text"
                      item-value="value"
                      class="order-fulfillment-select rounded-lg"
                      :loading="orderStatusUpdatingId === o.id"
                      :disabled="Boolean(orderStatusUpdatingId)"
                      @input="setOrderFulfillmentStatus(o, $event)"
                    />
                    <v-chip v-else small outlined class="text-none">{{ fulfillmentStatusLabel(o) }}</v-chip>
                  </div>

                  <div v-if="o.delivery_type === 'delivery' && o.delivery_address" class="mb-4">
                    <div class="text-caption text--secondary mb-1">Deliver to</div>
                    <div class="text-body-2">{{ o.delivery_address }}</div>
                  </div>

                  <div class="text-caption text--secondary mb-2">Lines</div>
                  <div class="order-lines-scroll">
                    <v-simple-table dense>
                      <template #default>
                        <thead>
                          <tr>
                            <th class="text-left">Product</th>
                            <th class="text-right">Qty</th>
                            <th class="text-right">Line</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="it in o.order_items || []" :key="it.id">
                            <td>{{ lineProductName(it) }}</td>
                            <td class="text-right">{{ it.quantity }}</td>
                            <td class="text-right">{{ formatZar(it.line_total_zar) }}</td>
                          </tr>
                        </tbody>
                      </template>
                    </v-simple-table>
                  </div>

                  <div class="d-flex flex-wrap align-center mt-4">
                    <div class="text-body-2">
                      Subtotal {{ formatZar(o.subtotal_zar) }} · Delivery {{ formatZar(o.delivery_fee_zar) }}
                    </div>
                    <v-spacer />
                    <v-btn
                      small
                      outlined
                      color="primary"
                      class="text-none font-weight-bold mr-2 mb-2"
                      @click.stop="openInvoicePrint(o)"
                    >
                      <v-icon left small color="primary">print</v-icon>
                      Print invoice
                    </v-btn>
                    <v-btn
                      v-if="!o.payment_confirmed && !orderCancelled(o)"
                      small
                      outlined
                      color="error"
                      class="text-none font-weight-bold mr-2 mb-2"
                      :loading="cancellingOrderId === o.id"
                      :disabled="orderStatusUpdatingId === o.id"
                      @click.stop="openCancelOrderDialog(o)"
                    >
                      <v-icon left small color="error">cancel</v-icon>
                      Cancel order
                    </v-btn>
                    <v-btn
                      v-if="!o.payment_confirmed && !orderCancelled(o)"
                      small
                      depressed
                      color="success"
                      class="text-none font-weight-bold mb-2"
                      :loading="confirmingId === o.id"
                      :disabled="orderStatusUpdatingId === o.id"
                      @click.stop="confirmPayment(o)"
                    >
                      <v-icon left small color="white">verified</v-icon>
                      {{ o.payment_method === 'eft' ? 'Confirm EFT received' : 'Confirm cash received' }}
                    </v-btn>
                  </div>

                  <v-divider class="my-4" />

                  <div class="d-flex flex-wrap align-center">
                    <p class="text-caption text--secondary mb-2 mb-sm-0 mr-sm-4 flex-grow-1">
                      Permanently removes this order and its lines from the database to save storage. Printed invoices
                      will no longer match a stored order.
                    </p>
                    <v-btn
                      small
                      outlined
                      color="error"
                      class="text-none font-weight-bold"
                      :disabled="
                        Boolean(deletingOrderId) ||
                        Boolean(cancellingOrderId) ||
                        confirmingId === o.id ||
                        orderStatusUpdatingId === o.id
                      "
                      :loading="deletingOrderId === o.id"
                      @click.stop="openDeleteOrderDialog(o)"
                    >
                      <v-icon left small color="error">delete_forever</v-icon>
                      Delete permanently
                    </v-btn>
                  </div>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>

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
          Payment has not been confirmed. Cancelling releases these items for other customers. You cannot undo this, but
          the order stays in the list as <strong>Cancelled</strong> for your records.
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
        <p class="text-body-2 text--secondary font-mono mb-4" style="font-size: 0.8125rem">{{ deleteOrderTarget.id }}</p>
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
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminOrdersView',
  mixins: [adminModuleMixin]
}
</script>
