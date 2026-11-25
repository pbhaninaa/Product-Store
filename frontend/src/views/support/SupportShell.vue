<template>
  <div class="support-shell">
    <workspace-sidebar
      ref="sidebar"
      title="Platform console"
      :subtitle="sessionEmail"
      icon="support_agent"
      :items="sidebarItems"
      :footer-actions="sidebarFooter"
      storage-key="ps_support_sidebar_rail"
      @action="onSidebarAction"
    />
    <div class="support-shell__main">
    <section class="admin-hero">
      <v-container class="pt-6 pb-10 pt-md-8 px-3 px-sm-4">
        <div class="admin-hero__inner d-flex flex-column flex-md-row align-start align-md-center">
          <div>
            <v-btn
              v-if="$vuetify.breakpoint.smAndDown"
              small
              depressed
              color="primary"
              class="text-none font-weight-bold mb-3"
              @click="$refs.sidebar.open()"
            >
              <v-icon left small>menu</v-icon>
              Menu
            </v-btn>
            <div class="admin-kicker mb-2">
              <v-icon small color="secondary" class="mr-1">support_agent</v-icon>
              Protected area
            </div>
            <h1 class="admin-title">Platform console</h1>
            <p class="admin-lead mb-0">
              Merchants, billing, tickets, shadow help, alerts, and platform ops.
            </p>
          </div>
          <div class="mt-4 mt-md-0 ml-md-auto d-flex flex-wrap align-center sc-gap">
            <v-chip class="text-none font-weight-bold px-4" color="primary" outlined label>
              <v-icon left small color="primary">cloud_done</v-icon>
              Live sync
            </v-chip>
          </div>
        </div>
      </v-container>
    </section>

    <v-container class="pb-6 support-body px-3 px-sm-4">
      <router-view />
    </v-container>
    </div>
    <support-action-dialog ref="actionDialog" />
  </div>
</template>

<script>
import SupportActionDialog from '@/components/support/SupportActionDialog.vue'
import WorkspaceSidebar from '@/components/nav/WorkspaceSidebar.vue'
import { getSessionUser, logout } from '@/services/auth'
import { belongsToInbox } from '@/services/notificationsRealtime'
import {
  fetchSupportMe,
  fetchSupportNotifications,
  fetchSupportTickets
} from '@/services/supportApi'
import { isTabVisible } from './supportNav'

/** Icons from material-design-icons-iconfont (avoid newer glyphs missing in the bundle). */
const DEFAULT_SUPPORT_PERMS = [
  'MANAGE_SUBSCRIPTIONS',
  'MANAGE_MERCHANTS',
  'USE_SHADOW',
  'MANAGE_TICKETS',
  'VIEW_OPS'
]

/** Support sign-in lives on the platform tenant's admin login screen. */
const SUPPORT_META_POLL_MS = 3 * 60 * 1000
const SUPPORT_SIGN_IN_ROUTE =Object.freeze({ path: '/m/platform/admin', query: { support: '1' } })

