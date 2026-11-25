<template>
  <div>
    <page-hero
      kicker="Help"
      kicker-icon="help_outline"
      title="Help centre"
      lead="Answers for customers and business owners. For a live order, use Track with your email and order ID."
      container-class="page-narrow"
    >
      <template v-if="merchantSlug" #actions>
        <v-btn
          outlined
          color="primary"
          class="text-none font-weight-bold"
          :to="{ name: 'merchant-track', params: { merchantSlug } }"
        >
          <v-icon left small aria-hidden="true">local_shipping</v-icon>
          Track an order
        </v-btn>
      </template>
    </page-hero>

    <v-container class="admin-body page-narrow pb-12 pb-md-16 px-3 px-sm-4">
      <v-alert v-if="error" type="error" dense outlined class="mb-6 rounded-lg">{{ error }}</v-alert>
      <v-card v-if="loading" class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
        <v-skeleton-loader type="heading, list-item@4" />
      </v-card>
      <empty-state
        v-else-if="!error && !sections.length"
        icon="help_outline"
        title="No help articles yet"
        text="Check back soon, or contact the store directly."
      />
      <v-card
        v-for="section in sections"
        :key="section.id"
        class="admin-card pa-4 pa-sm-6 mb-4"
        elevation="3"
        rounded="xl"
      >
        <h2 class="text-h6 font-weight-bold mb-3">{{ section.title }}</h2>
        <v-expansion-panels accordion class="rounded-lg">
          <v-expansion-panel v-for="(item, i) in section.items || []" :key="i">
            <v-expansion-panel-header class="font-weight-medium">{{ item.question }}</v-expansion-panel-header>
            <v-expansion-panel-content>
              <p class="text-body-2 mb-0">{{ item.answer }}</p>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-card>
    </v-container>
  </div>
</template>

<script>
import EmptyState from '@/components/layout/EmptyState.vue'
import PageHero from '@/components/layout/PageHero.vue'
import { fetchPublicFaqs } from '@/services/publicStore'

export default {
  name: 'HelpCenterView',
  components: { EmptyState, PageHero },
  data() {
    return {
      loading: true,
      error: '',
      sections: []
    }
  },
  computed: {
    merchantSlug() {
      return String(this.$route.params.merchantSlug || '').trim()
    }
  },
  async created() {
    try {
      this.sections = await fetchPublicFaqs()
    } catch (e) {
      this.error = e && e.message ? e.message : 'Could not load help.'
    } finally {
      this.loading = false
    }
  }
}
</script>
