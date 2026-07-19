<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-row>
      <v-col cols="12" md="5">
        <v-card class="admin-card pa-4" elevation="3" rounded="xl">
          <div class="card-label mb-3">Create support user</div>
          <v-text-field v-model="create.email" outlined dense label="Email" class="mb-3" />
          <v-text-field v-model="create.password" outlined dense type="password" label="Password" class="mb-3" />
          <v-btn color="primary" class="text-none font-weight-bold" :loading="creating" @click="doCreate">
            Create
          </v-btn>
        </v-card>
      </v-col>
      <v-col cols="12" md="7">
        <v-card class="admin-card pa-4" elevation="3" rounded="xl">
          <div class="d-flex mb-3">
            <div class="card-label mb-0">Staff roster</div>
            <v-spacer />
            <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
          </div>
          <v-data-table :headers="headers" :items="staff" :items-per-page="20" class="elevation-0">
            <template v-slot:[`item.suspended`]="{ item }">
              <v-chip x-small :color="item.suspended ? 'error' : 'success'" label>
                {{ item.suspended ? 'Suspended' : 'Active' }}
              </v-chip>
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn
                v-if="!item.suspended"
                small
                text
                color="error"
                class="text-none"
                @click="suspend(item)"
              >
                Suspend
              </v-btn>
              <v-btn v-else small text color="success" class="text-none" @click="activate(item)">Activate</v-btn>
              <v-btn small text class="text-none" @click="resetPw(item)">Reset PW</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import {
  activateSupportStaff,
  createSupportStaff,
  fetchSupportStaff,
  resetSupportStaffPassword,
  suspendSupportStaff
} from '@/services/supportApi'

export default {
  name: 'SupportStaffView',
  inject: ['supportDialog'],
  data() {
    return {
      loading: false,
      creating: false,
      error: '',
      staff: [],
      create: { email: '', password: '' },
      headers: [
        { text: 'Email', value: 'email' },
        { text: 'Role', value: 'role' },
        { text: 'Status', value: 'suspended' },
        { text: '', value: 'actions', sortable: false }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await fetchSupportStaff()
        this.staff = (res && res.staff) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load staff'
      } finally {
        this.loading = false
      }
    },
    async doCreate() {
      this.creating = true
      this.error = ''
      try {
        await createSupportStaff(this.create)
        this.create = { email: '', password: '' }
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Create failed'
      } finally {
        this.creating = false
      }
    },
    async suspend(item) {
      try {
        await suspendSupportStaff(item.id)
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Suspend failed'
      }
    },
    async activate(item) {
      try {
        await activateSupportStaff(item.id)
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Activate failed'
      }
    },
    async resetPw(item) {
      if (!this.supportDialog) return
      let password
      try {
        password = await this.supportDialog.prompt({
          title: 'Reset password',
          message: `Set a new password for ${item.email || 'this support user'}.`,
          inputLabel: 'New password',
          inputType: 'password',
          confirmLabel: 'Reset password',
          tone: 'default',
          confirmColor: 'warning',
          validate: (val) => (val.length < 8 ? 'Password must be at least 8 characters.' : null)
        })
      } catch {
        return
      }
      try {
        await resetSupportStaffPassword(item.id, password)
        await this.supportDialog.info({
          title: 'Password reset',
          message: `Password updated for ${item.email || 'support user'}.`,
          tone: 'success'
        })
      } catch (e) {
        this.error = (e && e.message) || 'Reset failed'
      }
    }
  }
}
</script>
