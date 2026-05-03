<template>
  <div>
    <section class="checkout-hero" :class="{ 'checkout-hero--success': successOrderId }">
      <v-container class="py-10 py-md-12 px-3 px-sm-4">
        <div v-if="!successOrderId" class="d-flex flex-column flex-sm-row align-start align-sm-center">
          <div>
            <div class="checkout-kicker mb-2">Checkout</div>
            <h1 class="checkout-title">Complete your order</h1>
            <p class="checkout-lead mb-0">
              This checkout is for <strong>products in your cart</strong> — not salon appointments (those use the salon
              booking flow). Tell us how you’d like to get your order and <strong>how you’ll pay</strong> — bank transfer
              (EFT) or <strong>cash</strong> when you’re here. We’ll show delivery costs before you confirm.
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

    <v-container class="pb-12 pb-md-16 px-3 px-sm-4">
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
                  <template v-if="successNeedsCashPaymentCode">
                    Order number plus your payment code (below) — staff enters the code to mark your order paid.
                  </template>
                  <template v-else>Show this number at the counter when you collect — it’s your proof of order.</template>
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
                    <dl v-if="hasBankingDetails" class="eft-bank-dl success-eft-dl mb-4">
                      <div class="eft-bank-row">
                        <dt>Bank</dt>
                        <dd>{{ bankName }}</dd>
                      </div>
                      <div class="eft-bank-row">
                        <dt>Account name</dt>
                        <dd>{{ bankAccountHolder }}</dd>
                      </div>
                      <div class="eft-bank-row">
                        <dt>Account no.</dt>
                        <dd class="font-mono">{{ bankAccountNumber }}</dd>
                      </div>
                      <div v-if="bankBranchCode" class="eft-bank-row">
                        <dt>Branch</dt>
                        <dd>{{ bankBranchCode }}</dd>
                      </div>
                    </dl>
                    <p v-else class="success-panel__body text-body-2 mb-4">
                      Banking details are not on file — contact the store for payment instructions.
                    </p>
                    <p v-if="eftInstructions" class="success-panel__body text-body-2 mb-4">
                      {{ eftInstructions }}
                    </p>
                    <ul class="success-steps pl-0 mb-0">
                      <li class="success-steps__item text-body-2">
                        <span class="success-steps__dot" aria-hidden="true" />
                        Pay the <strong>full amount</strong> shown in your order summary (in rands).
                      </li>
                      <li class="success-steps__item text-body-2">
                        <span class="success-steps__dot" aria-hidden="true" />
                        In your banking app, use reference <strong class="font-mono">{{ successOrderId }}</strong> (same as
                        the order number above) so we recognise your payment.
                      </li>
                      <li class="success-steps__item text-body-2">
                        <span class="success-steps__dot" aria-hidden="true" />
                        Upload a screenshot of your proof below. If your <strong>reference matches</strong> this order
                        number, we confirm automatically; otherwise the store will verify your transfer manually before
                        finalising.
                      </li>
                      <li class="success-steps__item text-body-2 mb-0">
                        <span class="success-steps__dot" aria-hidden="true" />
                        We reduce stock when payment is <strong>confirmed</strong> (auto match or after staff review).
                      </li>
                    </ul>
                    <template v-if="successNeedsEftProof">
                      <v-divider class="my-5" />
                      <div class="text-subtitle-2 font-weight-bold mb-3">Upload proof of payment</div>
                      <v-text-field
                        v-model="orderEftBankReference"
                        outlined
                        dense
                        hide-details="auto"
                        label="Reference you used on the transfer"
                        prepend-inner-icon="tag"
                        class="rounded-lg mb-3"
                      />
                      <div class="text-caption text--secondary mb-2">
                        PDF recommended (amount + date auto-checked) or image (reference auto-checked)
                      </div>
                      <v-file-input
                        v-model="orderEftProofFile"
                        outlined
                        dense
                        hide-details="auto"
                        prepend-icon="attach_file"
                        accept="application/pdf,image/jpeg,image/png,image/gif,image/webp,.pdf"
                        label="Choose PDF or image"
                        class="rounded-lg mb-3"
                        truncate-length="28"
                      />
                      <v-btn
                        color="primary"
                        depressed
                        block
                        large
                        class="text-none font-weight-bold btn-amber"
                        :loading="orderEftProofSubmitting"
                        :disabled="!canSubmitOrderEftProof"
                        @click="submitOrderEftProof"
                      >
                        Upload proof
                      </v-btn>
                      <v-alert v-if="orderEftProofError" type="error" dense outlined class="mt-4 rounded-lg mb-0">{{
                        orderEftProofError
                      }}</v-alert>
                      <v-alert v-if="orderEftProofSuccessMsg" type="success" dense outlined class="mt-4 rounded-lg mb-0">{{
                        orderEftProofSuccessMsg
                      }}</v-alert>
                    </template>
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
                    <div class="success-panel__title font-weight-bold mb-2">Pay with cash</div>
                    <p class="success-panel__body text-body-2 mb-3">
                      Your order is <strong>pending</strong> until the shop confirms payment. Pay <strong>cash</strong>
                      when you collect or when your delivery arrives, and give the staff this code so they can mark it
                      paid:
                    </p>
                    <div v-if="successNeedsCashPaymentCode && successCashPaymentCode" class="mb-0">
                      <div class="text-caption text--secondary mb-1">Your payment code</div>
                      <code class="success-ref-code d-inline-block pa-3 rounded-lg">{{ successCashPaymentCode }}</code>
                    </div>
                    <p v-else class="success-panel__body text-body-2 mb-0">
                      The shop will confirm payment in their admin after you pay — that is when stock is reduced and the
                      sale is final.
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

      <v-row v-else :justify="cart.lines.length ? 'start' : 'center'">
        <template v-if="cart.lines.length">
          <v-col cols="12" lg="7">
            <v-card class="pa-6 mb-6 rounded-xl" elevation="2">
            <div class="field-label mb-4">Your details</div>
            <p class="text-caption text--secondary mb-3">
              Full name, email and phone are required so we can identify and contact you about your order.
            </p>
            <v-text-field
              v-model="customerName"
              outlined
              label="Full name"
              hide-details="auto"
              :rules="customerNameRules"
              required
              class="rounded-lg"
            />
            <v-text-field
              v-model="customerEmail"
              outlined
              label="Email"
              type="email"
              hide-details="auto"
              :rules="customerEmailRules"
              required
              hint="We’ll use this for order updates and identification."
              persistent-hint
              class="mt-4 rounded-lg"
            />
            <v-text-field
              v-model="customerPhone"
              outlined
              label="Phone"
              hide-details="auto"
              :rules="customerPhoneRules"
              required
              hint="Include country/area code if needed — at least 8 digits."
              persistent-hint
              class="mt-4 rounded-lg"
            />

            <div class="checkout-flow-divider" aria-hidden="true" />

            <section class="checkout-panel checkout-panel--delivery" aria-labelledby="delivery-heading">
              <header class="checkout-panel__header">
                <div class="checkout-panel__header-icon-wrap" aria-hidden="true">
                  <v-icon color="primary" size="22">local_shipping</v-icon>
                </div>
                <div class="checkout-panel__header-text">
                  <h2 id="delivery-heading" class="checkout-panel__title">Delivery</h2>
                  <p class="checkout-panel__lead mb-0">
                    Choose pickup (no fee) or delivery to your door. We’ll confirm the total before you pay.
                    <template v-if="deliveryFeeMode === 'per_km'">
                      Delivery is <strong>{{ formatZar(deliveryFeePerKm) }} per km</strong> (straight-line distance). Set your
                      address, then drop a pin on the map (or use your location).
                    </template>
                  </p>
                  <v-alert
                    v-if="deliveryFeeMode === 'per_km' && !hasStoreLocation"
                    type="warning"
                    dense
                    outlined
                    class="mt-3 mb-0 rounded-lg"
                  >
                    Distance-based delivery isn’t available yet — the shop hasn’t set its location. Choose
                    <strong>pickup</strong> or contact the store.
                  </v-alert>
                </div>
              </header>

              <div class="delivery-options" role="radiogroup" aria-label="Receive order by">
                <button
                  type="button"
                  class="delivery-option"
                  :class="{ 'delivery-option--active': deliveryType === 'pickup' }"
                  role="radio"
                  :aria-checked="deliveryType === 'pickup' ? 'true' : 'false'"
                  @click="setDeliveryType('pickup')"
                >
                  <span class="delivery-option__radio" aria-hidden="true">
                    <span class="delivery-option__radio-dot" />
                  </span>
                  <span class="delivery-option__main">
                    <span class="delivery-option__title">Pickup in store</span>
                    <span class="delivery-option__desc">Collect when your order is ready — no delivery charge.</span>
                  </span>
                  <span class="delivery-option__badge delivery-option__badge--free">Free</span>
                  <v-icon class="delivery-option__glyph" color="primary">storefront</v-icon>
                </button>

                <button
                  type="button"
                  class="delivery-option"
                  :class="{
                    'delivery-option--active': deliveryType === 'delivery',
                    'delivery-option--disabled': deliveryFeeMode === 'per_km' && !hasStoreLocation
                  }"
                  role="radio"
                  :aria-checked="deliveryType === 'delivery' ? 'true' : 'false'"
                  @click="setDeliveryType('delivery')"
                >
                  <span class="delivery-option__radio" aria-hidden="true">
                    <span class="delivery-option__radio-dot" />
                  </span>
                  <span class="delivery-option__main">
                    <span class="delivery-option__title">Delivery</span>
                    <span class="delivery-option__desc">We’ll courier to the address you enter below.</span>
                  </span>
                  <span class="delivery-option__badge">{{ deliveryPricingBadge }}</span>
                  <v-icon class="delivery-option__glyph" color="primary">location_on</v-icon>
                </button>
              </div>

              <transition name="checkout-expand">
                <div v-if="deliveryType === 'delivery'" class="checkout-address">
                  <label class="checkout-address__label" for="delivery-address-field">Delivery address</label>
                  <v-textarea
                    id="delivery-address-field"
                    v-model="deliveryAddress"
                    outlined
                    hide-details="auto"
                    rows="3"
                    class="rounded-lg checkout-address__field"
                    placeholder="Street number & name, suburb, city, postal code"
                    auto-grow
                  />
                  <p class="checkout-address__hint mb-0">
                    Use the address where you’ll be available to receive the parcel.
                  </p>
                  <p v-if="deliveryAddressGeocoding" class="text-caption primary--text mt-2 mb-0">
                    Looking up address…
                  </p>
                  <p v-if="deliveryAddressGeoError" class="text-caption error--text mt-2 mb-0">
                    {{ deliveryAddressGeoError }}
                  </p>
                  <div
                    v-if="deliveryType === 'delivery' && deliveryFeeMode === 'standard'"
                    class="mt-3"
                  >
                    <v-btn
                      text
                      small
                      color="primary"
                      class="text-none px-0"
                      :loading="deliveryAddressGeocoding"
                      :disabled="deliveryAddressGeocoding"
                      @click="fillDeliveryAddressFromGeolocation"
                    >
                      <v-icon left small>my_location</v-icon>
                      Use my location to fill address
                    </v-btn>
                  </div>
                  <div v-if="deliveryFeeMode === 'per_km' && hasStoreLocation" class="mt-4">
                    <div class="checkout-address__label">Where should we deliver?</div>
                    <p class="text-caption text--secondary mb-2">
                      Pin your location on the map (drag the marker or use <strong>Use my location</strong>). Your
                      <strong>delivery address</strong> above is filled from that point — edit the text if you need a
                      full street address. Distance is from the shop to this pin.
                    </p>
                    <map-location-picker
                      :value="{ lat: deliveryLat, lng: deliveryLng }"
                      :center-lat="mapCenterLat"
                      :center-lng="mapCenterLng"
                      :zoom="hasStoreLocation ? 11 : 5"
                      :height="280"
                      hint=""
                      @input="onDeliveryMapInput"
                    />
                    <p v-if="deliveryDistancePreviewKm != null" class="text-body-2 mt-2 mb-0">
                      <strong>Straight-line distance:</strong> ~{{ formatDistanceKm(deliveryDistancePreviewKm) }} km →
                      delivery
                      <strong>{{ formatZar(deliveryCharge) }}</strong>
                    </p>
                  </div>
                </div>
              </transition>
            </section>

            <div class="checkout-flow-divider checkout-flow-divider--spaced" aria-hidden="true" />

            <section class="checkout-panel checkout-panel--payment" aria-labelledby="payment-heading">
              <header class="checkout-panel__header">
                <div class="checkout-panel__header-icon-wrap checkout-panel__header-icon-wrap--payment" aria-hidden="true">
                  <v-icon color="primary" size="22">payments</v-icon>
                </div>
                <div class="checkout-panel__header-text">
                  <h2 id="payment-heading" class="checkout-panel__title">How would you like to pay?</h2>
                  <p class="checkout-panel__lead mb-0 payment-intro">
                    <strong class="payment-intro__strong">Choose one.</strong>
                    Pay by bank transfer (EFT), or with cash when you pick up — or when the courier hands over your
                    delivery.
                  </p>
                </div>
              </header>

              <v-alert
                v-if="!hasBankingDetails"
                type="info"
                dense
                outlined
                class="mb-4 rounded-lg"
              >
                <strong>EFT unavailable:</strong> this shop has not published bank details yet. Use
                <strong>cash</strong> or contact the store.
              </v-alert>

              <div class="payment-cards">
                <button
                  type="button"
                  class="payment-card"
                  :class="{
                    'payment-card--active': paymentMethod === 'eft',
                    'payment-card--disabled': !hasBankingDetails
                  }"
                  :aria-pressed="paymentMethod === 'eft' ? 'true' : 'false'"
                  :disabled="!hasBankingDetails"
                  @click="choosePayment('eft')"
                >
                  <span class="payment-card__selected-mark" aria-hidden="true">
                    <v-icon size="18" color="white">check</v-icon>
                  </span>
                  <div class="payment-card__icon-wrap payment-card__icon-wrap--eft">
                    <v-icon class="payment-card__icon" color="white" size="26">account_balance</v-icon>
                  </div>
                  <div class="payment-card__content">
                    <div class="payment-card__title">Bank transfer (EFT)</div>
                    <p class="payment-card__text mb-0">
                      Pay from your banking app. We prepare your order once we can see your payment on our account.
                    </p>
                  </div>
                </button>
                <button
                  type="button"
                  class="payment-card"
                  :class="{ 'payment-card--active': paymentMethod === 'cash_store' }"
                  :aria-pressed="paymentMethod === 'cash_store' ? 'true' : 'false'"
                  @click="choosePayment('cash_store')"
                >
                  <span class="payment-card__selected-mark" aria-hidden="true">
                    <v-icon size="18" color="white">check</v-icon>
                  </span>
                  <div class="payment-card__icon-wrap payment-card__icon-wrap--cash">
                    <v-icon class="payment-card__icon" color="white" size="26">payments</v-icon>
                  </div>
                  <div class="payment-card__content">
                    <div class="payment-card__title">Cash</div>
                    <p class="payment-card__text mb-0">
                      Pay cash at the counter when you collect, or to the courier when you receive your order — no
                      transfer needed.
                    </p>
                  </div>
                </button>
              </div>

              <v-card
                v-if="paymentMethod === 'eft' && hasBankingDetails"
                outlined
                class="mt-4 pa-4 rounded-lg eft-bank-card"
              >
                <div class="text-subtitle-2 font-weight-bold mb-3">Transfer to</div>
                <dl class="eft-bank-dl mb-0">
                  <div class="eft-bank-row">
                    <dt>Bank</dt>
                    <dd>{{ bankName }}</dd>
                  </div>
                  <div class="eft-bank-row">
                    <dt>Account name</dt>
                    <dd>{{ bankAccountHolder }}</dd>
                  </div>
                  <div class="eft-bank-row">
                    <dt>Account no.</dt>
                    <dd class="d-flex flex-wrap align-center">
                      <span class="font-mono mr-2">{{ bankAccountNumber }}</span>
                      <v-btn x-small outlined color="primary" class="text-none" @click="copyBankDetail(bankAccountNumber)">
                        Copy
                      </v-btn>
                    </dd>
                  </div>
                  <div v-if="bankBranchCode" class="eft-bank-row">
                    <dt>Branch</dt>
                    <dd>{{ bankBranchCode }}</dd>
                  </div>
                </dl>
                <p v-if="eftInstructions" class="text-body-2 mt-3 mb-0">{{ eftInstructions }}</p>
              </v-card>
            </section>
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
        </v-col>

        <v-col cols="12" lg="5">
          <v-card class="pa-6 rounded-xl sticky-summary" elevation="2">
            <div class="field-label mb-4">Order summary</div>
            <div v-for="line in cart.lines" :key="line.product.id" class="summary-line d-flex align-start mb-4">
              <div class="flex-grow-1 pr-2">
                <div class="font-weight-medium">{{ line.product.name }}</div>
                <div class="d-flex flex-wrap align-center mt-2 checkout-summary-qty-row">
                  <span class="text-caption text--secondary mr-3 mb-1 mb-sm-0">
                    {{ formatZar(line.product.price) }} each
                  </span>
                  <v-text-field
                    :value="line.quantity"
                    type="number"
                    dense
                    outlined
                    hide-details="auto"
                    label="Qty"
                    :min="1"
                    :max="maxOrderQtyForProduct(line.product)"
                    class="checkout-summary-qty rounded-lg mb-1 mb-sm-0"
                    @change="onSummaryQtyInput(line, $event)"
                  />
                </div>
              </div>
              <div class="font-weight-bold text-right pl-2" style="white-space: nowrap">
                {{ formatZar(lineTotal(line)) }}
              </div>
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
            <div v-if="paymentMethod === 'eft' && hasBankingDetails" class="text-body-2">
              <v-icon small color="primary" class="mr-1">account_balance</v-icon>
              EFT to <strong>{{ bankName }}</strong>
            </div>
            <div v-else-if="paymentMethod === 'eft'" class="text-caption text--secondary">
              EFT selected — banking details missing; choose cash or contact the store.
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
        </template>

        <v-col v-else cols="12" sm="10" md="7" lg="5">
          <v-card class="checkout-empty-cart pa-10 text-center rounded-xl" elevation="1">
            <v-icon size="48" color="secondary" class="mb-4">shopping_cart</v-icon>
            <div class="text-h6 font-weight-bold mb-2">Your cart is empty</div>
            <p class="text-body-2 text--secondary mb-6">
              Add products from the shop, then return here to check out.
            </p>
            <v-btn depressed color="primary" class="text-none font-weight-bold btn-amber" to="/">
              Browse products
            </v-btn>
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

    <v-dialog v-model="stockConflictDialog" max-width="560" persistent content-class="rounded-xl">
      <v-card class="stock-conflict-card rounded-xl" v-if="stockConflict">
        <v-card-title class="stock-conflict-card__title pb-2 pt-6 px-6">
          Some items are no longer available
        </v-card-title>
        <v-card-text class="px-6 pb-0">
          <p class="text-body-2 text--secondary mb-4">
            While placing your order, stock changed. Below is what we can still put on this order for you. You can
            continue with only these items, or go back and change your cart yourself.
          </p>

          <div v-if="stockConflict.unavailable.length" class="mb-4">
            <div class="stock-conflict-section-label mb-2">Not available anymore</div>
            <ul class="stock-conflict-list stock-conflict-list--muted pl-0 mb-0">
              <li v-for="(row, i) in stockConflict.unavailable" :key="'u-' + i">
                <strong>{{ row.name }}</strong> — you asked for {{ row.wanted }}, none left in stock.
              </li>
            </ul>
          </div>

          <div v-if="stockConflict.reduced.length" class="mb-4">
            <div class="stock-conflict-section-label mb-2">Lower quantity available</div>
            <ul class="stock-conflict-list stock-conflict-list--muted pl-0 mb-0">
              <li v-for="(row, i) in stockConflict.reduced" :key="'r-' + i">
                <strong>{{ row.name }}</strong> — you asked for {{ row.wanted }}; only
                <strong>{{ row.have }}</strong> left. We’ll include <strong>{{ row.willOrder }}</strong> if you
                continue.
              </li>
            </ul>
          </div>

          <div class="mb-2">
            <div class="stock-conflict-section-label mb-2">Still available on this order</div>
            <ul class="stock-conflict-list stock-conflict-list--ok pl-0 mb-0">
              <li v-for="(line, i) in stockConflict.nextLines" :key="'a-' + i" class="d-flex justify-space-between align-baseline">
                <span class="flex-grow-1 pr-2">
                  <strong>{{ line.product.name }}</strong>
                  <span class="text--secondary"> × {{ line.quantity }}</span>
                </span>
                <span class="font-weight-medium">{{ formatZar(previewLineUnit(line) * line.quantity) }}</span>
              </li>
            </ul>
          </div>

          <v-alert v-if="stockConflictAdjustedSubtotal != null" type="info" dense outlined class="mt-4 rounded-lg mb-0">
            <span class="text-body-2">
              New subtotal for these items:
              <strong>{{ formatZar(stockConflictAdjustedSubtotal) }}</strong>
              (plus delivery if you chose it).
            </span>
          </v-alert>
        </v-card-text>
        <v-card-actions class="stock-conflict-actions px-6 pb-6 pt-4 flex-wrap">
          <v-btn text class="text-none mb-2" @click="cancelStockConflict">Go back</v-btn>
          <v-spacer class="d-none d-sm-block" />
          <v-btn
            depressed
            color="primary"
            class="text-none font-weight-bold btn-amber mb-2"
            :loading="submitting"
            @click="confirmStockConflictContinue"
          >
            Continue with this order
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import {
  getCartState,
  clearCart,
  addToCart,
  cartSubtotalNumber,
  setLineQuantity,
  MAX_LINE_QUANTITY
} from '@/services/cart'
import { fetchShopSettings, placeOrder, submitCheckoutOrderEftProof } from '@/services/publicStore'
import { isSalonOnlyShopType } from '@/services/shopType'
import { fetchProductsByIds } from '@/services/publicStore'
import { formatZar } from '@/utils/price'
import { haversineKm } from '@/utils/distance'
import { fetchReversePlaceLabel } from '@/utils/geocode'
import { reconcileCartLinesAgainstStock } from '@/utils/stockReconcile'
import MapLocationPicker from '@/components/MapLocationPicker.vue'

