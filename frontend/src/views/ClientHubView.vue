<template>
  <v-container class="py-8 px-3 px-sm-4">
    <div class="d-flex align-start mb-6">
      <div>
        <div class="text-overline mb-1">Customer hub</div>
        <h1 class="text-h4 font-weight-bold mb-2">{{ greeting }}</h1>
        <p class="text-body-1 text--secondary mb-0" style="max-width: 42rem">
          Choose items sold or services, then browse every nearby provider (ratings first) and pick the one you like.
        </p>
      </div>
      <v-spacer />
      <v-btn text class="text-none" @click="signOut">Sign out</v-btn>
    </div>

    <v-row class="card-grid">
      <v-col cols="12" sm="6">
        <v-card
          class="hub-card equal-card rounded-xl pa-6"
          outlined
          elevation="2"
          role="button"
          tabindex="0"
          @click="go('shop')"
          @keydown.enter="go('shop')"
        >
          <v-icon large color="primary" class="mb-4">storefront</v-icon>
          <h2 class="text-h5 font-weight-bold mb-2">Items sold</h2>
          <p class="text-body-2 text--secondary mb-4">
            See all nearby providers with ratings and prices, then choose the one you prefer.
          </p>
          <div class="equal-card__actions">
            <v-btn depressed color="primary" class="text-none font-weight-bold btn-amber">Browse items</v-btn>
          </div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6">
        <v-card
          class="hub-card equal-card rounded-xl pa-6"
          outlined
          elevation="2"
          role="button"
          tabindex="0"
          @click="go('salon')"
          @keydown.enter="go('salon')"
        >
          <v-icon large color="primary" class="mb-4">event_available</v-icon>
          <h2 class="text-h5 font-weight-bold mb-2">Services</h2>
          <p class="text-body-2 text--secondary mb-4">
            Compare every nearby service provider, including star ratings, then book with the provider you like.
          </p>
          <div class="equal-card__actions">
            <v-btn depressed color="primary" class="text-none font-weight-bold btn-amber">Browse services</v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-card class="mt-6 pa-5 rounded-xl" outlined>
      <div class="d-flex align-center flex-wrap">
        <div class="mr-4">
          <div class="font-weight-bold">Your orders &amp; bookings</div>
          <p class="text-caption text--secondary mb-0">Track history, open a provider again, or pay a pending checkout.</p>
        </div>
        <v-spacer />
        <v-btn text color="primary" class="text-none font-weight-bold" :to="{ name: 'client-history' }">
          Open history
        </v-btn>
      </div>
    </v-card>
  </v-container>
</template>

<script>
import { getClientCheckoutPrefill, logout } from '@/services/auth'

export default {
  name: 'ClientHubView',
  computed: {
    greeting() {
      const { displayName } = getClientCheckoutPrefill()
      return displayName ? `Hi ${displayName}` : 'Find a provider'
    }
  },
  methods: {
    go(kind) {
      this.$router.push({ path: '/discover', query: { kind } }).catch(() => {})
    },
    signOut() {
      logout()
      this.$router.replace({ name: 'client-login' }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.hub-card {
  cursor: pointer;
  border: 1px solid rgba(15, 23, 42, 0.08);
}
.hub-card:hover {
  border-color: rgba(194, 65, 12, 0.45);
}
</style>
