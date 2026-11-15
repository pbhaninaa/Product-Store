<template>
  <div v-if="user" class="admin-dashboard">
    <v-card class="admin-card admin-dash-welcome pa-4 pa-sm-6 mb-4" elevation="3" rounded="xl">
      <div class="text-overline font-weight-bold text--secondary mb-1">Overview</div>
      <h2 class="text-h5 text-sm-h4 font-weight-bold mb-1 admin-dash-welcome__title">
        {{ dashboardGreeting }}, {{ dashboardFirstName }}
      </h2>
      <p class="text-body-2 text--secondary mb-0">
        {{ dashboardWelcomeBlurb }}
      </p>
    </v-card>

    <v-row dense class="admin-card-row mb-4">
      <v-col v-for="tile in dashboardStatTiles" :key="tile.key" v-bind="statColProps">
        <v-card
          :to="tile.to"
          class="admin-dash-stat-tile admin-eq-card pa-4 rounded-xl"
          outlined
          :class="{ 'admin-dash-stat-tile--accent': tile.accent }"
        >
          <div class="admin-stat-tile__head mb-2">
            <span class="admin-dash-stat-tile__label admin-clamp-1">{{ tile.label }}</span>
            <v-icon :color="tile.iconColor" size="22" class="ml-2 flex-shrink-0">{{ tile.icon }}</v-icon>
          </div>
          <div class="admin-dash-stat-tile__value">
            <v-skeleton-loader v-if="tile.loading" type="text" width="48" class="admin-dash-stat-tile__skeleton" />
            <template v-else>{{ tile.value }}</template>
          </div>
          <div class="admin-dash-stat-tile__hint admin-stat-tile__hint text-caption text--secondary">
            {{ tile.hint }}
          </div>
        </v-card>
      </v-col>
    </v-row>

    <div class="card-label mb-2">Shortcuts</div>
    <v-row dense class="admin-card-row mb-4">
      <v-col v-for="link in quickLinks" :key="link.name" v-bind="shortcutColProps">
        <v-card
          :to="link.to"
          class="admin-link-tile admin-eq-card pa-4 rounded-xl"
          elevation="2"
          hover
          ripple
        >
          <div class="admin-link-tile__inner">
            <v-avatar :color="link.color" size="44" class="mr-3 flex-shrink-0">
              <v-icon dark size="22">{{ link.icon }}</v-icon>
            </v-avatar>
            <div class="admin-link-tile__text">
              <div class="text-subtitle-1 font-weight-bold mb-1 admin-clamp-1">{{ link.title }}</div>
              <p class="text-body-2 text--secondary mb-2 admin-clamp-2">{{ link.blurb }}</p>
              <span class="admin-link-tile__open text-caption font-weight-bold primary--text text-none">
                Open
                <v-icon x-small color="primary" class="ml-1">chevron_right</v-icon>
              </span>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense class="admin-card-row">
      <v-col cols="12" lg="7">
        <v-card class="admin-card admin-eq-card pa-4 pa-sm-6" elevation="3" rounded="xl">
          <div class="card-label mb-4">Your account</div>

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
            </template>

            <template v-else>
              <p class="text-caption text--secondary mb-2 mb-0">
                No store pin saved yet — showing this device’s location for reference. Set the store on the map under
                <strong>Store &amp; delivery</strong> when using per-km pricing.
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
      </v-col>

      <v-col cols="12" lg="5">
        <v-card class="admin-card admin-eq-card pa-4 pa-sm-6 admin-dash-tips" elevation="3" rounded="xl">
          <div class="card-label mb-4">Tips</div>
          <ul class="admin-dash-tips__list pl-0 mb-0">
            <li v-for="(tip, i) in dashboardTips" :key="'tip-' + i" class="d-flex mb-3">
              <v-icon color="primary" small class="mr-3 mt-0 flex-shrink-0">check_circle_outline</v-icon>
              <span class="text-body-2 text--secondary">{{ tip }}</span>
            </li>
          </ul>
          <div class="admin-eq-card__actions pt-1">
            <v-divider class="mb-4" />
            <div class="text-caption font-weight-bold text-uppercase text--secondary mb-2">Public pages</div>
            <div class="d-flex flex-wrap" style="gap: 8px">
              <v-btn small outlined color="primary" class="text-none" :to="publicShopPath" exact>
                <v-icon left x-small>storefront</v-icon>
                Store
              </v-btn>
              <v-btn small outlined color="primary" class="text-none" :to="publicContactPath">
                <v-icon left x-small>contact_support</v-icon>
                Contact
              </v-btn>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'
import { isSalonShopType, isSalonOnlyShopType } from '@/services/shopType'
import { storefrontContactPath, storefrontShopPath } from './adminRules'

