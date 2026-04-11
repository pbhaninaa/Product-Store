<template>
  <div>
    <section class="admin-hero">
      <v-container class="py-10 py-md-12">
        <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
          <div>
            <div class="admin-kicker mb-2">
              <v-icon small color="white" class="mr-1">lock</v-icon>
              Protected area
            </div>
            <h1 class="admin-title">Admin</h1>
            <p class="admin-lead mb-0">
              <template v-if="user">
                You’re signed in — manage products, inventory, and orders below.
              </template>
              <template v-else>
                Sign in with your staff account to open the dashboard. Only the login form is shown until you’re in.
              </template>
            </p>
          </div>
          <v-chip
            v-if="user"
            class="mt-6 mt-md-0 ml-md-auto text-none font-weight-bold px-4"
            color="white"
            outlined
            label
          >
            <v-icon left small color="white">cloud_done</v-icon>
            Live sync
          </v-chip>
        </div>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16 admin-body px-3 px-sm-4">
      <v-alert
        v-if="supabaseConfigHint"
        type="warning"
        prominent
        border="left"
        colored-border
        class="mb-8 rounded-lg"
      >
        {{ supabaseConfigHint }}
      </v-alert>

      <v-row v-if="!user" justify="center" class="admin-login-row">
        <v-col cols="12" sm="10" md="6" lg="5">
          <v-card class="admin-card admin-login-card pa-5 pa-sm-8" elevation="3" rounded="xl">
            <div class="card-label mb-2">Sign in</div>
            <p class="text-body-2 text--secondary mb-8">
              Use your admin email and password from Supabase Authentication. After you sign in, the full dashboard
              will load.
            </p>

            <v-text-field
              v-model="email"
              outlined
              hide-details="auto"
              label="Email"
              type="email"
              autocomplete="email"
              class="rounded-lg"
              :disabled="authLoading"
            />
            <v-text-field
              v-model="password"
              outlined
              hide-details="auto"
              label="Password"
              type="password"
              autocomplete="current-password"
              class="mt-4 rounded-lg"
              :disabled="authLoading"
              @keyup.enter="doLogin"
            />

            <v-btn
              block
              x-large
              depressed
              class="mt-6 text-none font-weight-bold btn-admin-primary"
              :loading="authLoading"
              @click="doLogin"
            >
              Sign in
            </v-btn>

            <v-alert v-if="authError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ authError }}
            </v-alert>

            <p class="text-caption text--secondary mt-6 mb-0">
              Enable <strong>Email</strong> in Supabase → Authentication → Providers, then add a user under
              Authentication → Users.
            </p>
          </v-card>
        </v-col>
      </v-row>

      <v-row v-else>
        <v-col cols="12" md="5">
          <v-card class="admin-card pa-4 pa-sm-6 mb-4 mb-md-6" elevation="3" rounded="xl">
            <div class="card-label mb-6">Your account</div>

            <div class="d-flex flex-column flex-sm-row align-start align-sm-center">
              <v-avatar color="success" size="44" class="mr-sm-4 mb-4 mb-sm-0">
                <v-icon dark>person</v-icon>
              </v-avatar>
              <div class="flex-grow-1">
                <div class="text-subtitle-1 font-weight-bold">Signed in</div>
                <div class="text-body-2 text--secondary text-truncate">{{ user.email }}</div>
              </div>
              <v-btn text color="error" class="mt-4 mt-sm-0 text-none font-weight-bold" @click="doLogout">
                Sign out
              </v-btn>
            </div>

            <div class="account-location mt-4 pt-6">
              <div class="text-caption font-weight-bold text-uppercase mb-2 account-location__label">
                <template v-if="accountCardHasStorePin">Store location</template>
                <template v-else>Current location</template>
              </div>

              <template v-if="accountCardHasStorePin">
                <div v-if="accountStorePlaceLoading" class="text-body-2 text--secondary mb-1">Loading address…</div>
                <div
                  v-else-if="accountStorePlaceName"
                  class="text-body-1 font-weight-medium account-location__place mb-2"
                >
                  {{ accountStorePlaceName }}
                </div>
               <!--   <div class="text-body-2 font-mono account-location__coords text--secondary">
                  {{ formatAccountCoord(storeLat) }}, {{ formatAccountCoord(storeLng) }}
                </div>
               <a
                  class="text-caption font-weight-bold text-decoration-none mt-2 d-inline-block"
                  :href="accountMapsUrl(storeLat, storeLng)"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Open in Maps
                  <v-icon x-small color="primary" class="ml-1">open_in_new</v-icon>
                </a> -->
              </template>

              <template v-else>
                <p class="text-caption text--secondary mb-2 mb-0">
                  No store pin saved yet — showing this device’s location for reference. Set the store on the map under
                  <strong>Shop &amp; delivery</strong> when using per-km pricing.
                </p>
                <div v-if="accountGeoLoading" class="d-flex align-center text-body-2 text--secondary">
                  <v-progress-circular indeterminate size="18" width="2" color="primary" class="mr-2" />
                  Finding your location…
                </div>
                <template v-else-if="accountGeoLat != null && accountGeoLng != null">
                  <div v-if="accountGeoPlaceLoading" class="text-body-2 text--secondary mb-1">Loading address…</div>
                  <div
                    v-else-if="accountGeoPlaceName"
                    class="text-body-1 font-weight-medium account-location__place mb-2"
                  >
                    {{ accountGeoPlaceName }}
                  </div>
                  <div class="text-body-2 font-mono account-location__coords text--secondary">
                    {{ formatAccountCoord(accountGeoLat) }}, {{ formatAccountCoord(accountGeoLng) }}
                  </div>
                  <a
                    class="text-caption font-weight-bold text-decoration-none mt-2 d-inline-block"
                    :href="accountMapsUrl(accountGeoLat, accountGeoLng)"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Open in Maps
                    <v-icon x-small color="primary" class="ml-1">open_in_new</v-icon>
                  </a>
                </template>
                <template v-else>
                  <p v-if="accountGeoError" class="text-caption error--text mb-2">{{ accountGeoError }}</p>
                  <v-btn
                    small
                    outlined
                    color="primary"
                    class="text-none"
                    :disabled="accountGeoLoading"
                    @click="fetchAccountCurrentLocation"
                  >
                    <v-icon left small>my_location</v-icon>
                    Use my location
                  </v-btn>
                </template>
              </template>
            </div>
          </v-card>

          <v-card class="admin-card pa-4 pa-sm-6 mb-4 mb-md-0" elevation="3" rounded="xl">
            <div class="card-label mb-6">New product</div>

            <v-text-field
              v-model="name"
              outlined
              hide-details="auto"
              label="Product name"
              class="rounded-lg"
              :disabled="submitting"
            />

            <v-text-field
              v-model="category"
              outlined
              hide-details="auto"
              label="Category"
              hint="Buyers use this to filter the shop (e.g. Clothing, Home, Accessories)."
              persistent-hint
              class="mt-2 rounded-lg"
              :disabled="submitting"
            />

            <v-text-field
              v-model="price"
              outlined
              hide-details="auto"
              label="Price (e.g. 100)"
              type="number"
              class="mt-2 rounded-lg"
              :disabled="submitting"
            />

            <v-text-field
              v-model="initialStock"
              outlined
              hide-details="auto"
              label="Stock quantity"
              type="number"
              min="0"
              hint="How many units you have ready to sell. Customers cannot order more than this."
              persistent-hint
              class="mt-2 rounded-lg"
              :disabled="submitting"
            />

            <v-file-input
              v-model="file"
              outlined
              hide-details="auto"
              accept="image/*"
              label="Product image"
              class="mt-2 rounded-lg"
              :disabled="submitting"
              prepend-icon="image"
              show-size
            />

            <v-btn
              block
              x-large
              depressed
              class="mt-6 text-none font-weight-bold btn-admin-primary"
              :loading="submitting"
              @click="submit"
            >
              <v-icon left dark>cloud_upload</v-icon>
              Publish product
            </v-btn>

            <v-alert v-if="submitError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ submitError }}
            </v-alert>
            <v-alert v-if="submitSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Product uploaded — it should appear on the shop immediately.
            </v-alert>
          </v-card>
        </v-col>

        <v-col cols="12" md="7">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Inventory</div>
              <v-spacer />
              <div
                v-if="!loading && sortedProducts.length"
                class="text-caption text--secondary mr-2"
              >
                <template v-if="filteredInventoryProducts.length">
                  Items {{ inventoryRangeFrom }}–{{ inventoryRangeTo }} of {{ filteredInventoryProducts.length }}
                  <span
                    v-if="filteredInventoryProducts.length !== sortedProducts.length"
                    class="text--disabled"
                  > ({{ sortedProducts.length }} in catalog)</span>
                </template>
              </div>
              <v-progress-circular v-if="loading" indeterminate size="22" width="2" color="accent" />
            </div>
            <p class="text-caption text--secondary mb-4">
              Edit <strong>category</strong> and <strong>quantity</strong>, then click <strong>Save changes</strong>.
              Sales reduce stock automatically when orders are placed. Use <strong>Delete</strong> to remove a product
              and its image.
            </p>

            <v-alert v-if="inventoryError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ inventoryError }}
            </v-alert>

            <v-alert v-if="deleteError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ deleteError }}
            </v-alert>

            <div v-if="!loading && sortedProducts.length === 0" class="muted-panel rounded-lg pa-6 pa-sm-8 text-center">
              <v-icon size="40" color="secondary" class="mb-3">inventory</v-icon>
              <div class="text-subtitle-1 font-weight-bold mb-1">No products yet</div>
              <div class="text-body-2 text--secondary d-none d-md-block">Add one using the form on the left.</div>
              <div class="text-body-2 text--secondary d-md-none">Add one using the new product form above.</div>
            </div>

            <template v-else>
              <div class="inventory-toolbar mb-4">
                <v-text-field
                  v-model="inventorySearch"
                  outlined
                  dense
                  hide-details
                  clearable
                  prepend-inner-icon="search"
                  label="Search products"
                  placeholder="Name or category…"
                  class="inventory-search-field rounded-lg"
                  aria-label="Search inventory"
                />
                <v-select
                  v-model="inventoryCategoryFilter"
                  :items="inventoryCategoryMenuItems"
                  item-text="text"
                  item-value="value"
                  outlined
                  dense
                  hide-details
                  clearable
                  label="Category"
                  prepend-inner-icon="category"
                  class="inventory-filter-select rounded-lg"
                  aria-label="Filter by category"
                />
              </div>

              <div
                v-if="!filteredInventoryProducts.length"
                class="muted-panel rounded-lg pa-6 text-center"
              >
                <v-icon size="36" color="secondary" class="mb-2">manage_search</v-icon>
                <div class="text-subtitle-2 font-weight-bold mb-1">No matching products</div>
                <div class="text-body-2 text--secondary mb-0">
                  Try a different search or choose <strong>All categories</strong>.
                </div>
              </div>

              <div v-else class="admin-inventory-wrap">
                <div class="admin-inventory-list">
                  <div
                    v-for="p in paginatedInventoryProducts"
                    :key="p.id"
                    class="admin-inventory-card"
                  >
                <div class="admin-inventory-card__details">
                  <div class="admin-inventory-card__thumb rounded-lg">
                    <v-img :src="p.imageUrl" height="72" max-width="72" class="rounded-lg">
                      <template #placeholder>
                        <v-row class="fill-height ma-0" align="center" justify="center">
                          <v-progress-circular indeterminate size="20" width="2" />
                        </v-row>
                      </template>
                    </v-img>
                  </div>
                  <div class="admin-inventory-card__text">
                    <div class="inventory-product-name">{{ p.name }}</div>
                    <div class="inventory-product-meta d-flex flex-wrap align-center">
                      <v-chip
                        small
                        label
                        outlined
                        class="inventory-category-chip text-none mr-2 mb-1"
                        color="secondary"
                      >
                        {{ categoryDraft[p.id] || 'Uncategorized' }}
                      </v-chip>
                      <span class="list-price inventory-price-pill mb-1">{{ formatZar(p.price) }}</span>
                      <span class="inventory-stock-pill text-body-2 mb-1 ml-2">
                        Stock
                        <strong class="text--primary">{{ p.stock != null ? p.stock : 0 }}</strong>
                      </span>
                    </div>
                  </div>
                </div>

                <div class="admin-inventory-card__actions">
                  <v-text-field
                    v-model="categoryDraft[p.id]"
                    outlined
                    hide-details="auto"
                    label="Category"
                    hint="Shown in the shop for filtering."
                    persistent-hint
                    class="inventory-cat-input mb-2"
                    :disabled="stockUpdatingId === p.id"
                  />
                  <div class="inventory-actions-bar">
                    <v-text-field
                      v-model.number="stockDraft[p.id]"
                      outlined
                      hide-details
                      type="number"
                      min="0"
                      label="Quantity"
                      class="inventory-qty-input"
                      :disabled="stockUpdatingId === p.id"
                    />
                    <v-btn
                      depressed
                      outlined
                      color="primary"
                      height="44"
                      class="inventory-btn-update text-none font-weight-bold px-4"
                      :loading="stockUpdatingId === p.id"
                      @click="saveInventoryRow(p)"
                    >
                      Save changes
                    </v-btn>
                    <v-btn
                      depressed
                      color="error"
                      height="44"
                      class="inventory-btn-delete text-none white--text px-4"
                      :disabled="deletingId === p.id || stockUpdatingId === p.id"
                      :loading="deletingId === p.id"
                      @click="openDeleteConfirm(p)"
                    >
                      <v-icon left small color="white">delete</v-icon>
                      Delete
                    </v-btn>
                  </div>
                </div>
                  </div>
                </div>
              </div>

              <div
                v-if="filteredInventoryProducts.length"
                class="inventory-pagination-row d-flex flex-column flex-sm-row align-center justify-space-between gap-3 pt-3"
              >
                <v-select
                  v-model="inventoryPerPage"
                  :items="inventoryPerPageOptions"
                  label="Items per page"
                  outlined
                  dense
                  hide-details
                  class="pagination-size-select rounded-lg mb-0"
                />
                <v-pagination
                  v-if="inventoryPageCount > 1"
                  v-model="inventoryPage"
                  :length="inventoryPageCount"
                  :total-visible="paginationVisible"
                  color="primary"
                  class="admin-pagination flex-grow-1 justify-sm-end"
                />
              </div>
            </template>
          </v-card>
        </v-col>
      </v-row>

   

      <v-row v-if="user" class="mt-2">
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

      <v-row v-if="user" class="mt-2">
        <v-col cols="12">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Sales & performance</div>
            </div>
            <p class="text-caption text--secondary mb-4">
              Filter by period and order type. Revenue uses each order’s total (including delivery).
              <strong>Top selling product</strong> is the one with the <strong>most units sold</strong> in this period (e.g. 5
              juice vs 2 blocks → juice). The table lists all products by units, then line revenue.
              <template v-if="statsOnlyPaid">Only <strong>confirmed payments</strong> are included.</template>
              <template v-else>Unpaid orders are included — totals may change when payments are confirmed.</template>
              Cancelled orders are always excluded.
            </p>

            <div class="stats-toolbar mb-4">
              <v-select
                v-model="statsPreset"
                :items="statsPresetItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                label="Period"
                class="stats-field stats-preset rounded-lg"
                @change="onStatsPresetChange"
              />
              <v-text-field
                v-model="statsDateFrom"
                outlined
                dense
                hide-details
                label="From"
                type="date"
                class="stats-field stats-date rounded-lg"
                @input="bumpStatsToCustom"
              />
              <v-text-field
                v-model="statsDateTo"
                outlined
                dense
                hide-details
                label="To"
                type="date"
                class="stats-field stats-date rounded-lg"
                @input="bumpStatsToCustom"
              />
              <v-select
                v-model="statsDelType"
                :items="statsDelTypeItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Delivery"
                class="stats-field stats-filter rounded-lg"
              />
              <v-select
                v-model="statsPayMethod"
                :items="statsPayMethodItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Payment"
                class="stats-field stats-filter rounded-lg"
              />
              <v-select
                v-model="statsCategoryFilter"
                :items="statsCategoryFilterItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Product category"
                class="stats-field stats-filter rounded-lg"
              />
              <v-switch
                v-model="statsOnlyPaid"
                hide-details
                class="stats-switch mt-sm-2"
                label="Confirmed payments only"
              />
            </div>

            <div v-if="ordersLoading" class="py-6 text-center">
              <v-progress-circular indeterminate color="primary" size="28" width="2" />
            </div>

            <template v-else>
              <div v-if="!statsFilteredOrders.length" class="muted-panel rounded-lg pa-8 text-center">
                <v-icon size="40" color="secondary" class="mb-3">insert_chart_outlined</v-icon>
                <div class="text-subtitle-1 font-weight-bold mb-1">No data for these filters</div>
                <div class="text-body-2 text--secondary">
                  Widen the date range, turn off “confirmed only”, or clear filters — or wait for matching orders.
                </div>
              </div>

              <template v-else>
                <v-row dense class="mb-6">
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile stat-tile--accent rounded-xl pa-4">
                      <div class="stat-tile__label">Revenue</div>
                      <div class="stat-tile__value">{{ formatZar(statsTotalRevenue) }}</div>
                      <div class="stat-tile__hint">{{ statsFilteredOrders.length }} orders</div>
                    </div>
                  </v-col>
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile rounded-xl pa-4">
                      <div class="stat-tile__label">Subtotal (items)</div>
                      <div class="stat-tile__value">{{ formatZar(statsTotalSubtotal) }}</div>
                      <div class="stat-tile__hint">Before delivery</div>
                    </div>
                  </v-col>
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile rounded-xl pa-4">
                      <div class="stat-tile__label">Delivery fees</div>
                      <div class="stat-tile__value">{{ formatZar(statsTotalDelivery) }}</div>
                      <div class="stat-tile__hint">Collected on orders</div>
                    </div>
                  </v-col>
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile rounded-xl pa-4">
                      <div class="stat-tile__label">Avg. order value</div>
                      <div class="stat-tile__value">{{ formatZar(statsAvgOrderValue) }}</div>
                      <div class="stat-tile__hint">Per order in range</div>
                    </div>
                  </v-col>
                </v-row>

                <v-alert
                  v-if="statsTopProductsCategoryFallback"
                  dense
                  text
                  type="info"
                  border="left"
                  colored-border
                  class="mb-4 rounded-lg"
                >
                  No sales in <strong>{{ statsCategoryFilter }}</strong> for this period — showing top sellers across
                  <strong>all</strong> categories.
                </v-alert>

                <div
                  v-if="statsTopSellingByUnits"
                  class="stat-tile stat-tile--accent stat-tile--top-seller rounded-xl pa-6 mb-6"
                >
                  <div class="stat-tile__label">Top selling product</div>
                  <div class="stat-tile--top-seller-name">{{ statsTopSellingByUnits.name }}</div>
                  <div class="stat-tile__hint mt-2 mb-0">
                    {{ statsTopSellingByUnits.units }} unit{{ statsTopSellingByUnits.units === 1 ? '' : 's' }} sold in this
                    period (most units in your selection).
                  </div>
                </div>

                <div v-if="false" class="text-subtitle-2 font-weight-bold mb-2">All products by units sold</div>
                <v-simple-table v-if="false"  dense class="stats-table rounded-lg elevation-1">
                  <template #default>
                    <thead>
                      <tr>
                        <th class="text-left">#</th>
                        <th class="text-left">Product</th>
                        <th class="text-right">Units</th>
                        <th class="text-right">Line total</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, idx) in statsProductsByUnits" :key="'u-' + row.productId">
                        <td>{{ idx + 1 }}</td>
                        <td>{{ row.name }}</td>
                        <td class="text-right">{{ row.units }}</td>
                        <td class="text-right">{{ formatZar(row.revenue) }}</td>
                      </tr>
                    </tbody>
                  </template>
                </v-simple-table>
               
              </template>
            </template>
          </v-card>
        </v-col>
      </v-row>
      <v-row v-if="user" align="stretch" class="admin-shop-bank-row mt-2 mt-md-4">
        <v-col cols="12" md="6" class="d-flex flex-column admin-shop-bank-col">
          <v-card
            class="admin-card admin-card--settings pa-4 pa-sm-6 d-flex flex-column flex-grow-1"
            elevation="3"
            rounded="xl"
          >
            <div class="admin-shop-bank-card__body flex-grow-1">
            <div class="card-label mb-2">Shop &amp; delivery</div>
            <p class="text-body-2 text--secondary mb-4">
              Choose how delivery is priced at checkout. Pickup is always free.
            </p>
            <v-select
              v-model="deliveryFeeModeDraft"
              :items="deliveryFeeModeItems"
              outlined
              hide-details="auto"
              label="Delivery pricing"
              item-text="text"
              item-value="value"
              class="rounded-lg"
              :disabled="shopSettingsSaving || shopSettingsLoading"
            />
            <v-text-field
              v-if="deliveryFeeModeDraft === 'standard'"
              v-model="deliveryFeeDraft"
              outlined
              hide-details="auto"
              label="Flat delivery fee (ZAR)"
              type="number"
              min="0"
              step="0.01"
              prefix="R"
              hint="One amount added to every delivery order."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="shopSettingsSaving || shopSettingsLoading"
            />
            <v-text-field
              v-if="deliveryFeeModeDraft === 'per_km'"
              v-model="deliveryFeePerKmDraft"
              outlined
              hide-details="auto"
              label="Rate per kilometre (ZAR)"
              type="number"
              min="0"
              step="0.01"
              prefix="R"
              hint="Straight-line distance × this rate (same formula as checkout)."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="shopSettingsSaving || shopSettingsLoading"
            />
            <div v-if="deliveryFeeModeDraft === 'per_km'" class="mt-4">
              <div class="text-subtitle-2 font-weight-bold mb-1">Store location</div>
              <p class="text-body-2 text--secondary mb-2">
                Click the map where your shop is. Customer delivery fees are calculated from this point to their pin.
              </p>
              <map-location-picker
                :value="{ lat: storeLat, lng: storeLng }"
                :center-lat="storeLat"
                :center-lng="storeLng"
                :height="220"
                hint="Drag the pin or click to place it. Use “Use my location” if you’re at the store."
                @input="onStoreMapInput"
              />
            </div>
            </div>
            <div class="admin-shop-bank-card__footer pt-2 mt-auto">
            <v-btn
              block
              depressed
              color="primary"
              class="text-none font-weight-bold btn-amber"
              :loading="shopSettingsSaving"
              :disabled="shopSettingsLoading"
              @click="saveShopSettings"
            >
              <v-icon left small color="white">local_shipping</v-icon>
              Save delivery settings
            </v-btn>
            <v-alert v-if="shopSettingsError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ shopSettingsError }}
            </v-alert>
            <v-alert v-if="shopSettingsSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Delivery settings saved — checkout will use this for new orders.
            </v-alert>
            </div>
          </v-card>
        </v-col>
        <v-col cols="12" md="6" class="d-flex flex-column admin-shop-bank-col">
          <v-card
            class="admin-card admin-card--settings pa-4 pa-sm-6 d-flex flex-column flex-grow-1"
            elevation="3"
            rounded="xl"
          >
            <div class="admin-shop-bank-card__body flex-grow-1">
            <div class="card-label mb-2">Banking (EFT)</div>
            <p class="text-body-2 text--secondary mb-4">
              Customers who pay by bank transfer see these details at checkout and on the confirmation screen. All fields
              marked with * are required before EFT is offered.
            </p>
            <v-text-field
              v-model="bankNameDraft"
              outlined
              hide-details="auto"
              label="Bank name *"
              class="rounded-lg"
              :disabled="bankingSaving || shopSettingsLoading"
            />
            <v-text-field
              v-model="bankAccountHolderDraft"
              outlined
              hide-details="auto"
              label="Account holder (name) *"
              hint="Exactly as it appears on the bank account."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="bankingSaving || shopSettingsLoading"
            />
            <v-text-field
              v-model="bankAccountNumberDraft"
              outlined
              hide-details="auto"
              label="Account number *"
              class="mt-4 rounded-lg"
              :disabled="bankingSaving || shopSettingsLoading"
            />
            <v-text-field
              v-model="bankBranchCodeDraft"
              outlined
              hide-details="auto"
              label="Branch / universal branch code"
              hint="Optional but recommended for SA banks."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="bankingSaving || shopSettingsLoading"
            />
            <v-textarea
              v-model="eftNotesDraft"
              outlined
              hide-details="auto"
              label="Extra payment notes"
              rows="3"
              auto-grow
              hint="e.g. Reference must include your order number, or use your name as reference."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="bankingSaving || shopSettingsLoading"
            />
            </div>
            <div class="admin-shop-bank-card__footer pt-2 mt-auto">
            <v-btn
              block
              depressed
              color="primary"
              class="text-none font-weight-bold btn-amber"
              :loading="bankingSaving"
              :disabled="shopSettingsLoading"
              @click="saveBankingSettings"
            >
              <v-icon left small color="white">account_balance</v-icon>
              Save banking details
            </v-btn>
            <v-alert v-if="bankingError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ bankingError }}
            </v-alert>
            <v-alert v-if="bankingSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Banking details saved — customers paying by EFT will see them at checkout.
            </v-alert>
            </div>
          </v-card>
        </v-col>
      </v-row>
    </v-container>

    <v-dialog
      v-model="deleteDialogOpen"
      max-width="460"
      content-class="delete-dialog-surface"
      :persistent="Boolean(deletingId)"
      @click:outside="closeDeleteDialogIfIdle"
    >
      <v-card v-if="deleteTarget" class="delete-dialog-card rounded-xl" elevation="10">
        <v-card-text class="pa-8 pb-4">
          <div class="delete-dialog-icon-wrap mb-6" aria-hidden="true">
            <v-icon color="white" size="32">delete_outline</v-icon>
          </div>
          <h2 class="delete-dialog-title mb-3">Remove this product?</h2>
          <p class="delete-dialog-lead text-body-1 mb-4">
            <strong class="delete-dialog-name">{{ deleteTarget.name }}</strong> will be removed from the shop and its
            image will be deleted from storage. This action cannot be undone.
          </p>
          <v-alert type="warning" outlined prominent border="left" colored-border class="delete-dialog-alert rounded-lg mb-0">
            <span class="text-body-2">Please confirm you selected the correct product before continuing.</span>
          </v-alert>
        </v-card-text>
        <v-card-actions class="delete-dialog-actions px-8 pb-8 pt-0 flex-wrap">
          <v-btn
            large
            outlined
            rounded
            color="primary"
            class="text-none font-weight-bold mb-2"
            :disabled="Boolean(deletingId)"
            @click="closeDeleteDialogIfIdle"
          >
            Cancel
          </v-btn>
          <v-spacer />
          <v-btn
            large
            depressed
            rounded
            color="error"
            class="text-none font-weight-bold delete-dialog-confirm mb-2 white--text"
            :loading="deletingId === deleteTarget.id"
            @click="confirmDeleteProduct"
          >
            <v-icon left color="white">delete_forever</v-icon>
            Remove product
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

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
import {
  compareProductsByCategoryThenName,
  createProduct,
  deleteProduct,
  subscribeToProducts,
  updateProductInventory
} from '@/services/products'
import {
  cancelUnpaidOrder,
  confirmOrderPayment,
  deleteOrderPermanently,
  fetchShopSettings,
  orderDisplayRef,
  subscribeToOrders,
  updateBankingDetails,
  updateOrderStatus,
  updateShopSettings
} from '@/services/orders'
import { loginWithEmailPassword, logout, subscribeToAuth } from '@/services/auth'
import { supabaseSetupMessage } from '@/supabase'
import { formatZar } from '@/utils/price'
import MapLocationPicker from '@/components/MapLocationPicker.vue'

