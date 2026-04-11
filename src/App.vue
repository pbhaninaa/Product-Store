<template>
  <v-app class="app-root">
    <v-app-bar
      v-if="!minimalChrome"
      app
      elevate-on-scroll
      color="surface"
      height="72"
      class="app-bar app-bar--blur"
    >
      <v-container class="app-bar-inner d-flex align-center py-0 fill-height">
        <router-link to="/" class="brand-link d-flex align-center text-decoration-none flex-shrink-0 mr-2">
          <div class="brand-mark mr-2 mr-sm-3" aria-hidden="true">
            <span class="brand-mark__inner" />
          </div>
          <div class="d-flex flex-column min-width-0">
            <span class="brand-name text--primary text-truncate">{{ siteName }}</span>
            <span class="brand-tagline d-none d-sm-block">Curated products</span>
          </div>
        </router-link>

        <v-spacer />

        <v-btn
          text
          rounded
          class="px-2 px-sm-3 text-none font-weight-medium flex-shrink-0"
          color="secondary"
          to="/"
          exact
          aria-label="Shop"
        >
          <v-icon :left="$vuetify.breakpoint.smAndUp" small color="secondary">store</v-icon>
          <span class="d-none d-sm-inline">Shop</span>
        </v-btn>

        <v-btn
          outlined
          rounded
          color="primary"
          class="ml-1 ml-sm-2 px-2 px-sm-3 text-none font-weight-bold flex-shrink-0"
          to="/checkout"
          aria-label="Cart"
        >
          <v-icon :left="$vuetify.breakpoint.smAndUp" small color="primary">shopping_cart</v-icon>
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
          rounded
          depressed
          color="primary"
          class="ml-1 ml-sm-2 px-2 px-sm-4 text-none font-weight-bold btn-amber flex-shrink-0"
          to="/admin"
          aria-label="Admin"
        >
          <v-icon :left="$vuetify.breakpoint.smAndUp" small color="white">admin_panel_settings</v-icon>
          <span class="d-none d-sm-inline">Admin</span>
        </v-btn>
      </v-container>
    </v-app-bar>

    <v-main class="main-shell" :class="{ 'main-shell--minimal': minimalChrome }">
      <div v-if="!minimalChrome" class="main-shell__bg" aria-hidden="true" />
      <router-view />
    </v-main>

    <v-footer v-if="!minimalChrome" padless color="transparent" class="footer-wrap">
      <v-container class="footer-inner py-8">
        <div class="d-flex flex-column flex-sm-row align-center">
          <div class="text-body-2 text--secondary">
            © {{ new Date().getFullYear() }} {{ siteName }}. All rights reserved.
          </div>
          <v-spacer />
          <div class="text-caption text--secondary mt-3 mt-sm-0">
            Crafted with Vue 2 · Supabase · Vuetify
          </div>
        </div>
      </v-container>
    </v-footer>
  </v-app>
</template>

<script>
import { getCartState } from '@/services/cart'

export default {
  name: 'App',
  computed: {
    minimalChrome() {
      return Boolean(this.$route.meta && this.$route.meta.minimalChrome)
    },
    siteName() {
      return process.env.VUE_APP_SITE_NAME || 'Product Store'
    },
    cartCount() {
      const lines = getCartState().lines
      return lines.reduce((n, l) => n + l.quantity, 0)
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
}

.app-root.v-application {
  font-family: var(--app-font) !important;
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
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(145deg, #0f172a 0%, #1e3a5f 45%, #c2410c 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 30px -12px rgba(194, 65, 12, 0.65);
}

.brand-mark__inner {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.92);
  transform: rotate(12deg);
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

.main-shell > * {
  position: relative;
  z-index: 1;
}

.main-shell--minimal {
  padding-top: 0 !important;
}

.main-shell--minimal > * {
  z-index: auto;
}

.btn-amber {
  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%) !important;
  box-shadow: 0 8px 24px -8px rgba(194, 65, 12, 0.55) !important;
}

.footer-wrap {
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(248, 250, 252, 0.9) !important;
  backdrop-filter: blur(8px);
}
</style>
