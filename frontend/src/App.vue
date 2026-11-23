<template>
  <v-app class="app-root">
    <v-banner
      v-if="apiOffline"
      sticky
      single-line
      color="error"
      dark
      class="api-health-banner"
      icon="cloud_off"
    >
      <span class="text-body-2">
        Cannot reach the API
        <span class="d-none d-sm-inline">({{ apiBaseShown }})</span>.
        Check your connection or API URL.
      </span>
      <template #actions>
        <v-btn text class="text-none" :loading="apiHealthChecking" @click="runApiHealthCheck">
          Retry
        </v-btn>
      </template>
    </v-banner>

    <v-app-bar
      v-if="!minimalChrome"
      app
      elevate-on-scroll
      color="surface"
      height="72"
      class="app-bar app-bar--blur"
    >
      <v-container class="app-bar-inner d-flex flex-nowrap align-center py-0 fill-height">
        <router-link
          :to="brandHomeTo"
          class="brand-link d-flex align-center text-decoration-none mr-2"
        >
          <div class="brand-mark mr-2 mr-sm-3" aria-hidden="true">
            <img
              v-if="shopDisplay.logoUrl"
              :src="shopDisplay.logoUrl"
              alt=""
              class="brand-mark__img"
            />
            <span v-else class="brand-mark__inner" />
          </div>
          <div class="d-flex flex-column min-width-0">
            <span class="brand-name text--primary text-truncate">{{ siteName }}</span>
            <span class="brand-tagline d-none d-sm-block">{{ brandTagline }}</span>
          </div>
        </router-link>

        <v-spacer />

        <!-- Marketplace (hub, directory, client account): Hub only for signed-in clients; Providers for everyone. -->
        <template v-if="showClientNav">
          <v-btn
            v-if="clientSignedIn"
            text
            rounded
            exact
            class="px-2 px-sm-3 text-none font-weight-medium flex-shrink-0"
            color="secondary"
            :to="{ name: 'client-hub' }"
          >
            <v-icon :left="$vuetify.breakpoint.smAndUp" small>home</v-icon>
            <span class="d-none d-sm-inline">Hub</span>
          </v-btn>
          <v-btn
            text
            rounded
            exact
            class="px-2 px-sm-3 text-none font-weight-medium flex-shrink-0"
            color="secondary"
            :to="{ name: 'provider-directory' }"
          >
            <v-icon :left="$vuetify.breakpoint.smAndUp" small>storefront</v-icon>
            <span class="d-none d-sm-inline">Providers</span>
          </v-btn>
          <v-btn
            v-if="showAccountNav"
            text
            rounded
            exact
            class="px-2 px-sm-3 text-none font-weight-medium flex-shrink-0"
            color="secondary"
            :to="accountNavTo"
          >
            <v-icon :left="$vuetify.breakpoint.smAndUp" small>{{ accountNavIcon }}</v-icon>
            <span class="d-none d-sm-inline">{{ accountNavLabel }}</span>
          </v-btn>
        </template>

        <v-btn
          v-if="showClientHubShortcut"
          text
          rounded
          class="px-2 px-sm-3 text-none font-weight-medium flex-shrink-0"
          color="secondary"
          :to="{ name: 'provider-directory' }"
        >
          <v-icon :left="$vuetify.breakpoint.smAndUp" small>explore</v-icon>
          <span class="d-none d-sm-inline">Find providers</span>
        </v-btn>

        <v-btn
          v-if="showCartInNav && storeSlug"
          outlined
          rounded
          color="primary"
          class="ml-1 ml-sm-2 px-2 px-sm-3 text-none font-weight-bold flex-shrink-0"
          :to="storePath('checkout')"
          aria-label="Product cart and checkout"
        >
          <v-icon :left="$vuetify.breakpoint.smAndUp" small color="primary">shopping_bag</v-icon>
          <span class="d-none d-sm-inline">Cart</span>
          <v-chip
            v-if="cartCount > 0"
            x-small
            color="primary"
            text-color="white"
            class="ml-0 ml-sm-2 px-2 font-weight-bold"
          >
            {{ cartCount }}
          </v-chip>
        </v-btn>

        <v-btn
          v-if="showPublicNav"
          text
          rounded
          class="px-2 px-sm-3 text-none font-weight-medium flex-shrink-0"
          color="secondary"
          :to="accountNavTo"
        >
          <v-icon :left="$vuetify.breakpoint.smAndUp" small>{{ accountNavIcon }}</v-icon>
          <span class="d-none d-sm-inline">{{ accountNavLabel }}</span>
        </v-btn>
      </v-container>
    </v-app-bar>

    <v-main
      class="main-shell"
      :class="{
        'main-shell--minimal': minimalChrome,
        'main-shell--workspace': isAdminRoute || isSupportRoute,
      }"
    >
      <div v-if="!minimalChrome" class="main-shell__bg" aria-hidden="true" />
      <router-view />
    </v-main>

    <v-footer v-if="showFooter" padless color="transparent" class="footer-wrap">
      <v-container class="footer-inner py-8">
        <div class="d-flex flex-column flex-md-row align-center flex-wrap footer-row">
          <div class="text-body-2 text--secondary text-center text-md-left">
            © {{ new Date().getFullYear() }} {{ siteName }}. All rights reserved.
          </div>
          <v-spacer class="d-none d-md-flex" />
          <div
            class="d-flex flex-column flex-sm-row align-center justify-center mt-4 mt-md-0 footer-links"
          >
            <router-link
              v-if="!isWorkspaceRoute && $route.name !== 'provider-directory'"
              :to="{ name: 'provider-directory' }"
              class="footer-contact-link text-body-2 font-weight-medium mb-2 mb-sm-0"
            >
              All providers
            </router-link>
            <span
              v-if="!isWorkspaceRoute && $route.name !== 'provider-directory' && (showPublicNav && (storeSlug || helpPath))"
              class="footer-link-sep text--secondary d-none d-sm-inline mx-sm-4"
              aria-hidden="true"
            >
              ·
            </span>
            <router-link
              v-if="showPublicNav && storeSlug"
              :to="storePath('contact')"
              class="footer-contact-link text-body-2 font-weight-medium mb-2 mb-sm-0"
            >
              Contact us
            </router-link>
            <span
              v-if="showPublicNav && storeSlug"
              class="footer-link-sep text--secondary d-none d-sm-inline mx-sm-4"
              aria-hidden="true"
            >
              ·
            </span>
            <router-link
              v-if="showPublicNav && storeSlug"
              :to="storePath('track')"
              class="footer-contact-link text-body-2 font-weight-medium mb-2 mb-sm-0"
            >
              Track order
            </router-link>
            <span
              v-if="showPublicNav && storeSlug"
              class="footer-link-sep text--secondary d-none d-sm-inline mx-sm-4"
              aria-hidden="true"
            >
              ·
            </span>
            <router-link
              v-if="showPublicNav && helpPath"
              :to="helpPath"
              class="footer-contact-link text-body-2 font-weight-medium mb-2 mb-sm-0"
            >
              Help
            </router-link>
          </div>
        </div>
      </v-container>
    </v-footer>
  </v-app>
