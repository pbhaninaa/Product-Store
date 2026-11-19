<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Help tickets</div>
        <div class="sc-toolbar__end">
          <v-btn-toggle v-model="statusFilter" dense mandatory>
            <v-btn small value="OPEN" class="text-none">Open</v-btn>
            <v-btn small value="" class="text-none">All</v-btn>
          </v-btn-toggle>
          <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        </div>
      </div>
      <v-data-table
        :headers="headers"
        :items="tickets"
        :items-per-page="15"
        :loading="loading"
        loading-text="Loading tickets…"
        class="elevation-0"
      >
        <template v-slot:[`item.status`]="{ item }">{{ ticketStatusLabel(item.status) }}</template>
        <template v-slot:[`item.createdAt`]="{ item }">{{ formatDateTime(item.createdAt) }}</template>
        <template v-slot:[`item.subject`]="{ item }">
          <span class="sc-clamp-2" :title="item.subject">{{ item.subject }}</span>
        </template>
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
import { formatDateTime, ticketStatusLabel } from '@/utils/displayLabels'

export default {
  name: 'SupportTicketsView',
  inject: ['supportDialog'],
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
        { text: '', value: 'actions', sortable: false, align: 'end' }
      ]
    }
  },
  watch: {
    statusFilter() {
      this.load()
    },
    '$route.query.ticketId'() {
      this.load()
    }
  },
  created() {
    this.load()
  },
  methods: {
    formatDateTime,
    ticketStatusLabel,
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await fetchSupportTickets(this.statusFilter || undefined)
        this.tickets = (res && res.tickets) || []
        const ticketId = String((this.$route.query && this.$route.query.ticketId) || '').trim()
        if (ticketId) {
          this.tickets = this.tickets.slice().sort((a, b) => {
            const aMatch = String(a.id || '') === ticketId ? 0 : 1
            const bMatch = String(b.id || '') === ticketId ? 0 : 1
            return aMatch - bMatch
          })
        }
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load tickets'
      } finally {
        this.loading = false
      }
    },
    async resolve(item) {
      if (!this.supportDialog) return
      let note = ''
      try {
        note = await this.supportDialog.prompt({
          title: 'Resolve ticket',
          message: `Mark "${item.subject || 'ticket'}" as resolved.`,
          inputLabel: 'Resolution note (optional)',
          maxLength: 2000,
          confirmLabel: 'Resolve',
          tone: 'success',
          confirmColor: 'success'
        })
      } catch {
        return
      }
      this.acting = item.id
      try {
        await resolveSupportTicket(item.id, note || '')
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
