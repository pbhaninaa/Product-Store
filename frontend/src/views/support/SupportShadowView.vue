<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="d-flex mb-3">
        <div class="card-label mb-0">Shadow Help</div>
        <v-spacer />
        <v-text-field
          v-model="q"
          dense
          outlined
          hide-details
          label="Search merchants"
          style="max-width: 280px"
          @keyup.enter="load"
        />
        <v-btn class="ml-2 text-none" color="primary" :loading="loading" @click="load">Search</v-btn>
      </div>
      <p class="text-body-2 text--secondary mb-4">
        Enter a merchant admin session as the store owner to diagnose issues. Exit from the banner in merchant admin.
      </p>
      <v-data-table :headers="headers" :items="merchants" :items-per-page="12" class="elevation-0">
        <template v-slot:[`item.actions`]="{ item }">
          <v-btn small color="primary" class="text-none" :loading="acting === item.slug" @click="enter(item)">
            Enter
          </v-btn>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { enterShadowSession } from '@/services/auth'
import { fetchShadowMerchants, mintShadowToken } from '@/services/supportApi'

export default {
  name: 'SupportShadowView',
  data() {
    return {
      q: '',
      loading: false,
      acting: null,
      error: '',
      merchants: [],
      headers: [
        { text: 'Name', value: 'name' },
        { text: 'Slug', value: 'slug' },
        { text: 'Owner', value: 'ownerEmail' },
        { text: '', value: 'actions', sortable: false }
      ]
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
        const res = await fetchShadowMerchants(this.q)
        this.merchants = (res && res.merchants) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load merchants'
      } finally {
        this.loading = false
      }
    },
    async enter(item) {
      this.acting = item.slug
      this.error = ''
      try {
        const res = await mintShadowToken(item.slug)
        enterShadowSession(res.token, res.tenant)
        await this.$router.push({
          name: 'merchant-admin',
          params: { merchantSlug: res.tenant.slug }
        })
      } catch (e) {
        this.error = (e && e.message) || 'Could not start shadow session'
      } finally {
        this.acting = null
      }
    }
  }
}
</script>
