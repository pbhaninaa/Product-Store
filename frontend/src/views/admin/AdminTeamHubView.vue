<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6 mb-4" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-sm-row align-start">
        <v-avatar color="primary" size="48" class="mb-3 mb-sm-0 mr-sm-4 flex-shrink-0">
          <v-icon dark>{{ staffOnly ? 'savings' : 'badge' }}</v-icon>
        </v-avatar>
        <div class="min-width-0">
          <div class="text-h6 font-weight-bold mb-1">{{ staffOnly ? 'My income' : 'Team & Payroll' }}</div>
          <p class="text-body-2 text--secondary mb-0">
            <template v-if="staffOnly">
              Expected pay for paid orders and confirmed bookings the store owner has attributed to you.
            </template>
            <template v-else>
              Create staff logins and set their pay rates under <strong>Login team</strong>, work out what each person is
              owed and mark jobs paid under <strong>Payroll</strong>, and check your own expected pay under
              <strong>My income</strong>.
            </template>
          </p>
        </div>
      </div>
    </v-card>

    <v-card v-if="tabs.length > 1" flat class="admin-nav-card mb-4 pa-1 rounded-xl" outlined>
      <v-tabs v-model="currentTab" background-color="transparent" show-arrows>
        <v-tab v-for="t in tabs" :key="t.key" class="text-none font-weight-bold">
          <v-icon left small class="mr-1">{{ t.icon }}</v-icon>
          {{ t.label }}
        </v-tab>
      </v-tabs>
    </v-card>

    <v-tabs-items v-model="currentTab">
      <v-tab-item v-for="t in tabs" :key="t.key">
        <admin-team-view-content v-if="t.key === 'team'" />
        <admin-team-payroll-view-content v-else-if="t.key === 'payroll'" />
        <admin-my-income-view-content v-else />
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script>
import AdminTeamViewContent from './AdminTeamView.vue'
import AdminTeamPayrollViewContent from './AdminTeamPayrollView.vue'
import AdminMyIncomeViewContent from './AdminMyIncomeView.vue'
import { isMerchantStaffOnly } from '@/utils/merchantRoles'
import { teamHubTabsFor } from './adminRules'

const TAB_META = {
  team: { label: 'Login team', icon: 'badge' },
  payroll: { label: 'Payroll', icon: 'account_balance_wallet' },
  income: { label: 'My income', icon: 'savings' }
}

export default {
  name: 'AdminTeamHubView',
  components: {
    AdminTeamViewContent,
    AdminTeamPayrollViewContent,
    AdminMyIncomeViewContent
  },
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return {
      currentTab: 0
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    },
    staffOnly() {
      return isMerchantStaffOnly(this.user)
    },
    /** Staff only get My income; Login team and Payroll use owner-only APIs. */
    tabs() {
      return teamHubTabsFor(this.user).map((key) => ({ key, ...TAB_META[key] }))
    }
  },
  watch: {
    '$route.query.tab': {
      immediate: true,
      handler() {
        this.syncTabFromRoute()
      }
    },
    tabs() {
      this.syncTabFromRoute()
    }
  },
  methods: {
    syncTabFromRoute() {
      const want = String(this.$route.query.tab || 'team')
      const idx = this.tabs.findIndex((t) => t.key === want)
      this.currentTab = idx < 0 ? 0 : idx
    }
  }
}
</script>