/**
 * Same-width columns for every tile in a row, whatever the tile count (no orphan half-width tile):
 * 4 tiles → 2×2 on tablets, 4 across on desktop; 3 → 3 across from sm; 2 → 2 across from sm.
 */
function equalColProps(count) {
  const n = Math.max(1, Math.min(count, 4))
  if (n === 4) return { cols: 12, sm: 6, lg: 3 }
  return { cols: 12, sm: 12 / n }
}

export default {
  name: 'AdminDashboardView',
  mixins: [adminModuleMixin],
  computed: {
    merchantSlugParam() {
      return String(this.$route.params.merchantSlug || '').trim()
    },
    publicShopPath() {
      return storefrontShopPath(this.merchantSlugParam)
    },
    publicContactPath() {
      return storefrontContactPath(this.merchantSlugParam)
    },
    statColProps() {
      const n = this.dashboardStatTiles.length
      // Two tiles sit side by side even on phones; three stack on phones and share a row from sm up.
      if (n <= 2) return { cols: 6 }
      return { cols: 12, sm: Math.floor(12 / Math.min(n, 4)) }
    },
    shortcutColProps() {
      return equalColProps(this.quickLinks.length)
    },
    dashboardWelcomeBlurb() {
      if (this.staffOnly) {
        return 'Here’s a snapshot for your shift. Use Orders, Bookings, and Alerts for daily work; My income shows pay attributed to you.'
      }
      return 'Here’s a snapshot of your store. Use Orders, Bookings, and Alerts for daily work; open Settings for catalogue, team, and store setup. Badges appear on tabs when something needs attention.'
    },
    dashboardGreeting() {
      const h = new Date().getHours()
      if (h < 12) return 'Good morning'
      if (h < 17) return 'Good afternoon'
      return 'Good evening'
    },
    dashboardFirstName() {
      const store = String(this.storeNameDraft || '').trim()
      if (store.length >= 2) return store
      const e = (this.user && this.user.email) || ''
      const local = (e.split('@')[0] || 'there').trim()
      if (!local) return 'there'
      // Keep the full local part (hyphens, dots, etc.) — only lightly title the first letter.
      return local.charAt(0).toUpperCase() + local.slice(1)
    },
    dashboardStatTiles() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      const toProducts = { name: 'merchant-admin-products', params: { merchantSlug: slug } }
      const toOrders = { name: 'merchant-admin-orders', params: { merchantSlug: slug } }
      const products = this.products || []
      const ordersRaw = this.orders || []
      /** Open pipeline: not cancelled and not yet completed (processing, ready, awaiting payment, etc.). */
      const openOrders = ordersRaw.filter((o) => {
        if (!o || o.cancelled_at) return false
        return this.effectiveOrderStatus(o) !== 'completed'
      })
      const awaiting = openOrders.filter((o) => !this.orderIsPaid(o)).length
      const ordersLoading = Boolean(this.ordersLoading && !ordersRaw.length)
      const tiles = []
      if (!this.staffOnly && !isSalonOnlyShopType(this.shopTypeDraft)) {
        tiles.push({
          key: 'products',
          label: 'Catalogue',
          value: products.length,
          loading: Boolean(this.loading && !products.length),
          hint: 'Items sold in your store',
          icon: 'inventory_2',
          iconColor: 'primary',
          accent: true,
          to: toProducts
        })
      }
      tiles.push(
        {
          key: 'orders',
          label: 'Open orders',
          value: openOrders.length,
          loading: ordersLoading,
          hint: 'Excludes completed & cancelled',
          icon: 'receipt_long',
          iconColor: 'deep-orange darken-1',
          accent: this.staffOnly,
          to: toOrders
        },
        {
          key: 'awaiting',
          label: 'Awaiting payment',
          value: awaiting,
          loading: ordersLoading,
          hint: 'Confirm when paid',
          icon: 'pending_actions',
          iconColor: 'amber darken-2',
          accent: false,
          to: toOrders
        }
      )
      return tiles
    },
    quickLinks() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      const salon = isSalonShopType(this.shopTypeDraft)
      const salonOnly = isSalonOnlyShopType(this.shopTypeDraft)
      if (this.staffOnly) {
        const staffLinks = []
        if (!salonOnly) {
          staffLinks.push({
            name: 'orders',
            title: 'Orders',
            blurb: 'Search, confirm payments, and print invoices.',
            icon: 'receipt_long',
            color: 'deep-orange darken-1',
            to: { name: 'merchant-admin-orders', params: { merchantSlug: slug } }
          })
        }
        if (salon) {
          staffLinks.push({
            name: 'bookings',
            title: 'Bookings',
            blurb: 'Confirm payments, start and complete appointments.',
            icon: 'event_note',
            color: 'deep-purple',
            to: { name: 'merchant-admin-salon-bookings', params: { merchantSlug: slug } }
          })
        }
        return staffLinks.concat([
          {
            name: 'alerts',
            title: 'Alerts',
            blurb: 'New orders, bookings, and payroll notifications.',
            icon: 'notifications',
            color: 'amber darken-2',
            to: { name: 'merchant-admin-notifications', params: { merchantSlug: slug } }
          },
          {
            name: 'income',
            title: 'My income',
            blurb: 'Expected income from work attributed to you.',
            icon: 'savings',
            color: 'green darken-1',
            to: { name: 'merchant-admin-team', params: { merchantSlug: slug }, query: { tab: 'income' } }
          }
        ])
      }
      return [
        salonOnly
          ? {
              name: 'bookings',
              title: 'Bookings',
              blurb: 'Confirm payments, start and complete appointments.',
              icon: 'event_note',
              color: 'deep-orange darken-1',
              to: { name: 'merchant-admin-salon-bookings', params: { merchantSlug: slug } }
            }
          : {
              name: 'orders',
              title: 'Orders',
              blurb: 'Search, confirm payments, and print invoices.',
              icon: 'receipt_long',
              color: 'deep-orange darken-1',
              to: { name: 'merchant-admin-orders', params: { merchantSlug: slug } }
            },
        salonOnly
          ? {
              name: 'salon-services',
              title: 'Services',
              blurb: 'Publish and edit bookable services and prices.',
              icon: 'event_available',
              color: 'primary',
              to: { name: 'merchant-admin-salon', params: { merchantSlug: slug } }
            }
          : {
              name: 'products',
              title: 'Products',
              blurb: 'Add items and inventory — also under Settings → Catalogue.',
              icon: 'inventory_2',
              color: 'primary',
              to: { name: 'merchant-admin-products', params: { merchantSlug: slug } }
            },
        {
          name: 'settings',
          title: 'Settings',
          blurb: 'Catalogue, team, store details, plan, and help in one place.',
          icon: 'settings',
          color: 'blue-grey',
          to: { name: 'merchant-admin-settings', params: { merchantSlug: slug } }
        },
        {
          name: 'store',
          title: 'Store settings',
          blurb: 'Delivery, banking, branding — deep link from Settings → Store.',
          icon: 'storefront',
          color: 'teal darken-1',
          to: { name: 'merchant-admin-store', params: { merchantSlug: slug } }
        }
      ]
    },
    dashboardTips() {
      if (this.staffOnly) {
        return [
          'Confirm EFT orders only after the payment shows in your bank.',
          'Use Alerts for new orders and bookings that need attention.',
          'My income shows pay from work attributed to your staff account.',
          'Ask the store owner for catalogue, team, or plan changes.'
        ]
      }
      return [
        'Confirm EFT orders only after the payment shows in your bank.',
        'Per-km delivery needs a store pin — set it under Settings → Store settings → Store & delivery.',
        'Open Settings for catalogue, team, payroll, plan, and help.',
        'Stock goes down when an order is marked paid — keep counts current under Products.',
        'Branding and contact details update your live store and its Contact page as soon as you save.'
      ]
    }
  }
}
</script>

