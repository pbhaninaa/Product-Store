<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="d-flex mb-3">
        <div class="card-label mb-0">Audit log</div>
        <v-spacer />
        <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-data-table :headers="headers" :items="entries" :items-per-page="25" class="elevation-0" />
    </v-card>
  </div>
</template>

<script>
import { fetchSupportAudit } from '@/services/supportApi'

export default {
  name: 'SupportAuditView',
  data() {
    return {
      loading: false,
      error: '',
      entries: [],
      headers: [
        { text: 'When', value: 'createdAt' },
        { text: 'Actor', value: 'actorEmail' },
        { text: 'Action', value: 'action' },
        { text: 'Entity', value: 'entityType' },
        { text: 'Id', value: 'entityId' },
        { text: 'Detail', value: 'detail' }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
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
