<template>
  <div class="admin-layout-root">
    <section class="admin-hero">
      <v-container class="py-10 py-md-12">
        <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
          <div>
            <div class="admin-kicker mb-2">
              <v-icon small color="white" class="mr-1">lock</v-icon>
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
            color="white"
            outlined
            label
          >
            <v-icon left small color="white">cloud_done</v-icon>
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
          <v-card class="admin-card admin-login-card pa-5 pa-sm-8" elevation="3" rounded="xl">
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

            <v-btn
              block
              x-large
              depressed
              class="mt-6 text-none font-weight-bold btn-admin-primary"
              :loading="authLoading"
              @click="doLogin"
            >
              Sign in
            </v-btn>

            <v-alert v-if="authError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ authError }}
            </v-alert>

            <p class="text-caption text--secondary mt-6 mb-0">
              Enable <strong>Email</strong> in Supabase → Authentication → Providers, then add a user under
              Authentication → Users.
            </p>
          </v-card>
        </v-col>
      </v-row>

      <template v-else>
        <v-card flat class="admin-nav-card mb-6 pa-1 rounded-xl" outlined>
          <v-tabs background-color="transparent" show-arrows>
            <v-tab
              v-for="link in adminNavLinks"
              :key="link.name"
              class="text-none font-weight-bold"
              :to="link.to"
              :exact="link.name === 'admin'"
            >
              <v-icon left small class="mr-1">{{ link.icon }}</v-icon>
              {{ link.label }}
            </v-tab>
          </v-tabs>
        </v-card>
        <router-view />
      </template>
    </v-container>
  </div>
</template>

<script>
import { loginWithEmailPassword, subscribeToAuth } from '@/services/auth'
import { supabaseSetupMessage } from '@/supabase'

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
      email: '',
      password: '',
      authLoading: false,
      authError: '',
      supabaseConfigHint: supabaseSetupMessage,
      unsubAuth: null
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
      return [
        { name: 'admin', to: { name: 'admin' }, label: 'Dashboard', icon: 'dashboard' },
        { name: 'admin-products', to: { name: 'admin-products' }, label: 'Products', icon: 'inventory_2' },
        { name: 'admin-orders', to: { name: 'admin-orders' }, label: 'Orders', icon: 'receipt_long' },
        { name: 'admin-insights', to: { name: 'admin-insights' }, label: 'Insights', icon: 'insights' },
        { name: 'admin-store', to: { name: 'admin-store' }, label: 'Store', icon: 'storefront' }
      ]
    },
  },
  created() {
    this.unsubAuth = subscribeToAuth((u) => {
      this.adminSession.user = u
    })
  },
  beforeDestroy() {
    if (this.unsubAuth) this.unsubAuth()
  },
  methods: {
    async doLogin() {
      this.authError = ''
      this.authLoading = true
      try {
        await loginWithEmailPassword(this.email, this.password)
        this.email = ''
        this.password = ''
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
</style>
