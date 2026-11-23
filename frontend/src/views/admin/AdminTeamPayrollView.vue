<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6 mb-4" elevation="3" rounded="xl">
      <div class="d-flex flex-column flex-sm-row align-sm-center mb-4">
        <div class="card-label mb-0">Payment calculations</div>
        <v-spacer />
        <AppDateField v-model="startDate" dense outlined hide-details label="From" clearable class="rounded-lg mr-sm-2 mb-2 mb-sm-0" style="max-width: 180px" />
        <AppDateField
          ref="endDateField"
          v-model="endDate"
          dense
          outlined
          hide-details="auto"
          label="To"
          clearable
          :min="startDate || undefined"
          :rules="endDateRules"
          class="rounded-lg mr-sm-2 mb-2 mb-sm-0"
          style="max-width: 180px"
        />
        <v-btn depressed color="primary" class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        <v-btn text class="text-none ml-1" @click="clearDates">All time</v-btn>
      </div>
      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>
      <v-progress-linear v-if="loading" indeterminate height="3" class="mb-3" />
      <v-expansion-panels v-if="calculations.length" accordion>
        <v-expansion-panel v-for="row in calculations" :key="row.employeeId">
          <v-expansion-panel-header>
            <div class="d-flex flex-wrap align-center" style="width: 100%">
              <strong class="mr-3">{{ row.displayName || row.email }}</strong>
              <span class="text-caption text--secondary mr-3">{{ payMethodLabel(row.payMethod) }} @ R{{ row.payRate }}</span>
              <v-chip small color="primary" class="mr-2">Pending R{{ Number(row.pendingExpected || 0).toFixed(2) }}</v-chip>
              <v-chip small outlined>{{ row.jobCount || 0 }} unpaid jobs</v-chip>
              <v-spacer />
              <v-btn small depressed color="success" class="text-none mr-2" @click.stop="payAll(row)">Pay all</v-btn>
            </div>
          </v-expansion-panel-header>
          <v-expansion-panel-content>
            <v-simple-table dense>
              <thead>
                <tr>
                  <th>Job</th>
                  <th>Type</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in row.lines || []" :key="line.jobId + line.jobType">
                  <td class="text-caption">{{ shortRef(line.jobId) }}</td>
                  <td>{{ jobTypeLabel(line.jobType) }}</td>
                  <td>R{{ Number(line.expectedAmount || 0).toFixed(2) }}</td>
                  <td>{{ line.employerPaid ? 'Paid' : 'Unpaid' }}</td>
                  <td>
                    <v-btn v-if="!line.employerPaid" x-small depressed class="text-none" @click="markPaid(row, line)">Mark paid</v-btn>
                    <v-btn v-else x-small text class="text-none" @click="unmark(row, line)">Unmark</v-btn>
                  </td>
                </tr>
              </tbody>
            </v-simple-table>
            <p v-if="!(row.lines || []).length" class="text-caption text--secondary mb-0">
              No paid orders or bookings credited to this person in this period. Jobs are credited when they confirm, start or complete a booking; you can also use “Assign to team member” on Orders or Bookings.
            </p>
          </v-expansion-panel-content>
        </v-expansion-panel>
      </v-expansion-panels>
      <p v-else-if="!loading" class="text-body-2 text--secondary mb-0">No active team members.</p>
    </v-card>
  </div>
</template>

<script>
import {
  fetchPaymentCalculations,
  markPayrollJobPaid,
  payAllPayroll,
  unmarkPayrollJobPaid
} from '@/services/teamApi'
import { rules } from '@/utils/validation'
import { jobTypeLabel, payMethodLabel, shortRef } from '@/utils/displayLabels'
import { defaultDateRange } from '@/utils/dateRange'

import AppDateField from '@/components/AppDateField.vue'

export default {
  name: 'AdminTeamPayrollView',
  components: { AppDateField },
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    // Opens on the last 7 days (FR-1). Set only here, so a change or "All time" is never overwritten.
    const { startDate, endDate } = defaultDateRange()
    return {
      loading: false,
      error: '',
      startDate,
      endDate,
      calculations: []
    }
  },
  computed: {
    endDateRules() {
      return [rules.notBefore(() => this.startDate, 'To date cannot be before the From date')]
    },
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
    jobTypeLabel,
    payMethodLabel,
    shortRef,
    clearDates() {
      this.startDate = ''
      this.endDate = ''
      this.load()
    },
    async load() {
      const end = this.$refs.endDateField
      if (end && !end.validate(true)) return
      this.loading = true
      this.error = ''
      try {
        const res = await fetchPaymentCalculations(this.$route, {
          startDate: this.startDate || undefined,
          endDate: this.endDate || undefined
        })
        this.calculations = Array.isArray(res.calculations) ? res.calculations : []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load calculations'
      } finally {
        this.loading = false
      }
    },
    async markPaid(row, line) {
      try {
        await markPayrollJobPaid(this.$route, {
          employeeId: row.employeeId,
          jobId: line.jobId,
          jobType: line.jobType,
          includeBonus: true
        })
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Mark paid failed'
      }
    },
    async unmark(row, line) {
      try {
        await unmarkPayrollJobPaid(this.$route, {
          employeeId: row.employeeId,
          jobId: line.jobId,
          jobType: line.jobType
        })
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Unmark failed'
      }
    },
    async payAll(row) {
      if (!confirm(`Mark all unpaid jobs paid for ${row.displayName}?`)) return
      try {
        await payAllPayroll(this.$route, { employeeId: row.employeeId, includeBonus: true })
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Pay all failed'
      }
    }
  }
}
</script>
