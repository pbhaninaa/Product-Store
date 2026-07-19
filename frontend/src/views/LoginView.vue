<template>
  <v-container class="py-10 px-3 px-sm-4 login-page">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="6" lg="5">
        <v-card class="pa-5 pa-sm-8 rounded-xl login-card" elevation="3" outlined>
          <div class="text-overline mb-2">Staff &amp; owners</div>
          <h1 class="text-h4 font-weight-bold mb-2">Sign in</h1>
          <p class="text-body-2 text--secondary mb-8">
            {{ leadCopy }}
          </p>

          <v-text-field
            v-model="email"
            outlined
            hide-details="auto"
            label="Email"
            type="email"
            autocomplete="email"
            class="rounded-lg"
            :disabled="loading"
          />
          <v-text-field
            v-model="password"
            outlined
            hide-details="auto"
            label="Password"
            type="password"
            autocomplete="current-password"
            class="mt-4 rounded-lg"
            :disabled="loading"
            @keyup.enter="submit"
          />

          <div class="text-right mt-2">
            <router-link class="login-link text-body-2 font-weight-medium" :to="forgotPasswordRoute">
              Forgot password?
            </router-link>
          </div>

          <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">
            {{ error }}
          </v-alert>

          <v-btn
            block
            x-large
            depressed
            color="tertiary"
            class="mt-6 text-none font-weight-bold"
            :loading="loading"
            @click="submit"
          >
            Sign in
          </v-btn>

          <div class="d-flex align-center flex-wrap mt-6" style="gap: 8px">
            <span class="text-body-2 text--secondary">New merchant?</span>
            <router-link class="login-link text-body-2 font-weight-bold" :to="signupRoute">
              Create a store
            </router-link>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import {
  getSessionUser,
  isSupportOrPlatformOnlyUser,
  loginWithEmailPassword
} from '@/services/auth'
import { isMerchantUser } from '@/utils/merchantRoles'

export default {
  name: 'LoginView',
  data() {
    return {
      email: String(this.$route.query.email || ''),
      password: '',
      loading: false,
      error: ''
    }
  },
  computed: {
    leadCopy() {
      if (this.$route.query.support === '1') {
        return 'Platform admin and support sign in here to open the Support console.'
      }
      return 'Merchant owners, staff, and platform support use this page. Customers shop at your store link — no login needed.'
    },
    forgotPasswordRoute() {
      const query = {}
      if (this.email) query.email = this.email
      const m = String(this.$route.query.m || '').trim()
      if (m) query.m = m
      return { name: 'forgot-password', query }
    },
    signupRoute() {
      const m = String(this.$route.query.m || '').trim()
      return {
        name: 'merchant-signup',
        query: m ? { m } : undefined
      }
    }
  },
  created() {
    const u = getSessionUser()
    if (u) this.redirectSignedIn(u)
  },
  methods: {
    redirectSignedIn(sess, loginRes) {
      if (isSupportOrPlatformOnlyUser(sess)) {
        this.$router.replace({ name: 'support-dashboard' }).catch(() => {})
        return
      }
      const roles = (loginRes && loginRes.roles) || (sess && sess.roles) || []
      const slug =
        (loginRes && loginRes.tenant && loginRes.tenant.slug) ||
        (loginRes && loginRes.merchantSlug) ||
        (sess && sess.tenant) ||
        ''
      const redirect = String(this.$route.query.redirect || '').trim()
      if (redirect.startsWith('/') && !redirect.startsWith('//')) {
        this.$router.replace(redirect).catch(() => {})
        return
      }
      if ((roles.includes('MERCHANT_OWNER') || roles.includes('MERCHANT_STAFF') || isMerchantUser(sess)) && slug) {
        this.$router
          .replace({ name: 'merchant-admin', params: { merchantSlug: String(slug).trim() } })
          .catch(() => {})
        return
      }
      this.$router.replace({ name: 'support-dashboard' }).catch(() => {})
    },
    async submit() {
      this.error = ''
      this.loading = true
      try {
        const res = await loginWithEmailPassword(this.email, this.password)
        this.password = ''
        const sess = getSessionUser()
        this.redirectSignedIn(sess, res)
      } catch (e) {
        this.error = (e && e.message) || 'Sign in failed.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - 64px);
}
.login-card {
  border-color: rgba(15, 23, 42, 0.08) !important;
  background: rgba(255, 255, 255, 0.98);
}
.login-link {
  color: #0f172a !important;
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
