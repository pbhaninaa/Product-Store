<template>
  <v-container class="py-10 py-md-16 px-3 px-sm-4" style="max-width: 860px">
    <div class="section-kicker mb-2">Help</div>
    <h1 class="text-h4 font-weight-bold mb-2">Help centre</h1>
    <p class="text-body-2 text--secondary mb-8">
      Answers for customers and shop owners. For a live order, use Track with your email and order ID.
    </p>

    <v-btn
      v-if="merchantSlug"
      outlined
      color="primary"
      class="text-none font-weight-bold mb-8"
      :to="{ name: 'merchant-track', params: { merchantSlug } }"
    >
      Track an order
    </v-btn>

    <v-alert v-if="error" type="error" dense outlined class="mb-6 rounded-lg">{{ error }}</v-alert>
    <div v-if="loading" class="text-center py-8">
      <v-progress-circular indeterminate color="primary" />
    </div>
    <div v-for="section in sections" :key="section.id" class="mb-8">
      <h2 class="text-h6 font-weight-bold mb-3">{{ section.title }}</h2>
      <v-expansion-panels accordion class="rounded-lg">
        <v-expansion-panel v-for="(item, i) in section.items || []" :key="i">
          <v-expansion-panel-header class="font-weight-medium">{{ item.question }}</v-expansion-panel-header>
          <v-expansion-panel-content>
            <p class="text-body-2 mb-0">{{ item.answer }}</p>
          </v-expansion-panel-content>
        </v-expansion-panel>
      </v-expansion-panels>
    </div>
  </v-container>
</template>

<script>
import { fetchPublicFaqs } from '@/services/publicStore'

export default {
  name: 'HelpCenterView',
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
