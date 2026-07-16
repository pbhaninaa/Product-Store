<template>
  <div class="support-page">
    <div class="mb-6">
      <div class="text-h5 font-weight-bold">Account</div>
      <p class="text-body-2 text--secondary mb-0">
        Change your password. Platform admins can also add support users.
      </p>
    </div>

    <v-row>
      <v-col cols="12" md="6">
        <v-card outlined class="rounded-xl pa-5">
          <div class="text-subtitle-1 font-weight-bold mb-1">Change password</div>
          <p class="text-caption text--secondary mb-4">Signed in as {{ sessionEmail || '—' }}</p>

          <v-text-field
            v-model="currentPassword"
            label="Current password"
            type="password"
            outlined
            dense
            :disabled="pwdLoading"
          />
          <v-text-field
            v-model="newPassword"
            label="New password"
            type="password"
            outlined
            dense
            class="mt-3"
            :disabled="pwdLoading"
            hint="At least 8 characters"
            persistent-hint
          />
          <v-text-field
            v-model="confirmPassword"
            label="Confirm new password"
            type="password"
            outlined
            dense
            class="mt-3"
            :disabled="pwdLoading"
            @keyup.enter="savePassword"
          />

          <v-alert v-if="pwdError" type="error" dense outlined class="mt-4 rounded-lg">{{ pwdError }}</v-alert>
          <v-alert v-if="pwdSuccess" type="success" dense outlined class="mt-4 rounded-lg">
            Password updated.
          </v-alert>

          <v-btn
            class="mt-4 text-none font-weight-bold"
            color="primary"
            depressed
            :loading="pwdLoading"
            @click="savePassword"
          >
            Update password
          </v-btn>
        </v-card>
      </v-col>

      <v-col v-if="isPlatformAdmin" cols="12" md="6">
        <v-card outlined class="rounded-xl pa-5">
          <div class="text-subtitle-1 font-weight-bold mb-1">Add support user</div>
          <p class="text-caption text--secondary mb-4">
            Support users can manage merchants and subscriptions. Only platform admins can create them.
          </p>

          <v-text-field
            v-model="supportEmail"
            label="Support email"
            type="email"
            outlined
            dense
            :disabled="supportLoading"
          />
          <v-text-field
            v-model="supportPassword"
            label="Temporary password"
            type="password"
            outlined
            dense
            class="mt-3"
            :disabled="supportLoading"
            @keyup.enter="createSupport"
          />

          <v-alert v-if="supportError" type="error" dense outlined class="mt-4 rounded-lg">
            {{ supportError }}
          </v-alert>
          <v-alert v-if="supportSuccess" type="success" dense outlined class="mt-4 rounded-lg">
            {{ supportSuccess }}
          </v-alert>

          <v-btn
            class="mt-4 text-none font-weight-bold"
            color="primary"
            depressed
            :loading="supportLoading"
            @click="createSupport"
          >
            Create support user
          </v-btn>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import { changePassword, getSessionUser } from '@/services/auth'
import { createPlatformSupportUser } from '@/services/supportApi'

export default {
  name: 'SupportAccountView',
  data() {
    return {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
      pwdLoading: false,
      pwdError: '',
      pwdSuccess: false,
      supportEmail: '',
      supportPassword: '',
      supportLoading: false,
      supportError: '',
      supportSuccess: ''
    }
  },
  computed: {
    session() {
      return getSessionUser()
    },
    sessionEmail() {
      return (this.session && this.session.email) || ''
    },
    isPlatformAdmin() {
      const roles = (this.session && this.session.roles) || []
      return roles.includes('PLATFORM_ADMIN')
    }
  },
  methods: {
    async savePassword() {
      this.pwdError = ''
      this.pwdSuccess = false
      if (this.newPassword !== this.confirmPassword) {
        this.pwdError = 'New password and confirmation do not match.'
        return
      }
      this.pwdLoading = true
      try {
        await changePassword(this.currentPassword, this.newPassword)
        this.currentPassword = ''
        this.newPassword = ''
        this.confirmPassword = ''
        this.pwdSuccess = true
      } catch (e) {
        this.pwdError = e && e.message ? e.message : 'Could not update password.'
      } finally {
        this.pwdLoading = false
      }
    },
    async createSupport() {
      this.supportError = ''
      this.supportSuccess = ''
      this.supportLoading = true
      try {
        const res = await createPlatformSupportUser({
          email: this.supportEmail,
          password: this.supportPassword
        })
        this.supportSuccess = `Support user created: ${res && res.email ? res.email : this.supportEmail}`
        this.supportEmail = ''
        this.supportPassword = ''
      } catch (e) {
        this.supportError = e && e.message ? e.message : 'Could not create support user.'
      } finally {
        this.supportLoading = false
      }
    }
  }
}
</script>
