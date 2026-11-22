<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>

    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">All users</div>
        <div class="sc-toolbar__end">
          <span class="text-caption text--secondary">{{ totalLabel }}</span>
          <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        </div>
      </div>

      <v-row dense class="mb-1">
        <v-col cols="12" sm="6" md="4">
          <v-text-field
            v-model="search"
            outlined
            dense
            clearable
            hide-details
            prepend-inner-icon="search"
            label="Search name or email"
            @input="onSearchInput"
          />
        </v-col>
        <v-col cols="12" sm="6" md="3">
          <v-autocomplete
            v-model="store"
            :items="storeItems"
            outlined
            dense
            clearable
            hide-details
            label="Store"
            no-data-text="No stores"
            @change="onFilterChange"
          />
        </v-col>
        <v-col cols="6" md="3">
          <v-select
            v-model="role"
            :items="roleItems"
            outlined
            dense
            clearable
            hide-details
            label="Role"
            @change="onFilterChange"
          />
        </v-col>
        <v-col cols="6" md="2">
          <v-select
            v-model="status"
            :items="statusItems"
            outlined
            dense
            clearable
            hide-details
            label="Status"
            @change="onFilterChange"
          />
        </v-col>
      </v-row>

      <p v-if="!canManage" class="text-caption text--secondary mb-3">
        Suspending accounts and sending password resets needs the <strong>Manage users</strong> permission.
      </p>

      <v-data-table
        :headers="headers"
        :items="users"
        :options.sync="options"
        :server-items-length="total"
        :loading="loading"
        :footer-props="{ 'items-per-page-options': [10, 25, 50, 100] }"
        disable-sort
        item-key="id"
        loading-text="Loading users…"
        no-data-text="No users match these filters."
        class="elevation-0"
      >
        <template v-slot:[`item.name`]="{ item }">
          <div class="font-weight-medium">{{ item.name || '—' }}</div>
          <div class="text-caption text--secondary">{{ item.email }}</div>
        </template>
        <template v-slot:[`item.roles`]="{ item }">
          <div class="d-flex flex-wrap sc-gap py-1">
            <v-chip v-for="r in item.roles" :key="r" x-small label :color="roleColor(r)">{{ roleLabel(r) }}</v-chip>
            <span v-if="!item.roles || !item.roles.length" class="text--secondary">—</span>
          </div>
        </template>
        <template v-slot:[`item.stores`]="{ item }">
          <div v-if="item.stores && item.stores.length">
            <div v-for="s in item.stores" :key="`${s.slug}-${s.role}`" class="text-body-2">
              {{ s.name || s.slug }} <span class="sc-code">{{ s.slug }}</span>
            </div>
          </div>
          <span v-else class="text--secondary">—</span>
        </template>
        <template v-slot:[`item.suspended`]="{ item }">
          <v-chip x-small label :color="item.suspended ? 'error' : 'success'">
            {{ item.suspended ? 'Suspended' : 'Active' }}
          </v-chip>
        </template>
        <template v-slot:[`item.createdAt`]="{ item }">{{ formatDate(item.createdAt) }}</template>
        <template v-slot:[`item.lastLoginAt`]="{ item }">{{ formatDateTime(item.lastLoginAt) }}</template>
        <template v-slot:[`item.actions`]="{ item }">
          <div v-if="canActOn(item)" class="d-flex flex-wrap justify-end sc-gap py-1">
            <v-btn
              v-if="!item.suspended"
              small
              text
              color="error"
              class="px-2"
              :loading="busyId === item.id"
              :disabled="Boolean(busyId)"
              @click="suspend(item)"
            >
              Suspend
            </v-btn>
            <v-btn
              v-else
              small
              text
              color="success"
              class="px-2"
              :loading="busyId === item.id"
              :disabled="Boolean(busyId)"
              @click="reactivate(item)"
            >
              Reactivate
            </v-btn>
            <v-btn
              v-if="!item.suspended"
              small
              text
              class="px-2"
              :disabled="Boolean(busyId)"
              @click="sendReset(item)"
            >
              Send reset
            </v-btn>
          </div>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import {
  fetchSupportUserStores,
  fetchSupportUsers,
  reactivateSupportUser,
  sendSupportUserPasswordReset,
  suspendSupportUser
} from '@/services/supportApi'
import { formatDate, formatDateTime } from '@/utils/displayLabels'

const ROLE_LABELS = Object.freeze({
  PLATFORM_ADMIN: 'Platform admin',
  SUPPORT_USER: 'Support',
  MERCHANT_OWNER: 'Merchant owner',
  MERCHANT_STAFF: 'Merchant staff',
  CLIENT: 'Client'
})

/** Accounts only a platform admin may suspend or reset (the API enforces this too). */
const STAFF_ROLES = ['PLATFORM_ADMIN', 'SUPPORT_USER']

const SEARCH_DEBOUNCE_MS = 350

const ACTION_ERRORS = Object.freeze({
  cannot_suspend_self: 'You cannot suspend your own account.',
  account_suspended: 'This account is suspended. Reactivate it before sending a password reset.',
  forbidden: 'You do not have permission to do that for this account.'
})

