<template>
  <div>
    <section class="checkout-hero" :class="{ 'checkout-hero--success': successOrderId }">
      <v-container class="py-10 py-md-12">
        <div v-if="!successOrderId" class="d-flex flex-column flex-sm-row align-start align-sm-center">
          <div>
            <div class="checkout-kicker mb-2">Checkout</div>
            <h1 class="checkout-title">Complete your order</h1>
            <p class="checkout-lead mb-0">
              Tell us how you’d like to get your order and <strong>how you’ll pay</strong> — bank transfer (EFT) or
              <strong>cash</strong> when you’re here. We’ll show delivery costs before you confirm.
            </p>
          </div>
        </div>
        <div v-else class="success-hero d-flex flex-column flex-sm-row align-center">
          <div class="success-hero__icon-wrap mb-4 mb-sm-0 mr-sm-6">
            <v-icon color="white" size="36">check</v-icon>
          </div>
          <div>
            <div class="checkout-kicker checkout-kicker--success mb-2">Thank you</div>
            <h1 class="checkout-title checkout-title--success mb-0">Your order is in</h1>
            <p class="checkout-lead checkout-lead--success mb-0 mt-2">
              Save your reference — you’ll need it for payment, pickup, or delivery updates.
            </p>
          </div>
        </div>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16">
      <v-alert v-if="supabaseConfigHint" type="warning" prominent border="left" colored-border class="mb-8 rounded-lg">
        {{ supabaseConfigHint }}
      </v-alert>

      <v-row v-if="successOrderId" justify="center">
        <v-col cols="12" md="10" lg="8">
          <v-card class="success-card pa-8 pa-md-10 rounded-xl" elevation="4" outlined>
            <div class="success-ref-block mb-8">
              <div class="success-ref-label">Your order number</div>
              <div class="success-ref-row d-flex flex-column flex-sm-row align-stretch align-sm-center">
                <code class="success-ref-code flex-grow-1">{{ successOrderId }}</code>
                <v-btn
                  outlined
                  color="primary"
                  class="success-copy-btn mt-3 mt-sm-0 ml-sm-4 text-none font-weight-bold"
                  height="48"
                  @click="copyOrderRef"
                >
                  <v-icon left small color="primary">content_copy</v-icon>
                  Copy number
                </v-btn>
              </div>
              <p class="success-ref-hint text-caption text--secondary mb-0 mt-3">
                <template v-if="successPaymentMethod === 'eft'">
                  Use this number as your <strong>payment reference</strong> in your banking app so we can match your
                  transfer.
                </template>
                <template v-else>
                  Show this number at the counter when you collect — it’s your proof of order.
                </template>
              </p>
            </div>

            <template v-if="successPaymentMethod === 'eft'">
              <div class="success-panel success-panel--eft pa-6 rounded-xl mb-8">
                <div class="d-flex align-start">
                  <v-avatar color="primary" size="40" class="success-panel__avatar mr-4">
                    <v-icon color="white" small>account_balance</v-icon>
                  </v-avatar>
                  <div class="flex-grow-1">
                    <div class="success-panel__title font-weight-bold mb-2">Pay by EFT</div>
                    <p class="success-panel__body text-body-2 mb-4">
                      {{ eftInstructions }}
                    </p>
                    <ul class="success-steps pl-0 mb-0">
                      <li class="success-steps__item text-body-2">
                        <span class="success-steps__dot" aria-hidden="true" />
                        Pay the <strong>full amount</strong> shown in your order summary (in rands).
                      </li>
                      <li class="success-steps__item text-body-2">
                        <span class="success-steps__dot" aria-hidden="true" />
                        In your banking app, use reference <strong class="font-mono">{{ successOrderId.slice(0, 8) }}…</strong>
                        (or the full number above) so we recognise your payment.
                      </li>
                      <li class="success-steps__item text-body-2 mb-0">
                        <span class="success-steps__dot" aria-hidden="true" />
                        We start preparing your order once we’ve <strong>seen your payment</strong> on our side.
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </template>

            <template v-else>
              <div class="success-panel success-panel--cash pa-6 rounded-xl mb-8">
                <div class="d-flex align-start">
                  <v-avatar color="grey darken-3" size="40" class="success-panel__avatar mr-4">
                    <v-icon color="white" small>payments</v-icon>
                  </v-avatar>
                  <div>
                    <div class="success-panel__title font-weight-bold mb-2">Pay in store</div>
                    <p class="success-panel__body text-body-2 mb-0">
                      When your order is ready, come to the store with this reference and pay <strong>cash</strong> at
                      the till. If you’re not sure, show this screen on your phone — we’re happy to help.
                    </p>
                  </div>
                </div>
              </div>
            </template>

            <div class="d-flex flex-column flex-sm-row align-stretch align-sm-center">
              <v-btn
                depressed
                x-large
                color="primary"
                class="text-none font-weight-bold btn-amber flex-grow-1"
                to="/"
              >
                <v-icon left color="white">storefront</v-icon>
                Back to shop
              </v-btn>
            </div>
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
              hint="We’ll only use this if we need to reach you about your order."
              persistent-hint
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

            <div class="field-label mt-8 mb-3">How would you like to pay?</div>
            <p class="text-caption text--secondary mb-4 payment-intro">
              Tap one option. You can pay from your bank with EFT, or with cash when you pick up (or when the courier
              meets you, if you chose delivery).
            </p>
            <div class="payment-cards">
              <button
                type="button"
                class="payment-card"
                :class="{ 'payment-card--active': paymentMethod === 'eft' }"
                :aria-pressed="paymentMethod === 'eft' ? 'true' : 'false'"
                @click="paymentMethod = 'eft'"
              >
                <v-icon class="payment-card__icon" color="primary">account_balance</v-icon>
                <div class="payment-card__title">Bank transfer (EFT)</div>
                <p class="payment-card__text mb-0">
                  Pay from your online banking. We’ll start on your order once payment shows on our side.
                </p>
              </button>
              <button
                type="button"
                class="payment-card"
                :class="{ 'payment-card--active': paymentMethod === 'cash_store' }"
                :aria-pressed="paymentMethod === 'cash_store' ? 'true' : 'false'"
                @click="paymentMethod = 'cash_store'"
              >
                <v-icon class="payment-card__icon" color="grey darken-2">payments</v-icon>
                <div class="payment-card__title">Cash</div>
                <p class="payment-card__text mb-0">
                  Pay with cash when you collect, or when you receive a delivery — no bank transfer needed.
                </p>
              </button>
            </div>
            <p v-if="paymentHint" class="text-caption error--text mt-2 mb-0">{{ paymentHint }}</p>

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
              <span>Total</span>
              <span>{{ formatZar(estimatedTotal) }}</span>
            </div>

            <v-divider class="my-4" />
            <div class="field-label mb-2">Payment choice</div>
            <div v-if="paymentMethod === 'eft'" class="text-body-2">
              <v-icon small color="primary" class="mr-1">account_balance</v-icon>
              Bank transfer (EFT)
            </div>
            <div v-else-if="paymentMethod === 'cash_store'" class="text-body-2">
              <v-icon small class="mr-1">payments</v-icon>
              Cash when you get your order
            </div>
            <div v-else class="text-caption text--secondary">Choose a payment option on the left.</div>

            <p class="text-caption text--secondary mt-4 mb-0">
              The amount above is what we charge — it’s double-checked against our latest prices when you tap
              <strong>Place order</strong>.
            </p>
          </v-card>
        </v-col>
      </v-row>
    </v-container>

    <v-snackbar v-model="copySnackbar" bottom color="grey darken-3" :timeout="2400">
      <span class="text-body-2">Copied — you can paste it into your banking app.</span>
      <template #action="{ attrs }">
        <v-btn text v-bind="attrs" class="white--text text-none" @click="copySnackbar = false">OK</v-btn>
      </template>
    </v-snackbar>
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
      paymentMethod: '',
      paymentHint: '',
      submitting: false,
      submitError: '',
      successOrderId: '',
      successPaymentMethod: '',
      copySnackbar: false,
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
    },
    paymentMethod() {
      this.paymentHint = ''
    }
  },
  async created() {
    const s = await fetchShopSettings()
    this.deliveryFee = s.deliveryFeeZar
    this.eftInstructions = s.eftBankInstructions
  },
  methods: {
    formatZar,
    async copyOrderRef() {
      const id = this.successOrderId
      if (!id || !navigator.clipboard) return
      try {
        await navigator.clipboard.writeText(id)
        this.copySnackbar = true
      } catch {
        // ignore
      }
    },
    lineTotal(line) {
      const n = typeof line.product.price === 'string' ? Number(line.product.price) : line.product.price
      const unit = Number.isFinite(n) ? n : 0
      return unit * line.quantity
    },
    async submit() {
      this.submitError = ''
      this.paymentHint = ''
      if (!this.cart.lines.length) return
      if (this.paymentMethod !== 'eft' && this.paymentMethod !== 'cash_store') {
        this.paymentHint = 'Please choose whether you’ll pay by bank transfer (EFT) or with cash.'
        return
      }
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
  transition: background 0.35s ease;
}

.checkout-hero--success {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 45%, #f0fdf4 100%);
  border-bottom-color: rgba(21, 128, 61, 0.12);
}

.success-hero__icon-wrap {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(145deg, #15803d, #16a34a);
  box-shadow: 0 14px 36px -12px rgba(22, 163, 74, 0.55);
}

.checkout-kicker--success {
  color: rgba(22, 101, 52, 0.75);
}

.checkout-title--success {
  color: #14532d;
}

.checkout-lead--success {
  color: rgba(21, 128, 61, 0.85);
  max-width: 42ch;
}

.success-card {
  border-color: rgba(15, 23, 42, 0.08) !important;
  box-shadow: 0 24px 48px -24px rgba(15, 23, 42, 0.2) !important;
}

.success-ref-label {
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 0.75rem;
}

.success-ref-code {
  display: block;
  font-family: 'JetBrains Mono', 'Consolas', ui-monospace, monospace;
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #0f172a;
  background: linear-gradient(180deg, #f1f5f9, #e2e8f0);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  padding: 14px 18px;
  word-break: break-all;
  line-height: 1.45;
}

.success-copy-btn {
  border-radius: 14px !important;
  border-width: 2px !important;
}

.success-ref-hint {
  line-height: 1.5;
}

.success-panel {
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.success-panel--eft {
  background: linear-gradient(180deg, rgba(239, 246, 255, 0.9), rgba(248, 250, 252, 0.95));
  border-color: rgba(59, 130, 246, 0.15);
}

.success-panel--cash {
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.95), rgba(241, 245, 249, 0.9));
}

.success-panel__title {
  font-size: 1.0625rem;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.success-panel__body {
  color: rgba(15, 23, 42, 0.72);
  line-height: 1.65;
}

.success-panel__avatar {
  flex-shrink: 0;
}

.success-steps {
  list-style: none;
}

.success-steps__item {
  position: relative;
  padding-left: 1.35rem;
  margin-bottom: 0.85rem;
  color: rgba(15, 23, 42, 0.75);
  line-height: 1.55;
}

.success-steps__dot {
  position: absolute;
  left: 0;
  top: 0.55rem;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.2);
}

.font-mono {
  font-family: 'JetBrains Mono', 'Consolas', ui-monospace, monospace;
  font-size: 0.85em;
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

.payment-intro {
  line-height: 1.5;
  max-width: 52ch;
}

.payment-cards {
  display: grid;
  gap: 12px;
}

@media (min-width: 600px) {
  .payment-cards {
    grid-template-columns: 1fr 1fr;
  }
}

.payment-card {
  text-align: left;
  border-radius: 16px;
  padding: 16px 18px;
  border: 2px solid rgba(15, 23, 42, 0.1);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.payment-card:hover {
  border-color: rgba(234, 88, 12, 0.35);
  box-shadow: 0 8px 28px -16px rgba(15, 23, 42, 0.2);
}

.payment-card:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.35);
}

.payment-card--active {
  border-color: #ea580c;
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.85), #fff);
  box-shadow: 0 10px 32px -18px rgba(194, 65, 12, 0.45);
}

.payment-card__icon {
  margin-bottom: 10px;
}

.payment-card__title {
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
  margin-bottom: 6px;
}

.payment-card__text {
  font-size: 0.8125rem;
  line-height: 1.45;
  color: rgba(15, 23, 42, 0.65);
}
</style>