export default {
  name: 'CheckoutView',
  components: { MapLocationPicker },
  data() {
    return {
      cart: getCartState(),
      deliveryFeeFlat: 50,
      deliveryFeeMode: 'standard',
      deliveryFeePerKm: 8,
      storeLat: null,
      storeLng: null,
      deliveryLat: null,
      deliveryLng: null,
      eftInstructions: '',
      bankName: '',
      bankAccountHolder: '',
      bankAccountNumber: '',
      bankBranchCode: '',
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
      successNeedsEftProof: false,
      successCashPaymentCode: '',
      successNeedsCashPaymentCode: false,
      orderEftBankReference: '',
      orderEftProofFile: null,
      orderEftProofSubmitting: false,
      orderEftProofError: '',
      orderEftProofSuccessMsg: '',
      copySnackbar: false,
      supabaseConfigHint: '',
      stockConflictDialog: false,
      stockConflict: null,
      deliveryAddressGeocoding: false,
      deliveryAddressGeoError: ''
    }
  },
  computed: {
    stockConflictAdjustedSubtotal() {
      if (!this.stockConflict || !this.stockConflict.nextLines.length) return null
      return this.stockConflict.nextLines.reduce((sum, line) => {
        const u = this.previewLineUnit(line)
        return sum + u * line.quantity
      }, 0)
    },
    subtotal() {
      return cartSubtotalNumber()
    },
    deliveryPricingBadge() {
      if (this.deliveryFeeMode === 'per_km') {
        return `${this.formatZar(this.deliveryFeePerKm)}/km`
      }
      return `+ ${this.formatZar(this.deliveryFeeFlat)}`
    },
    hasStoreLocation() {
      return Number.isFinite(this.storeLat) && Number.isFinite(this.storeLng)
    },
    mapCenterLat() {
      return this.hasStoreLocation ? this.storeLat : -26.2041
    },
    mapCenterLng() {
      return this.hasStoreLocation ? this.storeLng : 28.0473
    },
    deliveryDistancePreviewKm() {
      if (!this.hasStoreLocation || this.deliveryLat == null || this.deliveryLng == null) return null
      return haversineKm(this.storeLat, this.storeLng, this.deliveryLat, this.deliveryLng)
    },
    deliveryCharge() {
      if (this.deliveryType !== 'delivery') return 0
      if (this.deliveryFeeMode === 'per_km') {
        const d = this.deliveryDistancePreviewKm
        if (d == null || d < 0) return 0
        return Math.round(d * this.deliveryFeePerKm * 100) / 100
      }
      return this.deliveryFeeFlat
    },
    estimatedTotal() {
      return this.subtotal + this.deliveryCharge
    },
    customerNameRules() {
      return [
        (v) => !!String(v || '').trim() || 'Full name is required.',
        (v) => String(v || '').trim().length >= 2 || 'Enter at least 2 characters.'
      ]
    },
    customerEmailRules() {
      return [
        (v) => !!String(v || '').trim() || 'Email is required.',
        (v) =>
          /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(v || '').trim()) || 'Enter a valid email address.'
      ]
    },
    customerPhoneRules() {
      return [
        (v) => !!String(v || '').trim() || 'Phone is required.',
        (v) => {
          const t = String(v || '').trim()
          if (!t) return true
          if (t.length < 8 || t.length > 32) return 'Use 8–32 characters.'
          if (t.replace(/\D/g, '').length < 8) return 'Include at least 8 digits.'
          return true
        }
      ]
    },
    hasBankingDetails() {
      const n = (x) => String(x || '').trim()
      return (
        n(this.bankName).length >= 2 &&
        n(this.bankAccountHolder).length >= 2 &&
        String(this.bankAccountNumber || '').replace(/\D/g, '').length >= 4
      )
    },
    orderEftProofFileSingle() {
      const f = this.orderEftProofFile
      if (f instanceof File) return f
      if (Array.isArray(f) && f[0] instanceof File) return f[0]
      return null
    },
    isValidOrderEftProofFile() {
      const f = this.orderEftProofFileSingle
      if (!f) return false
      const t = String(f.type || '').toLowerCase()
      const n = String(f.name || '').toLowerCase()
      if (t === 'application/pdf' || n.endsWith('.pdf')) return true
      if (t.startsWith('image/') || /\.(jpe?g|png|gif|webp)$/i.test(n)) return true
      return false
    },
    canSubmitOrderEftProof() {
      return (
        !this.orderEftProofSubmitting &&
        Boolean(this.successOrderId) &&
        String(this.orderEftBankReference || '').trim().length >= 3 &&
        this.orderEftProofFileSingle != null &&
        this.isValidOrderEftProofFile
      )
    }
  },
  watch: {
    deliveryType(v) {
      if (v === 'pickup') {
        this.deliveryAddress = ''
        this.deliveryLat = null
        this.deliveryLng = null
        this.deliveryAddressGeoError = ''
      }
    },
    paymentMethod() {
      this.paymentHint = ''
    }
  },
  beforeDestroy() {
    if (this._deliveryAddrTimer) clearTimeout(this._deliveryAddrTimer)
  },
  async created() {
    const slug = String(this.$route.params.merchantSlug || '').trim()
    const s = await fetchShopSettings(slug)
    if (isSalonOnlyShopType(s.shopType)) {
      await this.$router.replace({ name: 'merchant-salon-services', params: { merchantSlug: slug } })
      return
    }
    this.deliveryFeeFlat = s.deliveryFeeZar
    this.deliveryFeeMode = s.deliveryFeeMode === 'per_km' ? 'per_km' : 'standard'
    this.deliveryFeePerKm = s.deliveryFeePerKmZar
    this.storeLat = Number.isFinite(s.storeLat) ? s.storeLat : null
    this.storeLng = Number.isFinite(s.storeLng) ? s.storeLng : null
    this.bankName = s.bankName || ''
    this.bankAccountHolder = s.bankAccountHolder || ''
    this.bankAccountNumber = s.bankAccountNumber || ''
    this.bankBranchCode = s.bankBranchCode || ''
    this.eftInstructions = s.eftBankInstructions || ''
  },
  methods: {
    formatZar,
    formatDistanceKm(km) {
      const n = Number(km)
      if (!Number.isFinite(n)) return '—'
      return String(Math.round(n * 1000) / 1000)
    },
    onDeliveryMapInput({ lat, lng }) {
      this.deliveryLat = lat
      this.deliveryLng = lng
      this.deliveryAddressGeoError = ''
      this.scheduleDeliveryAddressFromCoords(lat, lng)
    },
    scheduleDeliveryAddressFromCoords(lat, lng) {
      if (this._deliveryAddrTimer) clearTimeout(this._deliveryAddrTimer)
      this._deliveryAddrTimer = setTimeout(() => {
        this._deliveryAddrTimer = null
        this.fillDeliveryAddressFromCoords(lat, lng)
      }, 450)
    },
    async fillDeliveryAddressFromCoords(lat, lng) {
      this.deliveryAddressGeocoding = true
      this.deliveryAddressGeoError = ''
      try {
        const label = await fetchReversePlaceLabel(lat, lng)
        if (label) this.deliveryAddress = label
      } finally {
        this.deliveryAddressGeocoding = false
      }
    },
    fillDeliveryAddressFromGeolocation() {
      if (!navigator.geolocation) {
        this.deliveryAddressGeoError = 'Location is not available in this browser — type your address instead.'
        return
      }
      this.deliveryAddressGeoError = ''
      this.deliveryAddressGeocoding = true
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          const lat = pos.coords.latitude
          const lng = pos.coords.longitude
          if (this.deliveryFeeMode === 'per_km' && this.hasStoreLocation) {
            this.deliveryLat = lat
            this.deliveryLng = lng
          }
          try {
            const label = await fetchReversePlaceLabel(lat, lng)
            if (label) {
              this.deliveryAddress = label
            } else {
              this.deliveryAddressGeoError = 'Could not resolve an address for this point — please type it in.'
            }
          } finally {
            this.deliveryAddressGeocoding = false
          }
        },
        () => {
          this.deliveryAddressGeocoding = false
          this.deliveryAddressGeoError =
            'Could not read your location. Allow access in the browser or type your full address.'
        },
        { enableHighAccuracy: false, maximumAge: 60000, timeout: 15000 }
      )
    },
    setDeliveryType(t) {
      if (t === 'delivery' && this.deliveryFeeMode === 'per_km' && !this.hasStoreLocation) return
      this.deliveryType = t
    },
    choosePayment(m) {
      if (m === 'eft' && !this.hasBankingDetails) {
        this.paymentHint =
          'Bank transfer is not available yet — the shop has not added banking details. Pay with cash or contact the store.'
        return
      }
      this.paymentHint = ''
      this.paymentMethod = m
    },
    async copyBankDetail(text) {
      const t = String(text || '')
      if (!t || !navigator.clipboard) return
      try {
        await navigator.clipboard.writeText(t)
        this.copySnackbar = true
      } catch {
        // ignore
      }
    },
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
    async submitOrderEftProof() {
      if (!this.canSubmitOrderEftProof) return
      const slug = String(this.$route.params.merchantSlug || '').trim()
      this.orderEftProofSubmitting = true
      this.orderEftProofError = ''
      this.orderEftProofSuccessMsg = ''
      try {
        const res = await submitCheckoutOrderEftProof(slug, this.successOrderId, {
          customerEmail: this.customerEmail,
          bankReference: this.orderEftBankReference,
          file: this.orderEftProofFileSingle
        })
        const auto = res && res.autoVerified
        const st = res && res.orderStatus
        const mode = String((res && res.autoVerifyMode) || '')
        if (auto) {
          this.orderEftProofSuccessMsg =
            mode === 'pdf_amount_and_date'
              ? 'Your bank PDF matched the order total and a recent transaction date — your order is paid and stock is allocated.'
              : 'Payment reference matched — your order is confirmed and stock has been allocated.'
        } else if (String(st || '').toLowerCase() === 'pending_payment') {
          this.orderEftProofSuccessMsg =
            mode === 'pdf_amount_and_date'
              ? 'Proof received. The amount or date on your PDF did not match our automatic checks — the store will verify your payment manually.'
              : 'Proof received. We could not auto-verify your reference — the store will review your payment manually before finalising your order.'
        } else {
          this.orderEftProofSuccessMsg = 'Proof submitted.'
        }
      } catch (e) {
        this.orderEftProofError = e && e.message ? e.message : 'Upload failed.'
      } finally {
        this.orderEftProofSubmitting = false
      }
    },
    maxOrderQtyForProduct(product) {
      const s = product && product.stock
      if (s == null || s === '') return MAX_LINE_QUANTITY
      const n = parseInt(String(s), 10)
      if (!Number.isFinite(n) || n < 0) return MAX_LINE_QUANTITY
      return Math.min(MAX_LINE_QUANTITY, n)
    },
    onSummaryQtyInput(line, raw) {
      const n = parseInt(String(raw !== undefined && raw !== null ? raw : '').replace(/\s/g, ''), 10)
      if (!Number.isFinite(n)) return
      setLineQuantity(line.product.id, n)
    },
    lineTotal(line) {
      const n = typeof line.product.price === 'string' ? Number(line.product.price) : line.product.price
      const unit = Number.isFinite(n) ? n : 0
      return unit * line.quantity
    },
    previewLineUnit(line) {
      const n = typeof line.product.price === 'string' ? Number(line.product.price) : line.product.price
      return Number.isFinite(n) ? n : 0
    },
    cancelStockConflict() {
      this.stockConflictDialog = false
      this.stockConflict = null
    },
    async confirmStockConflictContinue() {
      if (!this.stockConflict || !this.stockConflict.nextLines.length) {
        this.stockConflictDialog = false
        this.stockConflict = null
        return
      }
      const { nextLines } = this.stockConflict
      this.stockConflictDialog = false
      this.stockConflict = null
      clearCart()
      for (const { product, quantity } of nextLines) {
        addToCart(product, quantity)
      }
      this.submitError = ''
      await this.$nextTick()
      await this.submit({ retryAfterStockAdjust: true })
    },
    async submit(options = {}) {
      const { retryAfterStockAdjust = false } = options
      this.submitError = ''
      this.paymentHint = ''
      if (!this.cart.lines.length) return
      if (this.paymentMethod !== 'eft' && this.paymentMethod !== 'cash_store') {
        this.paymentHint = 'Please choose whether you’ll pay by bank transfer (EFT) or with cash.'
        return
      }
      if (this.paymentMethod === 'eft' && !this.hasBankingDetails) {
        this.submitError =
          'Bank transfer is not available — the shop has not published banking details yet. Pay with cash or contact the store.'
        return
      }
      const name = String(this.customerName || '').trim()
      const email = String(this.customerEmail || '').trim()
      if (name.length < 2) {
        this.submitError = 'Please enter your full name.'
        return
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.submitError = 'Please enter a valid email address.'
        return
      }
      const phone = String(this.customerPhone || '').trim()
      if (phone.length < 8 || phone.length > 32) {
        this.submitError = 'Please enter a valid phone number (8–32 characters).'
        return
      }
      if (phone.replace(/\D/g, '').length < 8) {
        this.submitError = 'Phone number must include at least 8 digits.'
        return
      }
      if (this.deliveryType === 'delivery' && String(this.deliveryAddress || '').trim().length < 6) {
        this.submitError = 'Please enter your full delivery address.'
        return
      }
      if (this.deliveryType === 'delivery' && this.deliveryFeeMode === 'per_km') {
        if (!this.hasStoreLocation) {
          this.submitError = 'Distance-based delivery is not available — the shop has not set its location.'
          return
        }
        if (this.deliveryLat == null || this.deliveryLng == null) {
          this.submitError = 'Tap the map to set your delivery location, or use “Use my location”.'
          return
        }
      }

      this.submitting = true
      try {
        const items = this.cart.lines.map((l) => ({
          product_id: l.product.id,
          quantity: l.quantity
        }))
        const slug = String(this.$route.params.merchantSlug || '').trim()
        const placed = await placeOrder(slug, {
          customerName: name,
          customerEmail: email,
          customerPhone: phone,
          deliveryType: this.deliveryType,
          deliveryAddress: this.deliveryType === 'delivery' ? this.deliveryAddress : '',
          deliveryLat: this.deliveryType === 'delivery' ? this.deliveryLat : null,
          deliveryLng: this.deliveryType === 'delivery' ? this.deliveryLng : null,
          paymentMethod: this.paymentMethod,
          items
        })
        this.successOrderId = placed.orderId
        this.successPaymentMethod = this.paymentMethod
        this.successNeedsEftProof = Boolean(placed.needsEftProof)
        this.successCashPaymentCode = placed.cashPaymentCode || ''
        this.successNeedsCashPaymentCode = Boolean(placed.needsCashPaymentCode)
        this.orderEftBankReference = placed.orderId
        this.orderEftProofFile = null
        this.orderEftProofError = ''
        this.orderEftProofSuccessMsg = ''
        clearCart()
      } catch (e) {
        if (e && String(e.message || '').toLowerCase().includes('insufficient_stock') && !retryAfterStockAdjust) {
          try {
            const ids = this.cart.lines.map((l) => l.product.id)
            const slug = String(this.$route.params.merchantSlug || '').trim()
            const live = await fetchProductsByIds(slug, ids)
            const { unavailable, reduced, nextLines } = reconcileCartLinesAgainstStock(this.cart.lines, live)
            if (!nextLines.length) {
              clearCart()
              this.submitError =
                'Nothing in your cart is in stock anymore. Your cart was cleared — browse the shop for what’s available.'
            } else {
              this.stockConflict = { unavailable, reduced, nextLines }
              this.stockConflictDialog = true
            }
          } catch (err) {
            this.submitError = err && err.message ? err.message : 'Could not check latest stock.'
          }
        } else {
          this.submitError = e && e.message ? e.message : 'Order failed.'
        }
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

.checkout-flow-divider {
  height: 1px;
  margin: 28px 0 24px;
  background: linear-gradient(90deg, transparent, rgba(15, 23, 42, 0.08) 15%, rgba(15, 23, 42, 0.08) 85%, transparent);
  border: 0;
}

.checkout-flow-divider--spaced {
  margin-top: 32px;
  margin-bottom: 28px;
}

.checkout-panel {
  border-radius: 20px;
  padding: 22px 20px 24px;
  background: linear-gradient(165deg, rgba(248, 250, 252, 0.97) 0%, rgba(255, 255, 255, 0.98) 55%, #fff 100%);
  border: 1px solid rgba(15, 23, 42, 0.06);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;
}

.checkout-panel--payment {
  background: linear-gradient(175deg, rgba(255, 247, 237, 0.35) 0%, rgba(255, 255, 255, 0.96) 38%, #fff 100%);
}

.checkout-panel__header {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.checkout-panel__header-icon-wrap {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, rgba(234, 88, 12, 0.12), rgba(194, 65, 12, 0.08));
  border: 1px solid rgba(234, 88, 12, 0.18);
}

.checkout-panel__header-icon-wrap--payment {
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.06), rgba(15, 23, 42, 0.03));
  border-color: rgba(15, 23, 42, 0.08);
}

.checkout-panel__title {
  font-size: 1.0625rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
  margin: 0 0 6px;
  line-height: 1.25;
}

.checkout-panel__lead {
  font-size: 0.875rem;
  line-height: 1.55;
  color: rgba(15, 23, 42, 0.58);
  max-width: 58ch;
}

.delivery-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.delivery-option {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  text-align: left;
  padding: 16px 18px;
  border-radius: 16px;
  border: 2px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.22s ease,
    box-shadow 0.22s ease,
    background 0.22s ease,
    transform 0.18s ease;
}

.delivery-option:hover {
  border-color: rgba(234, 88, 12, 0.28);
  box-shadow: 0 10px 36px -20px rgba(15, 23, 42, 0.18);
}

.delivery-option:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.28);
}