export default {
  name: 'AdminView',
  components: { MapLocationPicker },
  data() {
    return {
      user: null,
      email: '',
      password: '',
      authLoading: false,
      authError: '',
      name: '',
      category: '',
      price: '',
      initialStock: '0',
      file: null,
      submitting: false,
      submitError: '',
      submitSuccess: false,
      loading: true,
      products: [],
      deletingId: null,
      deleteError: '',
      supabaseConfigHint: supabaseSetupMessage,
      orders: [],
      ordersLoading: false,
      ordersActionError: '',
      confirmingId: null,
      orderStatusUpdatingId: null,
      unsubOrders: null,
      cancelOrderDialogOpen: false,
      cancelOrderTarget: null,
      cancellingOrderId: null,
      deleteOrderDialogOpen: false,
      deleteOrderTarget: null,
      deletingOrderId: null,
      deleteDialogOpen: false,
      deleteTarget: null,
      stockDraft: {},
      categoryDraft: {},
      inventoryError: '',
      stockUpdatingId: null,
      inventorySearch: '',
      inventoryCategoryFilter: '',
      inventoryPage: 1,
      inventoryPerPage: 4,
      inventoryPerPageOptions: [4, 6, 12, 24, 48],
      ordersPage: 1,
      ordersPerPage: 4,
      ordersPerPageOptions: [4, 5, 10, 20, 50],
      deliveryFeeDraft: '50',
      deliveryFeeModeDraft: 'standard',
      deliveryFeePerKmDraft: '8',
      storeLat: null,
      storeLng: null,
      bankNameDraft: '',
      bankAccountHolderDraft: '',
      bankAccountNumberDraft: '',
      bankBranchCodeDraft: '',
      eftNotesDraft: '',
      bankingSaving: false,
      bankingError: '',
      bankingSuccess: false,
      shopSettingsLoading: false,
      shopSettingsSaving: false,
      shopSettingsError: '',
      shopSettingsSuccess: false,
      ordersSearch: '',
      ordersDeliveryFilter: '',
      ordersPaymentMethodFilter: '',
      ordersStatusFilter: '',
      statsPreset: '30d',
      statsDateFrom: '',
      statsDateTo: '',
      statsOnlyPaid: true,
      statsDelType: '',
      statsPayMethod: '',
      statsCategoryFilter: '',
      accountGeoLat: null,
      accountGeoLng: null,
      accountGeoLoading: false,
      accountGeoError: '',
      accountGeoAutoFetched: false,
      accountStorePlaceName: '',
      accountStorePlaceLoading: false,
      accountGeoPlaceName: '',
      accountGeoPlaceLoading: false
    }
  },
  computed: {
    accountCardHasStorePin() {
      return Number.isFinite(this.storeLat) && Number.isFinite(this.storeLng)
    },
    sortedProducts() {
      return [...(this.products || [])].sort(compareProductsByCategoryThenName)
    },
    inventoryCategoryMenuItems() {
      const set = new Set()
      this.sortedProducts.forEach((p) => {
        const c = String(p.category || 'Uncategorized').trim()
        if (c) set.add(c)
      })
      const sorted = [...set].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      return [{ text: 'All categories', value: '' }, ...sorted.map((c) => ({ text: c, value: c }))]
    },
    filteredInventoryProducts() {
      let list = [...this.sortedProducts]
      const cat = String(this.inventoryCategoryFilter || '').trim()
      if (cat) {
        const want = cat.toLowerCase()
        list = list.filter((p) => String(p.category || 'Uncategorized').trim().toLowerCase() === want)
      }
      const q = String(this.inventorySearch || '').trim().toLowerCase()
      if (q) {
        list = list.filter((p) => {
          const name = String(p.name || '').toLowerCase()
          const pcat = String(p.category || '').toLowerCase()
          const draftCat = String(this.categoryDraft[p.id] || '').toLowerCase()
          return name.includes(q) || pcat.includes(q) || draftCat.includes(q)
        })
      }
      return list
    },
    sortedOrdersForAdmin() {
      return [...(this.orders || [])].sort((a, b) => {
        const ta = new Date(a.created_at).getTime()
        const tb = new Date(b.created_at).getTime()
        return tb - ta
      })
    },
    ordersDeliveryFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Delivery', value: 'delivery' },
        { text: 'Pickup', value: 'pickup' }
      ]
    },
    ordersPaymentMethodFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'EFT', value: 'eft' },
        { text: 'Cash in store', value: 'cash_store' }
      ]
    },
    deliveryFeeModeItems() {
      return [
        { text: 'Standard — flat fee per delivery', value: 'standard' },
        { text: 'Per kilometre — rate × distance (km)', value: 'per_km' }
      ]
    },
    ordersStatusFilterItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Awaiting payment', value: 'awaiting' },
        { text: 'Processing', value: 'processing' },
        { text: 'Ready', value: 'ready' },
        { text: 'Completed', value: 'completed' },
        { text: 'Cancelled', value: 'cancelled' }
      ]
    },
    filteredOrdersForAdmin() {
      let list = [...this.sortedOrdersForAdmin]
      const q = String(this.ordersSearch || '').trim().toLowerCase()
      if (q) {
        const qId = q.replace(/-/g, '')
        list = list.filter((o) => {
          if (String(o.customer_name || '').toLowerCase().includes(q)) return true
          if (String(o.customer_email || '').toLowerCase().includes(q)) return true
          if (String(o.delivery_address || '').toLowerCase().includes(q)) return true
          const refStr = String(o.order_ref || '').toLowerCase()
          if (refStr.includes(q)) return true
          const idStr = String(o.id || '').toLowerCase()
          if (idStr.includes(q)) return true
          if (qId.length >= 4 && idStr.replace(/-/g, '').includes(qId)) return true
          const lines = o.order_items || []
          for (let i = 0; i < lines.length; i++) {
            const it = lines[i]
            const pn = (it.products && it.products.name) || ''
            if (String(pn).toLowerCase().includes(q)) return true
          }
          return false
        })
      }
      const del = String(this.ordersDeliveryFilter || '').trim()
      if (del) list = list.filter((o) => o.delivery_type === del)
      const pm = String(this.ordersPaymentMethodFilter || '').trim()
      if (pm) list = list.filter((o) => o.payment_method === pm)
      const st = String(this.ordersStatusFilter || '').trim()
      if (st === 'cancelled') {
        list = list.filter((o) => this.orderCancelled(o))
      } else if (st === 'awaiting') {
        list = list.filter((o) => !this.orderCancelled(o) && !o.payment_confirmed)
      } else if (st === 'processing' || st === 'ready' || st === 'completed') {
        list = list.filter((o) => !this.orderCancelled(o) && this.effectiveOrderStatus(o) === st)
      }
      return list
    },
    inventoryPageCount() {
      const n = this.filteredInventoryProducts.length
      const per = this.inventoryPerPage
      return Math.max(1, Math.ceil(n / per) || 1)
    },
    paginatedInventoryProducts() {
      const list = this.filteredInventoryProducts
      const per = this.inventoryPerPage
      const start = (this.inventoryPage - 1) * per
      return list.slice(start, start + per)
    },
    inventoryRangeFrom() {
      const n = this.filteredInventoryProducts.length
      if (!n) return 0
      return (this.inventoryPage - 1) * this.inventoryPerPage + 1
    },
    inventoryRangeTo() {
      const n = this.filteredInventoryProducts.length
      return Math.min(this.inventoryPage * this.inventoryPerPage, n)
    },
    ordersPageCount() {
      const n = this.filteredOrdersForAdmin.length
      const per = this.ordersPerPage
      return Math.max(1, Math.ceil(n / per) || 1)
    },
    paginatedOrders() {
      const list = this.filteredOrdersForAdmin
      const per = this.ordersPerPage
      const start = (this.ordersPage - 1) * per
      return list.slice(start, start + per)
    },
    ordersRangeFrom() {
      const n = this.filteredOrdersForAdmin.length
      if (!n) return 0
      return (this.ordersPage - 1) * this.ordersPerPage + 1
    },
    ordersRangeTo() {
      const n = this.filteredOrdersForAdmin.length
      return Math.min(this.ordersPage * this.ordersPerPage, n)
    },
    paginationVisible() {
      return this.$vuetify.breakpoint.smAndDown ? 5 : 9
    },
    statsPresetItems() {
      return [
        { text: 'Last 7 days', value: '7d' },
        { text: 'Last 30 days', value: '30d' },
        { text: 'This month', value: 'month' },
        { text: 'All time', value: 'all' },
        { text: 'Custom range', value: 'custom' }
      ]
    },
    statsDelTypeItems() {
      return [
        { text: 'All', value: '' },
        { text: 'Delivery', value: 'delivery' },
        { text: 'Pickup', value: 'pickup' }
      ]
    },
    statsPayMethodItems() {
      return [
        { text: 'All', value: '' },
        { text: 'EFT', value: 'eft' },
        { text: 'Cash in store', value: 'cash_store' }
      ]
    },
    statsCategoryFilterItems() {
      const catByProductId = new Map()
      ;(this.products || []).forEach((p) => {
        catByProductId.set(p.id, String(p.category || 'Uncategorized').trim() || 'Uncategorized')
      })
      const set = new Set()
      ;(this.products || []).forEach((p) => {
        const c = String(p.category || 'Uncategorized').trim() || 'Uncategorized'
        set.add(c)
      })
      for (const o of this.statsFilteredOrders) {
        for (const it of o.order_items || []) {
          const pid = it.product_id
          if (!pid) continue
          set.add(catByProductId.get(pid) || 'Uncategorized')
        }
      }
      const sorted = [...set].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      return [{ text: 'All categories', value: '' }, ...sorted.map((c) => ({ text: c, value: c }))]
    },
    statsDateStartMs() {
      if (!this.statsDateFrom) return null
      const d = new Date(`${this.statsDateFrom}T00:00:00`)
      const t = d.getTime()
      return Number.isFinite(t) ? t : null
    },
    statsDateEndMs() {
      if (!this.statsDateTo) return null
      const d = new Date(`${this.statsDateTo}T23:59:59.999`)
      const t = d.getTime()
      return Number.isFinite(t) ? t : null
    },
    statsFilteredOrders() {
      let list = (this.orders || []).filter((o) => !o.cancelled_at)
      if (this.statsOnlyPaid) {
        list = list.filter((o) => o.payment_confirmed)
      }
      const fromMs = this.statsDateStartMs
      const toMs = this.statsDateEndMs
      list = list.filter((o) => {
        const t =
          o.payment_confirmed && o.payment_confirmed_at
            ? new Date(o.payment_confirmed_at).getTime()
            : new Date(o.created_at).getTime()
        if (fromMs != null && t < fromMs) return false
        if (toMs != null && t > toMs) return false
        return true
      })
      const del = String(this.statsDelType || '').trim()
      if (del) list = list.filter((o) => o.delivery_type === del)
      const pm = String(this.statsPayMethod || '').trim()
      if (pm) list = list.filter((o) => o.payment_method === pm)
      return list
    },
    statsTotalRevenue() {
      return this.statsFilteredOrders.reduce((s, o) => s + Number(o.total_zar || 0), 0)
    },
    statsTotalSubtotal() {
      return this.statsFilteredOrders.reduce((s, o) => s + Number(o.subtotal_zar || 0), 0)
    },
    statsTotalDelivery() {
      return this.statsFilteredOrders.reduce((s, o) => s + Number(o.delivery_fee_zar || 0), 0)
    },
    statsAvgOrderValue() {
      const n = this.statsFilteredOrders.length
      if (!n) return 0
      return this.statsTotalRevenue / n
    },
    /** Per-product totals from filtered orders (all lines). Category is current catalog label for filtering. */
    statsProductAggregatesFull() {
      const catByProductId = new Map()
      ;(this.products || []).forEach((p) => {
        catByProductId.set(p.id, String(p.category || 'Uncategorized').trim() || 'Uncategorized')
      })
      const map = new Map()
      for (const o of this.statsFilteredOrders) {
        for (const it of o.order_items || []) {
          const pid = it.product_id
          if (!pid) continue
          const category = catByProductId.get(pid) || 'Uncategorized'
          const name = it.products && it.products.name ? it.products.name : 'Unknown product'
          const qty = Number(it.quantity) || 0
          const line = Number(it.line_total_zar) || 0
          const cur = map.get(pid) || { productId: pid, name, category, units: 0, revenue: 0 }
          cur.name = name
          cur.category = category
          cur.units += qty
          cur.revenue += line
          map.set(pid, cur)
        }
      }
      return Array.from(map.values())
    },
    /** Rows used for top-product tables: category filter narrows; if nothing matches, fall back to full (best sellers overall). */
    statsProductsScopedForTop() {
      const full = this.statsProductAggregatesFull
      const catFilter = String(this.statsCategoryFilter || '').trim()
      if (!catFilter) return { rows: full, fallback: false }
      const filtered = full.filter((r) => r.category === catFilter)
      if (filtered.length) return { rows: filtered, fallback: false }
      if (!full.length) return { rows: full, fallback: false }
      return { rows: full, fallback: true }
    },
    statsTopProductsCategoryFallback() {
      return this.statsProductsScopedForTop.fallback
    },
    statsProductsByUnits() {
      const rows = this.statsProductsScopedForTop.rows
      return [...rows]
        .sort((a, b) => b.units - a.units || String(a.name || '').localeCompare(String(b.name || ''), undefined, { sensitivity: 'base' }))
        .slice(0, 25)
    },
    /** #1 by units sold — e.g. 5 juice vs 2 blocks → juice. */
    statsTopSellingByUnits() {
      const list = this.statsProductsByUnits
      return list.length ? list[0] : null
    }
  },
  watch: {
    inventorySearch() {
      this.inventoryPage = 1
    },
    inventoryCategoryFilter() {
      this.inventoryPage = 1
    },
    filteredInventoryProducts(val) {
      this.$nextTick(() => {
        const max = Math.max(1, Math.ceil(val.length / this.inventoryPerPage) || 1)
        if (this.inventoryPage > max) this.inventoryPage = max
      })
    },
    inventoryPerPage() {
      this.inventoryPage = 1
    },
    ordersSearch() {
      this.ordersPage = 1
    },
    ordersDeliveryFilter() {
      this.ordersPage = 1
    },
    ordersPaymentMethodFilter() {
      this.ordersPage = 1
    },
    ordersStatusFilter() {
      this.ordersPage = 1
    },
    filteredOrdersForAdmin(val) {
      this.$nextTick(() => {
        const max = Math.max(1, Math.ceil(val.length / this.ordersPerPage) || 1)
        if (this.ordersPage > max) this.ordersPage = max
      })
    },
    ordersPerPage() {
      this.ordersPage = 1
    },
    products: {
      deep: true,
      handler(list) {
        (list || []).forEach((p) => {
          const s = p.stock != null ? p.stock : 0
          this.$set(this.stockDraft, p.id, s)
          const c = p.category != null && String(p.category).trim() !== '' ? String(p.category).trim() : 'Uncategorized'
          this.$set(this.categoryDraft, p.id, c)
        })
      }
    },
    deleteDialogOpen(open) {
      if (!open && !this.deletingId) {
        this.deleteTarget = null
      }
    },
    cancelOrderDialogOpen(open) {
      if (!open && !this.cancellingOrderId) {
        this.cancelOrderTarget = null
      }
    },
    deleteOrderDialogOpen(open) {
      if (!open && !this.deletingOrderId) {
        this.deleteOrderTarget = null
      }
    },
    user: {
      immediate: true,
      handler(u) {
        if (this.unsubOrders) {
          this.unsubOrders()
          this.unsubOrders = null
        }
        if (u) {
          this.ordersLoading = true
          this.unsubOrders = subscribeToOrders((rows) => {
            this.orders = rows
            this.ordersLoading = false
          })
          this.loadShopSettingsForAdmin()
        } else {
          this.orders = []
          this.ordersLoading = false
          this.shopSettingsError = ''
          this.shopSettingsSuccess = false
          this.accountGeoLat = null
          this.accountGeoLng = null
          this.accountGeoError = ''
          this.accountGeoLoading = false
          this.accountGeoAutoFetched = false
          this.accountStorePlaceName = ''
          this.accountGeoPlaceName = ''
        }
      }
    }
  },
  created() {
    this.unsubAuth = subscribeToAuth((user) => {
      this.user = user
    })
    this.unsub = subscribeToProducts((products) => {
      this.products = products
      this.loading = false
    })
    this.applyStatsPreset('30d')
  },
  beforeDestroy() {
    if (this._accountStorePlaceTimer) clearTimeout(this._accountStorePlaceTimer)
    if (this.unsubAuth) this.unsubAuth()
    if (this.unsub) this.unsub()
    if (this.unsubOrders) this.unsubOrders()
  },
  methods: {
    displayOrderRef(o) {
      return orderDisplayRef(o)
    },
    formatZar,
    formatWhen(iso) {
      if (!iso) return ''
      try {
        return new Date(iso).toLocaleString()
      } catch {
        return String(iso)
      }
    },
    lineProductName(it) {
      if (it.products && it.products.name) return it.products.name
      return 'Product'
    },
    orderCancelled(o) {
      return Boolean(o && o.cancelled_at)
    },
    effectiveOrderStatus(o) {
      if (!o) return 'awaiting_payment'
      if (o.cancelled_at) return 'cancelled'
      const s = String(o.order_status || '').trim()
      if (s) return s
      return o.payment_confirmed ? 'processing' : 'awaiting_payment'
    },
    fulfillmentStatusLabel(o) {
      const s = this.effectiveOrderStatus(o)
      const d = o && o.delivery_type === 'delivery'
      const map = {
        cancelled: 'Cancelled',
        awaiting_payment: 'Awaiting payment',
        processing: 'Processing',
        ready: d ? 'Out for delivery' : 'Ready for pickup',
        completed: 'Completed'
      }
      return map[s] || s
    },
    fulfillmentChipColor(o) {
      const s = this.effectiveOrderStatus(o)
      if (s === 'completed') return 'success'
      if (s === 'ready') return 'accent'
      if (s === 'processing') return 'primary'
      return 'grey'
    },
    orderFulfillmentSelectItems(o) {
      const d = o && o.delivery_type === 'delivery'
      return [
        { text: 'Processing', value: 'processing' },
        { text: d ? 'Out for delivery' : 'Ready for pickup', value: 'ready' },
        { text: 'Completed', value: 'completed' }
      ]
    },
    async setOrderFulfillmentStatus(o, status) {
      if (!o || !o.id) return
      const next = String(status || '').trim()
      if (this.effectiveOrderStatus(o) === next) return
      this.ordersActionError = ''
      this.orderStatusUpdatingId = o.id
      try {
        await updateOrderStatus(o.id, next)
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not update status.'
      } finally {
        this.orderStatusUpdatingId = null
      }
    },
    applyStatsPreset(p) {
      const pad = (n) => String(n).padStart(2, '0')
      const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
      const today = new Date()
      this.statsPreset = p
      if (p === 'all') {
        this.statsDateFrom = ''
        this.statsDateTo = ''
        return
      }
      if (p === 'custom') return
      const start = new Date(today)
      if (p === '7d') start.setDate(start.getDate() - 7)
      else if (p === '30d') start.setDate(start.getDate() - 30)
      else if (p === 'month') {
        start.setFullYear(today.getFullYear(), today.getMonth(), 1)
      }
      this.statsDateFrom = fmt(start)
      this.statsDateTo = fmt(today)
    },
    onStatsPresetChange(val) {
      if (val && val !== 'custom') this.applyStatsPreset(val)
    },
    bumpStatsToCustom() {
      this.statsPreset = 'custom'
    },
    openCancelOrderDialog(o) {
      this.cancelOrderTarget = o
      this.cancelOrderDialogOpen = true
    },
    async confirmCancelOrder() {
      if (!this.cancelOrderTarget || !this.cancelOrderTarget.id) return
      this.ordersActionError = ''
      this.cancellingOrderId = this.cancelOrderTarget.id
      try {
        await cancelUnpaidOrder(this.cancelOrderTarget.id)
        this.cancelOrderDialogOpen = false
        this.cancelOrderTarget = null
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not cancel order.'
      } finally {
        this.cancellingOrderId = null
      }
    },
    openDeleteOrderDialog(o) {
      if (!o || !o.id) return
      this.ordersActionError = ''
      this.deleteOrderTarget = o
      this.deleteOrderDialogOpen = true
    },
    closeDeleteOrderDialogIfIdle() {
      if (this.deletingOrderId) return
      this.deleteOrderDialogOpen = false
      this.deleteOrderTarget = null
    },
    async confirmDeleteOrderPermanent() {
      const t = this.deleteOrderTarget
      if (!t || !t.id) return
      this.ordersActionError = ''
      this.deletingOrderId = t.id
      try {
        await deleteOrderPermanently(t.id)
        this.deleteOrderDialogOpen = false
        this.deleteOrderTarget = null
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not delete order.'
      } finally {
        this.deletingOrderId = null
      }
    },
    openInvoicePrint(o) {
      if (!o || !o.id) return
      const r = this.$router.resolve({
        name: 'admin-order-invoice',
        params: { orderId: o.id }
      })
      window.open(r.href, '_blank', 'noopener,noreferrer')
    },
    async confirmPayment(o) {
      this.ordersActionError = ''
      this.confirmingId = o.id
      try {
        await confirmOrderPayment(o.id)
      } catch (e) {
        this.ordersActionError = e && e.message ? e.message : 'Could not confirm.'
      } finally {
        this.confirmingId = null
      }
    },
    async loadShopSettingsForAdmin() {
      this.shopSettingsLoading = true
      this.shopSettingsError = ''
      try {
        const s = await fetchShopSettings()
        this.deliveryFeeDraft = String(Number.isFinite(s.deliveryFeeZar) ? s.deliveryFeeZar : 50)
        this.deliveryFeeModeDraft = s.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard'
        this.deliveryFeePerKmDraft = String(
          Number.isFinite(s.deliveryFeePerKmZar) ? s.deliveryFeePerKmZar : 8
        )
        this.storeLat = Number.isFinite(s.storeLat) ? s.storeLat : null
        this.storeLng = Number.isFinite(s.storeLng) ? s.storeLng : null
        this.bankNameDraft = s.bankName || ''
        this.bankAccountHolderDraft = s.bankAccountHolder || ''
        this.bankAccountNumberDraft = s.bankAccountNumber || ''
        this.bankBranchCodeDraft = s.bankBranchCode || ''
        this.eftNotesDraft = s.eftBankInstructions || ''
      } catch (e) {
        this.shopSettingsError = e && e.message ? e.message : 'Could not load delivery fee.'
      } finally {
        this.shopSettingsLoading = false
        this.$nextTick(() => {
          if (this.accountCardHasStorePin) {
            this._loadAccountStorePlaceName()
          } else {
            this.accountStorePlaceName = ''
          }
          if (
            this.user &&
            !this.accountCardHasStorePin &&
            !this.accountGeoAutoFetched
          ) {
            this.accountGeoAutoFetched = true
            this.fetchAccountCurrentLocation()
          }
        })
      }
    },
    formatPlaceFromReversePayload(d) {
      if (!d || typeof d !== 'object') return ''
      const place = String(d.locality || d.city || '').trim()
      const region = String(d.principalSubdivision || '').trim()
      const country = String(d.countryName || '').trim()
      const parts = []
      if (place) parts.push(place)
      if (region && region.toLowerCase() !== place.toLowerCase()) parts.push(region)
      if (country) parts.push(country)
      return parts.join(', ')
    },
    async fetchReversePlaceLabel(lat, lng) {
      const la = Number(lat)
      const ln = Number(lng)
      if (!Number.isFinite(la) || !Number.isFinite(ln)) return ''
      try {
        const url = `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${encodeURIComponent(
          la
        )}&longitude=${encodeURIComponent(ln)}&localityLanguage=en`
        const res = await fetch(url)
        if (!res.ok) return ''
        const data = await res.json()
        return this.formatPlaceFromReversePayload(data)
      } catch {
        return ''
      }
    },
    _loadAccountStorePlaceName() {
      if (!this.accountCardHasStorePin) {
        this.accountStorePlaceName = ''
        this.accountStorePlaceLoading = false
        return
      }
      this.accountStorePlaceLoading = true
      this.fetchReversePlaceLabel(this.storeLat, this.storeLng).then((label) => {
        this.accountStorePlaceName = label
        this.accountStorePlaceLoading = false
      })
    },
    scheduleAccountStorePlaceName() {
      if (this._accountStorePlaceTimer) clearTimeout(this._accountStorePlaceTimer)
      this._accountStorePlaceTimer = setTimeout(() => {
        this._accountStorePlaceTimer = null
        this._loadAccountStorePlaceName()
      }, 500)
    },
    _loadAccountGeoPlaceName() {
      if (this.accountCardHasStorePin) return
      if (this.accountGeoLat == null || this.accountGeoLng == null) {
        this.accountGeoPlaceName = ''
        this.accountGeoPlaceLoading = false
        return
      }
      this.accountGeoPlaceLoading = true
      this.fetchReversePlaceLabel(this.accountGeoLat, this.accountGeoLng).then((label) => {
        this.accountGeoPlaceName = label
        this.accountGeoPlaceLoading = false
      })
    },
    formatAccountCoord(n) {
      if (n == null || !Number.isFinite(Number(n))) return '—'
      return Number(n).toFixed(5)
    },
    accountMapsUrl(lat, lng) {
      const la = Number(lat)
      const ln = Number(lng)
      if (!Number.isFinite(la) || !Number.isFinite(ln)) return '#'
      return `https://www.google.com/maps?q=${la},${ln}`
    },
    fetchAccountCurrentLocation() {
      if (!navigator.geolocation) {
        this.accountGeoError = 'Location is not available in this browser.'
        return
      }
      this.accountGeoLoading = true
      this.accountGeoError = ''
      this.accountGeoPlaceName = ''
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          this.accountGeoLat = pos.coords.latitude
          this.accountGeoLng = pos.coords.longitude
          this.accountGeoLoading = false
          this._loadAccountGeoPlaceName()
        },
        (err) => {
          this.accountGeoLoading = false
          this.accountGeoError =
            err && err.code === 1
              ? 'Location permission denied — allow location or set the store pin under Shop & delivery.'
              : 'Could not read your location. Try again or set the store pin on the map.'
        },
        { enableHighAccuracy: false, maximumAge: 60000, timeout: 15000 }
      )
    },
    onStoreMapInput({ lat, lng }) {
      this.storeLat = lat
      this.storeLng = lng
      this.scheduleAccountStorePlaceName()
    },
    async saveShopSettings() {
      this.shopSettingsError = ''
      this.shopSettingsSuccess = false
      if (this.deliveryFeeModeDraft === 'per_km' && (this.storeLat == null || this.storeLng == null)) {
        this.shopSettingsError =
          'Per-km pricing needs the store location — place the pin on the store map (Shop & delivery section).'
        return
      }
      this.shopSettingsSaving = true
      try {
        await updateShopSettings({
          deliveryFeeZar: this.deliveryFeeDraft,
          deliveryFeeMode: this.deliveryFeeModeDraft,
          deliveryFeePerKmZar: this.deliveryFeePerKmDraft,
          storeLat: this.deliveryFeeModeDraft === 'per_km' ? this.storeLat : null,
          storeLng: this.deliveryFeeModeDraft === 'per_km' ? this.storeLng : null
        })
        this.shopSettingsSuccess = true
        setTimeout(() => {
          this.shopSettingsSuccess = false
        }, 3500)
      } catch (e) {
        this.shopSettingsError = e && e.message ? e.message : 'Could not save delivery fee.'
      } finally {
        this.shopSettingsSaving = false
      }
    },
    async saveBankingSettings() {
      this.bankingError = ''
      this.bankingSuccess = false
      this.bankingSaving = true
      try {
        await updateBankingDetails({
          bankName: this.bankNameDraft,
          bankAccountHolder: this.bankAccountHolderDraft,
          bankAccountNumber: this.bankAccountNumberDraft,
          bankBranchCode: this.bankBranchCodeDraft,
          eftBankInstructions: this.eftNotesDraft
        })
        this.bankingSuccess = true
        setTimeout(() => {
          this.bankingSuccess = false
        }, 3500)
      } catch (e) {
        this.bankingError = e && e.message ? e.message : 'Could not save banking details.'
      } finally {
        this.bankingSaving = false
      }
    },
    async doLogin() {
      this.authError = ''
      this.authLoading = true
      try {
        await loginWithEmailPassword(this.email, this.password)
        this.email = ''
        this.password = ''
      } catch (e) {
        this.authError = e && e.message ? e.message : 'Sign in failed.'
      } finally {
        this.authLoading = false
      }
    },
    async doLogout() {
      await logout()
    },
    async submit() {
      this.submitError = ''
      this.submitSuccess = false
      this.submitting = true

      try {
        await createProduct({
          name: this.name,
          category: this.category,
          price: this.price,
          stock: this.initialStock,
          file: this.file
        })
        this.name = ''
        this.category = ''
        this.price = ''
        this.initialStock = '0'
        this.file = null
        this.submitSuccess = true
        setTimeout(() => (this.submitSuccess = false), 2500)
      } catch (e) {
        this.submitError = e && e.message ? e.message : 'Upload failed.'
      } finally {
        this.submitting = false
      }
    },
    async saveInventoryRow(p) {
      if (!p || !p.id) return
      this.inventoryError = ''
      this.stockUpdatingId = p.id
      try {
        const raw = this.stockDraft[p.id]
        const n = typeof raw === 'string' ? parseInt(raw, 10) : raw
        const qty = Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 0
        const catRaw = this.categoryDraft[p.id]
        const category = catRaw != null ? String(catRaw).trim() : ''
        if (!category) {
          this.inventoryError = 'Please enter a category for this product.'
          return
        }
        await updateProductInventory({ id: p.id, stock: qty, category })
        this.$set(this.stockDraft, p.id, qty)
        this.$set(this.categoryDraft, p.id, category)
      } catch (e) {
        this.inventoryError = e && e.message ? e.message : 'Could not save changes.'
      } finally {
        this.stockUpdatingId = null
      }
    },
    openDeleteConfirm(p) {
      if (!this.user) return
      this.deleteError = ''
      this.deleteTarget = p
      this.deleteDialogOpen = true
    },
    closeDeleteDialogIfIdle() {
      if (this.deletingId) return
      this.deleteDialogOpen = false
      this.deleteTarget = null
    },
    async confirmDeleteProduct() {
      const p = this.deleteTarget
      if (!this.user || !p) return
      this.deleteError = ''
      const imagePath = p.imagePath || p.image_path
      this.deletingId = p.id
      try {
        await deleteProduct({ id: p.id, imagePath })
        this.deleteError = ''
        this.deleteDialogOpen = false
        this.deleteTarget = null
      } catch (e) {
        this.deleteError = e && e.message ? e.message : 'Delete failed.'
      } finally {
        this.deletingId = null
      }
    }
  }
}
</script>

