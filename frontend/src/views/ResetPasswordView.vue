<template>
  <v-container class="py-8 px-3 px-sm-4">
    <v-row justify="center">
      <v-col cols="12" sm="10" md="7" lg="5">
        <v-card class="pa-5 pa-sm-8 rounded-xl" elevation="3" outlined>
          <div class="text-overline mb-2">Account recovery</div>
          <h1 class="text-h4 font-weight-bold mb-2">Reset password</h1>
          <p class="text-body-2 text--secondary mb-6">
            Choose a new password for your Product Store account. Passwords must be at least 8 characters.
          </p>

          <v-alert v-if="!token" type="error" dense outlined class="mb-4 rounded-lg">
            This reset link is missing a token. Open the link from your email, or request a new one.
          </v-alert>

          <template v-else>
            <v-form ref="resetForm" @submit.prevent>
              <v-text-field
                v-model="newPassword"
                label="New password *"
                type="password"
                outlined
                dense
                autocomplete="new-password"
                :rules="[rules.required('New password'), rules.password()]"
                :disabled="loading || done"
              />
              <v-text-field
                v-model="confirmPassword"
                label="Confirm new password *"
                type="password"
                outlined
                dense
                class="mt-4"
                autocomplete="new-password"
                :rules="[rules.required('Confirm password'), rules.matches(() => newPassword, 'Passwords do not match')]"
                :disabled="loading || done"
                @keyup.enter="submit"
              />
            </v-form>

            <v-alert v-if="error" type="error" dense outlined class="mt-4 rounded-lg">{{ error }}</v-alert>
            <v-alert v-if="done" type="success" dense outlined class="mt-4 rounded-lg">
              Your password was updated. You can sign in with the new password.
            </v-alert>

            <v-btn
              v-if="done"
              block
              x-large
              depressed
              color="primary"
              class="mt-6 text-none font-weight-bold"
              :to="backToLogin"
            >
              Sign in
            </v-btn>
            <v-btn
              v-else
              block
              x-large
              depressed
              color="primary"
              class="mt-6 text-none font-weight-bold"
              :loading="loading"
              :disabled="!token"
              @click="submit"
            >
              Update password
            </v-btn>
          </template>

          <div class="d-flex align-center mt-6">
            <v-btn text class="text-none font-weight-bold" color="primary" :to="requestNewLink">
              Request a new link
            </v-btn>
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
import { resetPassword } from '@/services/auth'
import { forgotPasswordQuery, recallResetContext, signInContextFromQuery, signInRouteFor } from '@/utils/signInRoute'
import { rules, validateForm } from '@/utils/validation'

export default {
  name: 'ResetPasswordView',
  data() {
    return {
      newPassword: '',
      confirmPassword: '',
      loading: false,
      done: false,
      error: '',
      rules
    }
  },
  computed: {
    token() {
      return String(this.$route.query.token || '').trim()
    },
    signInContext() {
      const q = this.$route.query || {}
      if (q.audience || q.m || q.support) return signInContextFromQuery(q)
      // The emailed link only carries the token; reuse the context saved when the link was requested.
      return recallResetContext() || { audience: 'client' }
    },
    backToLogin() {
      return signInRouteFor(this.signInContext)
    },
    requestNewLink() {
      return { name: 'forgot-password', query: forgotPasswordQuery(this.signInContext) }
    }
  },
  methods: {
    async submit() {
      this.error = ''
      if (!this.token) {
        this.error = 'Reset token is missing.'
        return
      }
      if (!validateForm(this.$refs.resetForm)) return
      this.loading = true
      try {
        await resetPassword(this.token, this.newPassword)
        this.done = true
      } catch (e) {
        const msg = (e && e.message) || 'Could not reset password.'
        if (String(msg).includes('reset_invalid') || String(msg).toLowerCase().includes('invalid')) {
          this.error = 'This reset link is invalid or has expired. Request a new one.'
        } else if (String(msg).includes('password_too_short')) {
          this.error = 'New password must be at least 8 characters.'
        } else {
          this.error = msg
        }
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