</template>

<script>
import { getCartState } from '@/services/cart'
import { fetchShopSettings } from '@/services/publicStore'
import { checkApiHealth, apiBaseLabel } from '@/services/health'
import { getSessionUser, isClientUser, isSupportOrPlatformOnlyUser, subscribeToAuth } from '@/services/auth'
import { connectNotificationsRealtime, disconnectNotificationsRealtime } from '@/services/notificationsRealtime'
import { installNotificationSound, playNotificationSound } from '@/services/notificationSound'
import { merchantSlugFromRoute, isPublicStorefrontSlug } from '@/utils/merchantSlug'
import {
  isSalonOnlyShopType,
  normalizeShopType,
  SHOP_NORMAL,
  SHOP_SALON_AND_STORE,
  SHOP_SALON_ONLY
} from '@/services/shopType'

export default {
  name: 'App',
  data() {
    return {
      apiOffline: false,
      apiHealthChecking: false,
      apiBaseShown: apiBaseLabel(),
      /** Public shop name + branding URLs (Admin → Store Branding). */
      shopDisplay: {
        storeName: '',
        logoUrl: '',
        heroUrl: '',
        salonEnabled: false,
        /** @type {string} normalized: normal_store | salon_only | salon_and_store */
        shopType: SHOP_NORMAL
      },
      sessionTick: 0
    }
  },
  provide() {
    return {
      shopDisplay: this.shopDisplay
    }
  },
  computed: {
    minimalChrome() {
      return this.$route.matched.some((r) => r.meta && r.meta.minimalChrome)
    },
    /** True when URL is under `/admin` — share only non-admin links with customers so they never see the Admin control on shop/checkout. */
    isAdminRoute() {
      return (this.$route.path || '').includes('/admin')
    },
    isSupportRoute() {
      const p = this.$route.path || ''
      return p === '/support' || p.startsWith('/support/')
    },
    /**
     * The public site footer is for shoppers. In the merchant admin and support console the sidebar runs the
     * full height of the window, and a footer underneath it would cut it off, so it is left out there.
     */
    showFooter() {
      return !this.minimalChrome && !this.isAdminRoute && !this.isSupportRoute
    },
    /**
     * Merchant admin and the support console: the sidebar is the navigation, so the top bar is the brand only
     * (no storefront Shop/Cart/Sign in), and the brand returns to the workspace's own dashboard.
     */
    isWorkspaceRoute() {
      return this.isAdminRoute || this.isSupportRoute
    },
    isClientMarketplaceRoute() {
      const n = this.$route.name
      return (
        n === 'client-hub' ||
        n === 'client-discover' ||
        n === 'provider-directory' ||
        n === 'client-login' ||
        n === 'client-register' ||
        n === 'client-history'
      )
    },
    /** Merchant signup / password reset — brand only, no store Shop/Cart/Sign in. */
    isAuthLandingRoute() {
      const n = this.$route.name
      return n === 'merchant-signup' || n === 'forgot-password' || n === 'reset-password'
    },
    showClientNav() {
      return this.isClientMarketplaceRoute && !this.isWorkspaceRoute
    },
    /** No "Sign in" button on the sign-in / register page itself. */
    showAccountNav() {
      const n = this.$route.name
      return n !== 'client-login' && n !== 'client-register'
    },
    clientSignedIn() {
      void this.sessionTick
      return isClientUser(getSessionUser())
    },
    showClientHubShortcut() {
      void this.sessionTick
      return this.showPublicNav && isClientUser(getSessionUser())
    },
    /** Cart appears only once something is in it: never an empty cart button. */
    showCartInNav() {
      if (this.isWorkspaceRoute || this.isClientMarketplaceRoute || this.isAuthLandingRoute) return false
      if (this.cartCount <= 0) return false
      return !isSalonOnlyShopType(this.shopDisplay.shopType)
    },
    merchantSlugNav() {
      return merchantSlugFromRoute(this.$route)
    },
    brandHomeTo() {
      if (this.isAuthLandingRoute) {
        return this.$route.path || '/signup'
      }
      if (this.isSupportRoute) return { name: 'support-dashboard' }
      if (this.isAdminRoute && this.merchantSlugNav) {
        return { name: 'merchant-admin', params: { merchantSlug: this.merchantSlugNav } }
      }
      if (this.isClientMarketplaceRoute) {
        return { name: 'provider-directory' }
      }
      // The store name opens the store page, which shows everything it offers (filters under the hero).
      return { name: 'merchant-home', params: { merchantSlug: this.merchantSlugNav } }
    },
    brandTagline() {
      if (this.isAuthLandingRoute) return 'Business owners'
      if (this.isSupportRoute) return 'Platform console'
      if (this.isAdminRoute) return 'Store admin'
      if (this.isClientMarketplaceRoute) return 'Items & services near you'
      const t = normalizeShopType(this.shopDisplay.shopType)
      if (t === SHOP_SALON_ONLY) return 'Services & bookings'
      if (t === SHOP_SALON_AND_STORE) return 'Items sold & services'
      return 'Items sold'
    },
    showPublicNav() {
      return !this.isWorkspaceRoute && !this.isClientMarketplaceRoute && !this.isAuthLandingRoute
    },
    /** Store in context for footer/nav links: the /m/<slug> path, or ?merchant= on payment return pages. */
    storeSlug() {
      const fromPath = merchantSlugFromRoute(this.$route)
      const slug = fromPath || String((this.$route.query && this.$route.query.merchant) || '').trim()
      return isPublicStorefrontSlug(slug) ? slug : ''
    },
    /** Store help centre; there is no store-agnostic help route, so the link is hidden without a store. */
    helpPath() {
      return this.storeSlug ? this.storePath('help') : null
    },
    siteName() {
      if (this.isClientMarketplaceRoute || this.isAuthLandingRoute) {
        return process.env.VUE_APP_SITE_NAME || 'Product Store'
      }
      const fromDb = String(this.shopDisplay.storeName || '').trim()
      if (fromDb.length >= 2) return fromDb
      return process.env.VUE_APP_SITE_NAME || 'Product Store'
    },
    cartCount() {
      const lines = getCartState().lines
      return lines.reduce((n, l) => n + l.quantity, 0)
    },
    /** Who is signed in decides where the account button goes: client account, own store admin, or console. */
    accountNav() {
      void this.sessionTick
      const u = getSessionUser()
      if (isClientUser(u)) return { to: { name: 'client-history' }, label: 'Account', icon: 'person' }
      const roles = (u && u.roles) || []
      const tenant = u && typeof u.tenant === 'string' ? u.tenant.trim() : ''
      if (tenant && (roles.includes('MERCHANT_OWNER') || roles.includes('MERCHANT_STAFF'))) {
        return { to: { name: 'merchant-admin', params: { merchantSlug: tenant } }, label: 'My store', icon: 'dashboard' }
      }
      if (isSupportOrPlatformOnlyUser(u)) {
        return { to: { name: 'support-dashboard' }, label: 'Console', icon: 'support_agent' }
      }
      return { to: { name: 'client-login' }, label: 'Sign in', icon: 'person' }
    },
    accountNavTo() {
      return this.accountNav.to
    },
    accountNavLabel() {
      return this.accountNav.label
    },
    accountNavIcon() {
      return this.accountNav.icon
    }
  },
  watch: {
    '$route.path'() {
      this.loadShopDisplayFromSettings()
    },
    siteName: {
      immediate: true,
      handler(name) {
        document.title = name || 'Product Store'
      }
    }
  },
  async created() {
    installNotificationSound()
    await this.runApiHealthCheck()
    await this.loadShopDisplayFromSettings()
    this._shopSettingsListener = () => {
      this.loadShopDisplayFromSettings()
    }
    this.$root.$on('shop-settings-updated', this._shopSettingsListener)
    this._unsubAuth = subscribeToAuth(() => {
      this.sessionTick += 1
      this.bindRealtime()
    })
    this.bindRealtime()
  },
  beforeDestroy() {
    if (this._shopSettingsListener) {
      this.$root.$off('shop-settings-updated', this._shopSettingsListener)
    }
    if (this._unsubAuth) this._unsubAuth()
    disconnectNotificationsRealtime()
  },
  methods: {
    storePath(section) {
      return `/m/${encodeURIComponent(this.storeSlug)}/${section}`
    },
    bindRealtime() {
      const u = getSessionUser()
      if (u) {
        // No-op while the token is unchanged, so the 60s auth tick does not reconnect.
        connectNotificationsRealtime({
          onNotification: (payload) => {
            this.$root.$emit('notification-received', payload)
            playNotificationSound(payload)
            if (payload && payload.tenantSlug) this.$root.$emit('merchant-admin-badges-refresh')
          },
          onResync: () => {
            this.$root.$emit('notifications-resync')
            this.$root.$emit('merchant-admin-badges-refresh')
          }
        })
      } else {
        disconnectNotificationsRealtime()
      }
    },
    async runApiHealthCheck() {
      this.apiHealthChecking = true
      this.apiBaseShown = apiBaseLabel()
      try {
        await checkApiHealth()
        this.apiOffline = false
      } catch {
        this.apiOffline = true
      } finally {
        this.apiHealthChecking = false
      }
    },
    async loadShopDisplayFromSettings() {
      if (this.isClientMarketplaceRoute || this.isAuthLandingRoute) {
        this.clearShopDisplay()
        return
      }
      const slug = merchantSlugFromRoute(this.$route)
      try {
        if (!isPublicStorefrontSlug(slug)) {
          this.clearShopDisplay()
          return
        }
        const s = await fetchShopSettings(slug)
        this.shopDisplay.storeName = s.storeName || ''
        this.shopDisplay.logoUrl = s.storeLogoUrl || ''
        this.shopDisplay.heroUrl = s.storeHeroUrl || ''
        this.shopDisplay.salonEnabled = Boolean(s.salonEnabled)
        this.shopDisplay.shopType = normalizeShopType(s.shopType)
      } catch (e) {
        this.clearShopDisplay()
        const msg = String((e && (e.code || e.message)) || '')
        const missing =
          msg.includes('tenant_not_found') ||
          msg.includes('merchant_not_found') ||
          Number(e && e.status) === 404
        const onAdmin = (this.$route.path || '').includes('/admin')
        if (missing && !onAdmin && isClientUser(getSessionUser())) {
          this.$router.replace('/').catch(() => {})
          return
        }
        // No seeded store for this public /m/:slug URL — send guests to merchant signup.
        if (missing && !onAdmin) {
          this.$router
            .replace({ name: 'merchant-signup', query: { missing: slug || undefined } })
            .catch(() => {})
        }
      }
    },
    clearShopDisplay() {
      this.shopDisplay.storeName = ''
      this.shopDisplay.logoUrl = ''
      this.shopDisplay.heroUrl = ''
      this.shopDisplay.salonEnabled = false
      this.shopDisplay.shopType = SHOP_NORMAL
    }
  }
}
</script>