export default {
  name: 'SupportUsersView',
  inject: {
    supportDialog: { default: null },
    supportAccess: { default: null }
  },
  data() {
    return {
      loading: false,
      error: '',
      users: [],
      total: 0,
      options: { page: 1, itemsPerPage: 25 },
      search: '',
      role: null,
      store: null,
      status: null,
      stores: [],
      busyId: '',
      searchTimer: null,
      requestSeq: 0,
      roleItems: Object.entries(ROLE_LABELS).map(([value, text]) => ({ value, text })),
      statusItems: [
        { value: 'ACTIVE', text: 'Active' },
        { value: 'SUSPENDED', text: 'Suspended' }
      ],
      headers: [
        { text: 'User', value: 'name' },
        { text: 'Roles', value: 'roles' },
        { text: 'Stores', value: 'stores' },
        { text: 'Status', value: 'suspended' },
        { text: 'Signed up', value: 'createdAt' },
        { text: 'Last sign-in', value: 'lastLoginAt' },
        { text: '', value: 'actions', align: 'end' }
      ]
    }
  },
  computed: {
    canManage() {
      return Boolean(this.supportAccess && this.supportAccess.can('MANAGE_USERS'))
    },
    isPlatformAdmin() {
      return Boolean(this.supportAccess && this.supportAccess.isPlatformAdmin())
    },
    storeItems() {
      return this.stores.map((s) => ({ value: s.slug, text: s.name ? `${s.name} (${s.slug})` : s.slug }))
    },
    totalLabel() {
      return this.total === 1 ? '1 user' : `${this.total} users`
    }
  },
  watch: {
    options: {
      handler(next, prev) {
        if (prev && next.page === prev.page && next.itemsPerPage === prev.itemsPerPage) return
        this.load()
      },
      deep: true
    }
  },
  created() {
    this.load()
    this.loadStores()
  },
  beforeDestroy() {
    if (this.searchTimer) clearTimeout(this.searchTimer)
  },
  methods: {
    formatDate,
    formatDateTime,
    roleLabel(role) {
      return ROLE_LABELS[role] || role
    },
    roleColor(role) {
      if (role === 'PLATFORM_ADMIN') return 'primary'
      if (role === 'SUPPORT_USER') return 'secondary'
      return 'grey lighten-2'
    },
    canActOn(item) {
      if (!this.canManage || item.self) return false
      const isStaff = (item.roles || []).some((r) => STAFF_ROLES.includes(r))
      return !isStaff || this.isPlatformAdmin
    },
    onSearchInput(value) {
      this.search = value || ''
      if (this.searchTimer) clearTimeout(this.searchTimer)
      this.searchTimer = setTimeout(() => this.onFilterChange(), SEARCH_DEBOUNCE_MS)
    },
    onFilterChange() {
      if (this.options.page !== 1) {
        // The options watcher reloads.
        this.options = { ...this.options, page: 1 }
        return
      }
      this.load()
    },
    async load() {
      const seq = ++this.requestSeq
      this.loading = true
      try {
        const res = await fetchSupportUsers({
          q: this.search,
          role: this.role,
          store: this.store,
          status: this.status,
          page: Math.max(0, (this.options.page || 1) - 1),
          size: this.options.itemsPerPage > 0 ? this.options.itemsPerPage : 25
        })
        if (seq !== this.requestSeq) return
        this.users = (res && res.users) || []
        this.total = Number((res && res.total) || 0)
        this.error = ''
      } catch (e) {
        if (seq !== this.requestSeq) return
        this.error =
          e && e.status === 403
            ? 'You need the View users permission. Ask a platform admin to grant it.'
            : (e && e.message) || 'Failed to load users'
      } finally {
        if (seq === this.requestSeq) this.loading = false
      }
    },
    async loadStores() {
      try {
        const res = await fetchSupportUserStores()
        this.stores = (res && res.stores) || []
      } catch {
        this.stores = []
      }
    },
    actionError(e, fallback) {
      return ACTION_ERRORS[e && e.code] || (e && e.message) || fallback
    },
    replaceRow(updated) {
      if (!updated || !updated.id) return
      const idx = this.users.findIndex((u) => u.id === updated.id)
      if (idx >= 0) this.$set(this.users, idx, { ...this.users[idx], ...updated })
    },
    async confirm(opts) {
      if (!this.supportDialog) return false
      try {
        await this.supportDialog.confirm(opts)
        return true
      } catch {
        return false
      }
    },
    async suspend(item) {
      const ok = await this.confirm({
        title: 'Suspend user',
        message: `Suspend ${item.email}? They will be signed out everywhere and cannot sign in until reactivated.`,
        confirmLabel: 'Suspend',
        tone: 'danger'
      })
      if (!ok) return
      await this.runAction(item, () => suspendSupportUser(item.id), 'Suspend failed')
    },
    async reactivate(item) {
      const ok = await this.confirm({
        title: 'Reactivate user',
        message: `Reactivate ${item.email}? They will be able to sign in again.`,
        confirmLabel: 'Reactivate'
      })
      if (!ok) return
      await this.runAction(item, () => reactivateSupportUser(item.id), 'Reactivate failed')
    },
    async sendReset(item) {
      const ok = await this.confirm({
        title: 'Send password reset',
        message: `Email ${item.email} a link to set a new password? Their current password keeps working until they use it.`,
        confirmLabel: 'Send reset'
      })
      if (!ok) return
      const sent = await this.runAction(item, () => sendSupportUserPasswordReset(item.id), 'Could not send the reset')
      if (sent && this.supportDialog) {
        this.supportDialog
          .info({ title: 'Reset sent', message: `A password reset link was emailed to ${item.email}.` })
          .catch(() => {})
      }
    },
    async runAction(item, call, fallback) {
      this.busyId = item.id
      this.error = ''
      try {
        const res = await call()
        if (res && res.id) this.replaceRow(res)
        return true
      } catch (e) {
        this.error = this.actionError(e, fallback)
        return false
      } finally {
        this.busyId = ''
      }
    }
  }
}
</script>
