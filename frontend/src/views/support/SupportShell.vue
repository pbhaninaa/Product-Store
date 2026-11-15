<template>
  <div class="support-shell">
    <section class="admin-hero">
      <v-container class="py-10 py-md-12">
        <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
          <div>
            <div class="admin-kicker mb-2">
              <v-icon small color="secondary" class="mr-1">support_agent</v-icon>
              Protected area
            </div>
            <h1 class="admin-title">Platform console</h1>
            <p class="admin-lead mb-0">
              Merchants, billing, tickets, shadow help, alerts, and platform ops.
            </p>
          </div>
          <div class="mt-6 mt-md-0 ml-md-auto d-flex flex-column flex-sm-row align-stretch align-sm-center">
            <v-chip class="text-none font-weight-bold px-4 mb-3 mb-sm-0 mr-sm-3" color="primary" outlined label>
              <v-icon left small color="primary">cloud_done</v-icon>
              Live sync
            </v-chip>
            <v-btn depressed color="primary" class="text-none font-weight-bold" @click="doLogout">
              <v-icon left small>logout</v-icon>
              Sign out
            </v-btn>
          </div>
        </div>
      </v-container>
    </section>

    <v-container class="pb-2 support-body px-3 px-sm-4">
      <v-card flat class="admin-nav-card mb-6 pa-1 rounded-xl" outlined>
        <v-tabs background-color="transparent" show-arrows>
          <v-tab
            v-for="tab in visibleTabs"
            :key="tab.name"
            class="text-none font-weight-bold"
            :to="{ name: tab.name }"
            :exact="tab.exact"
          >
            <v-badge
              v-if="tab.badge > 0"
              :content="tab.badge"
              color="error"
              overlap
              class="mr-1"
            >
              <v-icon left small>{{ tab.icon }}</v-icon>
            </v-badge>
            <template v-else>
              <v-icon left small class="mr-1">{{ tab.icon }}</v-icon>
            </template>
            {{ tab.label }}
          </v-tab>
        </v-tabs>
      </v-card>
      <router-view />
    </v-container>
    <support-action-dialog ref="actionDialog" />
  </div>
</template>

<script>
import SupportActionDialog from '@/components/support/SupportActionDialog.vue'
import { getSessionUser, logout } from '@/services/auth'
import {
  fetchPendingSubscriptionProofs,
  fetchSupportMe,
  fetchSupportNotifications,
  fetchSupportTickets
} from '@/services/supportApi'

/** Icons from material-design-icons-iconfont (avoid newer glyphs missing in the bundle). */
const DEFAULT_SUPPORT_PERMS = [
  'MANAGE_SUBSCRIPTIONS',
  'MANAGE_MERCHANTS',
  'USE_SHADOW',
  'MANAGE_TICKETS',
  'VIEW_OPS'
]

export default {
  name: 'SupportShell',
  components: { SupportActionDialog },
  provide() {
    return {
      supportDialog: {
        confirm: (opts) => this.$refs.actionDialog.confirm(opts),
        prompt: (opts) => this.$refs.actionDialog.prompt(opts),
        select: (opts) => this.$refs.actionDialog.select(opts),
        info: (opts) => this.$refs.actionDialog.info(opts)
      }
    }
  },
  data() {
    return {
      permissions: [],
      platformAdmin: false,
      pendingProofs: 0,
      openTickets: 0,
      unreadNotifications: 0,
      pollTimer: null
    }
  },
  computed: {
    allTabs() {
      return [
        { name: 'support-dashboard', label: 'Dashboard', icon: 'dashboard', exact: true, perm: null },
        { name: 'support-merchants', label: 'Merchants', icon: 'store', perm: 'MANAGE_MERCHANTS' },
        {
          name: 'support-subscriptions',
          label: 'Billing',
          icon: 'payment',
          perm: 'MANAGE_SUBSCRIPTIONS',
          badge: this.pendingProofs
        },
        { name: 'support-orders', label: 'Orders', icon: 'receipt', perm: 'VIEW_OPS' },
        { name: 'support-bookings', label: 'Bookings', icon: 'event', perm: 'VIEW_OPS' },
        {
          name: 'support-tickets',
          label: 'Tickets',
          icon: 'message',
          perm: 'MANAGE_TICKETS',
          badge: this.openTickets
        },
        { name: 'support-shadow', label: 'Shadow', icon: 'visibility', perm: 'USE_SHADOW' },
        {
          name: 'support-notifications',
          label: 'Alerts',
          icon: 'notifications',
          perm: null,
          badge: this.unreadNotifications
        },
        { name: 'support-features', label: 'Features', icon: 'settings', perm: 'MANAGE_FEATURES' },
        { name: 'support-audit', label: 'Audit', icon: 'history', perm: 'VIEW_AUDIT' },
        { name: 'support-help-contact', label: 'Help contact', icon: 'email', perm: null },
        { name: 'support-staff', label: 'Staff', icon: 'group', perm: 'MANAGE_STAFF' },
        { name: 'support-referrals', label: 'Referrals', icon: 'share', perm: 'VIEW_OPS' },
        { name: 'support-account', label: 'Account', icon: 'person', perm: null }
      ]
    },
    visibleTabs() {
      return this.allTabs.filter((t) => this.can(t.perm))
    }
  },
  created() {
    this.refreshMeta()
    this.pollTimer = setInterval(() => this.refreshMeta(), 60000)
  },
  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    can(perm) {
      if (!perm) return true
      if (this.platformAdmin) return true
      return this.permissions.includes(perm)
    },
    applySessionFallback() {
      const u = getSessionUser()
      const roles = (u && u.roles) || []
      if (roles.includes('PLATFORM_ADMIN')) {
        this.platformAdmin = true
        this.permissions = []
        return
      }
      if (roles.includes('SUPPORT_USER')) {
        this.platformAdmin = false
        this.permissions = [...DEFAULT_SUPPORT_PERMS]
      }
    },
    async refreshMeta() {
      try {
        const me = await fetchSupportMe()
        this.platformAdmin = Boolean(me && me.platformAdmin)
        this.permissions = (me && me.permissions) || []
        if (!this.platformAdmin && (!this.permissions || !this.permissions.length)) {
          this.applySessionFallback()
        }
      } catch {
        this.applySessionFallback()
      }
      try {
        if (this.can('MANAGE_SUBSCRIPTIONS')) {
          const proofs = await fetchPendingSubscriptionProofs()
          this.pendingProofs = ((proofs && proofs.pending) || []).length
        }
      } catch {
        this.pendingProofs = 0
      }
      try {
        if (this.can('MANAGE_TICKETS')) {
          const t = await fetchSupportTickets('OPEN')
          this.openTickets = Number((t && t.openCount) || ((t && t.tickets) || []).length) || 0
        }
      } catch {
        this.openTickets = 0
      }
      try {
        const n = await fetchSupportNotifications()
        this.unreadNotifications = Number((n && n.unreadCount) || 0) || 0
      } catch {
        this.unreadNotifications = 0
      }
    },
    async doLogout() {
      await logout()
      this.$router.replace({ name: 'login' }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.support-shell {
  min-height: 100vh;
  background: #f1f5f9;
}

.support-body {
  margin-top: -32px;
}

.admin-nav-card {
  border-color: rgba(15, 23, 42, 0.08) !important;
  background: rgba(255, 255, 255, 0.96) !important;
}
</style>
