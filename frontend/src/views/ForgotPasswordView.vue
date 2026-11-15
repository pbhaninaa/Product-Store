<template>
  <v-container class="py-10 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="7" lg="6">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div class="text-overline mb-2">Account recovery</div>
          <h1 class="text-h4 font-weight-bold mb-2">Forgot password</h1>
          <p class="text-body-2 text--secondary mb-8">
            Enter your account email. If it is registered, we will send a link to set a new password.
          </p>

          <v-text-field
            v-model="email"
            label="Email"
            type="email"
            outlined
            dense
            autocomplete="email"
            :disabled="loading || done"
            @keyup.enter="submit"
          />

          <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>
          <v-alert v-if="done" type="success" dense outlined class="mt-4 rounded-lg">
            If that email is registered, reset instructions have been sent. Check your inbox, then open the
            reset link.
          </v-alert>

          <v-btn
            block
            x-large
            depressed
            color="primary"
            class="mt-6 text-none font-weight-bold"
            :loading="loading"
            :disabled="done"
            @click="submit"
          >
            Send reset link
          </v-btn>

          <div class="d-flex align-center mt-6">
            <span class="text-body-2 text--secondary">Remembered your password?</span>
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
import { requestPasswordReset } from '@/services/auth'

export default {
  name: 'ForgotPasswordView',
  data() {
    return {
      email: String(this.$route.query.email || ''),
      loading: false,
      done: false,
      error: ''
    }
  },
  computed: {
    backToLogin() {
      return { name: 'login', query: this.$route.query.m ? { m: this.$route.query.m } : undefined }
    }
  },
  methods: {
    async submit() {
      this.error = ''
      this.done = false
      this.loading = true
      try {
        await requestPasswordReset(this.email)
        this.done = true
      } catch (e) {
        this.error = (e && e.message) || 'Could not send reset link.'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
