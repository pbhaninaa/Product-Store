<template>
  <v-container class="py-8 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="7" lg="5">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div class="text-overline mb-2">{{ isMerchantAudience ? 'Store sign in' : 'Customer account' }}</div>
          <h1 class="text-h4 font-weight-bold mb-2">{{ isRegister ? 'Create an account' : 'Sign in' }}</h1>
          <p v-if="isMerchantAudience" class="text-body-2 text--secondary mb-6">
            Store owners and staff: sign in with your account email and we’ll open your store admin.
          </p>
          <p v-else class="text-body-2 text--secondary mb-6">
            Same customer login as Wheel Hub — find nearby providers, track orders and bookings, and keep your referral code.
          </p>

          <v-form ref="accountForm" @submit.prevent>
            <v-text-field
              v-if="isRegister"
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
              class="mt-2"
            />
            <v-text-field
              v-model="password"
              label="Password *"
              type="password"
              outlined
              dense
              :rules="isRegister ? [rules.required('Password'), rules.password()] : [rules.required('Password')]"
              :disabled="loading"
              class="mt-2"
              @keyup.enter="submit"
            />
            <div v-if="!isRegister" class="text-right mt-n2">
              <router-link :to="forgotPasswordTo" class="text-body-2 font-weight-medium">Forgot password?</router-link>
            </div>
            <v-text-field
              v-if="isRegister"
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
            @click="submit"
          >
            {{ isRegister ? 'Create account' : 'Sign in' }}
          </v-btn>
          <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>
          <div class="d-flex align-center mt-6">
            <span class="text-body-2 text--secondary">
              {{ isRegister ? 'Already have an account?' : isMerchantAudience ? 'New store?' : 'New here?' }}
            </span>
            <v-spacer />
            <v-btn text class="text-none font-weight-bold" color="primary" :to="toggleTo">
              {{ isRegister ? 'Sign in' : isMerchantAudience ? 'Create your store' : 'Register' }}
            </v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { safeInternalPath } from '@/utils/safeRedirect'
import { loginWithEmailPassword, registerClient } from '@/services/auth'
import { forgotPasswordQuery } from '@/utils/signInRoute'
import { rules, validateForm } from '@/utils/validation'

export default {
  name: 'ClientAccountView',
  data() {
    return { email: '', password: '', displayName: '', invitedBy: '', loading: false, error: '', rules }
  },
  computed: {
    isRegister() {
      return this.$route.name === 'client-register'
    },
    isMerchantAudience() {
      return !this.isRegister && String(this.$route.query.audience || '').toLowerCase() === 'merchant'
    },
    forgotPasswordTo() {
      const audience = this.isMerchantAudience ? 'merchant' : 'client'
      return { name: 'forgot-password', query: forgotPasswordQuery({ audience }, this.email) }
    },
    toggleTo() {
      if (this.isMerchantAudience) return { name: 'merchant-signup' }
      return this.isRegister ? { name: 'client-login', query: this.$route.query } : { name: 'client-register', query: this.$route.query }
    }
  },
  created() {
    const ref = String(this.$route.query.ref || this.$route.query.invitedBy || '').trim()
    if (ref) this.invitedBy = ref
  },
  methods: {
    async submit() {
      this.error = ''
      if (!validateForm(this.$refs.accountForm)) return
      this.loading = true
      try {
        if (this.isRegister) {
          await registerClient({
            email: this.email,
            password: this.password,
            displayName: this.displayName,
            invitedBy: this.invitedBy
          })
        } else {
          await loginWithEmailPassword(this.email, this.password)
        }
        this.$router.replace(safeInternalPath(this.$route.query.next, '/')).catch(() => {})
      } catch (e) {
        this.error = (e && e.message) || 'Could not continue.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
