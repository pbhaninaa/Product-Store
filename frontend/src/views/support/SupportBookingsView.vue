<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="d-flex mb-3">
        <div class="card-label mb-0">Recent salon bookings (platform)</div>
        <v-spacer />
        <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
      </div>
      <v-data-table :headers="headers" :items="bookings" :items-per-page="20" class="elevation-0">
        <template v-slot:[`item.link`]="{ item }">
          <router-link
            v-if="item.tenantSlug"
            :to="{ name: 'merchant-admin-salon-bookings', params: { merchantSlug: item.tenantSlug } }"
          >
            Open
          </router-link>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportBookings } from '@/services/supportApi'

export default {
  name: 'SupportBookingsView',
  data() {
    return {
      loading: false,
      error: '',
      bookings: [],
      headers: [
        { text: 'Merchant', value: 'tenantName' },
        { text: 'Customer', value: 'customerName' },
        { text: 'Status', value: 'status' },
        { text: 'Start', value: 'startAt' },
        { text: '', value: 'link', sortable: false }
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
        const res = await fetchSupportBookings()
        this.bookings = (res && res.bookings) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load bookings'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
