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

    <v-container class="pb-12 pb-md-16 admin-body">
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
          <v-card class="admin-card admin-login-card pa-8" elevation="3" rounded="xl">
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
          <v-card class="admin-card pa-6 mb-6" elevation="3" rounded="xl">
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
          </v-card>

          <v-card class="admin-card pa-6" elevation="3" rounded="xl">
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
          <v-card class="admin-card pa-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Inventory</div>
              <v-spacer />
              <v-progress-circular v-if="loading" indeterminate size="22" width="2" color="accent" />
            </div>
            <p class="text-caption text--secondary mb-4">
              Set <strong>stock</strong> for each product and click <strong>Update stock</strong>. Sales reduce stock
              automatically when orders are placed. Use <strong>Delete</strong> to remove a product and its image.
            </p>

            <v-alert v-if="stockError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ stockError }}
            </v-alert>

            <v-alert v-if="deleteError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ deleteError }}
            </v-alert>

            <div v-if="!loading && products.length === 0" class="muted-panel rounded-lg pa-8 text-center">
              <v-icon size="40" color="secondary" class="mb-3">inventory</v-icon>
              <div class="text-subtitle-1 font-weight-bold mb-1">No products yet</div>
              <div class="text-body-2 text--secondary">Add one using the form on the left.</div>
            </div>

            <v-list v-else three-line class="admin-list py-0">
              <v-list-item
                v-for="p in products"
                :key="p.id"
                class="admin-list-item px-4 py-3 mb-3"
              >
                <v-list-item-avatar tile size="64" class="rounded-lg mr-4 my-0">
                  <v-img :src="p.imageUrl">
                    <template #placeholder>
                      <v-row class="fill-height ma-0" align="center" justify="center">
                        <v-progress-circular indeterminate size="20" width="2" />
                      </v-row>
                    </template>
                  </v-img>
                </v-list-item-avatar>

                <v-list-item-content>
                  <v-list-item-title class="list-title font-weight-bold">
                    {{ p.name }}
                  </v-list-item-title>
                  <v-list-item-subtitle class="list-meta d-flex flex-wrap align-center">
                    <span class="list-price">{{ formatZar(p.price) }}</span>
                    <span class="list-meta-sep mx-2 text--disabled">·</span>
                    <span class="text-body-2">
                      Stock
                      <strong class="text--primary">{{ p.stock != null ? p.stock : 0 }}</strong>
                    </span>
                  </v-list-item-subtitle>
                </v-list-item-content>

                <v-list-item-action class="ma-0 ml-2 align-self-center">
                  <div class="inventory-actions d-flex flex-column flex-sm-row align-stretch align-sm-center">
                    <v-text-field
                      v-model.number="stockDraft[p.id]"
                      dense
                      outlined
                      hide-details
                      type="number"
                      min="0"
                      label="Qty"
                      style="width: 96px"
                      class="inventory-stock-field rounded-lg mr-sm-2 mb-2 mb-sm-0"
                      :disabled="stockUpdatingId === p.id"
                    />
                    <v-btn
                      small
                      depressed
                      outlined
                      color="primary"
                      class="text-none font-weight-bold mb-2 mb-sm-0 mr-sm-2"
                      :loading="stockUpdatingId === p.id"
                      @click="saveStock(p)"
                    >
                      Update stock
                    </v-btn>
                    <v-btn
                      small
                      depressed
                      color="error"
                      class="text-none white--text"
                      :disabled="deletingId === p.id || stockUpdatingId === p.id"
                      :loading="deletingId === p.id"
                      @click="openDeleteConfirm(p)"
                    >
                      <v-icon left small color="white">delete</v-icon>
                      Delete
                    </v-btn>
                  </div>
                </v-list-item-action>
              </v-list-item>
            </v-list>
          </v-card>
        </v-col>
      </v-row>

      <v-row v-if="user" class="mt-2">
        <v-col cols="12">
          <v-card class="admin-card pa-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Orders</div>
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

            <v-expansion-panels v-else-if="orders.length" multiple class="orders-panels">
              <v-expansion-panel v-for="o in orders" :key="o.id" class="rounded-lg mb-3">
                <v-expansion-panel-header class="order-panel-header">
                  <div class="d-flex flex-column flex-sm-row align-start align-sm-center flex-grow-1 pr-2">
                    <div>
                      <div class="font-weight-bold text-subtitle-1">
                        {{ formatZar(o.total_zar) }}
                        <span class="text-caption text--secondary font-weight-regular ml-2">
                          {{ formatWhen(o.created_at) }}
                        </span>
                      </div>
                      <div class="text-body-2 text--secondary text-truncate" style="max-width: 420px">
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
                        small
                        class="mb-1 text-none white--text"
                        :color="o.payment_confirmed ? 'success' : 'warning'"
                      >
                        <template v-if="o.payment_method === 'eft'">
                          {{ o.payment_confirmed ? 'EFT confirmed' : 'Awaiting EFT' }}
                        </template>
                        <template v-else>
                          {{ o.payment_confirmed ? 'Cash received' : 'Awaiting cash payment' }}
                        </template>
                      </v-chip>
                    </div>
                  </div>
                </v-expansion-panel-header>
                <v-expansion-panel-content class="order-panel-body">
                  <div class="text-caption text--secondary mb-1">Order ID</div>
                  <div class="text-body-2 font-mono mb-4">{{ o.id }}</div>

                  <div v-if="o.delivery_type === 'delivery' && o.delivery_address" class="mb-4">
                    <div class="text-caption text--secondary mb-1">Deliver to</div>
                    <div class="text-body-2">{{ o.delivery_address }}</div>
                  </div>

                  <div class="text-caption text--secondary mb-2">Lines</div>
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
                      v-if="!o.payment_confirmed"
                      small
                      depressed
                      color="success"
                      class="text-none font-weight-bold mb-2"
                      :loading="confirmingId === o.id"
                      @click.stop="confirmPayment(o)"
                    >
                      <v-icon left small color="white">verified</v-icon>
                      {{ o.payment_method === 'eft' ? 'Confirm EFT received' : 'Confirm cash received' }}
                    </v-btn>
                  </div>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>
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
  </div>
