<template>
  <div class="admin-layout-root">
    <section class="admin-hero">
      <v-container class="py-10 py-md-12">
        <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
          <div>
            <div class="admin-kicker mb-2">
              <v-icon small color="secondary" class="mr-1">lock</v-icon>
              Protected area
            </div>
            <h1 class="admin-title">{{ adminPageTitle }}</h1>
            <p class="admin-lead mb-0">
              <template v-if="user">
                {{ adminPageLead }}
              </template>
              <template v-else>
                Sign in with your staff account to open the dashboard. Only the login form is shown until you’re in.
              </template>
            </p>
          </div>
          <v-chip
            v-if="user"
            class="mt-6 mt-md-0 ml-md-auto text-none font-weight-bold px-4"
            color="primary"
            outlined
            label
          >
            <v-icon left small color="primary">cloud_done</v-icon>
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
          <v-card class="admin-card admin-login-card pa-5 pa-sm-8 d-flex flex-column" elevation="3" rounded="xl">
            <div class="card-label mb-2">Sign in</div>
            <p class="text-body-2 text--secondary mb-8">
              Use your admin email and password from Supabase Authentication. After you sign in, the full dashboard will
              load.
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

            <v-alert v-if="authError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ authError }}
            </v-alert>

            <p class="text-caption text--secondary mt-6 mb-0">
              Merchants can sign up here. Platform Admin assigns support users.
            </p>

            <div class="auth-actions mt-auto pt-6">
              <v-btn
                x-large
                depressed
                color="tertiary"
                class="text-none font-weight-bold auth-actions__btn"
                :loading="authLoading"
                @click="doLogin"
              >
                Sign in
              </v-btn>
              <div class="auth-actions__links mt-3">
                <span class="text-body-2 text--secondary">New merchant?</span>
                <router-link
                  class="auth-actions__link text-body-2 font-weight-bold"
                  :to="{ name: 'merchant-signup', query: { m: $route.params.merchantSlug || 'demo' } }"
                >
                  Sign up
                </router-link>
              </div>
            </div>
          </v-card>
        </v-col>
      </v-row>

      <template v-else>
        <v-card flat class="admin-nav-card mb-6 pa-1 rounded-xl" outlined>
          <v-tabs background-color="transparent" show-arrows>
            <v-tab
              v-for="link in adminNavLinks"
              :key="link.name"
              class="text-none font-weight-bold admin-nav-tab"
              :to="link.to"
              :exact="link.exact === true || link.name === 'merchant-admin'"
            >
              <v-badge
                :value="link.badgeCount > 0"
                :content="link.badgeCount > 99 ? '99+' : String(link.badgeCount)"
                color="deep-orange darken-2"
                overlap
                offset-x="14"
                offset-y="10"
              >
                <span class="d-inline-flex align-center">
                  <v-icon left small class="mr-1">{{ link.icon }}</v-icon>
                  {{ link.label }}
                </span>
              </v-badge>
            </v-tab>
          </v-tabs>
        </v-card>
        <router-view />
      </template>
    </v-container>
  </div>
</template>

<script>
import {
  getSessionUser,
  isSupportOrPlatformOnlyUser,
  loginWithEmailPassword,
  subscribeToAuth
} from '@/services/auth'
import { fetchAdminOrders, fetchAdminStoreSettings } from '@/services/adminApi'
import { fetchCatalog } from '@/services/publicStore'
import { fetchAdminSalonBookings } from '@/services/salonAdmin'
import { normalizeShopType, isSalonShopType, isSalonOnlyShopType } from '@/services/shopType'