<style>
:root {
  --app-font: 'Plus Jakarta Sans', 'Segoe UI', system-ui, sans-serif;
}

html {
  scroll-behavior: smooth;
  overflow-x: hidden;
}

/*
 * `clip` (not `hidden`): with both html and body set to hidden, body became its own scroll box, so the
 * window never scrolled and pages opened at the previous page's offset.
 */
body {
  overflow-x: clip;
}

.app-root.v-application {
  font-family: var(--app-font) !important;
}

/*
 * Admin / support workspaces: the sidebar is pinned to the left edge on desktop (WorkspaceSidebar sets
 * --ws-sidebar-width and the has-ws-sidebar class), so the page and footer start beside it.
 * !important beats the padding Vuetify writes inline on v-main.
 */
.has-ws-sidebar .main-shell,
.has-ws-sidebar .footer-wrap {
  padding-left: var(--ws-sidebar-width, 0px) !important;
  transition: padding-left 0.2s ease;
}

.app-root .text-h4,
.app-root .text-h5,
.app-root .text-h6,
.app-root .text-subtitle-1 {
  font-family: var(--app-font) !important;
  letter-spacing: -0.02em;
}

.app-bar--blur {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06) !important;
  backdrop-filter: blur(12px);
  background-color: rgba(255, 255, 255, 0.82) !important;
}

