<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6 mb-4" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-sm-row align-start">
        <v-avatar color="primary" size="48" class="mb-3 mb-sm-0 mr-sm-4 flex-shrink-0">
          <v-icon dark>{{ incomeOnly ? 'savings' : 'badge' }}</v-icon>
        </v-avatar>
        <div class="min-width-0">
          <div class="text-h6 font-weight-bold mb-1">{{ incomeOnly ? 'My income' : 'Team & Payroll' }}</div>
          <p class="text-body-2 text--secondary mb-0">
            <template v-if="incomeOnly">
              Expected pay for paid orders and confirmed bookings the store owner has attributed to you.
            </template>
            <template v-else-if="!isStoreOwner">
              Work out what each person is owed and mark jobs paid under <strong>Payroll</strong>, and check your own
              expected pay under <strong>My income</strong>.
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
import adminAccessMixin from './mixins/adminAccessMixin'
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
  mixins: [adminAccessMixin],
  data() {
    return {
      currentTab: 0
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    },
    /** Login team is owner-only; Payroll needs TEAM_PAYROLL; everyone gets My income. */
    tabs() {
      const access = this.adminSession && this.adminSession.access
      return teamHubTabsFor(this.user, access).map((key) => ({ key, ...TAB_META[key] }))
    },
    incomeOnly() {
      return this.tabs.length === 1
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
