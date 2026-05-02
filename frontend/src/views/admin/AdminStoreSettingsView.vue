<template>
  <div>
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
              Bank details for customers who pay by EFT at checkout. Store type, name, and visuals are set under
              <strong>Store Branding</strong>. Fields marked * are required before EFT is offered.
            </p>
            <v-text-field
              v-model="bankNameDraft"
              outlined
              hide-details="auto"
              label="Bank name *"
              class="mt-4 rounded-lg"
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
            <div class="text-subtitle-2 font-weight-bold mt-6 mb-2">Checkout &amp; salon booking</div>
            <p class="text-body-2 text--secondary mb-2">
              Choose which payment options customers see at product checkout and on the public salon booking page. At
              least one must stay on.
            </p>
            <v-switch
              v-model="acceptCustomerEftDraft"
              label="Offer EFT (customer pays by transfer)"
              inset
              color="primary"
              class="mt-2"
              hide-details
              :disabled="bankingSaving || shopSettingsLoading"
            />
            <v-switch
              v-model="acceptCustomerCashDraft"
              label="Offer pay in store (cash at pickup)"
              inset
              color="primary"
              class="mt-1"
              hide-details
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

      <v-row v-if="user" align="stretch" class="mt-2 mt-md-4">
        <v-col cols="12" md="6" class="d-flex flex-column admin-shop-bank-col">
          <v-card
            class="admin-card admin-card--settings pa-4 pa-sm-6 d-flex flex-column flex-grow-1"
            elevation="3"
            rounded="xl"
          >
            <div class="flex-grow-1">
              <div class="card-label mb-2">Store Branding</div>
              <p class="text-body-2 text--secondary mb-4">
                Set <strong>business type</strong> (normal store, salon with catalogue, or salon-only bookings), your
                <strong>store name</strong> (header, footer, invoices), <strong>store icon</strong> in the navigation bar, and
                <strong>store advert image</strong> on the customer home hero. Icons work best square; advert images are
                usually wide.
              </p>
              <v-select
                v-model="shopTypeDraft"
                :items="shopTypeItems"
                outlined
                hide-details="auto"
                label="Store type"
                item-text="text"
                item-value="value"
                class="rounded-lg mb-4"
                :disabled="brandingSaving || shopSettingsLoading"
              />
              <v-text-field
                v-model="storeNameDraft"
                outlined
                hide-details="auto"
                label="Store name *"
                hint="Your shop or brand name — replaces the default site title for customers."
                persistent-hint
                class="rounded-lg"
                :disabled="brandingSaving || shopSettingsLoading"
              />
              <v-file-input
                :key="'logo-' + brandingLogoInputKey"
                v-model="brandingLogoFile"
                label="Store icon"
                accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml"
                prepend-icon="image"
                outlined
                hide-details="auto"
                show-size
                class="rounded-lg mt-4"
                :disabled="brandingSaving || shopSettingsLoading"
                @change="onBrandingLogoChange"
              />
              <!-- <div v-if="brandingLogoPreview" class="branding-preview-wrap branding-preview-wrap--logo mt-3">
                <img :src="brandingLogoPreview" alt="" class="branding-preview branding-preview--logo" />
              </div> -->
              <v-btn
                v-if="brandingShowRemoveLogo"
                text
                small
                color="secondary"
                class="text-none mt-1 px-0"
                :disabled="brandingSaving || shopSettingsLoading"
                @click="clearBrandingLogoIntent"
              >
                Remove icon
              </v-btn>

              <v-file-input
                :key="'hero-' + brandingHeroInputKey"
                v-model="brandingHeroFile"
                label="Store advert image"
                accept="image/png,image/jpeg,image/webp,image/gif"
                prepend-icon="panorama"
                outlined
                hide-details="auto"
                show-size
                hint="Shown on the shop home page hero (replaces the default artwork when set)."
                persistent-hint
                class="rounded-lg mt-4"
                :disabled="brandingSaving || shopSettingsLoading"
                @change="onBrandingHeroChange"
              />
              <!-- <div v-if="brandingHeroPreview" class="branding-preview-wrap mt-3">
                <img :src="brandingHeroPreview" alt="" class="branding-preview branding-preview--hero" />
              </div> -->
              <v-btn
                v-if="brandingShowRemoveHero"
                text
                small
                color="secondary"
                class="text-none mt-1 px-0"
                :disabled="brandingSaving || shopSettingsLoading"
                @click="clearBrandingHeroIntent"
              >
                Remove advert image
              </v-btn>
            </div>
            <div class="pt-4 mt-auto">
              <v-btn
                block
                depressed
                color="primary"
                class="text-none font-weight-bold btn-amber"
                :loading="brandingSaving"
                :disabled="shopSettingsLoading"
                @click="saveStoreBranding"
              >
                <v-icon left small color="white">palette</v-icon>
                Save store branding
              </v-btn>
              <v-alert v-if="brandingError" type="error" dense outlined class="mt-4 rounded-lg">
                {{ brandingError }}
              </v-alert>
              <v-alert v-if="brandingSuccess" type="success" dense outlined class="mt-4 rounded-lg">
                Store branding saved — business type, name, and images update what customers see and which admin tools are
                available.
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
            <div class="card-label mb-2">Contact page</div>
            <p class="text-body-2 text--secondary mb-4">
              These details appear on the public <strong>Contact</strong> page (<code class="text-body-2">/contact</code>).
              Leave fields blank to hide them.
            </p>
            <v-text-field
              v-model="contactEmailDraft"
              outlined
              hide-details="auto"
              label="Email"
              type="email"
              autocomplete="off"
              class="rounded-lg"
              :disabled="contactSaving || shopSettingsLoading"
            />
            <v-text-field
              v-model="contactPhoneDraft"
              outlined
              hide-details="auto"
              label="Phone"
              autocomplete="off"
              class="mt-4 rounded-lg"
              :disabled="contactSaving || shopSettingsLoading"
            />
            <v-textarea
              v-model="contactAddressDraft"
              outlined
              hide-details="auto"
              label="Address"
              rows="3"
              auto-grow
              hint="Street, suburb, city — shown as plain text."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="contactSaving || shopSettingsLoading"
            />
            <v-textarea
              v-model="contactPageNotesDraft"
              outlined
              hide-details="auto"
              label="Extra notes"
              rows="3"
              auto-grow
              hint="WhatsApp link, parking notes, or other info. Structured hours use the Working hours card below."
              persistent-hint
              class="mt-4 rounded-lg"
              :disabled="contactSaving || shopSettingsLoading"
            />
            <v-btn
              block
              depressed
              color="primary"
              class="text-none font-weight-bold btn-amber mt-6"
              :loading="contactSaving"
              :disabled="shopSettingsLoading"
              @click="saveContactSettings"
            >
              <v-icon left small color="white">contact_support</v-icon>
              Save contact details
            </v-btn>
            <v-alert v-if="contactError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ contactError }}
            </v-alert>
            <v-alert v-if="contactSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Contact details saved — they are visible on the Contact page.
            </v-alert>
          </v-card>
        </v-col>
      </v-row>

      <v-row v-if="user && showOpeningHoursCard" align="stretch" class="mt-2 mt-md-4">
        <v-col cols="12">
          <v-card
            class="admin-card admin-card--settings pa-4 pa-sm-6 d-flex flex-column"
            elevation="3"
            rounded="xl"
          >
            <div class="flex-grow-1">
              <div class="card-label mb-2">Working hours</div>
              <p class="text-body-2 text--secondary mb-4">
                Every store can publish a weekly schedule. For
                <strong>salon</strong> businesses, the same hours can also limit when online booking slots are offered
                (together with staff availability). Weekdays use ISO numbering: Monday = 1 through Sunday = 7.
              </p>
              <v-switch
                v-model="openingHoursUseShopWindow"
                :label="
                  adminSalonBookingsEnabled
                    ? 'Publish hours and clip salon booking slots to them'
                    : 'Publish weekly working hours for customers'
                "
                inset
                color="primary"
                class="mt-0"
                :disabled="openingHoursSaving || shopSettingsLoading"
              />
              <div v-if="openingHoursUseShopWindow" class="wh-weekly mt-4">
                <p class="text-caption text--secondary mb-3">
                  Toggle each day on, then set open and close (24h).
                  <template v-if="adminSalonBookingsEnabled">
                    Disabled days are treated as closed for booking when shop hours apply.
                  </template>
                  <template v-else> Disabled days are shown as closed on your Contact page. </template>
                </p>
                <div
                  v-for="row in openingHoursRows"
                  :key="row.dayOfWeek"
                  class="wh-day-panel rounded-lg pa-3 mb-3"
                  :class="{
                    'wh-day-panel--off': !row.enabled,
                    'wh-day-panel--weekend': row.dayOfWeek >= 6
                  }"
                >
                  <div class="d-flex flex-column flex-sm-row align-start align-sm-center flex-wrap">
                    <div class="d-flex align-center mb-3 mb-sm-0" style="min-width: 200px">
                      <v-switch
                        v-model="row.enabled"
                        hide-details
                        class="mt-0 mr-3"
                        color="primary"
                        :disabled="openingHoursSaving || shopSettingsLoading"
                      />
                      <div>
                        <div class="text-body-1 font-weight-bold">{{ row.label }}</div>
                        <div class="text-caption text--secondary">
                          {{ row.dayOfWeek >= 6 ? 'Weekend' : 'Weekday' }}
                        </div>
                      </div>
                    </div>
                    <div class="d-flex flex-wrap align-center flex-grow-1" style="gap: 12px">
                      <v-text-field
                        v-model="row.start"
                        type="time"
                        outlined
                        dense
                        hide-details="auto"
                        label="From"
                        style="max-width: 160px"
                        class="rounded-lg"
                        :disabled="!row.enabled || openingHoursSaving || shopSettingsLoading"
                      />
                      <v-icon small color="primary" class="d-none d-sm-inline">arrow_forward</v-icon>
                      <v-text-field
                        v-model="row.end"
                        type="time"
                        outlined
                        dense
                        hide-details="auto"
                        label="To"
                        style="max-width: 160px"
                        class="rounded-lg"
                        :disabled="!row.enabled || openingHoursSaving || shopSettingsLoading"
                      />
                    </div>
                  </div>
                  <div v-if="!row.enabled" class="text-caption text--secondary mt-1 mb-0">
                    Closed — booking slots are not offered on this day when shop hours apply.
                  </div>
                </div>
              </div>
            </div>
            <div class="pt-4 mt-auto">
              <v-btn
                block
                depressed
                color="primary"
                class="text-none font-weight-bold btn-amber"
                :loading="openingHoursSaving"
                :disabled="shopSettingsLoading"
                @click="saveOpeningHours"
              >
                <v-icon left small color="white">schedule</v-icon>
                Save opening hours
              </v-btn>
              <v-alert v-if="openingHoursError" type="error" dense outlined class="mt-4 rounded-lg">
                {{ openingHoursError }}
              </v-alert>
              <v-alert v-if="openingHoursSuccess" type="success" dense outlined class="mt-4 rounded-lg">
                {{ openingHoursSavedMessage }}
              </v-alert>
            </div>
          </v-card>
        </v-col>
      </v-row>
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminStoreSettingsView',
  mixins: [adminModuleMixin]
}
</script>
<style lang="css">
.tetx-none{
  max-width: fit-content;
}
.btn-amber {
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  box-shadow: 0 8px 24px -8px rgba(194, 65, 12, 0.55) !important;
  max-width: fit-content;
}

/* Weekly hours (Wheel Hub–inspired day panels) */
.wh-day-panel {
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: rgba(248, 250, 252, 0.95);
}
.wh-day-panel--weekend:not(.wh-day-panel--off) {
  border-color: rgba(14, 165, 233, 0.28);
  background: rgba(240, 249, 255, 0.75);
}
.wh-day-panel--off {
  opacity: 0.88;
  background: rgba(241, 245, 249, 0.9);
}
</style>
