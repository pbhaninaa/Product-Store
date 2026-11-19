<template>
  <v-container class="py-8 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="7" lg="5">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div v-if="checking" class="d-flex justify-center py-10">
            <v-progress-circular indeterminate color="primary" />
          </div>

          <template v-else>
            <div class="text-overline mb-2">{{ overline }}</div>
            <h1 class="text-h4 font-weight-bold mb-2">{{ heading }}</h1>
            <p class="text-body-2 text--secondary mb-6">
              <template v-if="needsSystemAdmin">
                No accounts exist yet. The first account becomes the <strong>system admin</strong>, who can then add
                support users from the Support console.
              </template>
              <template v-else-if="role === 'provider'">
                Create your store and manage products, orders and settings. New stores include a
                <strong>7-day Free Trial</strong> with full access — no plan choice or payment required until it ends.
              </template>
              <template v-else>
                Find nearby providers, track orders and bookings, and keep your referral code.
              </template>
            </p>

            <v-btn-toggle
              v-if="!needsSystemAdmin"
              v-model="role"
              mandatory
              color="primary"
              class="register-role-toggle d-flex mb-6"
              data-cy="register-role-toggle"
            >
              <v-btn value="client" class="flex-grow-1 text-none" :disabled="loading" data-cy="role-client">
                <v-icon left small>person</v-icon>
                Client
              </v-btn>
              <v-btn value="provider" class="flex-grow-1 text-none" :disabled="loading" data-cy="role-provider">
                <v-icon left small>storefront</v-icon>
                Provider
              </v-btn>
            </v-btn-toggle>

            <v-form ref="registerForm" @submit.prevent>
              <template v-if="isProvider">
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
              </template>
              <v-text-field
                v-else
                v-model="displayName"
                label="Name"
                outlined
                dense
                :rules="[rules.maxLength(120, 'Name')]"
                :disabled="loading"
              />
              <v-text-field
                v-model="email"
                label="Email *"
                type="email"
                outlined
                dense
                :rules="[rules.required('Email'), rules.email()]"
                :disabled="loading"
                class="mt-4"
              />
              <v-text-field
                v-model="password"
                label="Password *"
                type="password"
                outlined
                dense
                :rules="[rules.required('Password'), rules.password()]"
                :disabled="loading"
                class="mt-2"
                @keyup.enter="submit"
              />
              <v-text-field
                v-if="!needsSystemAdmin"
                v-model="invitedBy"
                label="Referral code (optional)"
                outlined
                dense
                :rules="[rules.maxLength(254, 'Referral code')]"
                :disabled="loading"
                class="mt-2"
              />
            </v-form>

            <v-btn
              block
              x-large
              depressed
              color="primary"
              class="mt-6 text-none font-weight-bold"
              :loading="loading"
              data-cy="register-submit"
              @click="submit"
            >
              {{ submitLabel }}
            </v-btn>

            <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>

            <div v-if="!needsSystemAdmin" class="d-flex align-center mt-6">
              <span class="text-body-2 text--secondary">Already have an account?</span>
              <v-spacer />
              <v-btn text class="text-none font-weight-bold" color="primary" :to="signInTo">Sign in</v-btn>
            </div>
          </template>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { getSetupStatus, registerClient, registerMerchant, registerSystemAdmin } from '@/services/auth'
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

const ERROR_TEXT = {
  email_taken: 'An account with this email already exists. Sign in instead.',
  password_too_short: 'Password must be at least 8 characters.',
  feature_disabled: 'Provider sign-up is switched off right now.',
  setup_required: 'The system admin account has not been created yet.',
  setup_complete: 'The system admin already exists. Register as a client or provider instead.',
  validation_error: 'Check the highlighted fields and try again.'
}

/** Initial role: `?as=provider|client`, else /signup → provider and /account/register → client. */
function initialRole(route) {
  const as = String(route.query.as || '').toLowerCase()
  if (as === 'provider' || as === 'client') return as
  return route.name === 'merchant-signup' ? 'provider' : 'client'
}

export default {
  name: 'RegisterView',
  data() {
    return {
      checking: true,
      needsSystemAdmin: false,
      role: initialRole(this.$route),
      merchantName: '',
      displayName: '',
      email: '',
      password: '',
      invitedBy: String(this.$route.query.ref || this.$route.query.invitedBy || '').trim(),
      loading: false,
      error: '',
      rules,
      businessNameRules: [rules.required('Business name'), rules.maxLength(200, 'Business name'), businessNameGivesSlug]
    }
  },
  computed: {
    isProvider() {
      return !this.needsSystemAdmin && this.role === 'provider'
    },
    overline() {
      if (this.needsSystemAdmin) return 'First-time setup'
      return this.isProvider ? 'Provider' : 'Client'
    },
    heading() {
      if (this.needsSystemAdmin) return 'Create the system admin'
      return this.isProvider ? 'Create your store' : 'Create an account'
    },
    submitLabel() {
      if (this.needsSystemAdmin) return 'Create system admin'
      return this.isProvider ? 'Create provider account' : 'Create client account'
    },
    slugPreview() {
      try {
        return slugFromBusinessName(this.merchantName)
      } catch {
        return ''
      }
    },
    signInTo() {
      if (this.isProvider) {
        return signInRouteFor({ audience: 'merchant', m: String(this.$route.query.m || '').trim() })
      }
      return { name: 'client-login', query: this.$route.query }
    }
  },
  watch: {
    role() {
      this.error = ''
      if (this.$refs.registerForm) this.$refs.registerForm.resetValidation()
    }
  },
  async created() {
    try {
      const status = await getSetupStatus()
      this.needsSystemAdmin = status.needsSystemAdmin
    } catch {
      // API unreachable: show the normal form; submitting will surface the real error.
      this.needsSystemAdmin = false
    } finally {
      this.checking = false
    }
  },
  methods: {
    async submit() {
      this.error = ''
      if (!validateForm(this.$refs.registerForm)) return
      this.loading = true
      try {
        if (this.needsSystemAdmin) {
          await registerSystemAdmin({ email: this.email, password: this.password, displayName: this.displayName })
          this.$router.replace('/support/staff').catch(() => {})
        } else if (this.isProvider) {
          const res = await registerMerchant({
            merchantName: this.merchantName,
            ownerEmail: this.email,
            ownerPassword: this.password,
            invitedBy: this.invitedBy
          })
          const slug =
            (res && res.tenant && res.tenant.slug && String(res.tenant.slug).trim()) ||
            (res && res.merchantSlug && String(res.merchantSlug).trim()) ||
            ''
          this.$router.push(`/m/${encodeURIComponent(slug)}/admin/subscription`).catch(() => {})
        } else {
          await registerClient({
            email: this.email,
            password: this.password,
            displayName: this.displayName,
            invitedBy: this.invitedBy
          })
          const next = String(this.$route.query.next || '').trim()
          this.$router.replace(next || '/').catch(() => {})
        }
      } catch (e) {
        const code = String((e && e.code) || '')
        if (code === 'setup_required') this.needsSystemAdmin = true
        if (code === 'setup_complete') this.needsSystemAdmin = false
        this.error = ERROR_TEXT[code] || (e && e.message) || 'Could not create the account.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.register-role-toggle {
  width: 100%;
}
</style>