.delivery-option--active {
  border-color: #ea580c;
  background: linear-gradient(105deg, rgba(255, 247, 237, 0.65) 0%, #fff 45%);
  box-shadow: 0 12px 40px -24px rgba(194, 65, 12, 0.35);
}

.delivery-option__radio {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid rgba(15, 23, 42, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.delivery-option--active .delivery-option__radio {
  border-color: #ea580c;
  background: rgba(234, 88, 12, 0.08);
}

.delivery-option__radio-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: transparent;
  transform: scale(0);
  transition: transform 0.18s ease;
}

.delivery-option--active .delivery-option__radio-dot {
  background: linear-gradient(135deg, #ea580c, #c2410c);
  transform: scale(1);
}

.delivery-option__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.delivery-option__title {
  font-size: 0.9375rem;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: #0f172a;
}

.delivery-option__desc {
  font-size: 0.8125rem;
  line-height: 1.45;
  color: rgba(15, 23, 42, 0.55);
}

.delivery-option__badge {
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #c2410c;
  background: rgba(234, 88, 12, 0.12);
  padding: 6px 10px;
  border-radius: 999px;
}

.delivery-option__badge--free {
  color: #15803d;
  background: rgba(22, 163, 74, 0.12);
}

.delivery-option__glyph {
  flex-shrink: 0;
  opacity: 0.55;
}

.delivery-option--active .delivery-option__glyph {
  opacity: 0.9;
}

.delivery-option--disabled {
  opacity: 0.55;
  pointer-events: none;
  cursor: not-allowed;
}

.eft-bank-card {
  border-color: rgba(234, 88, 12, 0.35) !important;
  background: rgba(255, 247, 237, 0.45);
}

.eft-bank-dl {
  margin: 0;
}

.eft-bank-row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px 16px;
  margin-bottom: 10px;
  font-size: 0.9375rem;
  line-height: 1.45;
}

.eft-bank-row:last-child {
  margin-bottom: 0;
}

.eft-bank-row dt {
  margin: 0;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.55);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.eft-bank-row dd {
  margin: 0;
  color: #0f172a;
}

.success-eft-dl .eft-bank-row {
  grid-template-columns: 110px 1fr;
}

.checkout-address {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px dashed rgba(15, 23, 42, 0.1);
}

.checkout-address__label {
  display: block;
  font-size: 0.6875rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
  margin-bottom: 10px;
}

.checkout-address__hint {
  margin-top: 10px;
  font-size: 0.8125rem;
  line-height: 1.45;
  color: rgba(15, 23, 42, 0.5);
}

.checkout-expand-enter-active,
.checkout-expand-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.checkout-expand-enter,
.checkout-expand-leave-to {
  opacity: 0;
  transform: translateY(-6px);
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
  line-height: 1.55;
  max-width: 58ch;
}

.payment-intro__strong {
  color: #0f172a;
  font-weight: 700;
}

.payment-cards {
  display: grid;
  gap: 14px;
}

@media (min-width: 600px) {
  .payment-cards {
    grid-template-columns: 1fr 1fr;
    align-items: stretch;
  }
}

.payment-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  text-align: left;
  border-radius: 18px;
  padding: 20px 18px 20px;
  border: 2px solid rgba(15, 23, 42, 0.09);
  background: #fff;
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color 0.22s ease,
    box-shadow 0.22s ease,
    background 0.22s ease,
    transform 0.18s ease;
}

.payment-card:hover {
  border-color: rgba(234, 88, 12, 0.32);
  box-shadow: 0 14px 40px -22px rgba(15, 23, 42, 0.2);
}

.payment-card:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.32);
}

