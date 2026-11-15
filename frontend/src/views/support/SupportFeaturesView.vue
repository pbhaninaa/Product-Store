<template>
  <div>
    <v-alert v-if="error" type="error" dense outlined class="mb-4 rounded-lg" dismissible @input="error = ''">{{ error }}</v-alert>
    <v-card class="admin-card pa-4" elevation="3" rounded="xl">
      <div class="card-label mb-2">Platform feature flags</div>
      <v-skeleton-loader v-if="loading && !features.length" type="list-item-two-line@3" />
      <p v-else-if="!features.length" class="text-body-2 text--secondary mb-0">No feature flags.</p>
      <v-list v-else class="py-0">
        <v-list-item v-for="f in features" :key="f.key" class="px-0">
          <v-list-item-content>
            <v-list-item-title>{{ f.key }}</v-list-item-title>
            <v-list-item-subtitle class="sc-clamp-2" :title="f.description">{{ f.description }}</v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action>
            <v-switch
              :input-value="f.enabled"
              color="primary"
              hide-details
              @change="(v) => toggle(f, v)"
            />
          </v-list-item-action>
        </v-list-item>
      </v-list>
    </v-card>
  </div>
</template>

<script>
import { fetchPlatformFeatures, setPlatformFeature } from '@/services/supportApi'

export default {
  name: 'SupportFeaturesView',
  data() {
    return { error: '', loading: false, features: [] }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await fetchPlatformFeatures()
        this.features = (res && res.features) || []
      } catch (e) {
        this.error = (e && e.message) || 'Failed to load features'
      } finally {
        this.loading = false
      }
    },
    async toggle(f, enabled) {
      try {
        const updated = await setPlatformFeature(f.key, enabled)
        Object.assign(f, updated)
      } catch (e) {
        this.error = (e && e.message) || 'Update failed'
        await this.load()
      }
    }
  }
}
</script>
