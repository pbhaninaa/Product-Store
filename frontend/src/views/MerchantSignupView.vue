<template>
  <v-container class="py-8 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="7" lg="5">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div class="text-overline mb-2">Business Owner</div>
          <h1 class="text-h4 font-weight-bold mb-2">Create your store</h1>
          <p class="text-body-2 text--secondary mb-6">
            Sign up as a Business Owner to create your store and manage products, orders, and settings.
            New stores include a <strong>7-day Free Trial</strong> with full access — no plan choice or payment required until it ends.
          </p>

          <v-form ref="signupForm" @submit.prevent>
            <v-text-field
              v-model="merchantName"
              label="Business name *"
              outlined
              dense
              :rules="businessNameRules"
              :disabled="loading"
              hint="Your store link is created automatically from this name"
              persistent-hint
            />
            <p v-if="slugPreview" class="text-caption text--secondary mt-1 mb-0">
              Store link: <strong>/{{ slugPreview }}</strong>
              <span class="font-weight-regular"> (final link may add -2 if the name is taken)</span>
            </p>
            <v-text-field
              v-model="ownerEmail"
              label="Owner email *"
              type="email"
              outlined
              dense
              :rules="[rules.required('Owner email'), rules.email()]"
              :disabled="loading"
              class="mt-4"
            />
            <v-text-field
              v-model="ownerPassword"
              label="Password *"
              type="password"
              outlined
              dense
              :rules="[rules.required('Password'), rules.password()]"
              :disabled="loading"
              class="mt-4"
              @keyup.enter="submitMerchant"
            />
          </v-form>

          <v-btn
            block
            x-large
            depressed
            color="primary"
            class="mt-6 text-none font-weight-bold"
            :loading="loading"
            @click="submitMerchant"
          >
            Create Business Owner account
          </v-btn>

          <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>

          <div class="d-flex align-center mt-6">
            <span class="text-body-2 text--secondary">Already have an account?</span>
            <v-spacer />
            <v-btn text class="text-none font-weight-bold" color="primary" :to="backToLogin">
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
import { slugFromBusinessName } from '@/utils/slug'
import { signInRouteFor } from '@/utils/signInRoute'
import { isBlank, rules, validateForm } from '@/utils/validation'

/** Business name must yield a 2–48 character store link (TenantSlugUtil.fromBusinessName). */
function businessNameGivesSlug(v) {
  if (isBlank(v)) return true
  try {
    slugFromBusinessName(v)
    return true
  } catch {
    return 'Use at least 2 letters or numbers'
  }
}

export default {
  name: 'MerchantSignupView',
  data() {
    return {
      merchantName: '',
      ownerEmail: '',
      ownerPassword: '',
      loading: false,
      error: '',
      rules,
      businessNameRules: [rules.required('Business name'), rules.maxLength(200, 'Business name'), businessNameGivesSlug]
    }
  },
  computed: {
    slugPreview() {
      try {
        return slugFromBusinessName(this.merchantName)
      } catch {
        return ''
      }
    },
    backToLogin() {
      // Only trust a store passed in the link (?m=); never guess one from the name being typed.
      return signInRouteFor({ audience: 'merchant', m: String(this.$route.query.m || '').trim() })
    }
  },
  methods: {
    async submitMerchant() {
      this.error = ''
      if (!validateForm(this.$refs.signupForm)) return
      this.loading = true
      try {
        const res = await registerMerchant({
          merchantName: this.merchantName,
          ownerEmail: this.ownerEmail,
          ownerPassword: this.ownerPassword,
          invitedBy: String(this.$route.query.ref || this.$route.query.invitedBy || '').trim()
        })
        const slug =
          (res && res.tenant && res.tenant.slug && String(res.tenant.slug).trim()) ||
          (res && res.merchantSlug && String(res.merchantSlug).trim()) ||
          ''
        this.$router.push(`/m/${encodeURIComponent(slug)}/admin/subscription`).catch(() => {})
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not sign up.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