.payment-card--active {
  border-color: #ea580c;
  background: linear-gradient(160deg, rgba(255, 247, 237, 0.9) 0%, #fff 55%);
  box-shadow: 0 16px 44px -24px rgba(194, 65, 12, 0.42);
}

.payment-card--disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.payment-card__selected-mark {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: scale(0.85);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease,
    background 0.18s ease;
}

.payment-card--active .payment-card__selected-mark {
  opacity: 1;
  transform: scale(1);
  background: linear-gradient(135deg, #ea580c, #c2410c);
}

.payment-card__icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
  box-shadow: 0 8px 20px -8px rgba(15, 23, 42, 0.25);
}

.payment-card__icon-wrap--eft {
  background: linear-gradient(145deg, #2563eb, #1d4ed8);
}

.payment-card__icon-wrap--cash {
  background: linear-gradient(145deg, #334155, #1e293b);
}

.payment-card__content {
  flex: 1;
}

.payment-card__title {
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
  margin-bottom: 8px;
  line-height: 1.25;
}

.payment-card__text {
  font-size: 0.8125rem;
  line-height: 1.5;
  color: rgba(15, 23, 42, 0.62);
}

@media (min-width: 600px) {
  .payment-card {
    min-height: 100%;
  }
}

.stock-conflict-card {
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.stock-conflict-card__title {
  font-size: 1.125rem !important;
  font-weight: 700 !important;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.35 !important;
}

.stock-conflict-section-label {
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
}

.stock-conflict-list {
  list-style: none;
}

.stock-conflict-list li {
  font-size: 0.875rem;
  line-height: 1.5;
  margin-bottom: 0.35rem;
  padding-left: 1rem;
  position: relative;
}

.stock-conflict-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55rem;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.25);
}

.stock-conflict-list--ok li::before {
  background: #16a34a;
}

.stock-conflict-list--muted li {
  color: rgba(15, 23, 42, 0.72);
}

.stock-conflict-actions {
  gap: 8px;
}

.checkout-summary-qty {
  flex: 0 0 118px;
  max-width: 160px;
}

.checkout-summary-qty >>> .v-input__slot {
  min-height: 40px !important;
}
</style>
