<template>
  <div class="mb-4">
    <v-select
      :value="value"
      :items="items"
      item-text="text"
      item-value="value"
      label="Search radius"
      outlined
      hide-details="auto"
      hint="How far from your location we look for providers"
      persistent-hint
      :rules="radiusRules"
      :disabled="disabled"
      @change="$emit('input', $event)"
    />
    <v-alert v-if="showExtendHint && canExtend" type="info" dense outlined class="mt-3 mb-0 rounded-lg">
      No {{ kindLabel }} found within {{ value }} km.
      <v-btn text small color="primary" class="text-none mt-2" :disabled="disabled" @click="extendRadius">
        Search {{ nextLabel }}
      </v-btn>
    </v-alert>
    <p v-else-if="!showExtendHint" class="text-caption text--secondary mt-1 mb-0">
      Showing {{ kindLabel }} within {{ value }} km of your location, best-rated first.
    </p>
  </div>
</template>

<script>
import {
  canExtendClientSearchRadius,
  clientSearchRadiusDropdownItems,
  formatClientSearchRadiusLabel,
  nextClientSearchRadiusKm,
  CLIENT_SEARCH_RADIUS_OPTIONS_KM,
  MAX_CLIENT_SEARCH_RADIUS_KM
} from '@/utils/clientSearchRadius'
import { rules } from '@/utils/validation'

/** Backend NearbyMerchantSearchService caps the radius at 200 km; the smallest option is the floor. */
const radiusRules = [
  rules.required('Search radius'),
  rules.number({ min: CLIENT_SEARCH_RADIUS_OPTIONS_KM[0], max: MAX_CLIENT_SEARCH_RADIUS_KM, label: 'Search radius' })
]

export default {
  name: 'ClientSearchRadiusControl',
  props: {
    value: { type: Number, required: true },
    disabled: { type: Boolean, default: false },
    showExtendHint: { type: Boolean, default: false },
    kind: { type: String, default: 'shop' }
  },
  data() {
    return { radiusRules }
  },
  computed: {
    items() {
      return clientSearchRadiusDropdownItems()
    },
    canExtend() {
      return canExtendClientSearchRadius(this.value)
    },
    nextLabel() {
      const n = nextClientSearchRadiusKm(this.value)
      return n == null ? '' : formatClientSearchRadiusLabel(n)
    },
    kindLabel() {
      return 'providers'
    }
  },
  methods: {
    extendRadius() {
      const n = nextClientSearchRadiusKm(this.value)
      if (n != null) this.$emit('input', n)
    }
  }
}
</script>
