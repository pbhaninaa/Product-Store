<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="sc-toolbar">
        <div class="card-label mb-0">Shadow Help</div>
        <div class="sc-toolbar__end shadow-search">
          <v-text-field
            v-model="q"
            dense
            outlined
            hide-details
            label="Search merchants"
            class="shadow-search__field"
            @keyup.enter="load"
          />
          <v-btn class="text-none" color="primary" :loading="loading" @click="load">Search</v-btn>
        </div>
      </div>
      <p class="text-body-2 text--secondary mb-3">
        Enter a merchant admin session as the store owner to diagnose issues. Exit from the banner in merchant admin.
      </p>
      <v-data-table
        :headers="headers"
        :items="merchants"
        :items-per-page="12"
        :loading="loading"
        loading-text="Loading merchants…"
        class="elevation-0"
      >
        <template v-slot:[`item.actions`]="{ item }">
          <v-btn
            small
            color="primary"
            class="text-none"
            :loading="acting === item.slug"
            :disabled="Boolean(acting)"
            @click="enter(item)"
          >
            Enter
          </v-btn>
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script>
import { fetchShadowMerchants } from '@/services/supportApi'
import { openMerchantAdminAsShadow, shadowErrorMessage } from './shadowNavigation'

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
        { text: '', value: 'actions', sortable: false, align: 'end' }
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
        await openMerchantAdminAsShadow(this.$router, item.slug, 'merchant-admin')
      } catch (e) {
        this.error = shadowErrorMessage(e)
      } finally {
        this.acting = null
      }
    }
  }
}
</script>

<style scoped>
.shadow-search {
  flex: 1 1 280px;
  justify-content: flex-end;
}

.shadow-search__field {
  flex: 1 1 200px;
  max-width: 280px;
}
</style>