<style scoped>
.admin-dash-welcome {
  border: 1px solid rgba(234, 88, 12, 0.2);
  background: linear-gradient(135deg, rgba(255, 247, 237, 0.95) 0%, rgba(255, 255, 255, 0.98) 100%);
}

.admin-dash-welcome__title {
  letter-spacing: -0.03em;
  color: #0f172a;
}

.admin-dash-stat-tile {
  border-color: rgba(15, 23, 42, 0.08) !important;
  background: rgba(255, 255, 255, 0.98);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  text-decoration: none;
  color: inherit !important;
}

.admin-dash-stat-tile--accent {
  border-color: rgba(234, 88, 12, 0.35) !important;
  background: linear-gradient(145deg, rgba(255, 247, 237, 0.88) 0%, rgba(255, 255, 255, 0.98) 100%);
}

.admin-dash-stat-tile__label {
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.45);
}

.admin-dash-stat-tile__value {
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1.15;
}

.admin-dash-stat-tile--accent .admin-dash-stat-tile__value {
  background: linear-gradient(120deg, #c2410c, #ea580c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.admin-dash-stat-tile__hint {
  line-height: 1.35;
}

.admin-dash-stat-tile__skeleton {
  /* Same height as the number so the tile does not jump when data arrives. */
  height: 1.725rem;
  display: flex;
  align-items: center;
}

.admin-dash-stat-tile__skeleton >>> .v-skeleton-loader__text {
  height: 1.1rem;
  margin-bottom: 0;
}

.admin-dash-tips__list {
  list-style: none;
}
</style>
