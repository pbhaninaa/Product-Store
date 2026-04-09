<template>
  <div>
    <section class="checkout-hero">
      <v-container class="py-10 py-md-12">
        <div class="d-flex flex-column flex-sm-row align-start align-sm-center">
          <div>
            <div class="checkout-kicker mb-2">Checkout</div>
            <h1 class="checkout-title">Complete your order</h1>
            <p class="checkout-lead mb-0">
              Pay by <strong>EFT</strong> (we confirm when your payment reflects) or <strong>cash in store</strong> when
              you collect. Delivery fees are set by the shop and verified on the server.
            </p>
          </div>
        </div>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16">
      <v-alert v-if="supabaseConfigHint" type="warning" prominent border="left" colored-border class="mb-8 rounded-lg">
        {{ supabaseConfigHint }}
      </v-alert>

      <v-row v-if="successOrderId">
        <v-col cols="12" md="8" lg="6">
          <v-card class="pa-6 rounded-xl" elevation="3">
            <div class="text-h6 font-weight-bold mb-2">Order placed</div>
            <p class="text-body-2 text--secondary mb-4">
              Your order reference is <strong class="text--primary">{{ successOrderId }}</strong>. Save this for your
              records.
            </p>

            <template v-if="successPaymentMethod === 'eft'">
              <v-alert type="info" outlined class="rounded-lg text-body-2">
                {{ eftInstructions }}
              </v-alert>
              <p class="text-caption text--secondary mt-4 mb-0">
                We will process your order after an admin confirms your EFT payment.
              </p>
            </template>
            <template v-else>
              <p class="text-body-2 mb-0">
                Please bring your order reference and pay <strong>cash in store</strong> when you collect.
              </p>
            </template>

            <v-btn color="primary" depressed class="mt-6 text-none font-weight-bold" to="/">
              Back to shop
            </v-btn>
          </v-card>
        </v-col>
      </v-row>

      <v-row v-else>
        <v-col cols="12" lg="7">
          <v-card class="pa-6 mb-6 rounded-xl" elevation="2" v-if="cart.lines.length">
            <div class="field-label mb-4">Your details</div>
            <v-text-field v-model="customerName" outlined label="Full name" hide-details="auto" class="rounded-lg" />
            <v-text-field
              v-model="customerEmail"
              outlined
              label="Email"
              type="email"
              hide-details="auto"
              class="mt-4 rounded-lg"
            />
            <v-text-field
              v-model="customerPhone"
              outlined
              label="Phone (optional)"
              hide-details="auto"
              class="mt-4 rounded-lg"
            />

            <div class="field-label mt-8 mb-2">Delivery</div>
            <v-radio-group v-model="deliveryType" hide-details class="mt-0">
              <v-radio value="pickup" label="Pickup in store — no delivery fee" />
              <v-radio value="delivery" :label="`Delivery — + ${formatZar(deliveryFee)}`" />
            </v-radio-group>
            <v-textarea
              v-if="deliveryType === 'delivery'"
              v-model="deliveryAddress"
              outlined
              label="Delivery address"
              rows="3"
              hide-details="auto"
              class="mt-2 rounded-lg"
              placeholder="Street, suburb, city, postal code"
            />

            <div class="field-label mt-8 mb-2">Payment</div>
            <v-radio-group v-model="paymentMethod" hide-details class="mt-0">
              <v-radio value="eft" label="EFT (bank transfer)" />
              <v-radio value="cash_store" label="Cash in store on pickup" />
            </v-radio-group>

            <v-alert v-if="submitError" type="error" dense outlined class="mt-6 rounded-lg">
              {{ submitError }}
            </v-alert>

            <v-btn
              block
              x-large
              depressed
              color="primary"
              class="mt-6 text-none font-weight-bold btn-amber"
              :loading="submitting"
              @click="submit"
            >
              Place order
            </v-btn>
          </v-card>

          <v-card v-else class="pa-10 text-center rounded-xl" elevation="1">
            <v-icon size="48" color="secondary" class="mb-4">shopping_cart</v-icon>
            <div class="text-h6 font-weight-bold mb-2">Your cart is empty</div>
            <p class="text-body-2 text--secondary mb-6">Add products from the shop, then return here to check out.</p>
            <v-btn depressed color="primary" class="text-none font-weight-bold btn-amber" to="/">
              Browse products
            </v-btn>
          </v-card>
        </v-col>

        <v-col cols="12" lg="5" v-if="cart.lines.length">
          <v-card class="pa-6 rounded-xl sticky-summary" elevation="2">
            <div class="field-label mb-4">Order summary</div>
            <div v-for="line in cart.lines" :key="line.product.id" class="summary-line d-flex align-start mb-4">
              <div class="flex-grow-1 pr-2">
                <div class="font-weight-medium">{{ line.product.name }}</div>
                <div class="text-caption text--secondary">
                  {{ formatZar(line.product.price) }} × {{ line.quantity }}
                </div>
              </div>
              <div class="font-weight-bold">{{ formatZar(lineTotal(line)) }}</div>
            </div>
            <v-divider class="my-2" />
            <div class="d-flex justify-space-between text-body-2 mb-1">
              <span>Subtotal</span>
              <span>{{ formatZar(subtotal) }}</span>
            </div>
            <div class="d-flex justify-space-between text-body-2 mb-1">
              <span>Delivery</span>
              <span>{{ formatZar(deliveryCharge) }}</span>
            </div>
            <v-divider class="my-3" />
            <div class="d-flex justify-space-between text-h6 font-weight-bold">
              <span>Total (estimate)</span>
              <span>{{ formatZar(estimatedTotal) }}</span>
            </div>
            <p class="text-caption text--secondary mt-4 mb-0">
              The total is recalculated on our server from live product prices — you cannot change amounts from the
              browser.
            </p>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
