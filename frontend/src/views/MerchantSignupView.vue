<template>
  <v-container class="py-10 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="7" lg="6">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div class="text-overline mb-2">Merchant</div>
          <h1 class="text-h4 font-weight-bold mb-2">Sign up</h1>
          <p class="text-body-2 text--secondary mb-8">
            Create your store, then sign in to manage products, orders, and settings.
          </p>

          <v-text-field v-model="merchantName" label="Business name" outlined dense :disabled="loading" />
          <v-text-field
            v-model="merchantSlug"
            label="Store link (slug)"
            outlined
            dense
            :disabled="loading"
            hint="Example: my-salon (used in /m/my-salon)"
            persistent-hint
            class="mt-4"
          />
          <v-text-field v-model="ownerEmail" label="Owner email" type="email" outlined dense :disabled="loading" class="mt-4" />
          <v-text-field
            v-model="ownerPassword"
            label="Password"
            type="password"
            outlined
            dense
            :disabled="loading"
            class="mt-4"
            @keyup.enter="submit"
          />

          <v-btn block x-large depressed color="primary" class="mt-6 text-none font-weight-bold" :loading="loading" @click="submit">
            Create merchant account
          </v-btn>

          <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>

          <div class="d-flex align-center mt-6">
            <span class="text-body-2 text--secondary">Already have an account?</span>
            <v-spacer />
            <v-btn text class="text-none font-weight-bold" color="primary" :to="backToAdminLogin">
              Sign in
            </v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { registerMerchant } from '@/services/auth'

export default {
  name: 'MerchantSignupView',
  data() {
    return {
      merchantName: '',
      merchantSlug: '',
      ownerEmail: '',
      ownerPassword: '',
      loading: false,
      error: ''
    }
  },
  computed: {
    backToAdminLogin() {
      // If user came from a merchant admin route, keep their slug; otherwise use demo.
      const slug = String(this.$route.query.m || 'demo').trim()
      return `/m/${encodeURIComponent(slug)}/admin`
    }
  },
  methods: {
    async submit() {
      this.error = ''
      this.loading = true
      try {
        const res = await registerMerchant({
          merchantName: this.merchantName,
          merchantSlug: this.merchantSlug,
          ownerEmail: this.ownerEmail,
          ownerPassword: this.ownerPassword
        })
        const slug =
          (res && res.tenant && res.tenant.slug && String(res.tenant.slug).trim()) ||
          (res && res.merchantSlug && String(res.merchantSlug).trim()) ||
          String(this.merchantSlug || '').trim()
        this.$router.push(`/m/${encodeURIComponent(slug)}/admin`).catch(() => {})
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not sign up.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

