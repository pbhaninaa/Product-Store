<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Audit log</div>
        <div class="sc-toolbar__end">
          <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        </div>
      </div>
      <v-data-table
        :headers="headers"
        :items="entries"
        :items-per-page="25"
        :loading="loading"
        loading-text="Loading audit log…"
        class="elevation-0"
      >
        <template v-slot:[`item.createdAt`]="{ item }">{{ formatDateTime(item.createdAt) }}</template>
        <template v-slot:[`item.action`]="{ item }">{{ auditActionLabel(item.action) }}</template>
        <template v-slot:[`item.entityType`]="{ item }">{{ auditEntityLabel(item.entityType) }}</template>
        <template v-slot:[`item.detail`]="{ item }">{{ auditDetail(item) }}</template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportAudit } from '@/services/supportApi'
import { auditActionLabel, auditDetail, auditEntityLabel, formatDateTime } from '@/utils/displayLabels'

export default {
  name: 'SupportAuditView',
  data() {
    return {
      loading: false,
      error: '',
      entries: [],
      headers: [
        { text: 'When', value: 'createdAt' },
        { text: 'Who', value: 'actorEmail' },
        { text: 'What', value: 'action' },
        { text: 'Area', value: 'entityType' },
        { text: 'Details', value: 'detail', sortable: false }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    auditActionLabel,
    auditDetail,
    auditEntityLabel,
    formatDateTime,
    async load() {
      this.loading = true
      try {
        const res = await fetchSupportAudit()
        this.entries = (res && res.entries) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load audit'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
