<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>

    <v-card class="admin-card pa-4 mb-4" elevation="3" rounded="xl">
      <div class="card-label mb-3">Create support user</div>
      <v-row dense align="start">
        <v-col cols="12" sm="5">
          <v-text-field v-model="create.email" outlined dense hide-details="auto" label="Email" type="email" />
        </v-col>
        <v-col cols="12" sm="4">
          <v-text-field
            v-model="create.password"
            outlined
            dense
            hide-details="auto"
            type="password"
            label="Password"
            @keyup.enter="doCreate"
          />
        </v-col>
        <v-col cols="12" sm="3">
          <v-btn block color="primary" class="text-none font-weight-bold" height="40" :loading="creating" @click="doCreate">
            Create
          </v-btn>
        </v-col>
      </v-row>
      <p class="text-caption text--secondary mt-2 mb-0">
        New support users start with the default permissions. A platform admin can change them below.
      </p>
    </v-card>

    <div class="sc-toolbar">
      <div class="card-label mb-0">Staff roster</div>
      <div class="sc-toolbar__end">
        <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
    </div>
    <p v-if="!isPlatformAdmin" class="text-caption text--secondary mb-3">
      Only platform admins can change permissions, suspend accounts or reset passwords.
    </p>

    <v-row v-if="loading && !staff.length">
      <v-col v-for="n in 3" :key="n" cols="12" sm="6" lg="4" class="d-flex">
        <v-card class="admin-card pa-4 sc-card" elevation="3" rounded="xl">
          <v-skeleton-loader type="list-item-two-line, text@2" />
        </v-card>
      </v-col>
    </v-row>
    <v-card v-else-if="!staff.length" class="admin-card pa-4" elevation="3" rounded="xl">
      <p class="text-body-2 text--secondary mb-0">No support staff yet.</p>
    </v-card>
    <v-row v-else>
      <v-col v-for="item in staff" :key="item.id" cols="12" sm="6" lg="4" class="d-flex">
        <v-card class="admin-card pa-4 sc-card" elevation="3" rounded="xl">
          <div class="sc-card__body">
            <div class="text-subtitle-1 font-weight-bold sc-clamp-1" :title="item.email">{{ item.email }}</div>
            <div class="d-flex flex-wrap sc-gap my-2">
              <v-chip x-small label :color="item.role === 'PLATFORM_ADMIN' ? 'primary' : 'grey lighten-2'">
                {{ item.role === 'PLATFORM_ADMIN' ? 'Platform admin' : 'Support' }}
              </v-chip>
              <v-chip x-small label :color="item.suspended ? 'error' : 'success'">
                {{ item.suspended ? 'Suspended' : 'Active' }}
              </v-chip>
            </div>
            <div class="text-caption text--secondary sc-clamp-3" :title="permissionSummary(item)">
              {{ permissionSummary(item) }}
            </div>
          </div>
          <div v-if="isPlatformAdmin" class="sc-card__actions pt-3">
            <v-btn
              v-if="item.role === 'SUPPORT_USER'"
              small
              text
              color="primary"
              class="text-none px-2"
              @click="openPermissions(item)"
            >
              Permissions
            </v-btn>
            <v-btn
              v-if="!item.suspended"
              small
              text
              color="error"
              class="text-none px-2"
              @click="suspend(item)"
            >
              Suspend
            </v-btn>
            <v-btn v-else small text color="success" class="text-none px-2" @click="activate(item)">Activate</v-btn>
            <v-btn small text class="text-none px-2" @click="resetPw(item)">Reset PW</v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="permDialog" max-width="520" :persistent="savingPerms">
      <v-card class="pa-4 rounded-xl">
        <div class="text-h6 font-weight-bold mb-1">Edit permissions</div>
        <p class="text-body-2 text--secondary mb-3 sc-clamp-2">{{ permTarget && permTarget.email }}</p>
        <v-checkbox
          v-for="p in allPermissions"
          :key="p.value"
          v-model="permSelection"
          :value="p.value"
          :label="p.label"
          :hint="p.hint"
          persistent-hint
          dense
          class="mt-0 mb-2"
        />
        <v-alert v-if="permError" type="error" dense outlined class="mt-2 rounded-lg">{{ permError }}</v-alert>
        <div class="d-flex justify-end flex-wrap sc-gap mt-4">
          <v-btn text class="text-none" :disabled="savingPerms" @click="permDialog = false">Cancel</v-btn>
          <v-btn
            color="primary"
            depressed
            class="text-none font-weight-bold"
            :loading="savingPerms"
            :disabled="!permSelection.length"
            @click="savePermissions"
          >
            Save permissions
          </v-btn>
        </div>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import {
  activateSupportStaff,
  createSupportStaff,
  fetchSupportStaff,
  resetSupportStaffPassword,
  suspendSupportStaff,
  updateSupportStaffPermissions
} from '@/services/supportApi'
import { SUPPORT_PERMISSIONS, normalizePermissions, permissionLabel } from './supportPermissions'

export default {
  name: 'SupportStaffView',
  inject: {
    supportDialog: { default: null },
    supportAccess: { default: null }
  },
  data() {
    return {
      loading: false,
      creating: false,
      error: '',
      staff: [],
      create: { email: '', password: '' },
      allPermissions: SUPPORT_PERMISSIONS,
      permDialog: false,
      permTarget: null,
      permSelection: [],
      permError: '',
      savingPerms: false
    }
  },
  computed: {
    isPlatformAdmin() {
      return Boolean(this.supportAccess && this.supportAccess.isPlatformAdmin())
    }
  },
  created() {
    this.load()
  },
  methods: {
    permissionSummary(item) {
      if (item.role === 'PLATFORM_ADMIN') return 'All permissions'
      const perms = normalizePermissions(item.permissions)
      if (!perms.length) return 'No permissions'
      return perms.map(permissionLabel).join(' · ')
    },
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
    openPermissions(item) {
      this.permTarget = item
      this.permSelection = normalizePermissions(item.permissions)
      this.permError = ''
      this.permDialog = true
    },
    async savePermissions() {
      if (!this.permTarget) return
      const perms = normalizePermissions(this.permSelection)
      if (!perms.length) {
        this.permError = 'Choose at least one permission.'
        return
      }
      this.savingPerms = true
      this.permError = ''
      try {
        const updated = await updateSupportStaffPermissions(this.permTarget.id, perms)
        const idx = this.staff.findIndex((s) => s.id === this.permTarget.id)
        if (idx >= 0 && updated) this.$set(this.staff, idx, { ...this.staff[idx], ...updated })
        this.permDialog = false
      } catch (e) {
        this.permError =
          e && e.status === 403
            ? 'Only platform admins can change support permissions.'
            : (e && e.message) || 'Could not save permissions'
      } finally {
        this.savingPerms = false
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