<style scoped>
.admin-hero {
  background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 52%, #7c2d12 100%);
  color: #f8fafc;
  position: relative;
  overflow: hidden;
}

.admin-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(500px 280px at 15% 20%, rgba(234, 88, 12, 0.25), transparent 70%),
    radial-gradient(400px 220px at 90% 0%, rgba(148, 163, 184, 0.2), transparent 65%);
  pointer-events: none;
}

.admin-hero__inner {
  position: relative;
  z-index: 1;
}

.admin-kicker {
  display: inline-flex;
  align-items: center;
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(248, 250, 252, 0.65);
}

.admin-title {
  font-size: clamp(1.75rem, 3vw, 2.25rem);
  font-weight: 700;
  letter-spacing: -0.035em;
  line-height: 1.15;
}

.admin-lead {
  margin-top: 0.75rem;
  max-width: 48ch;
  color: rgba(248, 250, 252, 0.78);
  line-height: 1.6;
  font-size: 1rem;
}

.admin-body {
  margin-top: -32px;
}

.admin-login-row {
  margin-top: 0;
}

.admin-login-card {
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.account-location {
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.account-location__label {
  letter-spacing: 0.06em;
  color: rgba(15, 23, 42, 0.45);
}

.account-location__coords {
  word-break: break-word;
}

.account-location__place {
  line-height: 1.45;
  color: #0f172a;
}

.admin-card {
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.admin-shop-bank-row.v-row {
  align-items: stretch;
}

.admin-shop-bank-row > .admin-shop-bank-col {
  align-items: stretch;
}

.admin-shop-bank-row > .admin-shop-bank-col > .v-card {
  flex: 1 1 auto;
  min-height: 0;
}

.admin-card--settings {
  min-width: 0;
  width: 100%;
}

.admin-shop-bank-card__body {
  min-height: 0;
}

.admin-shop-bank-card__footer {
  flex-shrink: 0;
}

.card-label {
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
}

.muted-panel {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  background: rgba(248, 250, 252, 0.85);
}

.btn-admin-primary {
  border-radius: 14px !important;
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  color: #fff !important;
  box-shadow: 0 12px 32px -12px rgba(194, 65, 12, 0.65) !important;
}

/* Inventory toolbar (search + filter) */
.inventory-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (min-width: 600px) {
  .inventory-toolbar {
    flex-direction: row;
    align-items: flex-start;
    gap: 12px;
  }

  .inventory-search-field {
    flex: 1 1 auto;
    min-width: 0;
  }

  .inventory-filter-select {
    flex: 0 1 220px;
    width: 100%;
    max-width: 260px;
  }
}

/* Orders toolbar (search + filters) */
.orders-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (min-width: 600px) {
  .orders-toolbar {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 12px;
  }

  .orders-search-field {
    flex: 1 1 260px;
    min-width: 200px;
  }

  .orders-filter-select {
    flex: 0 1 200px;
    max-width: 240px;
  }
}

.pagination-size-select {
  max-width: 220px;
  width: 100%;
}

.inventory-pagination-row,
.orders-pagination-row {
  width: 100%;
}

/* Sales & performance */
.stats-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (min-width: 960px) {
  .stats-toolbar {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
  }

  .stats-field.stats-preset {
    flex: 0 1 200px;
    max-width: 220px;
  }

  .stats-field.stats-date {
    flex: 0 1 170px;
    max-width: 190px;
  }

  .stats-field.stats-filter {
    flex: 0 1 190px;
    max-width: 220px;
  }
}

.stats-switch {
  flex: 0 0 auto;
}

.stat-tile {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(248, 250, 252, 0.95);
  height: 100%;
}

.stat-tile--accent {
  border-color: rgba(234, 88, 12, 0.35);
  background: linear-gradient(145deg, rgba(255, 247, 237, 0.9) 0%, rgba(255, 255, 255, 0.98) 100%);
}

.stat-tile__label {
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 6px;
}

.stat-tile__value {
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1.2;
}

.stat-tile__hint {
  margin-top: 8px;
  font-size: 0.75rem;
  color: rgba(15, 23, 42, 0.5);
}

.stat-tile--accent .stat-tile__value {
  background: linear-gradient(120deg, #c2410c, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.stat-tile--top-seller {
  border-width: 2px;
}

.stat-tile--top-seller-name {
  font-size: clamp(1.5rem, 4vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1.2;
}

.stat-tile--accent .stat-tile--top-seller-name {
  background: linear-gradient(120deg, #9a3412, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.stats-table {
  overflow: hidden;
}

.admin-inventory-wrap {
  width: 100%;
}

/* Two columns from md breakpoint; one column on small screens */
.admin-inventory-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  align-items: start;
}

@media (min-width: 960px) {
  .admin-inventory-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
}

/* Inner cards: details on top, controls below */
.admin-inventory-card {
  min-width: 0;
  border: 1px solid rgba(15, 23, 42, 0.07);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.8) inset,
    0 8px 28px -20px rgba(15, 23, 42, 0.35);
  overflow: hidden;
}

.admin-inventory-card__details {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 16px 14px;
}

@media (min-width: 600px) {
  .admin-inventory-card__details {
    padding: 18px 20px 16px;
    gap: 16px;
  }
}

.admin-inventory-card__thumb {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  overflow: hidden;
  background: linear-gradient(145deg, #e2e8f0, #f1f5f9);
  box-shadow: 0 4px 14px -8px rgba(15, 23, 42, 0.35);
}

.admin-inventory-card__thumb >>> .v-image {
  border-radius: 12px;
}

.admin-inventory-card__text {
  min-width: 0;
  flex: 1;
}

.inventory-product-name {
  font-size: 1.0625rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.35;
  color: #0f172a;
  word-break: break-word;
}

.inventory-product-meta {
  margin-top: 8px;
}

.inventory-category-chip {
  font-size: 0.6875rem !important;
  font-weight: 600 !important;
  letter-spacing: 0.04em;
  height: 24px !important;
  border-color: rgba(100, 116, 139, 0.35) !important;
}

.inventory-price-pill {
  font-size: 0.9375rem !important;
}

.inventory-stock-pill {
  color: rgba(15, 23, 42, 0.65);
}

.admin-inventory-card__actions {
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.92) 0%, rgba(241, 245, 249, 0.55) 100%);
  padding: 14px 16px 16px;
}

.inventory-cat-input {
  margin-top: 0;
}

.inventory-cat-input >>> .v-input__slot {
  min-height: 44px !important;
  border-radius: 12px !important;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.inventory-cat-input >>> .v-input__slot:hover {
  box-shadow: 0 4px 14px -8px rgba(15, 23, 42, 0.18);
}

.inventory-cat-input >>> fieldset {
  border-width: 1px !important;
}

.inventory-cat-input >>> .v-messages {
  min-height: 20px;
  margin-bottom: 4px;
}

@media (min-width: 600px) {
  .admin-inventory-card__actions {
    padding: 16px 20px 18px;
  }
}

.inventory-actions-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (min-width: 600px) {
  .inventory-actions-bar {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 12px;
  }
}

.inventory-qty-input {
  width: 100%;
  max-width: 100%;
  margin: 0;
  padding: 0;
}

@media (min-width: 600px) {
  .inventory-qty-input {
    width: 128px;
    max-width: 128px;
    flex-shrink: 0;
  }
}

.inventory-qty-input >>> .v-input__slot {
  min-height: 44px !important;
  border-radius: 12px !important;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.inventory-qty-input >>> .v-input__slot:hover {
  box-shadow: 0 4px 14px -8px rgba(15, 23, 42, 0.18);
}

.inventory-qty-input >>> fieldset {
  border-width: 1px !important;
}

.inventory-btn-update {
  border-radius: 12px !important;
  border-width: 1.5px !important;
  letter-spacing: 0.01em;
  transition: transform 0.15s ease, box-shadow 0.2s ease !important;
}

.inventory-btn-update:not(:disabled):hover {
  box-shadow: 0 6px 18px -10px rgba(234, 88, 12, 0.55) !important;
}

.inventory-btn-delete {
  border-radius: 12px !important;
  background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%) !important;
  box-shadow: 0 6px 18px -10px rgba(185, 28, 28, 0.45) !important;
  letter-spacing: 0.01em;
  transition: transform 0.15s ease, box-shadow 0.2s ease !important;
}

.inventory-btn-delete:not(:disabled):hover {
  box-shadow: 0 10px 24px -12px rgba(185, 28, 28, 0.55) !important;
}

@media (max-width: 599px) {
  .inventory-actions-bar .inventory-btn-update,
  .inventory-actions-bar .inventory-btn-delete {
    width: 100%;
  }
}

@media (min-width: 600px) {
  .inventory-btn-update {
    flex: 1 1 160px;
    min-width: 140px;
  }

  .inventory-btn-delete {
    flex: 0 1 auto;
    min-width: 120px;
  }
}

.order-customer-line {
  max-width: min(420px, 100%);
  word-break: break-word;
}

.order-lines-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin-left: -4px;
  margin-right: -4px;
  padding-left: 4px;
  padding-right: 4px;
}

.order-lines-scroll >>> table {
  min-width: 260px;
}

.list-title {
  font-size: 1rem !important;
  letter-spacing: -0.02em;
  line-height: 1.35 !important;
}

.list-meta {
  margin-top: 6px !important;
  flex-wrap: wrap;
}

.list-price {
  font-size: 0.9375rem !important;
  font-weight: 700 !important;
  background: linear-gradient(120deg, #c2410c, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent !important;
}

.list-meta-sep {
  user-select: none;
}

.delete-dialog-card {
  border: 1px solid rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.delete-dialog-icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #b91c1c 0%, #dc2626 100%);
  box-shadow: 0 14px 36px -12px rgba(185, 28, 28, 0.55);
}

.delete-dialog-title {
  font-size: 1.375rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1.2;
}

.delete-dialog-lead {
  color: rgba(15, 23, 42, 0.72);
  line-height: 1.6;
}

.delete-dialog-name {
  color: #0f172a;
  font-weight: 700;
}

.delete-dialog-alert >>> .v-alert__wrapper {
  align-items: flex-start;
}

.delete-dialog-actions {
  gap: 8px;
}

.delete-dialog-confirm {
  box-shadow: 0 8px 24px -10px rgba(185, 28, 28, 0.55) !important;
}

.order-panel--cancelled {
  opacity: 0.92;
}

.order-panel--cancelled >>> .v-expansion-panel-header {
  border-left: 3px solid rgba(185, 28, 28, 0.5);
}

.admin-pagination {
  flex-wrap: wrap;
  justify-content: center;
}
</style>

<style>
/* content-class on v-dialog is not scoped to this component */
.delete-dialog-surface {
  border-radius: 20px !important;
  overflow: hidden;
  box-shadow: 0 24px 64px -16px rgba(15, 23, 42, 0.35) !important;
}
</style>
