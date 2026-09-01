<template>
  <v-container class="py-10 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="6" lg="5">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div class="text-overline mb-2">Customer account</div>
          <h1 class="text-h4 font-weight-bold mb-2">{{ isRegister ? 'Create an account' : 'Sign in' }}</h1>
          <p class="text-body-2 text--secondary mb-8">
            Same customer login as Wheel Hub ù find nearby shops and salons, track orders and bookings, and keep your referral code.
          </p>

          <v-text-field
            v-if="isRegister"
            v-model="displayName"
            label="Name"
            outlined
            dense
            :disabled="loading"
          />
          <v-text-field
            v-model="email"
            label="Email"
            type="email"
            outlined
            dense
            :disabled="loading"
            class="mt-2"
          />
          <v-text-field
            v-model="password"
            label="Password"
            type="password"
            outlined
            dense
            :disabled="loading"
            class="mt-2"
            @keyup.enter="submit"
          />
          <v-text-field
            v-if="isRegister"
            v-model="invitedBy"
            label="Referral code (optional)"
            outlined
            dense
            :disabled="loading"
            class="mt-2"
          />

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
              {{ isRegister ? 'Already have an account?' : 'New here?' }}
            </span>
            <v-spacer />
            <v-btn text class="text-none font-weight-bold" color="primary" :to="toggleTo">
              {{ isRegister ? 'Sign in' : 'Register' }}
            </v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { loginWithEmailPassword, registerClient } from '@/services/auth'

export default {
  name: 'ClientAccountView',
  data() {
    return { email: '', password: '', displayName: '', invitedBy: '', loading: false, error: '' }
  },
  computed: {
    isRegister() {
      return this.$route.name === 'client-register'
    },
    toggleTo() {
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
        const next = String(this.$route.query.next || '').trim()
        this.$router.replace(next || '/').catch(() => {})
      } catch (e) {
        this.error = (e && e.message) || 'Could not continue.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