import { getCartState, clearCart, cartSubtotalNumber } from '@/services/cart'
import { fetchShopSettings, placeOrder } from '@/services/orders'
import { supabaseSetupMessage } from '@/supabase'
import { formatZar } from '@/utils/price'

export default {
  name: 'CheckoutView',
  data() {
    return {
      cart: getCartState(),
      deliveryFee: 50,
      eftInstructions: '',
      customerName: '',
      customerEmail: '',
      customerPhone: '',
      deliveryType: 'pickup',
      deliveryAddress: '',
      paymentMethod: 'eft',
      submitting: false,
      submitError: '',
      successOrderId: '',
      successPaymentMethod: '',
      supabaseConfigHint: supabaseSetupMessage
    }
  },
  computed: {
    subtotal() {
      return cartSubtotalNumber()
    },
    deliveryCharge() {
      return this.deliveryType === 'delivery' ? this.deliveryFee : 0
    },
    estimatedTotal() {
      return this.subtotal + this.deliveryCharge
    }
  },
  watch: {
    deliveryType(v) {
      if (v === 'pickup') this.deliveryAddress = ''
    }
  },
  async created() {
    const s = await fetchShopSettings()
    this.deliveryFee = s.deliveryFeeZar
    this.eftInstructions = s.eftBankInstructions
  },
  methods: {
    formatZar,
    lineTotal(line) {
      const n = typeof line.product.price === 'string' ? Number(line.product.price) : line.product.price
      const unit = Number.isFinite(n) ? n : 0
      return unit * line.quantity
    },
    async submit() {
      this.submitError = ''
      if (!this.cart.lines.length) return
      const name = String(this.customerName || '').trim()
      const email = String(this.customerEmail || '').trim()
      if (name.length < 2) {
        this.submitError = 'Please enter your name.'
        return
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.submitError = 'Please enter a valid email.'
        return
      }
      if (this.deliveryType === 'delivery' && String(this.deliveryAddress || '').trim().length < 6) {
        this.submitError = 'Please enter your full delivery address.'
        return
      }

      this.submitting = true
      try {
        const items = this.cart.lines.map((l) => ({
          product_id: l.product.id,
          quantity: l.quantity
        }))
        const orderId = await placeOrder({
          customerName: name,
          customerEmail: email,
          customerPhone: String(this.customerPhone || '').trim(),
          deliveryType: this.deliveryType,
          deliveryAddress: this.deliveryType === 'delivery' ? this.deliveryAddress : '',
          paymentMethod: this.paymentMethod,
          items
        })
        this.successOrderId = orderId
        this.successPaymentMethod = this.paymentMethod
        clearCart()
      } catch (e) {
        this.submitError = e && e.message ? e.message : 'Order failed.'
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.checkout-hero {
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.checkout-kicker {
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
}

.checkout-title {
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.checkout-lead {
  margin-top: 0.5rem;
  max-width: 54ch;
  color: rgba(15, 23, 42, 0.65);
  line-height: 1.6;
}

.field-label {
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
}

.summary-line {
  font-size: 0.9375rem;
}

@media (min-width: 1264px) {
  .sticky-summary {
    position: sticky;
    top: 96px;
  }
}
</style>