export default {
  name: 'AdminLayout',
  provide() {
    return {
      adminSession: this.adminSession
    }
  },
  data() {
    return {
      adminSession: { user: null },
      merchantShopKind: 'normal_store',
      email: '',
      password: '',
      authLoading: false,
      authError: '',
      supabaseConfigHint: '',
      unsubAuth: null,
      navBadgeOrdersUnpaid: 0,
      navBadgeProductsOos: 0,
      navBadgeBookingsEftReview: 0
    }
  },
  computed: {
    user() {
      return this.adminSession.user
    },
    adminPageTitle() {
      const r = this.$route.matched
        .slice()
        .reverse()
        .find((x) => x.meta && x.meta.adminTitle)
      return (r && r.meta.adminTitle) || 'Admin'
    },
    adminPageLead() {
      const r = this.$route.matched
        .slice()
        .reverse()
        .find((x) => x.meta && x.meta.adminLead)
      return (
        (r && r.meta.adminLead) ||
        'Use the tabs below to switch between dashboard, catalogue, orders, insights, and store settings.'
      )
    },
    adminNavLinks() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      const links = [
        {
          name: 'merchant-admin',
          to: { name: 'merchant-admin', params: { merchantSlug: slug } },
          label: 'Dashboard',
          icon: 'dashboard',
          badgeCount: 0
        }
      ]
      if (!isSalonOnlyShopType(this.merchantShopKind)) {
        links.push({
          name: 'merchant-admin-products',
          to: { name: 'merchant-admin-products', params: { merchantSlug: slug } },
          label: 'Products',
          icon: 'inventory_2',
          badgeCount: this.navBadgeProductsOos
        })
      }
      links.push({
        name: 'merchant-admin-salon-staff',
        to: { name: 'merchant-admin-salon-staff', params: { merchantSlug: slug } },
        label: 'Staff management',
        icon: 'groups',
        badgeCount: 0
      })
      if (isSalonShopType(this.merchantShopKind)) {
        links.push(
          {
            name: 'merchant-admin-salon',
            to: { name: 'merchant-admin-salon', params: { merchantSlug: slug } },
            label: 'Salon',
            icon: 'content_cut',
            exact: true,
            badgeCount: 0
          },
          {
            name: 'merchant-admin-salon-payments',
            to: { name: 'merchant-admin-salon-payments', params: { merchantSlug: slug } },
            label: 'Payments',
            icon: 'payments',
            badgeCount: 0
          },
          {
            name: 'merchant-admin-salon-bookings',
            to: { name: 'merchant-admin-salon-bookings', params: { merchantSlug: slug } },
            label: 'Bookings',
            icon: 'event_note',
            badgeCount: this.navBadgeBookingsEftReview
          }
        )
      }
      if (!isSalonOnlyShopType(this.merchantShopKind)) {
        links.push({
          name: 'merchant-admin-orders',
          to: { name: 'merchant-admin-orders', params: { merchantSlug: slug } },
          label: 'Orders',
          icon: 'receipt_long',
          badgeCount: this.navBadgeOrdersUnpaid
        })
      }
      links.push(
        {
          name: 'merchant-admin-insights',
          to: { name: 'merchant-admin-insights', params: { merchantSlug: slug } },
          label: 'Insights',
          icon: 'insights',
          badgeCount: 0
        },
        {
          name: 'merchant-admin-store',
          to: { name: 'merchant-admin-store', params: { merchantSlug: slug } },
          label: 'Store',
          icon: 'storefront',
          badgeCount: 0
        }
      )
      return links
    },
  },
  watch: {
    user(u) {
      if (u) this.refreshMerchantShopKind()
      else this.merchantShopKind = 'normal_store'
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.refreshMerchantShopKind()
    }
  },
  created() {
    this.unsubAuth = subscribeToAuth((u) => {
      this.adminSession.user = u
    })
    this._onMerchantShopMeta = (payload) => {
      this.merchantShopKind = normalizeShopType(payload && payload.shopType)
    }
    this.$root.$on('merchant-shop-meta-updated', this._onMerchantShopMeta)
    this.$root.$on('merchant-admin-badges-refresh', this.scheduleNavBadgeRefresh)
    if (this.user) this.scheduleNavBadgeRefresh()
  },
  beforeDestroy() {
    if (this.unsubAuth) this.unsubAuth()
    this.$root.$off('merchant-shop-meta-updated', this._onMerchantShopMeta)
    this.$root.$off('merchant-admin-badges-refresh', this.scheduleNavBadgeRefresh)
    if (this._navBadgeTimer) clearTimeout(this._navBadgeTimer)
  },
  methods: {
    scheduleNavBadgeRefresh() {
      if (!this.user) return
      if (this._navBadgeTimer) clearTimeout(this._navBadgeTimer)
      this._navBadgeTimer = setTimeout(() => {
        this._navBadgeTimer = null
        this.refreshNavAttentionBadges()
      }, 120)
    },
    orderRowCancelled(o) {
      return Boolean(o && (String(o.status || '').toLowerCase() === 'cancelled' || o.cancelled_at))
    },
    orderRowPaid(o) {
      if (!o) return false
      const st = String(o.status || '').toLowerCase()
      return st === 'paid' || Boolean(o.payment_confirmed || o.paymentConfirmed)
    },
    async refreshNavAttentionBadges() {
      if (!this.user) return
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (!slug) return

      try {
        const res = await fetchAdminOrders(this.$route)
        const orders = (res && res.orders) || []
        this.navBadgeOrdersUnpaid = orders.filter((o) => !this.orderRowCancelled(o) && !this.orderRowPaid(o)).length
      } catch {
        this.navBadgeOrdersUnpaid = 0
      }

      if (!isSalonOnlyShopType(this.merchantShopKind)) {
        try {
          const products = await fetchCatalog(slug)
          this.navBadgeProductsOos = (products || []).filter(
            (p) => p && p.stock != null && Number(p.stock) === 0
          ).length
        } catch {
          this.navBadgeProductsOos = 0
        }
      } else {
        this.navBadgeProductsOos = 0
      }

      if (isSalonShopType(this.merchantShopKind)) {
        try {
          const list = await fetchAdminSalonBookings(this.$route)
          const rows = Array.isArray(list) ? list : []
          this.navBadgeBookingsEftReview = rows.filter((b) => {
            const st = String(b.status || '').toLowerCase()
            const pm = String(b.clientPaymentMethod || '').toLowerCase()
            const pv = String(b.paymentVerificationState || '').toLowerCase()
            if (st !== 'pending') return false
            if (pm === 'eft' && pv === 'manual_pending') return true
            if (pm === 'cash_store') return true
            return false
          }).length
        } catch {
          this.navBadgeBookingsEftReview = 0
        }
      } else {
        this.navBadgeBookingsEftReview = 0
      }
    },
    async refreshMerchantShopKind() {
      if (!this.user) {
        this.merchantShopKind = 'normal_store'
        return
      }
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.merchantShopKind = normalizeShopType(s.shopType)
      } catch {
        this.merchantShopKind = 'normal_store'
      }
    },
    async doLogin() {
      this.authError = ''
      this.authLoading = true
      try {
        const res = await loginWithEmailPassword(this.email, this.password)
        this.email = ''
        this.password = ''
        const sess = getSessionUser()
        this.adminSession.user = sess
        const roles = (res && res.roles) || (sess && sess.roles) || []
        if (isSupportOrPlatformOnlyUser(sess)) {
          await this.$router.replace({ name: 'support-dashboard' }).catch(() => {})
          return
        }
        await this.refreshMerchantShopKind()
        const isMerchant = roles.includes('MERCHANT_OWNER') || roles.includes('MERCHANT_STAFF')
        const slug =
          (res && res.tenant && res.tenant.slug) ||
          (res && res.merchantSlug) ||
          (getSessionUser() && getSessionUser().tenant) ||
          ''
        if (isMerchant && slug) {
          const want = String(slug).trim()
          const cur = String(this.$route.params.merchantSlug || '').trim()
          if (want && cur !== want) {
            const tail = String(this.$route.path || '').replace(/^\/m\/[^/]+/, '')
            const path = `/m/${encodeURIComponent(want)}${tail || '/admin'}`
            await this.$router.replace({ path, query: this.$route.query }).catch(() => {})
          }
        }
      } catch (e) {
        this.authError = e && e.message ? e.message : 'Sign in failed.'
      } finally {
        this.authLoading = false
      }
    }
  }
}
</script>

<style scoped>
.admin-nav-card {
  border-color: rgba(15, 23, 42, 0.08) !important;
  background: rgba(255, 255, 255, 0.96) !important;
}

.admin-nav-tab >>> .v-badge__badge {
  font-size: 0.65rem;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
}

.auth-actions {
  max-width: 280px;
  margin-left: auto;
  text-align: right;
}

.auth-actions__btn {
  width: 100%;
  justify-content: center;
}

.auth-actions__links {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  justify-content: flex-end;
}

.auth-actions__link {
  color: var(--v-tertiary-base);
  text-decoration: none;
}

.auth-actions__link:hover {
  text-decoration: underline;
}
</style>
