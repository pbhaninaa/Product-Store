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
              <template v-if="user && subscriptionLocked">
                {{ subscriptionLockMessage }}
              </template>
              <template v-else-if="user">
                {{ adminPageLead }}
              </template>
              <template v-else>
                Redirecting to sign in…
              </template>
            </p>
          </div>
          <div class="admin-hero__actions mt-6 mt-md-0 ml-md-auto d-flex flex-column flex-sm-row align-stretch align-sm-center" style="gap: 10px">
            <v-btn
              v-if="user"
              outlined
              color="primary"
              class="text-none font-weight-bold px-4"
              :loading="copyStoreUrlBusy"
              @click="copyStoreUrl"
            >
              <v-icon left small>link</v-icon>
              {{ copyStoreUrlDone ? 'Link copied' : 'Copy store link' }}
            </v-btn>
            <v-chip
              v-if="user"
              class="text-none font-weight-bold px-4 align-self-sm-center"
              color="primary"
              outlined
              label
            >
              <v-icon left small color="primary">cloud_done</v-icon>
              Live sync
            </v-chip>
          </div>
        </div>
      </v-container>
    </section>

    <v-container class="pb-12 pb-md-16 admin-body px-3 px-sm-4">
      <template v-if="user">
        <v-alert
          v-if="shadowSession"
          type="info"
          dense
          outlined
          class="rounded-lg mb-4"
        >
          <div class="d-flex flex-wrap align-center">
            <span class="mr-3">Shadow support session — you are viewing this store as the merchant owner.</span>
            <v-btn small color="primary" class="text-none font-weight-bold" @click="exitShadow">
              Exit shadow
            </v-btn>
          </div>
        </v-alert>
        <v-alert
          v-if="subscriptionLocked"
          type="warning"
          dense
          outlined
          class="rounded-lg mb-4"
        >
          {{ subscriptionLockMessage }}
        </v-alert>
        <v-card flat class="admin-nav-card mb-6 pa-1 rounded-xl" outlined>
          <v-tabs
            background-color="transparent"
            show-arrows
            class="admin-nav-tabs"
            :value="adminNavTabIndex"
          >
            <v-tab
              v-for="link in adminNavLinks"
              :key="link.name"
              class="text-none font-weight-bold admin-nav-tab"
              :to="link.to"
              :exact="link.exact === true"
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
                  <span class="admin-nav-tab__label">{{ link.shortLabel || link.label }}</span>
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
  exitShadowSession,
  getMerchantTenantContext,
  getSessionUser,
  isShadowSession,
  subscribeToAuth
} from '@/services/auth'
import { fetchAdminOrders, fetchAdminStoreSettings } from '@/services/adminApi'
import { fetchCatalog } from '@/services/publicStore'
import { fetchAdminSalonBookings } from '@/services/salonAdmin'
import { fetchNotificationUnreadCount } from '@/services/teamApi'
import { fetchSubscriptionStatus } from '@/services/subscriptionApi'
import { normalizeShopType, isSalonShopType, isSalonOnlyShopType } from '@/services/shopType'
import { isMerchantOwner, isMerchantStaffOnly } from '@/utils/merchantRoles'

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
      subscriptionActive: null,
      subscriptionMeta: null,
      unsubAuth: null,
      navBadgeOrdersUnpaid: 0,
      navBadgeProductsOos: 0,
      navBadgeBookingsEftReview: 0,
      navBadgeNotifications: 0,
      copyStoreUrlBusy: false,
      copyStoreUrlDone: false,
      copyStoreUrlTimer: null
    }
  },
  computed: {
    storefrontShareUrl() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (!slug || typeof window === 'undefined') return ''
      return `${window.location.origin}/${encodeURIComponent(slug)}`
    },
    user() {
      return this.adminSession.user
    },
    staffOnly() {
      return isMerchantStaffOnly(this.user)
    },
    ownerUser() {
      return isMerchantOwner(this.user)
    },
    subscriptionLocked() {
      return this.user != null && this.subscriptionActive === false
    },
    subscriptionLockMessage() {
      if (this.staffOnly) {
        return "This store's subscription is not active. Ask the store owner to renew Plan & billing to unlock admin."
      }
      const st = this.subscriptionMeta
      if (st && st.onTrial) {
        const days = Number(st.daysRemaining)
        if (Number.isFinite(days) && days > 0) {
          return `Free Trial — ${days} day${days === 1 ? '' : 's'} remaining. Open Plan & billing for details.`
        }
        return 'Free Trial active. Open Plan & billing for details.'
      }
      if (st && st.trialExpired) {
        return 'Your free trial has ended. Open Plan & billing and pay with Peach (card or Instant EFT) to unlock admin.'
      }
      return 'Your subscription is not active. Open Plan & billing to choose a plan and pay with Peach to unlock admin.'
    },
    shadowSession() {
      return isShadowSession()
    },
    adminPageTitle() {
      const r = this.$route.matched
        .slice()
        .reverse()
        .find((x) => x.meta && x.meta.adminTitle)
      if (this.$route.name === 'merchant-admin-subscription' && this.subscriptionMeta && this.subscriptionMeta.onTrial) {
        return 'Plan & billing'
      }
      return (r && r.meta.adminTitle) || 'Admin'
    },
    adminPageLead() {
      if (this.staffOnly) {
        const r = this.$route.matched
          .slice()
          .reverse()
          .find((x) => x.meta && x.meta.adminLead)
        return (
          (r && r.meta.adminLead) ||
          'Day-to-day work — orders, bookings, alerts, and your income.'
        )
      }
      if (this.$route.name === 'merchant-admin-subscription') {
        const st = this.subscriptionMeta
        if (st && st.onTrial) {
          const days = Number(st.daysRemaining)
          const daysText =
            Number.isFinite(days) && days > 0
              ? `${days} day${days === 1 ? '' : 's'} remaining`
              : 'ending soon'
          return `Free Trial — ${daysText}. Full access until trial end (UTC). Peach payment only after expiry.`
        }
        if (st && st.trialExpired && !st.valid) {
          return 'Your free trial has ended. Choose a plan and renew securely with Peach Hosted Checkout.'
        }
        if (st && st.valid) {
          return 'Your plan is active. Upgrade anytime; renewals use Peach Hosted Checkout.'
        }
        return 'Choose a plan and pay with Peach (card or Instant EFT) to unlock admin.'
      }
      const r = this.$route.matched
        .slice()
        .reverse()
        .find((x) => x.meta && x.meta.adminLead)
      return (
        (r && r.meta.adminLead) ||
        'Use the tabs below for daily work, or open Settings to configure your store.'
      )
    },
    adminNavLinks() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (this.subscriptionLocked) {
        if (this.staffOnly) {
          return [
            {
              name: 'merchant-admin',
              to: { name: 'merchant-admin', params: { merchantSlug: slug } },
              label: 'Dashboard',
              icon: 'dashboard',
              badgeCount: 0,
              exact: true
            }
          ]
        }
        return [
          {
            name: 'merchant-admin-subscription',
            to: { name: 'merchant-admin-subscription', params: { merchantSlug: slug } },
            label: 'Plan',
            icon: 'card_membership',
            badgeCount: 0,
            exact: true
          },
          {
            name: 'merchant-admin-help',
            to: { name: 'merchant-admin-help', params: { merchantSlug: slug } },
            label: 'Help',
            icon: 'help_outline',
            badgeCount: 0,
            exact: true
          }
        ]
      }
      const links = [
        {
          name: 'merchant-admin',
          to: { name: 'merchant-admin', params: { merchantSlug: slug } },
          label: 'Dashboard',
          icon: 'dashboard',
          badgeCount: 0,
          exact: true
        }
      ]
      if (!isSalonOnlyShopType(this.merchantShopKind)) {
        links.push({
          name: 'merchant-admin-orders',
          to: { name: 'merchant-admin-orders', params: { merchantSlug: slug } },
          label: 'Orders',
          icon: 'receipt_long',
          badgeCount: this.navBadgeOrdersUnpaid,
          exact: true
        })
      }
      if (isSalonShopType(this.merchantShopKind)) {
        links.push({
          name: 'merchant-admin-salon-bookings',
          to: { name: 'merchant-admin-salon-bookings', params: { merchantSlug: slug } },
          label: 'Bookings',
          icon: 'event_note',
          badgeCount: this.navBadgeBookingsEftReview,
          exact: true
        })
      }
      links.push({
        name: 'merchant-admin-notifications',
        to: { name: 'merchant-admin-notifications', params: { merchantSlug: slug } },
        label: 'Alerts',
        icon: 'notifications',
        badgeCount: this.navBadgeNotifications,
        exact: true
      })
      if (this.staffOnly) {
        links.push({
          name: 'merchant-admin-my-income',
          to: { name: 'merchant-admin-my-income', params: { merchantSlug: slug } },
          label: 'My income',
          icon: 'savings',
          badgeCount: 0,
          exact: true
        })
      } else if (this.ownerUser) {
        links.push({
          name: 'merchant-admin-settings',
          to: { name: 'merchant-admin-settings', params: { merchantSlug: slug } },
          label: 'Settings',
          icon: 'settings',
          badgeCount: this.navBadgeProductsOos,
          exact: false,
          settingsHub: true
        })
      }
      return links
    },
    adminNavTabIndex() {
      const links = this.adminNavLinks
      const name = this.$route.name
      const onSettingsChild = this.$route.matched.some((r) => r.meta && r.meta.adminSettingsChild)
      for (let i = 0; i < links.length; i++) {
        const link = links[i]
        if (link.settingsHub && (name === 'merchant-admin-settings' || onSettingsChild)) {
          return i
        }
        if (link.name === name) return i
      }
      return 0
    }
  },
  watch: {
    user(u) {
      if (!u) {
        this.merchantShopKind = 'normal_store'
        this.subscriptionActive = null
        this.subscriptionMeta = null
        this.redirectToLogin()
        return
      }
      this.refreshMerchantShopKind()
      this.refreshSubscriptionGate()
    },
    '$route.params.merchantSlug'() {
      if (this.user) {
        this.refreshMerchantShopKind()
        this.refreshSubscriptionGate()
      }
    },
    '$route.name'() {
      this.enforceSubscriptionRoute()
    },
    subscriptionActive() {
      this.enforceSubscriptionRoute()
    }
  },
  created() {
    this.unsubAuth = subscribeToAuth((u) => {
      this.adminSession.user = u
    })
    this._onMerchantShopMeta = (payload) => {
      this.merchantShopKind = normalizeShopType(payload && payload.shopType)
    }
    this._onSubscriptionChanged = () => {
      this.refreshSubscriptionGate()
    }
    this.$root.$on('merchant-shop-meta-updated', this._onMerchantShopMeta)
    this.$root.$on('merchant-admin-badges-refresh', this.scheduleNavBadgeRefresh)
    this.$root.$on('admin-notifications-changed', this.scheduleNavBadgeRefresh)
    this.$root.$on('merchant-subscription-updated', this._onSubscriptionChanged)
    if (!this.user && !getSessionUser()) {
      this.redirectToLogin()
      return
    }
    if (this.user) {
      this.scheduleNavBadgeRefresh()
      this.refreshSubscriptionGate()
    }
  },
  beforeDestroy() {
    if (this.copyStoreUrlTimer) {
      window.clearTimeout(this.copyStoreUrlTimer)
      this.copyStoreUrlTimer = null
    }
    if (this.unsubAuth) this.unsubAuth()
    this.$root.$off('merchant-shop-meta-updated', this._onMerchantShopMeta)
    this.$root.$off('merchant-admin-badges-refresh', this.scheduleNavBadgeRefresh)
    this.$root.$off('admin-notifications-changed', this.scheduleNavBadgeRefresh)
    this.$root.$off('merchant-subscription-updated', this._onSubscriptionChanged)
    if (this._navBadgeTimer) clearTimeout(this._navBadgeTimer)
  },
  methods: {
    redirectToLogin() {
      const slug = String(this.$route.params.merchantSlug || '').trim()
      this.$router
        .replace({
          name: 'login',
          query: {
            redirect: this.$route.fullPath,
            ...(slug ? { m: slug } : {})
          }
        })
        .catch(() => {})
    },
    exitShadow() {
      const ok = exitShadowSession()
      if (ok) {
        this.$router.replace({ name: 'support-shadow' }).catch(() => {})
      } else {
        this.redirectToLogin()
      }
    },
    enforceSubscriptionRoute() {
      if (!this.subscriptionLocked) return
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (!slug) return
      if (this.staffOnly) {
        if (this.$route.name === 'merchant-admin') return
        this.$router.replace({ name: 'merchant-admin', params: { merchantSlug: slug } }).catch(() => {})
        return
      }
      if (
        this.$route.name === 'merchant-admin-subscription' ||
        this.$route.name === 'merchant-admin-help'
      ) {
        return
      }
      this.$router
        .replace({ name: 'merchant-admin-subscription', params: { merchantSlug: slug } })
        .catch(() => {})
    },
    async refreshSubscriptionGate() {
      if (!this.user) {
        this.subscriptionActive = null
        this.subscriptionMeta = null
        return
      }
      try {
        const st = await fetchSubscriptionStatus(this.$route)
        this.subscriptionMeta = st || null
        this.subscriptionActive = Boolean(st && st.valid)
      } catch {
        // Fail closed: treat as locked until status loads successfully.
        this.subscriptionMeta = null
        this.subscriptionActive = false
      }
      this.enforceSubscriptionRoute()
      this.scheduleNavBadgeRefresh()
    },
    scheduleNavBadgeRefresh() {
      if (!this.user || this.subscriptionLocked) return
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
      if (!this.user || this.subscriptionLocked) return
      const slug = String(this.$route.params.merchantSlug || '').trim()
      if (!slug) return

      try {
        const res = await fetchAdminOrders(this.$route)
        const orders = (res && res.orders) || []
        this.navBadgeOrdersUnpaid = orders.filter((o) => !this.orderRowCancelled(o) && !this.orderRowPaid(o)).length
      } catch {
        this.navBadgeOrdersUnpaid = 0
      }

      if (!this.staffOnly && !isSalonOnlyShopType(this.merchantShopKind)) {
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

      try {
        const res = await fetchNotificationUnreadCount(this.$route)
        this.navBadgeNotifications = Number(res && res.count) || 0
      } catch {
        this.navBadgeNotifications = 0
      }
    },
    async refreshMerchantShopKind() {
      if (!this.user) {
        this.merchantShopKind = 'normal_store'
        return
      }
      const ctx = getMerchantTenantContext()
      if (ctx) {
        this.merchantShopKind = normalizeShopType(ctx.shopType)
      }
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.merchantShopKind = normalizeShopType(s && s.shopType)
      } catch {
        if (!ctx) this.merchantShopKind = 'normal_store'
      }
    },
    async copyStoreUrl() {
      const url = this.storefrontShareUrl
      if (!url || this.copyStoreUrlBusy) return
      this.copyStoreUrlBusy = true
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(url)
        } else {
          const ta = document.createElement('textarea')
          ta.value = url
          ta.setAttribute('readonly', '')
          ta.style.position = 'fixed'
          ta.style.left = '-9999px'
          document.body.appendChild(ta)
          ta.select()
          document.execCommand('copy')
          document.body.removeChild(ta)
        }
        this.copyStoreUrlDone = true
        if (this.copyStoreUrlTimer) window.clearTimeout(this.copyStoreUrlTimer)
        this.copyStoreUrlTimer = window.setTimeout(() => {
          this.copyStoreUrlDone = false
          this.copyStoreUrlTimer = null
        }, 2200)
      } catch {
        window.prompt('Copy this store link for your clients:', url)
      } finally {
        this.copyStoreUrlBusy = false
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

.admin-nav-tabs >>> .v-tab {
  min-width: 0;
  padding-left: 10px;
  padding-right: 10px;
}

@media (max-width: 599px) {
  .admin-nav-tabs >>> .v-tab {
    padding-left: 8px;
    padding-right: 8px;
    font-size: 0.8125rem;
  }

  .admin-nav-tab >>> .v-icon {
    margin-right: 2px !important;
  }
}
</style>

