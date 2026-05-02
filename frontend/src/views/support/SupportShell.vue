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
            <p class="admin-lead mb-0">Use the tabs below to switch between the system dashboard and merchant management.</p>
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
            class="text-none font-weight-bold"
            :to="{ name: 'support-dashboard' }"
            :exact="true"
          >
            <v-icon left small class="mr-1">dashboard</v-icon>
            Dashboard
          </v-tab>
          <v-tab class="text-none font-weight-bold" :to="{ name: 'support-merchants' }">
            <v-icon left small class="mr-1">storefront</v-icon>
            Merchants
          </v-tab>
        </v-tabs>
      </v-card>
      <router-view />
    </v-container>
  </div>
</template>

<script>
import { logout } from '@/services/auth'

export default {
  name: 'SupportShell',
  methods: {
    async doLogout() {
      await logout()
      this.$router.replace({ name: 'merchant-home', params: { merchantSlug: 'demo' } })
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
