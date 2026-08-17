<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6 mb-4" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-md-row align-start">
        <v-avatar color="primary" size="48" class="mb-3 mb-md-0 mr-md-4">
          <v-icon dark>badge</v-icon>
        </v-avatar>
        <div>
          <div class="text-h6 font-weight-bold mb-1">Team</div>
          <p class="text-body-2 text--secondary mb-0">
            Add team members, activation, and weekly bookable hours for salon booking.
          </p>
        </div>
      </div>
    </v-card>

    <v-card flat class="admin-nav-card mb-4 pa-1 rounded-xl" outlined>
      <v-tabs v-model="currentTab" background-color="transparent">
        <v-tab class="text-none font-weight-bold">
          <v-icon left small class="mr-1">badge</v-icon>
          Login team
        </v-tab>
        <v-tab class="text-none font-weight-bold">
          <v-icon left small class="mr-1">account_balance_wallet</v-icon>
          Payroll
        </v-tab>
        <v-tab class="text-none font-weight-bold">
          <v-icon left small class="mr-1">savings</v-icon>
          My income
        </v-tab>
      </v-tabs>
    </v-card>

    <v-tabs-items v-model="currentTab">
      <v-tab-item>
        <admin-team-view-content />
      </v-tab-item>
      <v-tab-item>
        <admin-team-payroll-view-content />
      </v-tab-item>
      <v-tab-item>
        <admin-my-income-view-content />
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script>
import AdminTeamViewContent from './AdminTeamView.vue'
import AdminTeamPayrollViewContent from './AdminTeamPayrollView.vue'
import AdminMyIncomeViewContent from './AdminMyIncomeView.vue'

export default {
  name: 'AdminTeamHubView',
  components: {
    AdminTeamViewContent,
    AdminTeamPayrollViewContent,
    AdminMyIncomeViewContent
  },
  data() {
    return {
      currentTab: 0
    }
  },
  watch: {
    '$route.query.tab': {
      immediate: true,
      handler(tab) {
        if (tab === 'payroll') this.currentTab = 1
        else if (tab === 'income') this.currentTab = 2
        else this.currentTab = 0
      }
    }
  }
}
</script>
