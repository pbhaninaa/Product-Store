<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-sm-row align-sm-center mb-4">
        <div class="card-label mb-0">My expected income</div>
        <v-spacer />
        <v-text-field v-model="startDate" dense outlined hide-details type="date" label="From" class="rounded-lg mr-sm-2 mb-2 mb-sm-0" style="max-width: 180px" />
        <v-text-field v-model="endDate" dense outlined hide-details type="date" label="To" class="rounded-lg mr-sm-2 mb-2 mb-sm-0" style="max-width: 180px" />
        <v-btn depressed color="primary" class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>
      <div v-if="bundle" class="mb-4">
        <div class="text-h6 font-weight-bold">{{ bundle.displayName }}</div>
        <div class="text-body-2 text--secondary">{{ bundle.payMethod }} · rate R{{ bundle.payRate }}</div>
        <v-chip class="mt-2" color="primary">Pending R{{ Number(bundle.pendingExpected || 0).toFixed(2) }}</v-chip>
      </div>
      <v-simple-table v-if="bundle && (bundle.lines || []).length" dense>
        <thead>
          <tr>
            <th>Job</th>
            <th>Type</th>
            <th>When</th>
            <th>Amount</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in bundle.lines" :key="line.jobId + line.jobType">
            <td class="text-caption">{{ String(line.jobId).slice(0, 8) }}</td>
            <td>{{ line.jobType }}</td>
            <td class="text-caption">{{ line.when || '—' }}</td>
            <td>R{{ Number(line.expectedAmount || 0).toFixed(2) }}</td>
            <td>{{ line.employerPaid ? 'Paid' : 'Unpaid' }}</td>
          </tr>
        </tbody>
      </v-simple-table>
      <p v-else-if="!loading && !error" class="text-body-2 text--secondary mb-0">
        No attributed work yet. Ask the owner to assign you on paid orders / confirmed bookings.
      </p>
    </v-card>
  </div>
</template>

<script>
import { fetchMyExpectedIncome } from '@/services/teamApi'

export default {
  name: 'AdminMyIncomeView',
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return { loading: false, error: '', startDate: '', endDate: '', bundle: null }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user
    }
  },
  watch: {
    user: {
      immediate: true,
      handler(u) {
        if (u) this.load()
      }
    }
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        this.bundle = await fetchMyExpectedIncome(this.$route, {
          startDate: this.startDate || undefined,
          endDate: this.endDate || undefined
        })
      } catch (e) {
        this.error = (e && e.message) || 'Not available (owner-only account or no employee record)'
        this.bundle = null
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