.brand-link {
  color: inherit !important;
  /* Never narrower than the 40px logo, so nav buttons cannot slide over it. */
  min-width: 40px;
  flex: 1 1 auto;
  max-width: calc(100% - 140px);
}

/* Phone: icon-only nav buttons drop Vuetify's 64px minimum so five of them fit beside the logo. */
@media (max-width: 599px) {
  .app-bar-inner .v-btn:not(.v-btn--icon) {
    min-width: 40px !important;
    padding: 0 8px !important;
  }
}

.min-width-0 {
  min-width: 0;
}

.app-bar-inner {
  max-width: 100%;
  padding-left: 12px !important;
  padding-right: 12px !important;
}

@media (min-width: 600px) {
  .app-bar-inner {
    padding-left: 16px !important;
    padding-right: 16px !important;
  }
}

.brand-name {
  font-size: clamp(0.95rem, 2.8vw, 1.125rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.brand-tagline {
  font-size: 0.6875rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: rgba(15, 23, 42, 0.45);
}

.brand-mark {
  position: relative;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  min-width: 40px;
  min-height: 40px;
  max-width: 40px;
  max-height: 40px;
  border-radius: 12px;
  background: linear-gradient(145deg, #0f172a 0%, #1e3a5f 45%, #c2410c 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 30px -12px rgba(194, 65, 12, 0.65);
  overflow: hidden;
}

.brand-mark__inner {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.92);
  transform: rotate(12deg);
}

/* Any source size/aspect: scales to fit the 40×40 box without overflowing */
.brand-mark__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  object-position: center;
  display: block;
}

.main-shell {
  position: relative;
  min-height: 100%;
  background: transparent !important;
}

.main-shell__bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(1200px 600px at 10% -10%, rgba(234, 88, 12, 0.08), transparent 55%),
    radial-gradient(900px 500px at 95% 10%, rgba(30, 58, 95, 0.12), transparent 50%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

/*
 * The page itself must sit above the decorative background. v-main wraps both in `.v-main__wrap`,
 * so `.main-shell > *` alone left the routed page un-positioned and the opaque background painted
 * over any section without its own positioning (the checkout header showed as an empty band).
 */
.main-shell > *,
.main-shell > .v-main__wrap > :not(.main-shell__bg) {
  position: relative;
  z-index: 1;
}

.main-shell--minimal {
  padding-top: 0 !important;
}

.main-shell--minimal > *,
.main-shell--minimal > .v-main__wrap > :not(.main-shell__bg) {
  z-index: auto;
}

/*
 * Admin / support pages hold the workspace sidebar, which on phones is a temporary drawer. Vuetify puts
 * that drawer's dimming overlay at the app root, so `z-index: 1` here would trap the drawer below its own
 * overlay and every menu tap would hit the overlay. `auto` keeps the page above the background (it is
 * positioned and comes after it) without creating that stacking context.
 */
.main-shell--workspace > *,
.main-shell--workspace > .v-main__wrap > :not(.main-shell__bg) {
  z-index: auto;
}

.api-health-banner {
  z-index: 10;
}

/*
 * App-wide button look, taken from the provider directory's "Choose provider" button (MerchantCard):
 * pill shape, sentence-case label, bold label on filled and outlined buttons, and the amber gradient on
 * every filled primary button. Text buttons keep the medium weight they have in the directory.
 * Icon / fab buttons (`v-btn--round`) keep their circle, and disabled buttons keep Vuetify's grey look.
 * Utility classes (font-weight-*, text-uppercase) still override this per button.
 */
.app-root .v-btn {
  text-transform: none;
}

.app-root .v-btn:not(.v-btn--round):not(.v-btn--tile) {
  border-radius: 28px;
}

.app-root .v-btn:not(.v-btn--text):not(.v-btn--plain):not(.v-btn--round) {
  font-weight: 700;
}

.btn-amber:not(.v-btn--disabled),
.app-root .v-btn.v-btn--has-bg.primary:not(.v-btn--disabled):not(.v-btn--round) {
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  box-shadow: 0 8px 24px -8px rgba(194, 65, 12, 0.55) !important;
}

.btn-amber {
  max-width: fit-content;
}

/* Segmented toggles: the group is the pill; inner buttons keep square joins (Vuetify's own pattern). */
.app-root .v-btn-toggle:not(.v-btn-toggle--group) {
  border-radius: 28px;
}

.app-root .v-btn-toggle > .v-btn.v-btn:not(.v-btn--round) {
  border-radius: 0;
}

.app-root .v-btn-toggle > .v-btn.v-btn:not(.v-btn--round):first-child {
  border-top-left-radius: inherit;
  border-bottom-left-radius: inherit;
}

.app-root .v-btn-toggle > .v-btn.v-btn:not(.v-btn--round):last-child {
  border-top-right-radius: inherit;
  border-bottom-right-radius: inherit;
}

/*
 * Storefront card grids: cards in the same row share one width (same v-col breakpoints) and one
 * height. Put `card-grid` on the v-row; each v-col becomes a flex box and the card inside it uses
 * `equal-card` (fills the column), `equal-card__body` (grows) and `equal-card__actions` (pinned to
 * the bottom). Heights line up because text is clamped and actions are pinned, not padded out.
 */
.card-grid > [class*='col'] {
  display: flex;
}

.equal-card {
  display: flex !important;
  flex-direction: column;
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
}

.equal-card__body {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
}

.equal-card__actions {
  margin-top: auto !important;
}

/* Single-column reading pages (history, promotions): no wide empty band beside short rows on desktop. */
.page-narrow {
  max-width: 960px !important;
}

.clamp-1,
.clamp-2,
.clamp-3 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.clamp-1 {
  -webkit-line-clamp: 1;
}

.clamp-2 {
  -webkit-line-clamp: 2;
}

.clamp-3 {
  -webkit-line-clamp: 3;
}

.footer-wrap {
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(248, 250, 252, 0.9) !important;
  backdrop-filter: blur(8px);
}

.footer-contact-link {
  color: rgba(15, 23, 42, 0.72) !important;
  text-decoration: none !important;
  letter-spacing: 0.02em;
}

.footer-contact-link:hover {
  color: #c2410c !important;
  text-decoration: underline !important;
}

/* Unified alerts — Product Store design system */
.app-root .v-alert {
  border-radius: 12px !important;
  font-family: var(--app-font) !important;
  letter-spacing: -0.01em;
  font-size: 0.875rem !important;
  line-height: 1.5 !important;
}

.app-root .v-alert--outlined {
  background-color: rgba(255, 255, 255, 0.96) !important;
  box-shadow: none !important;
}

.app-root .v-alert--dense .v-alert__wrapper {
  align-items: flex-start;
}

.app-root .v-alert .v-alert__icon {
  margin-top: 1px;
}

.app-root .v-alert .v-alert__content {
  font-weight: 500;
}

.app-root .v-banner.error {
  font-family: var(--app-font) !important;
}

.app-root .v-snackbar__wrapper {
  border-radius: 12px !important;
  font-family: var(--app-font) !important;
  background-color: #0f172a !important;
  min-width: 0 !important;
  max-width: min(420px, calc(100vw - 24px)) !important;
  padding: 4px 8px !important;
}
</style>
