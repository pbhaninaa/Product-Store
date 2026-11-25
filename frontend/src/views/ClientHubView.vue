<template>
  <div>
    <page-hero
      kicker="Customer hub"
      kicker-icon="home"
      :title="greeting"
      lead="Choose items sold or services, then browse every nearby provider (ratings first) and pick the one you like."
    />

    <v-container class="admin-body pb-12 px-3 px-sm-4">
      <v-row dense class="admin-card-row mb-4">
        <v-col v-for="tile in tiles" :key="tile.key" cols="12" sm="6">
          <v-card
            :to="tile.to"
            class="admin-link-tile admin-eq-card pa-4 pa-sm-5 rounded-xl"
            elevation="2"
            hover
            ripple
          >
            <div class="admin-link-tile__inner">
              <v-avatar :color="tile.color" size="44" class="mr-3 flex-shrink-0">
                <v-icon dark size="22" aria-hidden="true">{{ tile.icon }}</v-icon>
              </v-avatar>
              <div class="admin-link-tile__text">
                <h2 class="text-subtitle-1 font-weight-bold mb-1">{{ tile.title }}</h2>
                <p class="text-body-2 text--secondary mb-3">{{ tile.blurb }}</p>
                <span class="admin-link-tile__open text-caption font-weight-bold primary--text">
                  {{ tile.action }}
                  <v-icon x-small color="primary" class="ml-1" aria-hidden="true">chevron_right</v-icon>
                </span>
              </div>
            </div>
          </v-card>
        </v-col>
      </v-row>

      <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
        <div class="card-label mb-3">Your activity</div>
        <div class="d-flex flex-column flex-sm-row align-start align-sm-center">
          <v-avatar color="blue-grey lighten-5" size="44" class="mr-sm-4 mb-3 mb-sm-0 flex-shrink-0">
            <v-icon color="blue-grey darken-1" aria-hidden="true">receipt_long</v-icon>
          </v-avatar>
          <div class="flex-grow-1 min-width-0">
            <div class="text-subtitle-1 font-weight-bold">Your orders &amp; bookings</div>
            <p class="text-body-2 text--secondary mb-0">
              Track history, open a provider again, or pay a pending checkout.
            </p>
          </div>
          <v-btn
            depressed
            color="primary"
            class="mt-3 mt-sm-0 ml-sm-4 text-none font-weight-bold flex-shrink-0"
            :to="{ name: 'client-history' }"
          >
            Open history
          </v-btn>
        </div>
      </v-card>
    </v-container>
  </div>
</template>

<script>
import PageHero from '@/components/layout/PageHero.vue'
import { getClientCheckoutPrefill } from '@/services/auth'

export default {
  name: 'ClientHubView',
  components: { PageHero },
  computed: {
    greeting() {
      const { displayName } = getClientCheckoutPrefill()
      return displayName ? `Hi ${displayName}` : 'Find a provider'
    },
    tiles() {
      return [
        {
          key: 'shop',
          title: 'Items sold',
          blurb: 'See all nearby providers with ratings and prices, then choose the one you prefer.',
          action: 'Browse items',
          icon: 'storefront',
          color: 'primary',
          to: { path: '/discover', query: { kind: 'shop' } }
        },
        {
          key: 'salon',
          title: 'Services',
          blurb: 'Compare every nearby service provider, including star ratings, then book with the provider you like.',
          action: 'Browse services',
          icon: 'event_available',
          color: 'accent',
          to: { path: '/discover', query: { kind: 'salon' } }
        }
      ]
    }
  }
}
</script>
