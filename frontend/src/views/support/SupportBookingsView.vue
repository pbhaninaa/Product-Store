<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Recent salon bookings (platform)</div>
        <div class="sc-toolbar__end">
          <v-btn text small class="text-none" :loading="loading" @click="load">Refresh</v-btn>
        </div>
      </div>
      <p v-if="!canShadow" class="text-caption text--secondary mb-3">
        Opening a store's bookings needs the <strong>Use shadow help</strong> permission. Ask a platform admin to grant it.
      </p>
      <v-data-table
        :headers="headers"
        :items="bookings"
        :items-per-page="20"
        :loading="loading"
        loading-text="Loading bookings…"
        class="elevation-0"
      >
        <template v-slot:[`item.link`]="{ item }">
          <v-btn
            v-if="item.tenantSlug && canShadow"
            small
            text
            color="primary"
            class="text-none"
            :loading="opening === item.tenantSlug"
            :disabled="Boolean(opening)"
            @click="open(item)"
          >
            Open
          </v-btn>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchSupportBookings } from '@/services/supportApi'
import { confirmShadowEntry, openMerchantAdminAsShadow, shadowErrorMessage } from './shadowNavigation'

export default {
  name: 'SupportBookingsView',
  inject: {
    supportDialog: { default: null },
    supportAccess: { default: null }
  },
  data() {
    return {
      loading: false,
      opening: '',
      error: '',
      bookings: [],
      headers: [
        { text: 'Merchant', value: 'tenantName' },
        { text: 'Customer', value: 'customerName' },
        { text: 'Status', value: 'status' },
        { text: 'Start', value: 'startAt' },
        { text: '', value: 'link', sortable: false, align: 'end' }
      ]
    }
  },
  computed: {
    canShadow() {
      return Boolean(this.supportAccess && this.supportAccess.can('USE_SHADOW'))
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
        const code = (e && e.body && e.body.error) || (e && e.message) || ''
        if (e && e.status === 403) {
          this.error = 'You do not have permission to view platform bookings.'
        } else if (String(code).includes('Cannot reach the API')) {
          this.error = String(code)
        } else {
          this.error = (e && e.message) || 'Could not load bookings'
        }
      } finally {
        this.loading = false
      }
    },
    async open(item) {
      if (!(await confirmShadowEntry(this.supportDialog, item.tenantName || item.tenantSlug))) return
      this.opening = item.tenantSlug
      this.error = ''
      try {
        await openMerchantAdminAsShadow(this.$router, item.tenantSlug, 'merchant-admin-salon-bookings')
      } catch (e) {
        this.error = shadowErrorMessage(e)
      } finally {
        this.opening = ''
      }
    }
  }
}
</script>