</template>

<script>
import { createProduct, deleteProduct, subscribeToProducts, updateProductStock } from '@/services/products'
import { confirmOrderPayment, subscribeToOrders } from '@/services/orders'
import { loginWithEmailPassword, logout, subscribeToAuth } from '@/services/auth'
import { supabaseSetupMessage } from '@/supabase'
import { formatZar } from '@/utils/price'

export default {
  name: 'AdminView',
  data() {
    return {
      user: null,
      email: '',
      password: '',
      authLoading: false,
      authError: '',
      name: '',
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
      unsubOrders: null,
      deleteDialogOpen: false,
      deleteTarget: null,
      stockDraft: {},
      stockError: '',
      stockUpdatingId: null
    }
  },
  watch: {
    products: {
      deep: true,
      handler(list) {
        (list || []).forEach((p) => {
          const s = p.stock != null ? p.stock : 0
          this.$set(this.stockDraft, p.id, s)
        })
      }
    },
    deleteDialogOpen(open) {
      if (!open && !this.deletingId) {
        this.deleteTarget = null
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
        } else {
          this.orders = []
          this.ordersLoading = false
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
  },
  beforeDestroy() {
    if (this.unsubAuth) this.unsubAuth()
    if (this.unsub) this.unsub()
    if (this.unsubOrders) this.unsubOrders()
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
    lineProductName(it) {
      if (it.products && it.products.name) return it.products.name
      return 'Product'
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
          price: this.price,
          stock: this.initialStock,
          file: this.file
        })
        this.name = ''
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
    async saveStock(p) {
      if (!p || !p.id) return
      this.stockError = ''
      this.stockUpdatingId = p.id
      try {
        const raw = this.stockDraft[p.id]
        const n = typeof raw === 'string' ? parseInt(raw, 10) : raw
        const qty = Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 0
        await updateProductStock({ id: p.id, stock: qty })
        this.$set(this.stockDraft, p.id, qty)
      } catch (e) {
        this.stockError = e && e.message ? e.message : 'Could not update stock.'
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

.admin-card {
  border: 1px solid rgba(15, 23, 42, 0.06);
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

.admin-list {
  background: transparent !important;
}

.admin-list-item {
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 16px !important;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 24px -18px rgba(15, 23, 42, 0.35);
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

.inventory-stock-field >>> .v-input__slot {
  min-height: 40px !important;
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
</style>

<style>
/* content-class on v-dialog is not scoped to this component */
.delete-dialog-surface {
  border-radius: 20px !important;
  overflow: hidden;
  box-shadow: 0 24px 64px -16px rgba(15, 23, 42, 0.35) !important;
}
</style>
