<template>
  <div v-if="user" class="admin-dashboard">
    <v-row class="mb-2">
      <v-col cols="12">
        <v-card class="admin-card admin-dash-welcome pa-5 pa-sm-6 mb-2" elevation="3" rounded="xl">
          <div class="d-flex flex-column flex-sm-row align-start align-sm-center flex-wrap">
            <div class="flex-grow-1">
              <div class="text-overline font-weight-bold text--secondary mb-1">Overview</div>
              <h2 class="text-h5 text-sm-h4 font-weight-bold mb-1 admin-dash-welcome__title">
                {{ dashboardGreeting }}, {{ dashboardFirstName }}
              </h2>
              <p class="text-body-2 text--secondary mb-0 pr-sm-4">
                Here’s a snapshot of your shop. Use the tabs above or the shortcuts below to manage products, orders, and
                store settings. When something needs attention, you’ll see a badge on the matching tab.
              </p>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense class="mb-6">
      <v-col v-for="tile in dashboardStatTiles" :key="tile.key" cols="6" md="4">
        <v-card
          :to="tile.to"
          class="admin-dash-stat-tile pa-4 rounded-xl h-100 d-flex flex-column"
          outlined
          :class="{ 'admin-dash-stat-tile--accent': tile.accent }"
        >
          <div class="d-flex align-center justify-space-between mb-2">
            <span class="admin-dash-stat-tile__label">{{ tile.label }}</span>
            <v-icon :color="tile.iconColor" size="22">{{ tile.icon }}</v-icon>
          </div>
          <div class="admin-dash-stat-tile__value">{{ tile.value }}</div>
          <div class="admin-dash-stat-tile__hint text-caption text--secondary mt-1">{{ tile.hint }}</div>
        </v-card>
      </v-col>
    </v-row>

    <div class="card-label mb-3">Shortcuts</div>
    <v-row dense class="mb-6">
      <v-col v-for="link in quickLinks" :key="link.name" cols="12" sm="6" lg="3">
        <v-card
          :to="link.to"
          class="admin-dash-shortcut pa-4 rounded-xl h-100"
          elevation="2"
          hover
          ripple
        >
          <div class="d-flex align-start">
            <v-avatar :color="link.color" size="44" class="mr-3 flex-shrink-0">
              <v-icon dark size="22">{{ link.icon }}</v-icon>
            </v-avatar>
            <div class="flex-grow-1 min-width-0">
              <div class="text-subtitle-1 font-weight-bold mb-1">{{ link.title }}</div>
              <p class="text-body-2 text--secondary mb-2">{{ link.blurb }}</p>
              <span class="text-caption font-weight-bold primary--text text-none">
                Open
                <v-icon x-small color="primary" class="ml-1">chevron_right</v-icon>
              </span>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12" lg="7" class="mb-4 mb-lg-0">
        <v-card class="admin-card pa-4 pa-sm-6 mb-4 mb-md-6" elevation="3" rounded="xl">
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
      </v-col>

      <v-col cols="12" lg="5">
        <v-card class="admin-card pa-4 pa-sm-6 admin-dash-tips" elevation="3" rounded="xl">
          <div class="card-label mb-4">Tips</div>
          <ul class="admin-dash-tips__list pl-0 mb-0">
            <li v-for="(tip, i) in dashboardTips" :key="'tip-' + i" class="d-flex mb-3">
              <v-icon color="primary" small class="mr-3 mt-0 flex-shrink-0">check_circle_outline</v-icon>
              <span class="text-body-2 text--secondary">{{ tip }}</span>
            </li>
          </ul>
          <v-divider class="my-4" />
          <div class="text-caption font-weight-bold text-uppercase text--secondary mb-2">Public pages</div>
          <div class="d-flex flex-wrap" style="gap: 8px">
            <v-btn small outlined color="primary" class="text-none" to="/" exact>
              <v-icon left x-small>storefront</v-icon>
              Shop
            </v-btn>
            <v-btn small outlined color="primary" class="text-none" to="/contact">
              <v-icon left x-small>contact_support</v-icon>
              Contact
            </v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminDashboardView',
  mixins: [adminModuleMixin],
  computed: {
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
      return [
        {
          key: 'products',
          label: 'Catalogue',
          value: products.length,
          hint: 'Products on the shop',
          icon: 'inventory_2',
          iconColor: 'primary',
          accent: true,
          to: toProducts
        },
        {
          key: 'orders',
          label: 'Open orders',
          value: openOrders.length,
          hint: 'Excludes completed & cancelled',
          icon: 'receipt_long',
          iconColor: 'deep-orange darken-1',
          accent: false,
          to: toOrders
        },
        {
          key: 'awaiting',
          label: 'Awaiting payment',
          value: awaiting,
          hint: 'Confirm when paid',
          icon: 'pending_actions',
          iconColor: 'amber darken-2',
          accent: false,
          to: toOrders
        }
      ]
    },
    quickLinks() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      return [
        {
          name: 'products',
          title: 'Products',
          blurb: 'Add items, edit inventory, and remove listings.',
          icon: 'inventory_2',
          color: 'primary',
          to: { name: 'merchant-admin-products', params: { merchantSlug: slug } }
        },
        {
          name: 'orders',
          title: 'Orders',
          blurb: 'Search, confirm payments, and print invoices.',
          icon: 'receipt_long',
          color: 'deep-orange darken-1',
          to: { name: 'merchant-admin-orders', params: { merchantSlug: slug } }
        },
        {
          name: 'insights',
          title: 'Insights',
          blurb: 'Revenue, delivery fees, and top sellers by period.',
          icon: 'insights',
          color: 'indigo',
          to: { name: 'merchant-admin-insights', params: { merchantSlug: slug } }
        },
        {
          name: 'store',
          title: 'Store settings',
          blurb: 'Delivery rules, banking, branding, and contact page.',
          icon: 'storefront',
          color: 'teal darken-1',
          to: { name: 'merchant-admin-store', params: { merchantSlug: slug } }
        }
      ]
    },
    dashboardTips() {
      return [
        'Confirm EFT orders only after the payment shows in your bank.',
        'Per-km delivery needs a store pin — set it under Store → Shop & delivery.',
        'Use Insights to spot your best sellers before you restock.',
        'Branding and contact details update the live shop and /contact page immediately after save.'
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

.admin-dash-shortcut {
  border: 1px solid rgba(15, 23, 42, 0.06);
  text-decoration: none;
  color: inherit;
  display: block;
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}

.admin-dash-shortcut:hover {
  border-color: rgba(234, 88, 12, 0.25);
}

.admin-dash-tips__list {
  list-style: none;
}

.min-width-0 {
  min-width: 0;
}
</style>
