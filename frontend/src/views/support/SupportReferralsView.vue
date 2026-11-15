<template>
  <div>
    <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Referrals</div>
        <div class="sc-toolbar__end">
          <v-chip outlined small>Total {{ stats.totalReferrals || 0 }}</v-chip>
          <v-chip outlined small>Pending commission {{ stats.pendingCommissions || 0 }}</v-chip>
        </div>
      </div>
      <v-alert v-if="error" type="error" dense outlined class="mb-3 rounded-lg">{{ error }}</v-alert>
      <v-skeleton-loader v-if="loading && !items.length" type="table-tbody" />
      <p v-else-if="!items.length" class="text-body-2 text--secondary mb-0">No referrals yet.</p>
      <v-simple-table v-else>
        <thead>
          <tr>
            <th>Referrer</th>
            <th>Referee</th>
            <th>Role</th>
            <th>Subscribed</th>
            <th>Commission</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>{{ r.referrerEmail }}</td>
            <td>{{ r.refereeEmail }}</td>
            <td>{{ r.refereeRole }}</td>
            <td>{{ r.hasSubscribed ? 'Yes' : 'No' }}</td>
            <td>{{ r.commissionAmount || '—' }} {{ r.commissionPaid ? '(paid)' : '' }}</td>
            <td class="text-right">
              <v-btn
                v-if="r.hasSubscribed && !r.commissionPaid"
                x-small
                depressed
                color="primary"
                class="text-none"
                @click="markPaid(r)"
              >
                Mark paid
              </v-btn>
            </td>
          </tr>
        </tbody>
      </v-simple-table>
    </v-card>
  </div>
</template>

<script>
import { apiFetch } from '@/services/api'

export default {
  name: 'SupportReferralsView',
  data() {
    return { items: [], stats: {}, error: '', loading: false }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.error = ''
      this.loading = true
      try {
        const [list, stats] = await Promise.all([
          apiFetch('/api/referrals/admin/all', { auth: true }),
          apiFetch('/api/referrals/admin/stats', { auth: true })
        ])
        this.items = (list && list.content) || []
        this.stats = stats || {}
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load referrals'
      } finally {
        this.loading = false
      }
    },
    async markPaid(row) {
      try {
        await apiFetch('/api/referrals/admin/mark-paid', {
          method: 'POST',
          auth: true,
          json: { referralId: row.id, notes: 'Paid' }
        })
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Could not mark paid'
      }
    }
  }
}
</script>
