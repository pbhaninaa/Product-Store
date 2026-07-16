<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="d-flex mb-3">
        <div class="card-label mb-0">Help tickets</div>
        <v-spacer />
        <v-btn-toggle v-model="statusFilter" dense mandatory class="mr-2">
          <v-btn small value="OPEN" class="text-none">Open</v-btn>
          <v-btn small value="" class="text-none">All</v-btn>
        </v-btn-toggle>
        <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-data-table :headers="headers" :items="tickets" :items-per-page="15" class="elevation-0">
        <template v-slot:[`item.actions`]="{ item }">
          <v-btn
            v-if="item.status === 'OPEN'"
            small
            color="success"
            class="text-none"
            :loading="acting === item.id"
            @click="resolve(item)"
          >
            Resolve
          </v-btn>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportTickets, resolveSupportTicket } from '@/services/supportApi'

export default {
  name: 'SupportTicketsView',
  data() {
    return {
      loading: false,
      acting: null,
      error: '',
      statusFilter: 'OPEN',
      tickets: [],
      headers: [
        { text: 'Merchant', value: 'tenantName' },
        { text: 'Subject', value: 'subject' },
        { text: 'Status', value: 'status' },
        { text: 'Created', value: 'createdAt' },
        { text: '', value: 'actions', sortable: false }
      ]
    }
  },
  watch: {
    statusFilter() {
      this.load()
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await fetchSupportTickets(this.statusFilter || undefined)
        this.tickets = (res && res.tickets) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load tickets'
      } finally {
        this.loading = false
      }
    },
    async resolve(item) {
      const note = window.prompt('Resolution note (optional):', '') || ''
      this.acting = item.id
      try {
        await resolveSupportTicket(item.id, note)
        await this.load()
      } catch (e) {
        this.error = (e && e.message) || 'Resolve failed'
      } finally {
        this.acting = null
      }
    }
  }
}
</script>
