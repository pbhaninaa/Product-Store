<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
      <div class="d-flex flex-wrap align-baseline mb-1">
        <div class="card-label mb-0 my-income__title">
          My income<template v-if="isEmployee && bundle.displayName">: {{ bundle.displayName }}</template>
        </div>
        <v-spacer />
        <div class="text-body-2 text--secondary">{{ periodLabel }}</div>
      </div>
      <div v-if="isEmployee" class="text-caption text--secondary mb-4">
        {{ payMethodLabel(bundle.payMethod) }} · rate {{ formatZar(bundle.payRate || 0) }}
      </div>

      <div class="my-income__filters mb-4">
        <AppDateField v-model="startDate" dense outlined hide-details label="From" clearable class="rounded-lg my-income__date" />
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
          class="rounded-lg my-income__date"
        />
        <div class="d-flex">
          <v-btn depressed color="primary" :loading="loading" @click="load">Refresh</v-btn>
          <v-btn v-if="startDate || endDate" text class="ml-1" @click="clearDates">All dates</v-btn>
        </div>
      </div>

      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>

      <template v-if="isEmployee">
        <v-row dense class="admin-card-row mb-4">
          <v-col v-for="tile in tiles" :key="tile.key" cols="12" sm="4">
            <div class="stat-tile rounded-xl pa-4" :class="{ 'stat-tile--accent': tile.accent }">
              <div class="stat-tile__label admin-clamp-1">{{ tile.label }}</div>
              <div class="stat-tile__value">{{ formatZar(tile.total) }}</div>
              <div class="stat-tile__hint">{{ tile.hint }}</div>
            </div>
          </v-col>
        </v-row>

        <v-simple-table v-if="lines.length" dense class="my-income__table">
          <thead>
            <tr>
              <th>Job</th>
              <th>When</th>
              <th class="text-right">Amount</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in lines" :key="line.jobType + line.jobId">
              <td class="text-caption text-no-wrap" :title="jobTypeLabel(line.jobType) + ' ' + shortRef(line.jobId)">
                {{ incomeJobRef(line) }}
              </td>
              <td class="text-caption text-no-wrap">{{ incomeWhen(line) }}</td>
              <td class="text-right text-no-wrap">{{ formatZar(line.expectedAmount || 0) }}</td>
              <td>
                <v-chip
                  x-small
                  label
                  :color="incomeStatusMeta(line).color"
                  :text-color="incomeStatusMeta(line).textColor"
                  class="font-weight-bold"
                >
                  {{ incomeStatusMeta(line).label }}
                </v-chip>
              </td>
            </tr>
          </tbody>
        </v-simple-table>
        <p v-else-if="!loading && !error" class="text-body-2 text--secondary mb-0">
          {{ startDate && endDate ? 'No work in these dates. Try "All dates".' : 'No work yet. Bookings made with you show here as Upcoming, and move to Owed once the work is done.' }}
        </p>
      </template>
      <p v-else-if="!loading && !error" class="text-body-2 text--secondary mb-0">
        No attributed work yet. Ask the owner to assign you on paid orders / confirmed bookings.
      </p>
    </v-card>
  </div>
</template>

<script>
import { fetchMyExpectedIncome } from '@/services/teamApi'
import { rules } from '@/utils/validation'
import { jobTypeLabel, payMethodLabel, shortRef } from '@/utils/displayLabels'
import { formatZar } from '@/utils/price'
import {
  incomeJobRef,
  incomePeriodLabel,
  incomeStatusMeta,
  incomeWhen,
  sortIncomeLines
} from '@/utils/myIncome'

const jobs = (n, one, many) => `${n} ${n === 1 ? one : many}`

import AppDateField from '@/components/AppDateField.vue'

export default {
  name: 'AdminMyIncomeView',
  components: { AppDateField },
  inject: { adminSession: { default: () => ({ user: null }) } },
  data() {
    return { loading: false, error: '', startDate: '', endDate: '', bundle: null }
  },
  computed: {
    endDateRules() {
      return [rules.notBefore(() => this.startDate, 'To date cannot be before the From date')]
    },
    user() {
      return this.adminSession && this.adminSession.user
    },
    /** An owner without an employee record gets an empty bundle (employeeId null). */
    isEmployee() {
      return !!(this.bundle && this.bundle.employeeId)
    },
    periodLabel() {
      const b = this.bundle || {}
      return incomePeriodLabel(b.payPeriodStart, b.payPeriodEnd)
    },
    lines() {
      return sortIncomeLines(this.bundle && this.bundle.lines)
    },
    tiles() {
      const b = this.bundle || {}
      const owed = b.owedTotal != null ? b.owedTotal : b.pendingExpected
      const owedCount = b.owedCount != null ? b.owedCount : this.lines.filter((l) => !l.employerPaid).length
      return [
        {
          key: 'owed',
          label: 'Owed to me',
          total: Number(owed || 0),
          hint: `${jobs(owedCount, 'job', 'jobs')} done, not paid out yet`,
          accent: true
        },
        {
          key: 'upcoming',
          label: 'Upcoming',
          total: Number(b.upcomingTotal || 0),
          hint: `${jobs(Number(b.upcomingCount || 0), 'booking', 'bookings')} not done yet`
        },
        {
          key: 'paid',
          label: 'Paid out',
          total: Number(b.paidOutTotal || 0),
          hint: 'Marked paid by your employer'
        }
      ]
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
    formatZar,
    incomeJobRef,
    incomeStatusMeta,
    incomeWhen,
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
        this.bundle = await fetchMyExpectedIncome(this.$route, {
          startDate: this.startDate || undefined,
          endDate: this.endDate || undefined
        })
      } catch (e) {
        this.error = (e && e.message) || 'Not available (owner only account or no employee record)'
        this.bundle = null
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.my-income__title {
  min-width: 0;
  overflow-wrap: anywhere;
}

.my-income__filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (min-width: 600px) {
  .my-income__filters {
    flex-direction: row;
    align-items: center;
  }

  .my-income__date {
    flex: 0 1 180px;
    max-width: 180px;
  }
}

.my-income__table th,
.my-income__table td {
  padding-left: 6px !important;
  padding-right: 6px !important;
}
</style>