export default {
  name: 'SupportShell',
  components: { SupportActionDialog, WorkspaceSidebar },
  provide() {
    return {
      supportDialog: {
        confirm: (opts) => this.$refs.actionDialog.confirm(opts),
        prompt: (opts) => this.$refs.actionDialog.prompt(opts),
        select: (opts) => this.$refs.actionDialog.select(opts),
        info: (opts) => this.$refs.actionDialog.info(opts)
      },
      // Reactive: children's computed props re-evaluate once /api/support/me has loaded.
      supportAccess: {
        can: (perm) => this.can(perm),
        isPlatformAdmin: () => this.platformAdmin
      }
    }
  },
  data() {
    return {
      permissions: [],
      platformAdmin: false,
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
        // No badge: legacy EFT proof approval is retired, so there is nothing pending to act on.
        { name: 'support-subscriptions', label: 'Billing', icon: 'payment', perm: 'MANAGE_SUBSCRIPTIONS' },
        { name: 'support-payouts', label: 'Payouts', icon: 'account_balance', adminOnly: true },
        { name: 'support-payout-history', label: 'Payout history', icon: 'receipt_long', adminOnly: true },
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
        { name: 'support-users', label: 'Users', icon: 'people', perm: 'VIEW_USERS' },
        { name: 'support-staff', label: 'Staff', icon: 'group', perm: 'MANAGE_STAFF' },
        { name: 'support-referrals', label: 'Referrals', icon: 'share', perm: 'VIEW_OPS' },
        { name: 'support-account', label: 'Account', icon: 'person', perm: null }
      ]
    },
    visibleTabs() {
      return this.allTabs.filter((t) => isTabVisible(t, { platformAdmin: this.platformAdmin, can: this.can }))
    },
    /** Sidebar sections, in display order; pages keep their relative order inside a section. */
    sidebarItems() {
      const GROUP_BY_PAGE = {
        'support-dashboard': 'Overview',
        'support-notifications': 'Overview',
        'support-merchants': 'Merchants',
        'support-subscriptions': 'Merchants',
        'support-payouts': 'Merchants',
        'support-payout-history': 'Merchants',
        'support-referrals': 'Merchants',
        'support-orders': 'Operations',
        'support-bookings': 'Operations',
        'support-tickets': 'Operations',
        'support-shadow': 'Operations',
        'support-features': 'Platform',
        'support-users': 'Platform',
        'support-staff': 'Platform',
        'support-audit': 'Platform',
        'support-help-contact': 'Platform',
        'support-account': 'Platform'
      }
      const ORDER = ['Overview', 'Merchants', 'Operations', 'Platform']
      const rank = (name) => {
        const i = ORDER.indexOf(GROUP_BY_PAGE[name] || 'Platform')
        return i < 0 ? ORDER.length : i
      }
      return this.visibleTabs
        .map((t, index) => ({ t, index }))
        .sort((a, b) => rank(a.t.name) - rank(b.t.name) || a.index - b.index)
        .map(({ t }) => ({
          key: t.name,
          label: t.label,
          icon: t.icon,
          to: { name: t.name },
          exact: t.exact === true,
          badge: t.badge || 0,
          group: GROUP_BY_PAGE[t.name] || 'Platform'
        }))
    },
    sidebarFooter() {
      return [
        { key: 'account', label: 'Account', icon: 'person', to: { name: 'support-account' } },
        { key: 'logout', label: 'Sign out', icon: 'logout' }
      ]
    },
    sessionEmail() {
      const u = getSessionUser()
      return (u && u.email) || ''
    }
  },
  created() {
    this.refreshMeta()
    // Platform notifications are pushed instantly (unread count in the payload; a new ticket also
    // refreshes the open-ticket count). This slow poll only catches counts no notification covers,
    // e.g. a ticket resolved by a colleague.
    this.pollTimer = setInterval(() => this.refreshMeta(), SUPPORT_META_POLL_MS)
    this._onPushed = (payload) => {
      if (!belongsToInbox(payload, '')) return
      if (payload.unread != null) this.unreadNotifications = Number(payload.unread) || 0
      if (payload.notificationType === 'SUPPORT_TICKET') this.refreshMeta()
    }
    this._onResync = () => this.refreshMeta()
    this.$root.$on('notification-received', this._onPushed)
    this.$root.$on('notifications-resync', this._onResync)
  },
  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
    this.$root.$off('notification-received', this._onPushed)
    this.$root.$off('notifications-resync', this._onResync)
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
    onSidebarAction(key) {
      if (key === 'logout') this.doLogout()
    },
    async doLogout() {
      await logout()
      this.$router.replace(SUPPORT_SIGN_IN_ROUTE).catch(() => {})
    }
  }
}
</script>

<style scoped>
.support-shell {
  min-height: 100vh;
  background: #f1f5f9;
  display: flex;
  align-items: stretch;
}

.support-shell__main {
  flex: 1 1 auto;
  min-width: 0;
}

.support-body {
  margin-top: -24px;
}
</style>

<!--
  Shared support-console layout helpers (unscoped, but every rule is namespaced under .support-shell).
  Card grids: <v-col class="d-flex"> + <v-card class="sc-card"> + body .sc-card__body + actions .sc-card__actions
  give siblings in a row the same height with their actions on one line, without padding cards out.
-->
<style>
.support-shell .sc-gap {
  gap: 8px;
}

.support-shell .sc-page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  margin-bottom: 16px;
}

.support-shell .sc-page-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.support-shell .sc-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.support-shell .sc-toolbar__end {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.support-shell .sc-card {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
  width: 100%;
}

.support-shell .sc-card__body {
  flex: 1 1 auto;
  min-width: 0;
}

.support-shell .sc-card__actions {
  margin-top: auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.support-shell .sc-clamp-1,
.support-shell .sc-clamp-2,
.support-shell .sc-clamp-3 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.support-shell .sc-clamp-1 {
  -webkit-line-clamp: 1;
}

.support-shell .sc-clamp-2 {
  -webkit-line-clamp: 2;
}

.support-shell .sc-clamp-3 {
  -webkit-line-clamp: 3;
}

/* Stat tiles: label on one line, number on a fixed line box, caption reserved for two lines,
   so numbers and captions line up across a row. */
.support-shell .sc-stat {
  background: #fff;
}

.support-shell .sc-stat__label {
  line-height: 1.5rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.support-shell .sc-stat__value {
  line-height: 2rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.support-shell .sc-stat__caption {
  min-height: 2.5em;
  line-height: 1.25em;
}

.support-shell .sc-code {
  background: rgba(15, 23, 42, 0.06);
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12px;
  overflow-wrap: anywhere;
}

/* Long ids/emails in tables wrap instead of forcing horizontal scroll of the whole page. */
.support-shell .v-data-table td {
  overflow-wrap: anywhere;
}
</style>
